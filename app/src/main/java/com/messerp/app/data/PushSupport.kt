package com.messerp.app.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/** Instant push (Firebase Cloud Messaging). It is active only when google-services.json was included in the build. */
object Push {
    fun available(ctx: Context): Boolean = try {
        FirebaseApp.getApps(ctx).isNotEmpty()
    } catch (e: Exception) {
        false
    }

    suspend fun tokenOrNull(ctx: Context): String? = suspendCancellableCoroutine { cont ->
        if (!available(ctx)) {
            cont.resume(null)
        } else {
            try {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    cont.resume(if (task.isSuccessful) task.result else null)
                }
            } catch (e: Exception) {
                cont.resume(null)
            }
        }
    }
}

class PushResult(val recipients: Int, val sent: Int, val failed: Int, val noToken: Int)

class PushRepository(private val auth: AuthRepository) {

    suspend fun registerToken(token: String) {
        val res = SupabaseHttp.request(
            "POST", "/rest/v1/rpc/register_device_token",
            JSONObject().put("p_token", token).toString(), auth.accessToken()
        )
        if (res.code !in 200..299) throw AuthException("ডিভাইস নিবন্ধন হয়নি।")
    }

    suspend fun unregisterToken(token: String) {
        SupabaseHttp.request(
            "POST", "/rest/v1/rpc/unregister_device_token",
            JSONObject().put("p_token", token).toString(), auth.accessToken()
        )
    }

    /** Returns null when the server has no push configuration yet (the notice still reaches phones by polling). */
    suspend fun sendNotice(noticeId: String): PushResult? {
        val res = SupabaseHttp.request(
            "POST", "/functions/v1/send-push",
            JSONObject().put("notice_id", noticeId).toString(), auth.accessToken()
        )
        if (res.code !in 200..299) return null
        val o = JSONObject(res.body)
        return PushResult(o.optInt("recipients"), o.optInt("sent"), o.optInt("failed"), o.optInt("no_token"))
    }
}
