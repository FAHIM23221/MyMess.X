package com.messerp.app.data

import org.json.JSONArray
import org.json.JSONObject

/** The server owns every balance. The app never calculates money for display except formatting. */
class LedgerRepository(private val auth: AuthRepository) {

    private suspend fun call(method: String, path: String, body: String? = null): HttpResult =
        SupabaseHttp.request(method, path, body, auth.accessToken())

    private fun fail(res: HttpResult): Nothing {
        val msg = runCatching { JSONObject(res.body).optString("message") }.getOrDefault("")
        val text = when {
            msg.contains("forbidden") -> "এই কাজের অনুমতি আপনার নেই।"
            msg.contains("invalid_amount") -> "পরিমাণ সঠিক নয়।"
            msg.contains("amount_too_small") -> "মোট পরিমাণ সদস্য সংখ্যার চেয়ে কম হতে পারে না।"
            msg.contains("invalid_date") -> "তারিখ সঠিক নয়।"
            msg.contains("invalid_category") -> "বিভাগ সঠিক নয়।"
            msg.contains("invalid_value") -> "মান সঠিক নয়।"
            msg.contains("month_not_ended") -> "মাস শেষ হয়নি। মূল অ্যাডমিন কারণ দিয়ে আগে বসাতে পারেন।"
            msg.contains("reason_required") -> "কারণ লিখুন (কমপক্ষে ৩ অক্ষর)।"
            msg.contains("duplicate_request") -> "এই এন্ট্রি আগেই জমা হয়েছে।"
            msg.contains("not_found") -> "রেকর্ড পাওয়া যায়নি বা আগেই বাতিল হয়েছে।"
            else -> "কাজটি সম্পন্ন হয়নি।"
        }
        throw AuthException(text)
    }

    suspend fun ledger(memberId: String?, month: String?): MemberLedger {
        val body = JSONObject()
        if (memberId != null) body.put("p_member", memberId)
        if (month != null) body.put("p_month", month)
        val res = call("POST", "/rest/v1/rpc/get_member_ledger", body.toString())
        if (res.code !in 200..299) fail(res)
        return parseLedger(JSONObject(res.body))
    }

    suspend fun dues(): DuesOverview {
        val res = call("POST", "/rest/v1/rpc/get_dues_overview", "{}")
        if (res.code !in 200..299) fail(res)
        return parseDues(JSONObject(res.body))
    }

    suspend fun listPayments(): List<PaymentRow> {
        val res = call("GET", "/rest/v1/payments?select=*&order=created_at.desc&limit=50")
        if (res.code !in 200..299) fail(res)
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map { parsePayment(arr.getJSONObject(it)) }
    }

    /** Returns (paymentId, receiptNo). */
    suspend fun recordPayment(
        member: String, amountMinor: Long, method: String, date: String,
        note: String, txnRef: String, requestId: String
    ): Pair<String, String> {
        val body = JSONObject()
            .put("p_member", member).put("p_amount_minor", amountMinor).put("p_method", method)
            .put("p_date", date).put("p_note", note.trim()).put("p_txn_ref", txnRef.trim())
            .put("p_request_id", requestId).toString()
        val res = call("POST", "/rest/v1/rpc/record_payment", body)
        if (res.code !in 200..299) fail(res)
        val o = JSONObject(res.body)
        return Pair(o.getString("payment_id"), o.getString("receipt_no"))
    }

    suspend fun reversePayment(id: String, reason: String) {
        val body = JSONObject().put("p_id", id).put("p_reason", reason.trim()).toString()
        val res = call("POST", "/rest/v1/rpc/reverse_payment", body)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun addCharge(member: String, month: String, category: String, amountMinor: Long, description: String, requestId: String) {
        val body = JSONObject()
            .put("p_member", member).put("p_month", month).put("p_category", category)
            .put("p_amount_minor", amountMinor).put("p_description", description.trim())
            .put("p_request_id", requestId).toString()
        val res = call("POST", "/rest/v1/rpc/add_charge", body)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun splitCharge(month: String, category: String, totalMinor: Long, description: String, requestId: String): Int {
        val body = JSONObject()
            .put("p_month", month).put("p_category", category).put("p_total_minor", totalMinor)
            .put("p_description", description.trim()).put("p_request_id", requestId).toString()
        val res = call("POST", "/rest/v1/rpc/split_charge", body)
        if (res.code !in 200..299) fail(res)
        return res.body.trim().toIntOrNull() ?: 0
    }

    suspend fun postMealCharges(month: String, force: Boolean, reason: String): Int {
        val body = JSONObject().put("p_month", month).put("p_force", force).put("p_reason", reason.trim()).toString()
        val res = call("POST", "/rest/v1/rpc/post_meal_charges", body)
        if (res.code !in 200..299) fail(res)
        return res.body.trim().toIntOrNull() ?: 0
    }

    suspend fun reverseCharge(entryId: String, reason: String) {
        val body = JSONObject().put("p_entry_id", entryId).put("p_reason", reason.trim()).toString()
        val res = call("POST", "/rest/v1/rpc/reverse_charge", body)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun receipt(paymentId: String): Receipt {
        val res = call("POST", "/rest/v1/rpc/get_receipt", JSONObject().put("p_payment_id", paymentId).toString())
        if (res.code !in 200..299) fail(res)
        return parseReceipt(JSONObject(res.body))
    }
}
