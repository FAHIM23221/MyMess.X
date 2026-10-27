package com.messerp.app

import com.messerp.app.data.Dates
import com.messerp.app.data.Money
import com.messerp.app.data.ReceiptPdf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun parsesTakaIntoPoisha() {
        assertEquals(123400L, Money.parseTakaToMinor("1234"))
        assertEquals(123450L, Money.parseTakaToMinor("1234.5"))
        assertEquals(123405L, Money.parseTakaToMinor("1234.05"))
        assertEquals(123400L, Money.parseTakaToMinor("১২৩৪"))
        assertEquals(123456L, Money.parseTakaToMinor("1,234.56"))
    }

    @Test fun rejectsInvalidAmounts() {
        assertNull(Money.parseTakaToMinor("0"))
        assertNull(Money.parseTakaToMinor("-5"))
        assertNull(Money.parseTakaToMinor("12.345"))
        assertNull(Money.parseTakaToMinor("abc"))
        assertNull(Money.parseTakaToMinor("1000001"))
        assertNull(Money.parseTakaToMinor(""))
    }

    @Test fun formatsMoney() {
        assertEquals("৳ ১,২৩৪.৫৬", Money.format(123456L))
        assertEquals("৳ ০.০৫", Money.format(5L))
        assertEquals("৳ ১,০০০,০০০.০০", Money.format(100_000_000L))
    }

    @Test fun formatsRate() {
        assertEquals("৳ ৮৩.৩৩", Money.formatRate("8333.4167"))
        assertEquals("—", Money.formatRate(null))
    }

    @Test fun datesWork() {
        assertEquals("2026-11-01", Dates.addDays("2026-10-31", 1))
        assertEquals("2026-10-31", Dates.endOfMonth("2026-10-05"))
        assertEquals("2024-02-29", Dates.endOfMonth("2024-02-10"))
    }

    @Test fun monthMathWorks() {
        assertEquals("2026-10-01", Dates.monthStart("2026-10-25"))
        assertEquals("2026-12-01", Dates.addMonths("2026-11-01", 1))
        assertEquals("2025-12-01", Dates.addMonths("2026-01-01", -1))
    }

    @Test fun receiptTimeIsLocalAndBengali() {
        assertEquals("২৫ অক্টোবর ২০২৬, রাত ৯:৩৬", ReceiptPdf.formatLocal("2026-10-25 21:36"))
        assertEquals("২৫ অক্টোবর ২০২৬, সকাল ৮:০৫", ReceiptPdf.formatLocal("2026-10-25 08:05"))
        assertEquals("২৫ অক্টোবর ২০২৬, দুপুর ১২:১০", ReceiptPdf.formatLocal("2026-10-25 12:10"))
        assertEquals("২৫ অক্টোবর ২০২৬, রাত ১২:০৫", ReceiptPdf.formatLocal("2026-10-25 00:05"))
        assertEquals("১ নভেম্বর ২০২৬", Dates.bnDate("2026-11-01"))
        assertEquals("অক্টোবর ২০২৬", Dates.bnMonth("2026-10-01"))
    }
}
