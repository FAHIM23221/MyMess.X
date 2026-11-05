package com.messerp.app.data

import org.json.JSONArray
import org.json.JSONObject

object Weight {
    /** Parses kilograms (up to 3 decimals) into whole grams. Returns null when invalid, zero or too large. */
    fun parseKgToGrams(input: String, allowNegative: Boolean = false): Long? {
        val t = input.trim()
        val neg = allowNegative && t.startsWith("-")
        val body = if (neg) t.substring(1) else t
        val cleaned = body.map { c -> c.digitToIntOrNull()?.let { '0' + it } ?: c }.joinToString("")
        if (!Regex("^[0-9]{1,6}(\\.[0-9]{1,3})?$").matches(cleaned)) return null
        val parts = cleaned.split('.')
        val grams = parts[0].toLong() * 1000 + (if (parts.size == 2) parts[1].padEnd(3, '0').toLong() else 0L)
        val v = if (neg) -grams else grams
        return if (v != 0L && kotlin.math.abs(v) <= 1_000_000L) v else null
    }

    fun format(grams: Long): String {
        val sign = if (grams < 0) "-" else ""
        val a = kotlin.math.abs(grams)
        val whole = a / 1000
        val frac = (a % 1000).toString().padStart(3, '0').trimEnd('0')
        val text = if (frac.isEmpty()) whole.toString() else whole.toString() + "." + frac
        return sign + Money.bn(text) + " কেজি"
    }

    fun plain(grams: Long): String {
        if (grams <= 0L) return ""
        val whole = grams / 1000
        val frac = (grams % 1000).toString().padStart(3, '0').trimEnd('0')
        return if (frac.isEmpty()) whole.toString() else whole.toString() + "." + frac
    }
}

data class RiceMember(val id: String, val name: String, val code: String, val required: Long, val submitted: Long, val due: Long, val extra: Long)

data class RiceSummary(
    val monthStart: String,
    val defaultRequired: Long,
    val totalRequired: Long,
    val totalSubmitted: Long,
    val totalDue: Long,
    val totalExtra: Long,
    val members: List<RiceMember>
)

data class RiceEntry(val id: String, val kind: String, val grams: Long, val date: String, val note: String?, val reversed: Boolean, val byName: String?)

data class MemberRice(val monthStart: String, val required: Long, val submitted: Long, val entries: List<RiceEntry>)

data class Fine(
    val id: String,
    val memberId: String,
    val memberName: String,
    val amountMinor: Long,
    val reason: String,
    val date: String,
    val paidMinor: Long,
    val remainingMinor: Long,
    val recordedBy: String?,
    val status: String
)

/** Rice is counted in whole grams; fines use the same ledger and receipts as other money. The server enforces every rule. */
class RiceFineRepository(private val auth: AuthRepository) {

    private suspend fun call(path: String, body: String = "{}"): HttpResult =
        SupabaseHttp.request("POST", path, body, auth.accessToken())

    private fun fail(res: HttpResult): Nothing {
        val msg = runCatching { JSONObject(res.body).optString("message") }.getOrDefault("")
        val text = when {
            msg.contains("cannot_self") -> "নিজের নামে এই কাজ করা যায় না। অন্য অ্যাডমিন বা ম্যানেজারকে দিয়ে করান।"
            msg.contains("forbidden") -> "এই কাজের অনুমতি আপনার নেই।"
            msg.contains("invalid_amount") -> "পরিমাণ সঠিক নয় (জরিমানার ক্ষেত্রে বাকির চেয়ে বেশি দেওয়া যায় না)।"
            msg.contains("invalid_value") -> "মান সঠিক নয়।"
            msg.contains("invalid_date") -> "তারিখ সঠিক নয় (আজ থেকে সর্বোচ্চ ৬০ দিন আগে পর্যন্ত)।"
            msg.contains("reason_required") -> "কারণ লিখুন (কমপক্ষে ৩ অক্ষর)।"
            msg.contains("fine_has_payments") -> "এই জরিমানার পেমেন্ট আছে। আগে পেমেন্ট বাতিল করুন।"
            msg.contains("fine_voided") -> "এই জরিমানা বাতিল হয়ে গেছে।"
            msg.contains("duplicate_request") -> "এই এন্ট্রি আগেই জমা হয়েছে।"
            msg.contains("not_found") -> "রেকর্ড পাওয়া যায়নি বা আগেই বাতিল হয়েছে।"
            else -> "কাজটি সম্পন্ন হয়নি।"
        }
        throw AuthException(text)
    }

    private fun monthBody(month: String?): String = JSONObject().apply { if (month != null) put("p_month", month) }.toString()

    suspend fun riceSummary(month: String?): RiceSummary {
        val res = call("/rest/v1/rpc/get_rice_summary", monthBody(month))
        if (res.code !in 200..299) fail(res)
        val o = JSONObject(res.body)
        val arr = o.getJSONArray("members")
        return RiceSummary(
            monthStart = o.getString("month_start"),
            defaultRequired = o.getLong("default_required_grams"),
            totalRequired = o.getLong("total_required_grams"),
            totalSubmitted = o.getLong("total_submitted_grams"),
            totalDue = o.getLong("total_due_grams"),
            totalExtra = o.getLong("total_extra_grams"),
            members = (0 until arr.length()).map {
                val m = arr.getJSONObject(it)
                RiceMember(m.getString("id"), m.getString("full_name"), m.getString("member_code"),
                    m.getLong("required_grams"), m.getLong("submitted_grams"), m.getLong("due_grams"), m.getLong("extra_grams"))
            }
        )
    }

    suspend fun memberRice(member: String?, month: String?): MemberRice {
        val body = JSONObject().apply {
            if (member != null) put("p_member", member)
            if (month != null) put("p_month", month)
        }.toString()
        val res = call("/rest/v1/rpc/get_member_rice", body)
        if (res.code !in 200..299) fail(res)
        val o = JSONObject(res.body)
        val arr: JSONArray = o.getJSONArray("entries")
        return MemberRice(
            o.getString("month_start"), o.getLong("required_grams"), o.getLong("submitted_grams"),
            (0 until arr.length()).map {
                val e = arr.getJSONObject(it)
                RiceEntry(e.getString("id"), e.getString("kind"), e.getLong("grams"), e.getString("entry_date"),
                    e.strOrNull("note"), e.optBoolean("reversed"), e.strOrNull("by_name"))
            }
        )
    }

    suspend fun record(member: String, grams: Long, date: String, note: String, requestId: String) {
        val body = JSONObject().put("p_member", member).put("p_grams", grams).put("p_date", date)
            .put("p_note", note.trim()).put("p_request_id", requestId).toString()
        val res = call("/rest/v1/rpc/record_rice", body)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun adjust(member: String, month: String, grams: Long, reason: String, requestId: String) {
        val body = JSONObject().put("p_member", member).put("p_month", month).put("p_grams", grams)
            .put("p_reason", reason.trim()).put("p_request_id", requestId).toString()
        val res = call("/rest/v1/rpc/adjust_rice", body)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun reverse(id: String, reason: String) {
        val res = call("/rest/v1/rpc/reverse_rice_entry", JSONObject().put("p_id", id).put("p_reason", reason.trim()).toString())
        if (res.code !in 200..299) fail(res)
    }

    suspend fun setPolicy(grams: Long, reason: String) {
        val res = call("/rest/v1/rpc/set_rice_policy", JSONObject().put("p_grams", grams).put("p_reason", reason.trim()).toString())
        if (res.code !in 200..299) fail(res)
    }

    suspend fun setRequirement(member: String, month: String, grams: Long, reason: String) {
        val body = JSONObject().put("p_member", member).put("p_month", month).put("p_grams", grams).put("p_reason", reason.trim()).toString()
        val res = call("/rest/v1/rpc/set_rice_requirement", body)
        if (res.code !in 200..299) fail(res)
    }

    private fun fines(body: String): List<Fine> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Fine(o.getString("id"), o.getString("member_id"), o.optString("member_name", ""), o.getLong("amount_minor"),
                o.getString("reason"), o.getString("fine_date"), o.getLong("paid_minor"), o.getLong("remaining_minor"),
                o.strOrNull("recorded_by"), o.getString("status"))
        }
    }

    suspend fun myFines(): List<Fine> {
        val res = call("/rest/v1/rpc/get_my_fines")
        if (res.code !in 200..299) fail(res)
        return fines(res.body)
    }

    suspend fun adminFines(): List<Fine> {
        val res = call("/rest/v1/rpc/get_fines_admin")
        if (res.code !in 200..299) fail(res)
        return fines(res.body)
    }

    suspend fun addFine(member: String, amountMinor: Long, reason: String, date: String, requestId: String) {
        val body = JSONObject().put("p_member", member).put("p_amount_minor", amountMinor).put("p_reason", reason.trim())
            .put("p_date", date).put("p_request_id", requestId).toString()
        val res = call("/rest/v1/rpc/add_fine", body)
        if (res.code !in 200..299) fail(res)
    }

    /** Returns (paymentId, receiptNo). */
    suspend fun payFine(fine: String, amountMinor: Long, method: String, date: String, note: String, txn: String, requestId: String): Pair<String, String> {
        val body = JSONObject().put("p_fine", fine).put("p_amount_minor", amountMinor).put("p_method", method).put("p_date", date)
            .put("p_note", note.trim()).put("p_txn_ref", txn.trim()).put("p_request_id", requestId).toString()
        val res = call("/rest/v1/rpc/pay_fine", body)
        if (res.code !in 200..299) fail(res)
        val o = JSONObject(res.body)
        return Pair(o.getString("payment_id"), o.getString("receipt_no"))
    }

    suspend fun voidFine(id: String, reason: String) {
        val res = call("/rest/v1/rpc/void_fine", JSONObject().put("p_fine", id).put("p_reason", reason.trim()).toString())
        if (res.code !in 200..299) fail(res)
    }
}
