package com.messerp.app.data

import org.json.JSONArray
import org.json.JSONObject

data class AuditRow(
    val id: Long,
    val atLocal: String,
    val actor: String?,
    val action: String,
    val targetType: String?,
    val targetName: String?,
    val reason: String?,
    val oldValue: String?,
    val newValue: String?
)

data class AuditAction(val action: String, val count: Long)

/** Admin tools. The server checks who may do what; passwords are never written to the audit log. */
class AdminToolsRepository(private val auth: AuthRepository) {

    private suspend fun call(path: String, body: String): HttpResult =
        SupabaseHttp.request("POST", path, body, auth.accessToken())

    private fun fail(res: HttpResult): Nothing {
        val msg = runCatching { JSONObject(res.body).optString("message") }.getOrDefault("")
        val text = when {
            msg.contains("too_many_requests") -> "ঘণ্টায় সর্বোচ্চ ৫টি পাসওয়ার্ড রিসেট করা যায়। পরে চেষ্টা করুন।"
            msg.contains("invalid_password") -> "পাসওয়ার্ড ৮ থেকে ৭২ অক্ষরের হতে হবে।"
            msg.contains("reason_required") -> "কারণ লিখুন (কমপক্ষে ৩ অক্ষর)।"
            msg.contains("forbidden") -> "এই কাজের অনুমতি আপনার নেই। অ্যাডমিনের পাসওয়ার্ড শুধু মূল অ্যাডমিন রিসেট করতে পারেন।"
            msg.contains("not_found") -> "সদস্য পাওয়া যায়নি।"
            else -> "কাজটি সম্পন্ন হয়নি। তথ্য যাচাই করে আবার চেষ্টা করুন।"
        }
        throw AuthException(text)
    }

    suspend fun resetPassword(target: String, password: String, reason: String) {
        val body = JSONObject().put("p_target", target).put("p_new_password", password).put("p_reason", reason.trim()).toString()
        val res = call("/rest/v1/rpc/admin_reset_password", body)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun updateMember(target: String, room: String, seat: String, joinDate: String) {
        val body = JSONObject().put("p_target", target).put("p_room", room.trim()).put("p_seat", seat.trim()).put("p_join_date", joinDate).toString()
        val res = call("/rest/v1/rpc/admin_update_member", body)
        if (res.code !in 200..299) fail(res)
    }

    /** Every export is recorded in the audit log first; without that record nothing is exported. */
    suspend fun logReportExport(kind: String, month: String) {
        val res = call("/rest/v1/rpc/log_report_export", JSONObject().put("p_kind", kind).put("p_month", month).toString())
        if (res.code !in 200..299) fail(res)
    }

    suspend fun auditActions(): List<AuditAction> {
        val res = call("/rest/v1/rpc/get_audit_actions", "{}")
        if (res.code !in 200..299) fail(res)
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map { val o = arr.getJSONObject(it); AuditAction(o.getString("action"), o.getLong("count")) }
    }

    suspend fun auditLogs(before: Long?, action: String?, limit: Int): List<AuditRow> {
        val b = JSONObject().put("p_limit", limit)
        if (before != null) b.put("p_before", before)
        if (action != null) b.put("p_action", action)
        val res = call("/rest/v1/rpc/get_audit_logs", b.toString())
        if (res.code !in 200..299) fail(res)
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            AuditRow(
                id = o.getLong("id"), atLocal = o.getString("at_local"), actor = o.strOrNull("actor_name"),
                action = o.getString("action"), targetType = o.strOrNull("target_type"), targetName = o.strOrNull("target_name"),
                reason = o.strOrNull("reason"), oldValue = o.strOrNull("old_value"), newValue = o.strOrNull("new_value")
            )
        }
    }
}
