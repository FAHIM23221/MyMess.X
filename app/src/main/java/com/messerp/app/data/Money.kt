package com.messerp.app.data

import java.math.BigDecimal
import java.math.RoundingMode

/** Money is always stored and calculated as integer poisha (1 taka = 100). BigDecimal is used only for display. */
object Money {
    fun bn(text: String): String =
        text.map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")

    fun bn(n: Long): String = bn(n.toString())

    private fun group(n: Long): String {
        val s = n.toString()
        val sb = StringBuilder()
        for ((i, c) in s.withIndex()) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
            sb.append(c)
        }
        return sb.toString()
    }

    fun format(minor: Long): String {
        val sign = if (minor < 0) "-" else ""
        val abs = if (minor < 0) -minor else minor
        val taka = abs / 100
        val paisa = (abs % 100).toString().padStart(2, '0')
        return "${sign}৳ " + bn(group(taka) + "." + paisa)
    }

    /** [rateMinor] is the server's per-meal rate in poisha, e.g. "8333.4167". */
    fun formatRate(rateMinor: String?): String {
        if (rateMinor == null) return "—"
        val taka = runCatching {
            BigDecimal(rateMinor).divide(BigDecimal(100), 2, RoundingMode.HALF_UP).toPlainString()
        }.getOrNull() ?: return "—"
        return "৳ " + bn(taka)
    }

    /** Parses taka text (Bengali or English digits, up to 2 decimals) into poisha. Returns null if invalid. */
    fun parseTakaToMinor(input: String): Long? {
        val cleaned = input.trim().filter { it != ',' }
            .map { c -> c.digitToIntOrNull()?.let { '0' + it } ?: c }
            .joinToString("")
        if (!Regex("^[0-9]{1,9}(\\.[0-9]{1,2})?$").matches(cleaned)) return null
        val parts = cleaned.split('.')
        val taka = parts[0].toLong()
        val paisa = if (parts.size == 2) parts[1].padEnd(2, '0').toLong() else 0L
        val minor = taka * 100 + paisa
        return if (minor in 1..100_000_000L) minor else null
    }
}
