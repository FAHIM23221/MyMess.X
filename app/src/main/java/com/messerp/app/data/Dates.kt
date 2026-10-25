package com.messerp.app.data

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Dates are plain "yyyy-MM-dd" strings in the mess time zone (Asia/Dhaka). The server stays the authority. */
object Dates {
    private val DHAKA: TimeZone = TimeZone.getTimeZone("Asia/Dhaka")

    fun format(y: Int, m: Int, d: Int): String = String.format(Locale.US, "%04d-%02d-%02d", y, m, d)

    fun todayDhaka(): String {
        val c = Calendar.getInstance(DHAKA)
        return format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private fun parse(date: String): Calendar {
        val p = date.split("-").map { it.toInt() }
        val c = Calendar.getInstance(DHAKA)
        c.clear()
        c.set(p[0], p[1] - 1, p[2])
        return c
    }

    fun addDays(date: String, days: Int): String {
        val c = parse(date)
        c.add(Calendar.DAY_OF_MONTH, days)
        return format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun endOfMonth(date: String): String {
        val c = parse(date)
        val last = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        return format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, last)
    }

    fun monthStart(date: String): String = date.substring(0, 8) + "01"

    fun addMonths(monthStart: String, n: Int): String {
        val c = parse(monthStart)
        c.add(Calendar.MONTH, n)
        return format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, 1)
    }
}
