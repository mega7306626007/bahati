package com.pesaflow.app.data.online

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// Encrypted backup envelope. Threat model: any storage holding the file
// (including a future server) is untrusted. The payload is AES-256-GCM
// sealed with a PBKDF2 key stretched from the user's passphrase — the
// passphrase NEVER leaves the device, so a breach leaks only opaque blobs.
// Wrong passphrase fails loudly (GCM auth tag). Borrowed from PesaFlow-online.
@Serializable
data class BackupEnvelope(
    val version: Int = 1,
    val createdAtMs: Long,
    val saltB64: String,
    val ivB64: String,
    val cipherB64: String
)

object BackupCrypto {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private val rng = SecureRandom()

    private fun b64e(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)

    private fun b64d(s: String): ByteArray =
        java.util.Base64.getDecoder().decode(s)

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun encrypt(plainJson: String, passphrase: String, createdAtMs: Long = System.currentTimeMillis()): BackupEnvelope {
        require(passphrase.length >= 8) { "Passphrase must be at least 8 characters" }
        val salt = ByteArray(16).also { rng.nextBytes(it) }
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val sealed = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        return BackupEnvelope(
            createdAtMs = createdAtMs,
            saltB64 = b64e(salt),
            ivB64 = b64e(iv),
            cipherB64 = b64e(sealed)
        )
    }

    // Throws (AEADBadTagException and friends) on wrong passphrase or tampering.
    fun decrypt(env: BackupEnvelope, passphrase: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                deriveKey(passphrase, b64d(env.saltB64)),
                GCMParameterSpec(GCM_TAG_BITS, b64d(env.ivB64))
            )
        }
        return cipher.doFinal(b64d(env.cipherB64)).toString(Charsets.UTF_8)
    }

    fun toJson(env: BackupEnvelope): String = Json.encodeToString(env)
    fun fromJson(json: String): BackupEnvelope = Json.decodeFromString(json)
}

interface BackupBackend {
    suspend fun upload(env: BackupEnvelope)
    suspend fun download(): BackupEnvelope?
}

// On-device encrypted file backend: passphrase-protected backup that lives
// next to the app's files. Swap for HTTPS when a server lands — the envelope
// format does not change.
class FileBackupBackend(private val dir: java.io.File) : BackupBackend {
    private fun file() = java.io.File(dir, "pesaflow_backup.json")
    override suspend fun upload(env: BackupEnvelope) {
        dir.mkdirs()
        file().writeText(BackupCrypto.toJson(env))
    }
    override suspend fun download(): BackupEnvelope? {
        val f = file()
        return if (f.exists()) BackupCrypto.fromJson(f.readText()) else null
    }
}
