package com.messerp.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class Session(val access: String, val refresh: String, val expiresAt: Long, val userId: String)

/** Stores the login session in encrypted preferences. Falls back to memory only if encryption is unavailable. */
class SessionStore(context: Context) {
    private val prefs: SharedPreferences? = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "session_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        null
    }
    private var memory: Session? = null

    fun load(): Session? {
        val p = prefs ?: return memory
        val access = p.getString("access", null) ?: return null
        val refresh = p.getString("refresh", null) ?: return null
        val uid = p.getString("uid", null) ?: return null
        return Session(access, refresh, p.getLong("exp", 0L), uid)
    }

    fun save(s: Session) {
        val p = prefs
        if (p == null) {
            memory = s
        } else {
            p.edit()
                .putString("access", s.access)
                .putString("refresh", s.refresh)
                .putLong("exp", s.expiresAt)
                .putString("uid", s.userId)
                .apply()
        }
    }

    fun clear() {
        memory = null
        prefs?.edit()?.clear()?.apply()
    }
}
