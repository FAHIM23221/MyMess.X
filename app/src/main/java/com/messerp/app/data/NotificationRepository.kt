package com.messerp.app.data

import org.json.JSONArray
import org.json.JSONObject

data class AppNotification(
    val id: String,
    val kind: String,
    val level: String,
    val title: String,
    val body: String,
    val createdLocal: String,
    val read: Boolean,
    val dept: String = "GENERAL"
)

data class NoticeRow(
    val id: String,
    val title: String,
    val body: String,
    val level: String,
    val audience: String,
    val noticeType: String,
    val showPopup: Boolean,
    val createdLocal: String,
    val recipients: Int,
    val delivered: Int,
    val readCount: Int
)

fun parseNotification(o: JSONObject) = AppNotification(
    id = o.getString("id"),
    kind = o.getString("kind"),
    level = o.getString("level"),
    title = o.getString("title"),
    body = o.getString("body"),
    createdLocal = o.getString("created_local"),
    read = o.optBoolean("read", false),
    dept = o.optString("dept", "GENERAL")
)

class NotificationRepository(private val auth: AuthRepository) {

    private suspend fun call(path: String, body: String): HttpResult =
        SupabaseHttp.request("POST", path, body, auth.accessToken())

    private fun fail(res: HttpResult): Nothing {
        val msg = runCatching { JSONObject(res.body).optString("message") }.getOrDefault("")
        val text = when {
            msg.contains("forbidden") -> "এই ধরনের নোটিশ পাঠানোর অনুমতি আপনার নেই।"
            msg.contains("invalid_title") -> "শিরোনাম ১ থেকে ১২০ অক্ষরের হতে হবে।"
            msg.contains("invalid_body") -> "বিবরণ ১ থেকে ১০০০ অক্ষরের হতে হবে।"
            msg.contains("invalid_value") -> "মান সঠিক নয়। হেডলাইন চালু করতে লেখা থাকতে হবে (সর্বোচ্চ ২০০ অক্ষর)।"
            msg.contains("too_many_requests") -> "আজ অনেক নোটিশ পাঠানো হয়েছে। কাল আবার চেষ্টা করুন।"
            msg.contains("duplicate_request") -> "এই নোটিশ আগেই পাঠানো হয়েছে।"
            else -> "কাজটি সম্পন্ন হয়নি।"
        }
        throw AuthException(text)
    }

    private fun parseList(body: String): List<AppNotification> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map { parseNotification(arr.getJSONObject(it)) }
    }

    suspend fun list(limit: Int = 50): List<AppNotification> {
        val res = call("/rest/v1/rpc/get_my_notifications", JSONObject().put("p_limit", limit).toString())
        if (res.code !in 200..299) fail(res)
        return parseList(res.body)
    }

    /** Atomically takes notifications not yet shown on this phone, so two callers never show the same one twice. */
    suspend fun claim(limit: Int = 20): List<AppNotification> {
        val res = call("/rest/v1/rpc/claim_undelivered_notifications", JSONObject().put("p_limit", limit).toString())
        if (res.code !in 200..299) fail(res)
        return parseList(res.body)
    }

    suspend fun popups(): List<AppNotification> {
        val res = call("/rest/v1/rpc/get_my_popups", "{}")
        if (res.code !in 200..299) fail(res)
        return parseList(res.body)
    }

    suspend fun markPopupSeen(id: String) {
        val res = call("/rest/v1/rpc/mark_popup_seen", JSONObject().put("p_id", id).toString())
        if (res.code !in 200..299) fail(res)
    }

    suspend fun markRead(ids: List<String>) {
        val res = call("/rest/v1/rpc/mark_notifications_read", JSONObject().put("p_ids", JSONArray(ids)).toString())
        if (res.code !in 200..299) fail(res)
    }

    suspend fun markAllRead() {
        val res = call("/rest/v1/rpc/mark_all_notifications_read", "{}")
        if (res.code !in 200..299) fail(res)
    }

    /** Returns (noticeId, recipientCount). */
    suspend fun createNotice(
        title: String, body: String, level: String, audience: String,
        type: String, popup: Boolean, department: String, requestId: String
    ): Pair<String, Int> {
        val json = JSONObject().put("p_title", title).put("p_body", body).put("p_level", level)
            .put("p_audience", audience).put("p_request_id", requestId)
            .put("p_type", type).put("p_popup", popup).put("p_department", department).toString()
        val res = call("/rest/v1/rpc/create_notice", json)
        if (res.code !in 200..299) fail(res)
        val o = JSONObject(res.body)
        return Pair(o.getString("notice_id"), o.getInt("recipients"))
    }

    suspend fun noticeHistory(): List<NoticeRow> {
        val res = call("/rest/v1/rpc/get_notice_history", "{}")
        if (res.code !in 200..299) fail(res)
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            NoticeRow(
                id = o.getString("id"), title = o.getString("title"), body = o.getString("body"),
                level = o.getString("level"), audience = o.getString("audience"),
                noticeType = o.optString("notice_type", "NORMAL"), showPopup = o.optBoolean("show_popup", false),
                createdLocal = o.getString("created_local"),
                recipients = o.getInt("recipients"), delivered = o.getInt("delivered"), readCount = o.getInt("read_count")
            )
        }
    }

    /** Returns (enabled, text). */
    suspend fun headline(): Pair<Boolean, String> {
        val res = call("/rest/v1/rpc/get_headline", "{}")
        if (res.code !in 200..299) fail(res)
        val o = JSONObject(res.body)
        return Pair(o.optBoolean("enabled", false), o.optString("text", ""))
    }

    suspend fun setHeadline(text: String, enabled: Boolean) {
        val res = call("/rest/v1/rpc/set_headline", JSONObject().put("p_text", text).put("p_enabled", enabled).toString())
        if (res.code !in 200..299) fail(res)
    }
}
