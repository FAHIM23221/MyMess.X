package com.messerp.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** An error whose message is safe and meant to be shown to the user. */
class AuthException(val userMessage: String) : Exception(userMessage)

class AuthRepository(private val store: SessionStore) {

    private fun emailFor(phone: String) = "$phone@mess.app"
    private fun nowSeconds() = System.currentTimeMillis() / 1000

    private fun persist(json: JSONObject) {
        store.save(
            Session(
                access = json.getString("access_token"),
                refresh = json.getString("refresh_token"),
                expiresAt = nowSeconds() + json.optLong("expires_in", 3600),
                userId = json.getJSONObject("user").getString("id")
            )
        )
    }

    suspend fun signIn(phone: String, password: String): Profile {
        val body = JSONObject().put("email", emailFor(phone)).put("password", password).toString()
        val res = SupabaseHttp.request("POST", "/auth/v1/token?grant_type=password", body)
        if (res.code !in 200..299) throw AuthException("ফোন নম্বর বা পাসওয়ার্ড ভুল।")
        persist(JSONObject(res.body))
        return loadActiveProfile()
    }

    suspend fun signUp(
        name: String, phone: String, password: String, inviteCode: String,
        extra: Map<String, String>, hasGuardian: Boolean
    ): Profile {
        val data = JSONObject().put("phone", phone).put("full_name", name.trim()).put("invite_code", inviteCode)
        for ((k, v) in extra) if (v.isNotBlank()) data.put(k, v.trim())
        val body = JSONObject().put("email", emailFor(phone)).put("password", password).put("data", data).toString()
        val res = SupabaseHttp.request("POST", "/auth/v1/signup", body)
        if (res.code !in 200..299) {
            throw AuthException(
                if (hasGuardian) "রেজিস্ট্রেশন সম্ভব হয়নি। তথ্য ও ইনভাইট কোড যাচাই করুন।"
                else "রেজিস্ট্রেশন সম্ভব হয়নি। বাবা বা মায়ের তথ্য না দিলে মূল অ্যাডমিনের দেওয়া বিশেষ কোড লাগবে।"
            )
        }
        val json = JSONObject(res.body)
        if (!json.has("access_token")) return signIn(phone, password)
        persist(json)
        return loadActiveProfile()
    }

    /** Returns null when there is no saved session. Throws IOException when offline. */
    suspend fun restore(): Profile? {
        if (store.load() == null) return null
        return loadActiveProfile()
    }

    suspend fun accessToken(): String {
        val s = store.load() ?: throw AuthException("লগইন করা নেই।")
        if (s.expiresAt - 60 > nowSeconds()) return s.access
        val body = JSONObject().put("refresh_token", s.refresh).toString()
        val res = SupabaseHttp.request("POST", "/auth/v1/token?grant_type=refresh_token", body)
        if (res.code in 400..403) {
            store.clear()
            throw AuthException("সেশন শেষ হয়েছে, আবার লগইন করুন।")
        }
        if (res.code !in 200..299) throw IOException("server error " + res.code)
        persist(JSONObject(res.body))
        return store.load()?.access ?: throw AuthException("লগইন করা নেই।")
    }

    suspend fun loadProfile(): Profile {
        val token = accessToken()
        val uid = store.load()?.userId ?: throw AuthException("লগইন করা নেই।")
        val res = SupabaseHttp.request("GET", "/rest/v1/profiles?id=eq.$uid&select=*", null, token)
        if (res.code !in 200..299) throw IOException("profile error " + res.code)
        val arr = JSONArray(res.body)
        if (arr.length() == 0) throw AuthException("প্রোফাইল পাওয়া যায়নি।")
        return parseProfile(arr.getJSONObject(0))
    }

    private suspend fun loadActiveProfile(): Profile {
        val p = loadProfile()
        if (!p.isActive) {
            signOut()
            throw AuthException("এই অ্যাকাউন্ট সক্রিয় নয়। অ্যাডমিনের সঙ্গে যোগাযোগ করুন।")
        }
        return p
    }

    suspend fun updateName(name: String): Profile {
        val token = accessToken()
        val uid = store.load()?.userId ?: throw AuthException("লগইন করা নেই।")
        val body = JSONObject().put("full_name", name.trim()).toString()
        val res = SupabaseHttp.request(
            "PATCH", "/rest/v1/profiles?id=eq.$uid", body, token,
            mapOf("Prefer" to "return=representation")
        )
        if (res.code !in 200..299) throw AuthException("নাম বদলানো যায়নি।")
        val arr = JSONArray(res.body)
        if (arr.length() == 0) throw AuthException("নাম বদলানো যায়নি।")
        return parseProfile(arr.getJSONObject(0))
    }

    suspend fun signOut() {
        val token = store.load()?.access
        if (token != null) {
            try {
                SupabaseHttp.request("POST", "/auth/v1/logout", null, token)
            } catch (e: Exception) {
                // Best effort: the local session is removed regardless.
            }
        }
        store.clear()
    }

    suspend fun updateProfile(name: String, fatherName: String, fatherPhone: String, motherName: String, motherPhone: String): Profile {
        val token = accessToken()
        val uid = store.load()?.userId ?: throw AuthException("লগইন করা নেই।")
        fun v(x: String): Any = if (x.isBlank()) JSONObject.NULL else x.trim()
        val body = JSONObject()
            .put("full_name", name.trim())
            .put("father_name", v(fatherName)).put("father_phone", v(fatherPhone))
            .put("mother_name", v(motherName)).put("mother_phone", v(motherPhone))
            .toString()
        val res = SupabaseHttp.request(
            "PATCH", "/rest/v1/profiles?id=eq.$uid", body, token,
            mapOf("Prefer" to "return=representation")
        )
        if (res.code !in 200..299) {
            throw AuthException("তথ্য সংরক্ষণ হয়নি। বাবা বা মায়ের অন্তত একজনের নাম ও নম্বর থাকতে হবে।")
        }
        val arr = JSONArray(res.body)
        if (arr.length() == 0) throw AuthException("তথ্য সংরক্ষণ হয়নি।")
        return parseProfile(arr.getJSONObject(0))
    }
}
