package com.messerp.app.data

import org.json.JSONArray
import org.json.JSONObject

data class DuePolicy(val warningMinor: Long, val criticalMinor: Long, val suspendEnabled: Boolean, val notifyEnabled: Boolean) {
    val configured: Boolean get() = warningMinor > 0 || criticalMinor > 0
}

data class DueStatus(
    val totalDueMinor: Long,
    val messDueMinor: Long,
    val utilityDueMinor: Long,
    val status: String,
    val suspended: Boolean,
    val overrideUntil: String?,
    val policy: DuePolicy,
    val riceDueGrams: Long = 0
) {
    val warningMinor: Long get() = policy.warningMinor
    val criticalMinor: Long get() = policy.criticalMinor
}

data class DueRow(
    val id: String,
    val name: String,
    val code: String,
    val totalDueMinor: Long,
    val status: String,
    val suspended: Boolean,
    val overrideUntil: String?
)

data class DueBoard(val policy: DuePolicy, val rows: List<DueRow>)

private fun parsePolicy(o: JSONObject) = DuePolicy(
    warningMinor = o.optLong("warning_minor", 0),
    criticalMinor = o.optLong("critical_minor", 0),
    suspendEnabled = o.optBoolean("suspend_enabled", false),
    notifyEnabled = o.optBoolean("notify_enabled", true)
)

/** The server decides every status and every suspension; the app only shows and requests. */
class DueRepository(private val auth: AuthRepository) {

    private suspend fun call(path: String, body: String): HttpResult =
        SupabaseHttp.request("POST", path, body, auth.accessToken())

    private fun fail(res: HttpResult): Nothing {
        val msg = runCatching { JSONObject(res.body).optString("message") }.getOrDefault("")
        val text = when {
            msg.contains("forbidden") -> "এই কাজের অনুমতি আপনার নেই।"
            msg.contains("invalid_value") -> "সীমা সঠিক নয়। জরুরি সীমা সতর্কতার সীমার চেয়ে বড় হতে হবে। মিল স্থগিত চালু করতে জরুরি সীমা দিতে হবে।"
            msg.contains("reason_required") -> "কারণ লিখুন (কমপক্ষে ৩ অক্ষর)।"
            msg.contains("not_found") -> "সদস্য পাওয়া যায়নি।"
            else -> "কাজটি সম্পন্ন হয়নি।"
        }
        throw AuthException(text)
    }

    suspend fun myStatus(): DueStatus {
        val res = call("/rest/v1/rpc/get_my_due_status", "{}")
        if (res.code !in 200..299) fail(res)
        val o = JSONObject(res.body)
        return DueStatus(
            totalDueMinor = o.getLong("total_due_minor"),
            messDueMinor = o.getLong("mess_due_minor"),
            utilityDueMinor = o.getLong("utility_due_minor"),
            status = o.getString("status"),
            suspended = o.optBoolean("suspended", false),
            overrideUntil = o.strOrNull("override_until"),
            policy = parsePolicy(o.getJSONObject("policy")),
            riceDueGrams = o.optLong("rice_due_grams", 0)
        )
    }

    suspend fun board(): DueBoard {
        val res = call("/rest/v1/rpc/get_due_overview_admin", "{}")
        if (res.code !in 200..299) fail(res)
        val o = JSONObject(res.body)
        val arr: JSONArray = o.getJSONArray("members")
        return DueBoard(
            policy = parsePolicy(o.getJSONObject("policy")),
            rows = (0 until arr.length()).map {
                val m = arr.getJSONObject(it)
                DueRow(
                    m.getString("id"), m.getString("full_name"), m.getString("member_code"),
                    m.getLong("total_due_minor"), m.getString("status"), m.optBoolean("suspended", false), m.strOrNull("override_until")
                )
            }
        )
    }

    suspend fun setPolicy(warning: Long, critical: Long, suspend: Boolean, notify: Boolean, reason: String) {
        val body = JSONObject().put("p_warning", warning).put("p_critical", critical)
            .put("p_suspend", suspend).put("p_notify", notify).put("p_reason", reason.trim()).toString()
        val res = call("/rest/v1/rpc/set_due_policy", body)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun override(member: String, days: Int, reason: String) {
        val body = JSONObject().put("p_member", member).put("p_days", days).put("p_reason", reason.trim()).toString()
        val res = call("/rest/v1/rpc/override_meal_suspension", body)
        if (res.code !in 200..299) fail(res)
    }
}
