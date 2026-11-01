package com.messerp.app.data

import org.json.JSONArray
import org.json.JSONObject

data class MethodAmount(val method: String, val amountMinor: Long)
data class CategoryAmount(val category: String, val amountMinor: Long)
data class HeldRow(val name: String, val receivedMinor: Long, val spentMinor: Long, val netMinor: Long)
data class TrendPoint(val month: String, val collectedMinor: Long, val expenseMinor: Long)
data class RecentExpense(val date: String, val category: String, val description: String, val amountMinor: Long, val byName: String?)

data class Transparency(
    val book: String,
    val monthStart: String,
    val through: String,
    val updatedLocal: String,
    val openingMinor: Long,
    val collectedMinor: Long,
    val expenseMinor: Long,
    val closingMinor: Long,
    val cashMinor: Long,
    val digitalMinor: Long,
    val byMethod: List<MethodAmount>,
    val byCategory: List<CategoryAmount>,
    val totalDueMinor: Long,
    val totalAdvanceMinor: Long,
    val finesMinor: Long,
    val held: List<HeldRow>,
    val trend: List<TrendPoint>,
    val recent: List<RecentExpense>
)

data class CashEntry(
    val atLocal: String,
    val ev: String,
    val descr: String,
    val category: String,
    val ref: String?,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val balanceMinor: Long,
    val byName: String?,
    val memberName: String?
)

data class CashBook(
    val book: String,
    val monthStart: String,
    val updatedLocal: String,
    val openingMinor: Long,
    val closingMinor: Long,
    val entries: List<CashEntry>
)

/** Read-only views. All totals are calculated by the server from the append-only records. */
class TransparencyRepository(private val auth: AuthRepository) {

    private suspend fun call(path: String, body: String): HttpResult =
        SupabaseHttp.request("POST", path, body, auth.accessToken())

    private fun body(book: String, month: String?): String {
        val o = JSONObject().put("p_book", book)
        if (month != null) o.put("p_month", month)
        return o.toString()
    }

    suspend fun transparency(book: String, month: String?): Transparency {
        val res = call("/rest/v1/rpc/get_transparency", body(book, month))
        if (res.code !in 200..299) throw AuthException("স্বচ্ছতার তথ্য আনা যায়নি।")
        val o = JSONObject(res.body)
        fun <T> list(key: String, f: (JSONObject) -> T): List<T> {
            val a: JSONArray = o.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).map { f(a.getJSONObject(it)) }
        }
        return Transparency(
            book = o.getString("book"),
            monthStart = o.getString("month_start"),
            through = o.getString("through"),
            updatedLocal = o.getString("updated_local"),
            openingMinor = o.getLong("opening_minor"),
            collectedMinor = o.getLong("collected_minor"),
            expenseMinor = o.getLong("expense_minor"),
            closingMinor = o.getLong("closing_minor"),
            cashMinor = o.getLong("cash_minor"),
            digitalMinor = o.getLong("digital_minor"),
            byMethod = list("by_method") { MethodAmount(it.getString("method"), it.getLong("amount_minor")) },
            byCategory = list("by_category") { CategoryAmount(it.getString("category"), it.getLong("amount_minor")) },
            totalDueMinor = o.getLong("total_due_minor"),
            totalAdvanceMinor = o.getLong("total_advance_minor"),
            finesMinor = o.getLong("fines_minor"),
            held = list("held") { HeldRow(it.getString("name"), it.getLong("received_minor"), it.getLong("spent_minor"), it.getLong("net_minor")) },
            trend = list("trend") { TrendPoint(it.getString("month"), it.getLong("collected_minor"), it.getLong("expense_minor")) },
            recent = list("recent_expenses") {
                RecentExpense(it.getString("date"), it.getString("category"), it.getString("description"), it.getLong("amount_minor"), it.strOrNull("by_name"))
            }
        )
    }

    suspend fun cashBook(book: String, month: String?): CashBook {
        val res = call("/rest/v1/rpc/get_cash_book", body(book, month))
        if (res.code !in 200..299) throw AuthException("ক্যাশ বুক আনা যায়নি।")
        val o = JSONObject(res.body)
        val a = o.getJSONArray("entries")
        return CashBook(
            book = o.getString("book"),
            monthStart = o.getString("month_start"),
            updatedLocal = o.getString("updated_local"),
            openingMinor = o.getLong("opening_minor"),
            closingMinor = o.getLong("closing_minor"),
            entries = (0 until a.length()).map {
                val e = a.getJSONObject(it)
                CashEntry(
                    atLocal = e.getString("at_local"), ev = e.getString("ev"), descr = e.getString("descr"),
                    category = e.getString("category"), ref = e.strOrNull("ref"),
                    incomeMinor = e.getLong("income_minor"), expenseMinor = e.getLong("expense_minor"),
                    balanceMinor = e.getLong("balance_minor"), byName = e.strOrNull("by_name"), memberName = e.strOrNull("member_name")
                )
            }
        )
    }
}
