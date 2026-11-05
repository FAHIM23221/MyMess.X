package com.messerp.app.data

import org.json.JSONArray
import org.json.JSONObject

data class Duty(
    val id: String,
    val memberId: String,
    val memberName: String,
    val type: String,
    val date: String,
    val status: String,
    val notes: String?,
    val cannotReason: String?,
    val completionNote: String?,
    val reassigned: Boolean
)

data class DutyEvent(val action: String, val fromStatus: String?, val toStatus: String?, val note: String?, val actor: String?, val atLocal: String)

data class RotationRow(
    val id: String,
    val memberId: String,
    val name: String,
    val code: String,
    val start: String,
    val end: String,
    val note: String?,
    val allowOverlap: Boolean,
    val phase: String
)

data class Rotation(val today: String, val rows: List<RotationRow>)

private fun parseDuty(o: JSONObject) = Duty(
    id = o.getString("id"), memberId = o.getString("member_id"), memberName = o.optString("member_name", ""),
    type = o.getString("duty_type"), date = o.getString("duty_date"), status = o.getString("status"),
    notes = o.strOrNull("notes"), cannotReason = o.strOrNull("cannot_reason"),
    completionNote = o.strOrNull("completion_note"), reassigned = o.optBoolean("reassigned", false)
)

/** Every rule (who may do what, dates, duplicates, history) is enforced by the server. */
class DutyRepository(private val auth: AuthRepository) {

    private suspend fun call(path: String, body: String = "{}"): HttpResult =
        SupabaseHttp.request("POST", path, body, auth.accessToken())

    private fun fail(res: HttpResult): Nothing {
        val msg = runCatching { JSONObject(res.body).optString("message") }.getOrDefault("")
        val text = when {
            msg.contains("duplicate_duty") -> "ওই সদস্যের ওই দিনে একই ধরনের ডিউটি আছে।"
            msg.contains("invalid_dates") -> "তারিখ সঠিক নয়। শেষ তারিখ শুরুর তারিখের আগে বা আজকের আগে হতে পারে না।"
            msg.contains("invalid_date") -> "তারিখ সঠিক নয় (গতকাল থেকে ৬০ দিনের মধ্যে দিন)।"
            msg.contains("invalid_status") -> "এই অবস্থায় এই কাজ করা যায় না।"
            msg.contains("too_early") -> "ডিউটির দিনের আগে সম্পন্ন চিহ্নিত করা যায় না।"
            msg.contains("too_late") -> "ডিউটির দিন পেরিয়ে গেছে, এখন \"পারবো না\" দেওয়া যায় না।"
            msg.contains("rotation_conflict") -> "ওই সময়ে আরেকজন ম্যানেজার আছেন। ইচ্ছে করে একসঙ্গে রাখতে চাইলে \"একসঙ্গে থাকতে পারবেন\" টিক দিন।"
            msg.contains("reason_required") -> "কারণ লিখুন (কমপক্ষে ৩ অক্ষর)।"
            msg.contains("forbidden") -> "এই কাজের অনুমতি আপনার নেই।"
            msg.contains("not_found") -> "রেকর্ড বা সদস্য পাওয়া যায়নি।"
            else -> "কাজটি সম্পন্ন হয়নি।"
        }
        throw AuthException(text)
    }

    private fun dutyList(body: String): List<Duty> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map { parseDuty(arr.getJSONObject(it)) }
    }

    suspend fun myDuties(): List<Duty> {
        val res = call("/rest/v1/rpc/get_my_duties")
        if (res.code !in 200..299) fail(res)
        return dutyList(res.body)
    }

    suspend fun adminDuties(): List<Duty> {
        val res = call("/rest/v1/rpc/get_duties_admin")
        if (res.code !in 200..299) fail(res)
        return dutyList(res.body)
    }

    suspend fun roster(): List<Duty> {
        val res = call("/rest/v1/rpc/get_duty_roster")
        if (res.code !in 200..299) fail(res)
        return dutyList(res.body)
    }

    suspend fun history(id: String): List<DutyEvent> {
        val res = call("/rest/v1/rpc/get_duty_history", JSONObject().put("p_duty", id).toString())
        if (res.code !in 200..299) fail(res)
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            DutyEvent(o.getString("action"), o.strOrNull("from_status"), o.strOrNull("to_status"), o.strOrNull("note"), o.strOrNull("actor"), o.getString("at_local"))
        }
    }

    suspend fun assign(member: String, type: String, date: String, notes: String, requestId: String) {
        val body = JSONObject().put("p_member", member).put("p_type", type).put("p_date", date)
            .put("p_notes", notes.trim()).put("p_request_id", requestId).toString()
        val res = call("/rest/v1/rpc/assign_duty", body)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun complete(id: String, note: String) {
        val res = call("/rest/v1/rpc/complete_duty", JSONObject().put("p_id", id).put("p_note", note.trim()).toString())
        if (res.code !in 200..299) fail(res)
    }

    suspend fun cannot(id: String, reason: String) {
        val res = call("/rest/v1/rpc/cannot_do_duty", JSONObject().put("p_id", id).put("p_reason", reason.trim()).toString())
        if (res.code !in 200..299) fail(res)
    }

    suspend fun reassign(id: String, newMember: String, reason: String) {
        val body = JSONObject().put("p_id", id).put("p_new_member", newMember).put("p_reason", reason.trim()).toString()
        val res = call("/rest/v1/rpc/reassign_duty", body)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun cancel(id: String, reason: String) {
        val res = call("/rest/v1/rpc/cancel_duty", JSONObject().put("p_id", id).put("p_reason", reason.trim()).toString())
        if (res.code !in 200..299) fail(res)
    }

    suspend fun rotation(): Rotation {
        val res = call("/rest/v1/rpc/get_manager_rotation")
        if (res.code !in 200..299) fail(res)
        val o = JSONObject(res.body)
        val arr = o.getJSONArray("rows")
        return Rotation(
            today = o.getString("today"),
            rows = (0 until arr.length()).map {
                val r = arr.getJSONObject(it)
                RotationRow(
                    r.getString("id"), r.getString("member_id"), r.getString("member_name"), r.getString("member_code"),
                    r.getString("start_date"), r.getString("end_date"), r.strOrNull("note"), r.optBoolean("allow_overlap"), r.getString("phase")
                )
            }
        )
    }

    suspend fun setRotation(member: String, start: String, end: String, note: String, overlap: Boolean) {
        val body = JSONObject().put("p_member", member).put("p_start", start).put("p_end", end)
            .put("p_note", note.trim()).put("p_allow_overlap", overlap).toString()
        val res = call("/rest/v1/rpc/set_manager_rotation", body)
        if (res.code !in 200..299) fail(res)
    }

    suspend fun cancelRotation(id: String, reason: String) {
        val res = call("/rest/v1/rpc/cancel_manager_rotation", JSONObject().put("p_id", id).put("p_reason", reason.trim()).toString())
        if (res.code !in 200..299) fail(res)
    }
}
