package com.messerp.app.data

import org.json.JSONArray
import org.json.JSONObject

/** The server owns every balance. Two separate books exist: MESS (rent, meals, ...) and UTILITY (electricity and chef bills). */
class LedgerRepository(private val auth: AuthRepository) {

    private suspend fun call(method: String, path: String, body: String? = null): HttpResult =
        SupabaseHttp.request(method, path, body, auth.accessToken())

    private fun fail(res: HttpResult): Nothing {
        val msg = runCatching { JSONObject(res.body).optString("message") }.getOrDefault("")
        val text = when {
            msg.contains("cannot_self") -> "নিজের নামে এই কাজ করা যায় না। অন্য অ্যাডমিনকে দিয়ে করান।"
            msg.contains("forbidden") -> "এই কাজের অনুমতি আপনার নেই।"
            msg.contains("invalid_amount") -> "পরিমাণ সঠিক নয়।"
            msg.contains("amount_too_small") -> "মোট পরিমাণ সদস্য সংখ্যার চেয়ে কম হতে পারে না।"
            msg.contains("invalid_date") -> "তারিখ সঠিক নয়।"
            msg.contains("invalid_category") -> "এই খাতায় এই বিভাগ চলে না।"
            msg.contains("invalid_value") -> "মান সঠিক নয়।"
            msg.contains("month_not_ended") -> "মাস শেষ হয়নি। মূল অ্যাডমিন কারণ দিয়ে আগে বসাতে পারেন।"
            msg.contains("reason_required") -> "কারণ লিখুন (কমপক্ষে ৩ অক্ষর)।"
            msg.contains("duplicate_request") -> "এই এন্ট্রি আগেই জমা হয়েছে।"
            msg.contains("too_soon") -> "১২ ঘণ্টার মধ্যে একবারই স্মরণ পাঠানো যায়।"
            msg.contains("not_found") -> "রেকর্ড পাওয়া যায়নি বা আগেই বাতিল হয়েছে।"
            else -> "কাজটি সম্পন্ন হয়নি।"
        }
        throw AuthException(text)
    }

    suspend fun ledger(memberId: String?, month: String?, book: String = "MESS"): MemberLedger {
        val body = JSONObject()
        if (memberId != null) body.put("p_member", memberId)
        if (month != null) body.put("p_month", month)
        body.put("p_book", book)
        val res = call("POST", "/rest/v1/rpc/get_member_ledger", body.toString())
        if (res.code !in 200..299) fail(res)
        return parseLedger(JSONObject(res.body))
    }

    suspend fun dues(book: String = "MESS"): DuesOverview {
        val res = call("POST", "/rest/v1/rpc/get_dues_overview", JSONObject().put("p_book", book).toString())
        if (res.code !in 200..299) fail(res)
        return parseDues(JSONObject(res.body))
    }

    suspend fun listPayments(book: String = "MESS"): List<PaymentRow> {
        val res = call("GET", "/rest/v1/payments?select=*&book=eq.$book&order=created_at.desc&limit=50")
        if (res.code !in 200..299) fail(res)
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map { parsePayment(arr.getJSONObject(it)) }
    }

    /** Returns (paymentId, receiptNo). */
    suspend fun recordPayment(
        member: String, amountMinor: Long, method: String, date: String,
        note: String, txnRef: String, requestId: String, book: String = "MESS"
    ): Pair<String, String> {
        val body = JSONObject()
            .put("p_member", member).put("p_amount_minor", amountMinor).put("p_method", method)
            .put("p_date", date).put("p_note", note.trim()).put("p_txn_ref", txnRef.trim())
            .put("p_request_id", requestId).put("p_book", book).toString()
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

    suspend fun addCharge(
        member: String, month: String, category: String, amountMinor: Long,
        description: String, requestId: String, book: String = "MESS"
    ) {
        val body = JSONObject()
            .put("p_member", member).put("p_month", month).put("p_category", category)
            .put("p_amount_minor", amountMinor).put("p_description", description.trim())
            .put("p_request_id", requestId).put("p_book", book).toString()
        val res = call("POST", "/rest/v1/rpc/add_charge", body)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun splitCharge(
        month: String, category: String, totalMinor: Long, description: String,
        requestId: String, book: String = "MESS"
    ): Int {
        val body = JSONObject()
            .put("p_month", month).put("p_category", category).put("p_total_minor", totalMinor)
            .put("p_description", description.trim()).put("p_request_id", requestId).put("p_book", book).toString()
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

    suspend fun sendDueReminders(book: String): Int {
        val res = call("POST", "/rest/v1/rpc/send_due_reminders", JSONObject().put("p_book", book).toString())
        if (res.code !in 200..299) fail(res)
        return res.body.trim().toIntOrNull() ?: 0
    }

    suspend fun receipt(paymentId: String): Receipt {
        val res = call("POST", "/rest/v1/rpc/get_receipt", JSONObject().put("p_payment_id", paymentId).toString())
        if (res.code !in 200..299) fail(res)
        return parseReceipt(JSONObject(res.body))
    }
}
