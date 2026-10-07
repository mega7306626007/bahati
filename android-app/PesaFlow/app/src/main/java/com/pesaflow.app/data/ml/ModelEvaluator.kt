package com.pesaflow.app.data.ml

/**
 * ModelEvaluator — honest metrics for every classifier.
 *
 * Stratified train/test split (deterministic seed), confusion matrix,
 * per-label precision/recall/F1, macro-F1 gate. Used by MlMetricsTest to
 * block deployment of a model that is accurate overall but confuses
 * transfer/expense, income/expense, refund/expense or reversal/ordinary
 * (spec section 19).
 *
 * Pure Kotlin, JVM-safe.
 */
object ModelEvaluator {
    data class Split(
        val train: List<TfIdfClassifier.Labeled>,
        val test: List<TfIdfClassifier.Labeled>
    )

    data class LabelMetrics(
        val label: String,
        val precision: Double,
        val recall: Double,
        val f1: Double,
        val support: Int
    )

    data class Report(
        val accuracy: Double,
        val macroF1: Double,
        val perLabel: List<LabelMetrics>,
        /** confusion[actual][predicted] = count */
        val confusion: Map<String, Map<String, Int>>,
        val testSize: Int,
        val realTestSize: Int
    )

    /** Deterministic stratified split: every label keeps testFraction in test. */
    fun stratifiedSplit(
        examples: List<TfIdfClassifier.Labeled>,
        testFraction: Double = 0.25,
        seed: Long = 42L
    ): Split {
        val train = mutableListOf<TfIdfClassifier.Labeled>()
        val test = mutableListOf<TfIdfClassifier.Labeled>()
        examples.groupBy { it.label }.forEach { (_, rows) ->
            val shuffled = rows.sortedBy { (it.text.hashCode() * 31 + seed).hashCode() }
            val nTest = (rows.size * testFraction).toInt().coerceAtLeast(1).coerceAtMost(rows.size - 1)
            test.addAll(shuffled.take(nTest))
            train.addAll(shuffled.drop(nTest))
        }
        return Split(train, test)
    }

    fun evaluate(
        factory: () -> TfIdfClassifier,
        examples: List<TfIdfClassifier.Labeled>,
        testFraction: Double = 0.25
    ): Report {
        val (train, test) = stratifiedSplit(examples, testFraction)
        val clf = factory()
        clf.train(train)
        val labels = examples.map { it.label }.distinct().sorted()
        val confusion = labels.associateWith { a -> labels.associateWith { 0 }.toMutableMap() }
        var correct = 0
        for (ex in test) {
            val pred = clf.predict(ex.text, topK = 1).firstOrNull()?.label ?: "NONE"
            if (pred == ex.label) correct++
            if (confusion.containsKey(ex.label) && confusion[ex.label]!!.containsKey(pred)) {
                confusion[ex.label]!![pred] = confusion[ex.label]!![pred]!! + 1
            }
        }
        val perLabel = labels.map { l ->
            val tp = confusion[l]?.get(l) ?: 0
            val fp = labels.sumOf { confusion[it]?.get(l) ?: 0 } - tp
            val fn = labels.sumOf { confusion[l]?.get(it) ?: 0 } - tp
            val p = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 0.0
            val r = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 0.0
            val f = if (p + r > 0) 2 * p * r / (p + r) else 0.0
            LabelMetrics(l, p, r, f, labels.sumOf { confusion[l]?.get(it) ?: 0 })
        }
        return Report(
            accuracy = if (test.isNotEmpty()) correct.toDouble() / test.size else 0.0,
            macroF1 = if (perLabel.isNotEmpty()) perLabel.map { it.f1 }.average() else 0.0,
            perLabel = perLabel,
            confusion = confusion,
            testSize = test.size,
            realTestSize = test.count { !it.synthetic }
        )
    }

    /** Worst confused pair — surfaces transfer/expense type mix-ups. */
    fun worstConfusion(report: Report): Triple<String, String, Int>? {
        var best: Triple<String, String, Int>? = null
        report.confusion.forEach { (actual, row) ->
            row.forEach { (pred, count) ->
                if (actual != pred && count > (best?.third ?: 0)) best = Triple(actual, pred, count)
            }
        }
        return best
    }
}
