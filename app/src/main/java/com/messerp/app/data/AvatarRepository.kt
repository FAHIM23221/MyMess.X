package com.messerp.app.data

import org.json.JSONObject

/** Profile photos live in a private storage bucket. Each user can only write inside their own folder (enforced by the server). */
class AvatarRepository(private val auth: AuthRepository) {

    suspend fun upload(userId: String, jpeg: ByteArray): String {
        val token = auth.accessToken()
        val path = "$userId/avatar-${System.currentTimeMillis()}.jpg"
        val up = SupabaseHttp.requestBytes(
            "POST", "/storage/v1/object/avatars/$path", jpeg, "image/jpeg", token, mapOf("x-upsert" to "true")
        )
        if (up.code !in 200..299) throw AuthException("ছবি আপলোড করা যায়নি।")
        val body = JSONObject().put("avatar_url", path).toString()
        val res = SupabaseHttp.request("PATCH", "/rest/v1/profiles?id=eq.$userId", body, token)
        if (res.code !in 200..299) throw AuthException("ছবি সংরক্ষণ করা যায়নি।")
        return path
    }

    suspend fun fetch(path: String): ByteArray? {
        val token = auth.accessToken()
        return SupabaseHttp.getBytes("/storage/v1/object/authenticated/avatars/$path", token)
    }
}
