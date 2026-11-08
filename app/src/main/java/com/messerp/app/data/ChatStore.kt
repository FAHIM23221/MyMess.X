package com.messerp.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class OutboxItem(val id: String, val conversationId: String, val body: String, val createdMs: Long, val state: String)

/**
 * Keeps the last messages, the conversation list and the unsent queue on the phone, so chats can be read
 * without internet and messages written offline are not lost. Files are private to the app and removed at logout.
 */
class ChatStore(private val context: Context) {
    private val lock = Any()

    private fun dir(): File = File(context.filesDir, "chat").also { it.mkdirs() }
    private fun file(name: String) = File(dir(), name)

    private fun readArray(name: String): JSONArray = try {
        val f = file(name)
        if (f.exists()) JSONArray(f.readText()) else JSONArray()
    } catch (e: Exception) {
        JSONArray()
    }

    private fun write(name: String, arr: JSONArray) {
        try {
            file(name).writeText(arr.toString())
        } catch (e: Exception) {
            // Caching is best effort.
        }
    }

    private fun msgJson(m: ChatMessage) = JSONObject()
        .put("id", m.id).put("conversation_id", m.conversationId).put("sender_id", m.senderId ?: JSONObject.NULL)
        .put("sender_name", m.senderName ?: JSONObject.NULL).put("body", m.body ?: JSONObject.NULL)
        .put("ts", m.ts).put("updated_ts", m.updatedTs).put("at_local", m.atLocal)
        .put("edited", m.edited).put("deleted", m.deleted)

    fun saveConversations(uid: String, list: List<Conversation>) = synchronized(lock) {
        val arr = JSONArray()
        for (c in list) {
            arr.put(
                JSONObject().put("id", c.id).put("kind", c.kind).put("title", c.title)
                    .put("member_id", c.memberId ?: JSONObject.NULL).put("last_body", c.lastBody ?: JSONObject.NULL)
                    .put("last_sender", c.lastSender ?: JSONObject.NULL).put("last_local", c.lastLocal ?: JSONObject.NULL)
                    .put("unread", c.unread)
            )
        }
        write("conv_$uid.json", arr)
    }

    fun loadConversations(uid: String): List<Conversation> = synchronized(lock) {
        val arr = readArray("conv_$uid.json")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Conversation(o.getString("id"), o.getString("kind"), o.optString("title", ""), o.strOrNull("member_id"),
                o.strOrNull("last_body"), o.strOrNull("last_sender"), o.strOrNull("last_local"), o.optInt("unread", 0))
        }
    }

    fun saveMessages(uid: String, conv: String, list: List<ChatMessage>) = synchronized(lock) {
        val arr = JSONArray()
        for (m in list.takeLast(200)) arr.put(msgJson(m))
        write("msgs_${uid}_$conv.json", arr)
    }

    fun loadMessages(uid: String, conv: String): List<ChatMessage> = synchronized(lock) {
        val arr = readArray("msgs_${uid}_$conv.json")
        (0 until arr.length()).mapNotNull { runCatching { parseChatMessage(arr.getJSONObject(it)) }.getOrNull() }
    }

    fun appendMessage(uid: String, conv: String, m: ChatMessage) = synchronized(lock) {
        val current = loadMessages(uid, conv).filter { it.id != m.id } + m
        saveMessages(uid, conv, current.sortedBy { it.ts })
    }

    fun loadOutbox(uid: String): List<OutboxItem> = synchronized(lock) {
        val arr = readArray("outbox_$uid.json")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            OutboxItem(o.getString("id"), o.getString("conversation_id"), o.getString("body"), o.getLong("created_ms"), o.optString("state", "QUEUED"))
        }
    }

    private fun saveOutbox(uid: String, list: List<OutboxItem>) {
        val arr = JSONArray()
        for (i in list) {
            arr.put(JSONObject().put("id", i.id).put("conversation_id", i.conversationId).put("body", i.body)
                .put("created_ms", i.createdMs).put("state", i.state))
        }
        write("outbox_$uid.json", arr)
    }

    fun addOutbox(uid: String, item: OutboxItem) = synchronized(lock) { saveOutbox(uid, loadOutbox(uid) + item) }

    fun removeOutbox(uid: String, id: String) = synchronized(lock) { saveOutbox(uid, loadOutbox(uid).filter { it.id != id }) }

    fun setOutboxState(uid: String, id: String, state: String) = synchronized(lock) {
        saveOutbox(uid, loadOutbox(uid).map { if (it.id == id) it.copy(state = state) else it })
    }

    fun clearUser(uid: String) = synchronized(lock) {
        dir().listFiles()?.forEach { f ->
            if (f.name == "conv_$uid.json" || f.name == "outbox_$uid.json" || f.name.startsWith("msgs_${uid}_")) f.delete()
        }
    }
}
