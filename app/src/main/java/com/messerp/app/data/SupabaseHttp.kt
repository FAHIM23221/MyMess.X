package com.messerp.app.data

import com.messerp.app.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class HttpResult(val code: Int, val body: String)

/** Minimal REST client. The public key is sent as "apikey"; a user token is sent only when logged in. */
object SupabaseHttp {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        accessToken: String? = null,
        headers: Map<String, String> = emptyMap()
    ): HttpResult = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(AppConfig.supabaseUrl.trimEnd('/') + path)
            .header("apikey", AppConfig.supabaseAnonKey)
        if (accessToken != null) builder.header("Authorization", "Bearer $accessToken")
        for ((k, v) in headers) builder.header(k, v)
        val jsonType = "application/json".toMediaType()
        val requestBody = when {
            body != null -> body.toRequestBody(jsonType)
            method == "GET" -> null
            else -> "".toRequestBody(jsonType)
        }
        builder.method(method, requestBody)
        client.newCall(builder.build()).execute().use { resp ->
            HttpResult(resp.code, resp.body?.string().orEmpty())
        }
    }
}
