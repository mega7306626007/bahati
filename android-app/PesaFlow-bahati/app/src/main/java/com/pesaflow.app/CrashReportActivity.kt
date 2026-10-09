package com.pesaflow.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * Last-resort crash screen: plain SDK views only — no Compose, no Room, no
 * ViewModel — so it can render even when the main UI stack is what died.
 * Shows the first lines of the trace for a screenshot + one-tap copy.
 */
class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra(EXTRA_TRACE)
            ?: readTraceFile()
            ?: "No trace captured."
        val firstLines = trace.lines().take(40).joinToString("\n")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xFF060D2B.toInt())
            setPadding(48, 48, 48, 48)
        }
        val title = TextView(this).apply {
            text = "PesaPlanner stopped 🛑"
            setTextColor(-0x1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val hint = TextView(this).apply {
            text = "Screenshot this screen and send it — it names the exact crash."
            setTextColor(0xFFA9B4D4.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        val body = TextView(this).apply {
            text = firstLines
            setTextColor(0xFFFFD95A.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
        }
        val scroll = ScrollView(this).apply { addView(body) }
        val copy = Button(this).apply {
            text = "Copy full trace"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("pesaflow-crash", trace))
                text = "Copied ✓ — paste it to support"
            }
        }
        root.addView(title)
        root.addView(hint)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(copy)
        setContentView(root)
    }

    private fun readTraceFile(): String? {
        return try {
            val f = File(filesDir, CRASH_FILE)
            if (f.exists()) f.readText().take(20000).ifBlank { null } else null
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val EXTRA_TRACE = "crash_trace"
        const val CRASH_FILE = "crash.log"

        /** Installs first in MainActivity.onCreate — catches everything after. */
        fun installPreviousHandler(context: Context) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                try {
                    val trace = android.util.Log.getStackTraceString(error)
                    try {
                        File(context.filesDir, CRASH_FILE).writeText(
                            "${java.util.Date()}\nthread=${thread.name}\n$trace".take(100000)
                        )
                    } catch (_: Exception) {
                    }
                    val intent = android.content.Intent(context, CrashReportActivity::class.java).apply {
                        putExtra(EXTRA_TRACE, trace.take(20000))
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    context.startActivity(intent)
                    previous?.uncaughtException(thread, error)
                    if (previous == null) {
                        android.os.Process.killProcess(android.os.Process.myPid())
                        kotlin.system.exitProcess(10)
                    }
                } catch (_: Exception) {
                    try {
                        previous?.uncaughtException(thread, error)
                    } catch (_: Exception) {
                    }
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            }
        }
    }
}
