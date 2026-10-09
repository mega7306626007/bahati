package com.pesaflow.app.data.finance

import kotlin.math.roundToLong

// Canonical money: integer minor units end Double drift in business logic.
// KSh 150.90 = 15090. Room columns stay Double for now — convert at the
// repository boundary (Phase 2); the engine below never touches raw Doubles.
@JvmInline
value class Money(val minorUnits: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(minorUnits + other.minorUnits)
    operator fun minus(other: Money) = Money(minorUnits - other.minorUnits)
    operator fun times(factor: Double) = Money((minorUnits * factor).roundToLong())
    operator fun div(factor: Double) = Money((minorUnits / factor).roundToLong())
    operator fun unaryMinus() = Money(-minorUnits)
    override fun compareTo(other: Money) = minorUnits.compareTo(other.minorUnits)
    fun toDouble(): Double = minorUnits / 100.0
    fun isNegative() = minorUnits < 0L
    fun isZero() = minorUnits == 0L
    fun coerceAtLeast(min: Money) = if (this < min) min else this

    companion object {
        val ZERO = Money(0)
        fun of(amount: Double) = Money((amount * 100).roundToLong())
        fun ofKSh(shillings: Long) = Money(shillings * 100)
    }
}

// One global formatting layer (§43). Exact values always back the display.
object MoneyFormatter {
    // Exact: KSh 90,000 · KSh 150.90 · -KSh 10,000 (sign only where math needs it).
    fun exact(m: Money): String {
        val sign = if (m.isNegative()) "-" else ""
        val abs = kotlin.math.abs(m.minorUnits)
        val whole = abs / 100L
        val cents = (abs % 100L).toInt()
        return if (cents == 0) "$sign${"KSh"} ${grouped(whole)}"
        else "$sign${"KSh"} ${grouped(whole)}.${cents.toString().padStart(2, '0')}"
    }

    // Compact: 900 → KSh 900 · 9500 → KSh 9.5k · 90000 → KSh 90k · 1250000 → KSh 1.25M.
    fun compact(m: Money): String {
        val sign = if (m.isNegative()) "-" else ""
        val v = kotlin.math.abs(m.toDouble())
        val body = when {
            v >= 1_000_000 -> trimNum(v / 1_000_000, 2) + "M"
            v >= 1_000 -> trimNum(v / 1_000, 1) + "k"
            v == kotlin.math.floor(v) -> v.toLong().toString()
            else -> "%.2f".format(v)
        }
        return "${sign}KSh $body"
    }

    // Semantic negative: -10000 → "KSh 10k short". Positive values stay compact.
    fun shortfall(m: Money, noun: String = "short"): String {
        if (!m.isNegative()) return compact(m)
        return compact(-m) + " $noun"
    }

    private fun trimNum(v: Double, maxDp: Int): String {
        val s = "%.${maxDp}f".format(v)
        return s.trimEnd('0').trimEnd('.')
    }

    private fun grouped(value: Long): String = "%,d".format(value)
}
