package com.messerp.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

fun sha256Hex(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** What a member sees about their own submission (looked up with the secret key kept on their phone). */
data class FeedbackItem(
    val tokenHash: String,
    val kind: String,
    val topic: String,
    val body: String?,
    val status: String,
    val response: String?,
    val createdDay: String
)

data class AdminFeedback(
    val id: String,
    val kind: String,
    val topic: String,
    val body: String,
    val status: String,
    val response: String?,
    val createdDay: String
)

data class LocalFeedback(val key: String, val kind: String, val topic: String, val body: String, val createdMs: Long)

/**
 * The secret key of each submission stays on the phone only. The server never learns who wrote what;
 * it only stores a hash of the key, which lets the writer (and nobody else) read the reply later.
 */
class FeedbackStore(private val context: Context) {
    private val lock = Any()
    private fun file(uid: String) = File(File(context.filesDir, "feedback").also { it.mkdirs() }, "fb_$uid.json")

    fun load(uid: String): List<LocalFeedback> = synchronized(lock) {
        try {
            val f = file(uid)
            if (!f.exists()) return emptyList()
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                LocalFeedback(o.getString("key"), o.getString("kind"), o.getString("topic"), o.getString("body"), o.getLong("created_ms"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(uid: String, item: LocalFeedback) = synchronized(lock) {
        val list = load(uid).filter { it.key != item.key } + item
        val arr = JSONArray()
        for (i in list.takeLast(50)) {
            arr.put(JSONObject().put("key", i.key).put("kind", i.kind).put("topic", i.topic).put("body", i.body).put("created_ms", i.createdMs))
        }
        try {
            file(uid).writeText(arr.toString())
        } catch (e: Exception) {
            // Best effort.
        }
    }

    fun clearUser(uid: String) = synchronized(lock) { file(uid).delete() }
}

class FeedbackRepository(private val auth: AuthRepository) {

    private suspend fun call(path: String, body: String): HttpResult =
        SupabaseHttp.request("POST", path, body, auth.accessToken())

    private fun fail(res: HttpResult): Nothing {
        val msg = runCatching { JSONObject(res.body).optString("message") }.getOrDefault("")
        val text = when {
            msg.contains("too_many_requests") -> "আজকের সীমা (৩টি) শেষ। কাল আবার লিখতে পারবেন।"
            msg.contains("invalid_body") -> "লেখা ১০ থেকে ১০০০ অক্ষরের হতে হবে।"
            msg.contains("reason_required") -> "এই অবস্থায় কারণ বা উত্তর লিখতে হবে।"
            msg.contains("forbidden") -> "এই কাজের অনুমতি আপনার নেই।"
            msg.contains("not_found") -> "লেখাটি পাওয়া যায়নি বা আগেই সরানো হয়েছে।"
            else -> "কাজটি সম্পন্ন হয়নি।"
        }
        throw AuthException(text)
    }

    suspend fun submit(kind: String, topic: String, body: String, tokenHash: String) {
        val b = JSONObject().put("p_kind", kind).put("p_topic", topic).put("p_body", body.trim()).put("p_token_hash", tokenHash).toString()
        val res = call("/rest/v1/rpc/submit_feedback", b)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun mine(hashes: List<String>): List<FeedbackItem> {
        if (hashes.isEmpty()) return emptyList()
        val res = call("/rest/v1/rpc/get_my_feedback", JSONObject().put("p_hashes", JSONArray(hashes)).toString())
        if (res.code !in 200..299) fail(res)
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            FeedbackItem(o.getString("token_hash"), o.getString("kind"), o.getString("topic"), o.strOrNull("body"),
                o.getString("status"), o.strOrNull("response"), o.getString("created_day"))
        }
    }

    suspend fun admin(status: String?): List<AdminFeedback> {
        val b = JSONObject().apply { if (status != null) put("p_status", status) }.toString()
        val res = call("/rest/v1/rpc/get_feedback_admin", b)
        if (res.code !in 200..299) fail(res)
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            AdminFeedback(o.getString("id"), o.getString("kind"), o.getString("topic"), o.getString("body"),
                o.getString("status"), o.strOrNull("response"), o.getString("created_day"))
        }
    }

    suspend fun respond(id: String, status: String, response: String) {
        val b = JSONObject().put("p_id", id).put("p_status", status).put("p_response", response.trim()).toString()
        val res = call("/rest/v1/rpc/respond_feedback", b)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun hide(id: String, reason: String) {
        val res = call("/rest/v1/rpc/hide_feedback", JSONObject().put("p_id", id).put("p_reason", reason.trim()).toString())
        if (res.code !in 200..299) fail(res)
    }
}
