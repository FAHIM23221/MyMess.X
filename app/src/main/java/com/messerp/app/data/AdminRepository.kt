package com.messerp.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Every call is re-checked by the server (Row Level Security and role checks). The UI is not a security boundary. */
class AdminRepository(private val auth: AuthRepository) {

    private suspend fun call(method: String, path: String, body: String? = null): HttpResult =
        SupabaseHttp.request(method, path, body, auth.accessToken())

    private fun fail(res: HttpResult): Nothing {
        val msg = runCatching { JSONObject(res.body).optString("message") }.getOrDefault("")
        val text = when {
            msg.contains("forbidden") -> "এই কাজের অনুমতি আপনার নেই।"
            msg.contains("cannot_change_self") -> "নিজের রোল বা অবস্থা বদলানো যাবে না।"
            msg.contains("admin_limit") -> "অ্যাডমিনের সর্বোচ্চ সীমা পূর্ণ।"
            msg.contains("reason_required") -> "কারণ লিখুন (কমপক্ষে ৩ অক্ষর)।"
            msg.contains("not_found") -> "সদস্য পাওয়া যায়নি।"
            msg.contains("clear_balance_first") -> "আগে সদস্যের বাকি বা অগ্রিম শূন্য করুন, তারপর অ্যাডমিন করুন।"
            msg.contains("main_admin_protected") -> "মূল অ্যাডমিনকে বদলানো যায় না।"
            else -> "কাজটি সম্পন্ন হয়নি।"
        }
        throw AuthException(text)
    }

    suspend fun listMembers(): List<Profile> {
        val res = call("GET", "/rest/v1/profiles?select=*&order=created_at.asc")
        if (res.code !in 200..299) fail(res)
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map { parseProfile(arr.getJSONObject(it)) }
    }

    suspend fun createInvite(role: Role, waiveGuardian: Boolean): String {
        val body = JSONObject().put("p_role", role.name).put("p_waive_guardian", waiveGuardian).toString()
        val res = call("POST", "/rest/v1/rpc/create_invite_code", body)
        if (res.code !in 200..299) fail(res)
        return res.body.trim().trim('"')
    }

    suspend fun setRole(target: String, role: Role, reason: String) {
        val body = JSONObject().put("p_target", target).put("p_role", role.name).put("p_reason", reason).toString()
        val res = call("POST", "/rest/v1/rpc/set_member_role", body)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun setStatus(target: String, status: String, reason: String) {
        val body = JSONObject().put("p_target", target).put("p_status", status).put("p_reason", reason).toString()
        val res = call("POST", "/rest/v1/rpc/set_member_status", body)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun leaderboardEnabled(): Boolean {
        val res = call("GET", "/rest/v1/app_settings?key=eq.leaderboard_enabled&select=value")
        if (res.code !in 200..299) fail(res)
        val arr = JSONArray(res.body)
        return arr.length() > 0 && arr.getJSONObject(0).optBoolean("value", false)
    }

    suspend fun setLeaderboard(enabled: Boolean) {
        val body = JSONObject().put("p_key", "leaderboard_enabled").put("p_value", enabled).toString()
        val res = call("POST", "/rest/v1/rpc/set_app_setting", body)
        if (res.code !in 200..299) fail(res)
    }
}
