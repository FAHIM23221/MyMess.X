package com.messerp.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class Conversation(
    val id: String,
    val kind: String,
    val title: String,
    val memberId: String?,
    val lastBody: String?,
    val lastSender: String?,
    val lastLocal: String?,
    val unread: Int
)

/** state: SENT (confirmed by the server), QUEUED (waiting for internet) or FAILED (rejected by the server). */
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String?,
    val senderName: String?,
    val body: String?,
    val ts: Long,
    val updatedTs: Long,
    val atLocal: String,
    val edited: Boolean,
    val deleted: Boolean,
    val state: String = "SENT"
)

data class ChatRead(val userId: String, val name: String, val ts: Long)

data class ChatBatch(val serverTs: Long, val messages: List<ChatMessage>, val reads: List<ChatRead>, val typing: List<String>)

class ChatException(val userMessage: String) : Exception(userMessage)

fun parseChatMessage(o: JSONObject) = ChatMessage(
    id = o.getString("id"),
    conversationId = o.getString("conversation_id"),
    senderId = o.strOrNull("sender_id"),
    senderName = o.strOrNull("sender_name"),
    body = o.strOrNull("body"),
    ts = o.getLong("ts"),
    updatedTs = o.getLong("updated_ts"),
    atLocal = o.getString("at_local"),
    edited = o.optBoolean("edited"),
    deleted = o.optBoolean("deleted")
)

/**
 * Text messages only. Every rule (who may read, send, edit within 15 minutes, delete) is enforced by the server.
 * Temporary problems (no internet, server busy, rate limit) throw IOException so the message stays queued;
 * real rejections throw ChatException so the message is marked as failed.
 */
class ChatRepository(private val auth: AuthRepository) {

    private suspend fun call(path: String, body: String): HttpResult =
        SupabaseHttp.request("POST", path, body, auth.accessToken())

    private fun fail(res: HttpResult): Nothing {
        if (res.code >= 500 || res.code == 408 || res.code == 429) throw IOException("server " + res.code)
        val msg = runCatching { JSONObject(res.body).optString("message") }.getOrDefault("")
        if (msg.contains("too_many_requests")) throw IOException("rate limit")
        val text = when {
            msg.contains("forbidden") -> "এই কথোপকথনে আপনার অনুমতি নেই।"
            msg.contains("invalid_body") -> "বার্তা ১ থেকে ১০০০ অক্ষরের হতে হবে।"
            msg.contains("too_late") -> "১৫ মিনিট পেরিয়ে গেছে, আর সম্পাদনা করা যায় না।"
            msg.contains("not_found") -> "বার্তা বা সদস্য পাওয়া যায়নি।"
            msg.contains("duplicate_id") -> "এই বার্তা আগেই জমা হয়েছে।"
            else -> "বার্তা পাঠানো যায়নি।"
        }
        throw ChatException(text)
    }

    suspend fun conversations(): List<Conversation> {
        val res = call("/rest/v1/rpc/get_conversations", "{}")
        if (res.code !in 200..299) fail(res)
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Conversation(
                id = o.getString("id"), kind = o.getString("kind"), title = o.optString("title", ""),
                memberId = o.strOrNull("member_id"), lastBody = o.strOrNull("last_body"), lastSender = o.strOrNull("last_sender"),
                lastLocal = o.strOrNull("last_local"), unread = o.optInt("unread", 0)
            )
        }
    }

    suspend fun openPrivate(member: String?): String {
        val body = JSONObject().apply { if (member != null) put("p_member", member) }.toString()
        val res = call("/rest/v1/rpc/get_or_create_private", body)
        if (res.code !in 200..299) fail(res)
        return res.body.trim().trim('"')
    }

    suspend fun messages(conv: String, since: Long?, before: Long?, limit: Int): ChatBatch {
        val b = JSONObject().put("p_conversation", conv).put("p_limit", limit)
        if (since != null) b.put("p_since", since)
        if (before != null) b.put("p_before", before)
        val res = call("/rest/v1/rpc/get_messages", b.toString())
        if (res.code !in 200..299) fail(res)
        val o = JSONObject(res.body)
        val ma = o.getJSONArray("messages")
        val ra = o.getJSONArray("reads")
        val ta = o.getJSONArray("typing")
        return ChatBatch(
            serverTs = o.getLong("server_ts"),
            messages = (0 until ma.length()).map { parseChatMessage(ma.getJSONObject(it)) },
            reads = (0 until ra.length()).map {
                val r = ra.getJSONObject(it)
                ChatRead(r.getString("user_id"), r.optString("name", ""), r.getLong("ts"))
            },
            typing = (0 until ta.length()).map { ta.getString(it) }
        )
    }

    suspend fun send(conv: String, id: String, body: String): ChatMessage {
        val b = JSONObject().put("p_conversation", conv).put("p_id", id).put("p_body", body).toString()
        val res = call("/rest/v1/rpc/send_message", b)
        if (res.code !in 200..299) fail(res)
        return parseChatMessage(JSONObject(res.body))
    }

    suspend fun edit(id: String, body: String): ChatMessage {
        val res = call("/rest/v1/rpc/edit_message", JSONObject().put("p_id", id).put("p_body", body).toString())
        if (res.code !in 200..299) fail(res)
        return parseChatMessage(JSONObject(res.body))
    }

    suspend fun delete(id: String): ChatMessage {
        val res = call("/rest/v1/rpc/delete_message", JSONObject().put("p_id", id).toString())
        if (res.code !in 200..299) fail(res)
        return parseChatMessage(JSONObject(res.body))
    }

    suspend fun markRead(conv: String) {
        call("/rest/v1/rpc/mark_conversation_read", JSONObject().put("p_conversation", conv).toString())
    }

    suspend fun typing(conv: String) {
        call("/rest/v1/rpc/set_typing", JSONObject().put("p_conversation", conv).toString())
    }
}
