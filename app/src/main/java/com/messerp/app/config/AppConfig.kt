package com.messerp.app.config

import com.messerp.app.BuildConfig

/**
 * Backend settings injected at build time. Never hardcode real values here.
 * Only the public project URL and the public "anon" key belong in the app.
 * The Supabase service_role key must NEVER be placed in this app.
 */
object AppConfig {
    val supabaseUrl: String = BuildConfig.SUPABASE_URL
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY
    val isSupabaseConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
}
