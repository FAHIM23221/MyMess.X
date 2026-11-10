package com.messerp.app.data

import java.util.Locale

/**
 * Builds spreadsheet-friendly CSV files (UTF-8 with a byte-order mark so Excel shows Bengali correctly).
 * Numbers use English digits and a dot so they can be summed; text typed by people is protected against
 * spreadsheet formula injection by a leading apostrophe.
 */
object ReportCsv {
    fun money(minor: Long): String {
        val a = kotlin.math.abs(minor)
        return String.format(Locale.US, "%s%d.%02d", if (minor < 0) "-" else "", a / 100, a % 100)
    }

    fun kg(grams: Long): String {
        val a = kotlin.math.abs(grams)
        return String.format(Locale.US, "%s%d.%03d", if (grams < 0) "-" else "", a / 1000, a % 1000)
    }

    private fun quote(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    /** Text cell: neutralises values that a spreadsheet could run as a formula. */
    fun text(s: String?): String {
        var v = (s ?: "").replace("\r", " ").replace("\n", " ")
        if (v.isNotEmpty() && v[0] in "=+-@\t") v = "'" + v
        return quote(v)
    }

    private fun build(header: List<String>, rows: List<List<String>>): ByteArray {
        val sb = StringBuilder("\uFEFF")
        sb.append(header.joinToString(",") { quote(it) }).append("\r\n")
        for (r in rows) sb.append(r.joinToString(",")).append("\r\n")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun evLabel(ev: String) = when (ev) {
        "PAYMENT" -> "পেমেন্ট গ্রহণ"
        "PAYMENT_REVERSAL" -> "পেমেন্ট বাতিল"
        "EXPENSE" -> "খরচ"
        "EXPENSE_VOID" -> "খরচ বাতিল"
        else -> ev
    }

    private fun fineLabel(s: String) = when (s) {
        "PAID" -> "পরিশোধিত"
        "PARTIAL" -> "আংশিক"
        "VOIDED" -> "বাতিল"
        else -> "অনাদায়ী"
    }

    fun members(meals: MealSummary, mess: DuesOverview, util: DuesOverview, rice: RiceSummary): ByteArray {
        val messBy = mess.members.associateBy { it.id }
        val utilBy = util.members.associateBy { it.id }
        val riceBy = rice.members.associateBy { it.id }
        val header = listOf(
            "সদস্য কোড", "নাম", "মিল (এ মাস)", "গেস্ট মিল", "মিল খরচ (টাকা)",
            "মেস খাতার বর্তমান ব্যালেন্স (টাকা, ধনাত্মক = বাকি)", "বিদ্যুৎ ও রাঁধুনির বর্তমান ব্যালেন্স (টাকা, ধনাত্মক = বাকি)",
            "চাল প্রয়োজন (কেজি)", "চাল জমা (কেজি)", "চাল বাকি (কেজি)", "চাল বাড়তি (কেজি)"
        )
        val rows = meals.members.map { m ->
            val r = riceBy[m.id]
            listOf(
                text(m.code), text(m.name), m.meals.toString(), m.guestMeals.toString(), money(m.costMinor),
                money(messBy[m.id]?.balanceMinor ?: 0L), money(utilBy[m.id]?.balanceMinor ?: 0L),
                kg(r?.required ?: 0L), kg(r?.submitted ?: 0L), kg(r?.due ?: 0L), kg(r?.extra ?: 0L)
            )
        }
        return build(header, rows)
    }

    fun cashBook(cb: CashBook, bookLabel: String): ByteArray {
        val header = listOf("খাতা", "তারিখ ও সময়", "ধরন", "বিবরণ", "রসিদ", "আয় (টাকা)", "ব্যয় (টাকা)", "চলমান ব্যালেন্স (টাকা)", "রেকর্ডকারী", "সদস্য")
        val rows = mutableListOf<List<String>>()
        rows.add(listOf(text(bookLabel), cb.monthStart, "আগের জের", "", "", "", "", money(cb.openingMinor), "", ""))
        for (e in cb.entries) {
            rows.add(
                listOf(
                    text(bookLabel), e.atLocal, evLabel(e.ev), text(e.descr), text(e.ref),
                    if (e.incomeMinor > 0) money(e.incomeMinor) else "", if (e.expenseMinor > 0) money(e.expenseMinor) else "",
                    money(e.balanceMinor), text(e.byName), text(e.memberName)
                )
            )
        }
        rows.add(listOf(text(bookLabel), cb.monthStart, "মাস শেষের ব্যালেন্স", "", "", "", "", money(cb.closingMinor), "", ""))
        return build(header, rows)
    }

    fun fines(list: List<Fine>): ByteArray {
        val header = listOf("তারিখ", "সদস্য", "কারণ", "মোট (টাকা)", "পরিশোধ (টাকা)", "বাকি (টাকা)", "অবস্থা", "দিয়েছেন")
        val rows = list.map { f ->
            listOf(f.date, text(f.memberName), text(f.reason), money(f.amountMinor), money(f.paidMinor), money(f.remainingMinor), fineLabel(f.status), text(f.recordedBy))
        }
        return build(header, rows)
    }
}
