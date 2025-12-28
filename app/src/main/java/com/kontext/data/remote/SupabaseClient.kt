package com.kontext.data.remote

import io.github.jan.supabase.createSupabaseClient
// import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import com.kontext.BuildConfig

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        // install(Auth)
        install(Postgrest)
    }
}
