package com.pesaflow.app.data.ml

/**
 * Multinomial Naive Bayes text classifier with TF-IDF-weighted
 * unigram + bigram features, temperature-calibrated softmax, and
 * serializable weights (export path for future TFLite swap).
 *
 * v2 upgrades over v1: bigram features ("paid to", "sent to", "worth of")
 * disambiguate paybill vs till vs P2P; temperature scaling tempers the
 * overconfident posteriors NB is famous for; [serialize]/[deserialize]
 * round-trip weights so a Python-trained twin can ship weights later.
 *
 * Genuinely trained: [train] builds log-priors/log-likelihoods from
 * labelled examples. Evaluation lives in ModelEvaluator + MlMetricsTest.
 * JVM-safe: pure Kotlin, no Android imports.
 */
class TfIdfClassifier(
    private val alpha: Double = 1.0,
    private val useBigrams: Boolean = true,
    var temperature: Double = 1.6
) {
    data class Prediction(val label: String, val probability: Double)
    data class Labeled(val text: String, val label: String, val synthetic: Boolean = false)

    private var labels: List<String> = emptyList()
    private var logPrior: Map<String, Double> = emptyMap()
    private var logLikelihood: Map<String, Map<String, Double>> = emptyMap()
    private var idf: Map<String, Double> = emptyMap()
    private var trained = false

    fun tokenize(text: String): List<String> {
        val uni = text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
        if (!useBigrams || uni.size < 2) return uni
        val bi = uni.zipWithNext { a, b -> "${a}_$b" }
        return uni + bi
    }

    fun train(examples: List<Labeled>) {
        require(examples.isNotEmpty()) { "empty training dataset" }
        val docs = examples.map { tokenize(it.text) }
        val df = mutableMapOf<String, Int>()
        docs.forEach { toks -> toks.toSet().forEach { t -> df[t] = (df[t] ?: 0) + 1 } }
        val n = docs.size.toDouble()
        idf = df.mapValues { (_, d) -> kotlin.math.ln(n / d) + 1.0 }

        labels = examples.map { it.label }.distinct()
        val labelCounts = examples.groupingBy { it.label }.eachCount()
        logPrior = labelCounts.mapValues { (_, c) -> kotlin.math.ln(c / n) }

        val vocab = df.keys
        val like = mutableMapOf<String, MutableMap<String, Double>>()
        for (label in labels) {
            val tf = mutableMapOf<String, Double>()
            examples.filter { it.label == label }.forEach { ex ->
                tokenize(ex.text).forEach { t -> tf[t] = (tf[t] ?: 0.0) + (idf[t] ?: 1.0) }
            }
            val total = tf.values.sum() + alpha * vocab.size
            val row = mutableMapOf<String, Double>()
            for (v in vocab) {
                row[v] = kotlin.math.ln(((tf[v] ?: 0.0) + alpha) / total)
            }
            like[label] = row
        }
        logLikelihood = like
        trained = true
    }

    /** Raw log-scores before softmax; exposed for calibration + inspection. */
    fun logScores(text: String): Map<String, Double> {
        check(trained) { "classifier not trained" }
        val toks = tokenize(text).toSet()
        return labels.associateWith { label ->
            var s = logPrior[label] ?: Double.NEGATIVE_INFINITY
            val row = logLikelihood[label] ?: emptyMap()
            for (t in toks) {
                s += row[t] ?: kotlin.math.ln(alpha / (alpha * (row.size.coerceAtLeast(1))))
            }
            s
        }
    }

    fun predict(text: String, topK: Int = 3): List<Prediction> {
        val scores = logScores(text)
        // Temperature-scaled softmax: T>1 softens overconfident NB posteriors.
        val max = scores.values.maxOrNull() ?: return emptyList()
        val exps = scores.mapValues { (_, s) -> kotlin.math.exp((s - max) / temperature) }
        val denom = exps.values.sum()
        return exps.map { (l, e) -> Prediction(l, e / denom) }
            .sortedByDescending { it.probability }
            .take(topK)
    }

    /** Top log-odds tokens per label — inspectability for the diagnostics screen. */
    fun topFeatures(label: String, k: Int = 8): List<Pair<String, Double>> {
        val row = logLikelihood[label] ?: return emptyList()
        val others = labels.filter { it != label }
            .mapNotNull { logLikelihood[it] }
        return row.map { (tok, ll) ->
            val maxOther = others.map { it[tok] ?: Double.NEGATIVE_INFINITY }.maxOrNull()
                ?: Double.NEGATIVE_INFINITY
            tok to (ll - maxOther)
        }.sortedByDescending { it.second }.take(k)
    }

    /** Compact text serialization: header + priors + likelihood rows. */
    fun serialize(): String {
        check(trained) { "classifier not trained" }
        val sb = StringBuilder()
        sb.appendLine("NBv2|alpha=$alpha|temp=$temperature")
        sb.appendLine("LABELS:" + labels.joinToString(","))
        sb.appendLine("PRIOR:" + labels.joinToString(",") { "$it=${logPrior[it]}" })
        sb.appendLine("IDF:" + idf.entries.joinToString(",") { "${esc(it.key)}=${it.value}" })
        for (label in labels) {
            val row = logLikelihood[label] ?: continue
            sb.appendLine("ROW:$label:" + row.entries.joinToString(",") { "${esc(it.key)}=${it.value}" })
        }
        return sb.toString()
    }

    fun deserialize(blob: String) {
        val lines = blob.lines().filter { it.isNotBlank() }
        require(lines.first().startsWith("NBv2")) { "bad header" }
        val parsedLabels = lines.first { it.startsWith("LABELS:") }.removePrefix("LABELS:").split(",")
        val prior = mutableMapOf<String, Double>()
        lines.first { it.startsWith("PRIOR:") }.removePrefix("PRIOR:").split(",").forEach {
            val (k, v) = it.split("=").let { p -> p[0] to p[1].toDouble() }
            prior[k] = v
        }
        val parsedIdf = mutableMapOf<String, Double>()
        lines.first { it.startsWith("IDF:") }.removePrefix("IDF:").split(",").forEach {
            val idx = it.lastIndexOf("=")
            parsedIdf[unesc(it.substring(0, idx))] = it.substring(idx + 1).toDouble()
        }
        val like = mutableMapOf<String, Map<String, Double>>()
        lines.filter { it.startsWith("ROW:") }.forEach { line ->
            val label = line.removePrefix("ROW:").substringBefore(":")
            val body = line.substringAfter(":$label:")
            val row = mutableMapOf<String, Double>()
            body.split(",").forEach {
                val idx = it.lastIndexOf("=")
                row[unesc(it.substring(0, idx))] = it.substring(idx + 1).toDouble()
            }
            like[label] = row
        }
        labels = parsedLabels
        logPrior = prior
        idf = parsedIdf
        logLikelihood = like
        trained = true
    }

    private fun esc(s: String) = s.replace("%", "%25").replace("=", "%3D").replace(",", "%2C").replace(":", "%3A")
    private fun unesc(s: String) = s.replace("%3A", ":").replace("%2C", ",").replace("%3D", "=").replace("%25", "%")

    fun isTrained(): Boolean = trained
    fun vocabSize(): Int = idf.size
}
