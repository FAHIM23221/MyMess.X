package com.messerp.app.data

import org.json.JSONArray
import org.json.JSONObject

data class ManagerRole(val type: String, val canGeneralNotice: Boolean, val canMeetingNotice: Boolean)

data class ManagerRow(
    val userId: String,
    val name: String,
    val code: String,
    val type: String,
    val active: Boolean,
    val canGeneralNotice: Boolean,
    val canMeetingNotice: Boolean
)

/** Manager duties are given by the Main Admin only; the server checks every action again. */
class ManagerRepository(private val auth: AuthRepository) {

    private suspend fun call(path: String, body: String): HttpResult =
        SupabaseHttp.request("POST", path, body, auth.accessToken())

    private fun fail(res: HttpResult): Nothing {
        val msg = runCatching { JSONObject(res.body).optString("message") }.getOrDefault("")
        val text = when {
            msg.contains("forbidden") -> "এই কাজ শুধু মূল অ্যাডমিন করতে পারেন।"
            msg.contains("manager_limit") -> "প্রতিটি দায়িত্বে সর্বোচ্চ ৩ জন সক্রিয় ম্যানেজার থাকতে পারেন।"
            msg.contains("reason_required") -> "কারণ লিখুন (কমপক্ষে ৩ অক্ষর)।"
            msg.contains("not_found") -> "শুধু সক্রিয় সদস্যকে ম্যানেজারের দায়িত্ব দেওয়া যায়।"
            else -> "কাজটি সম্পন্ন হয়নি।"
        }
        throw AuthException(text)
    }

    suspend fun myRoles(): List<ManagerRole> {
        val res = call("/rest/v1/rpc/get_my_manager_roles", "{}")
        if (res.code !in 200..299) fail(res)
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ManagerRole(o.getString("manager_type"), o.optBoolean("can_general_notice"), o.optBoolean("can_meeting_notice"))
        }
    }

    suspend fun list(): List<ManagerRow> {
        val res = call("/rest/v1/rpc/list_managers", "{}")
        if (res.code !in 200..299) fail(res)
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ManagerRow(
                o.getString("user_id"), o.getString("full_name"), o.getString("member_code"), o.getString("manager_type"),
                o.optBoolean("active"), o.optBoolean("can_general_notice"), o.optBoolean("can_meeting_notice")
            )
        }
    }

    suspend fun set(userId: String, type: String, active: Boolean, general: Boolean, meeting: Boolean, reason: String) {
        val body = JSONObject().put("p_user", userId).put("p_type", type).put("p_active", active)
            .put("p_general", general).put("p_meeting", meeting).put("p_reason", reason.trim()).toString()
        val res = call("/rest/v1/rpc/set_manager_role", body)
        if (res.code !in 200..299) fail(res)
    }
}
