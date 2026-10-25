package com.messerp.app.data

import org.json.JSONArray
import org.json.JSONObject

/** All rules and all money maths are enforced by the server. The app only sends requests and shows results. */
class MealRepository(private val auth: AuthRepository) {

    private suspend fun call(method: String, path: String, body: String? = null): HttpResult =
        SupabaseHttp.request(method, path, body, auth.accessToken())

    private fun fail(res: HttpResult): Nothing {
        val msg = runCatching { JSONObject(res.body).optString("message") }.getOrDefault("")
        val text = when {
            msg.contains("forbidden") -> "এই কাজের অনুমতি আপনার নেই।"
            msg.contains("invalid_dates") -> "তারিখ সঠিক নয়। আজ বা ভবিষ্যতের তারিখ দিন (সর্বোচ্চ ৯০ দিন)।"
            msg.contains("invalid_date") -> "তারিখ সঠিক নয় (ভবিষ্যতের তারিখ বা অনেক পুরোনো তারিখ চলবে না)।"
            msg.contains("no_meal_selected") -> "অন্তত একটি বেলা বাছুন।"
            msg.contains("too_many_requests") -> "অনেক বেশি চালু অনুরোধ আছে। আগে কিছু বাতিল করুন।"
            msg.contains("already_past") -> "এই অনুরোধ আর বাতিল করা যাবে না।"
            msg.contains("invalid_amount") -> "পরিমাণ সঠিক নয়।"
            msg.contains("invalid_description") -> "বিবরণ ১ থেকে ২০০ অক্ষরের হতে হবে।"
            msg.contains("invalid_value") -> "মান সঠিক নয়।"
            msg.contains("duplicate_request") -> "এই এন্ট্রি আগেই জমা হয়েছে।"
            msg.contains("reason_required") -> "কারণ লিখুন (কমপক্ষে ৩ অক্ষর)।"
            msg.contains("not_found") -> "রেকর্ড পাওয়া যায়নি।"
            else -> "কাজটি সম্পন্ন হয়নি।"
        }
        throw AuthException(text)
    }

    suspend fun myStatus(): MealStatus {
        val res = call("POST", "/rest/v1/rpc/get_my_meal_status", "{}")
        if (res.code !in 200..299) fail(res)
        return parseMealStatus(JSONObject(res.body))
    }

    suspend fun summary(): MealSummary {
        val res = call("POST", "/rest/v1/rpc/get_meal_summary", "{}")
        if (res.code !in 200..299) fail(res)
        return parseMealSummary(JSONObject(res.body))
    }

    suspend fun createOff(from: String, to: String, b: Boolean, l: Boolean, d: Boolean, reason: String) {
        val body = JSONObject()
            .put("p_from", from).put("p_to", to)
            .put("p_breakfast", b).put("p_lunch", l).put("p_dinner", d)
            .put("p_reason", reason.trim()).toString()
        val res = call("POST", "/rest/v1/rpc/create_meal_off", body)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun cancelOff(id: String) {
        val res = call("POST", "/rest/v1/rpc/cancel_meal_off", JSONObject().put("p_id", id).toString())
        if (res.code !in 200..299) fail(res)
    }

    suspend fun listExpenses(start: String, end: String): List<Expense> {
        val path = "/rest/v1/expenses?expense_date=gte.$start&expense_date=lte.$end" +
            "&select=*&order=expense_date.desc,created_at.desc&limit=100"
        val res = call("GET", path)
        if (res.code !in 200..299) fail(res)
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map { parseExpense(arr.getJSONObject(it)) }
    }

    suspend fun addExpense(date: String, category: String, amountMinor: Long, description: String, requestId: String) {
        val body = JSONObject()
            .put("p_date", date).put("p_category", category)
            .put("p_amount_minor", amountMinor).put("p_description", description)
            .put("p_request_id", requestId).toString()
        val res = call("POST", "/rest/v1/rpc/add_expense", body)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun voidExpense(id: String, reason: String) {
        val body = JSONObject().put("p_id", id).put("p_reason", reason.trim()).toString()
        val res = call("POST", "/rest/v1/rpc/void_expense", body)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun listGuestMeals(start: String): List<GuestMeal> {
        val path = "/rest/v1/guest_meals?status=eq.ACTIVE&meal_date=gte.$start&select=*&order=meal_date.desc&limit=50"
        val res = call("GET", path)
        if (res.code !in 200..299) fail(res)
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map { parseGuestMeal(arr.getJSONObject(it)) }
    }

    suspend fun addGuestMeal(memberId: String, date: String, slot: String, count: Int, requestId: String) {
        val body = JSONObject()
            .put("p_member", memberId).put("p_date", date).put("p_slot", slot)
            .put("p_count", count).put("p_request_id", requestId).toString()
        val res = call("POST", "/rest/v1/rpc/add_guest_meal", body)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun voidGuestMeal(id: String, reason: String) {
        val body = JSONObject().put("p_id", id).put("p_reason", reason.trim()).toString()
        val res = call("POST", "/rest/v1/rpc/void_guest_meal", body)
        if (res.code !in 200..299) fail(res)
    }
}
