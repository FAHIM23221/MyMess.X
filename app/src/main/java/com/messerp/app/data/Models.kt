package com.messerp.app.data

import org.json.JSONObject

enum class Role { MEMBER, ADMIN, MAIN_ADMIN }

data class Profile(
    val id: String,
    val phone: String,
    val fullName: String,
    val memberCode: String,
    val roomNo: String?,
    val seatNo: String?,
    val joinDate: String,
    val status: String,
    val role: Role,
    val isCurrentManager: Boolean,
    val avatarUrl: String?
) {
    val isActive: Boolean get() = status == "ACTIVE"
}

internal fun JSONObject.strOrNull(key: String): String? = if (isNull(key)) null else getString(key)

fun parseProfile(o: JSONObject): Profile = Profile(
    id = o.getString("id"),
    phone = o.getString("phone"),
    fullName = o.getString("full_name"),
    memberCode = o.getString("member_code"),
    roomNo = o.strOrNull("room_no"),
    seatNo = o.strOrNull("seat_no"),
    joinDate = o.getString("join_date"),
    status = o.getString("status"),
    // Unknown values fall back to the least privileged role.
    role = runCatching { Role.valueOf(o.getString("role")) }.getOrDefault(Role.MEMBER),
    isCurrentManager = o.optBoolean("is_current_manager", false),
    avatarUrl = o.strOrNull("avatar_url")
)
