package com.kontext.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("kontext_session", Context.MODE_PRIVATE)

    fun getCurrentUserId(): String {
        return prefs.getString("user_id", "local_user") ?: "local_user"
    }
    
    fun setUserId(userId: String) {
        prefs.edit().putString("user_id", userId).apply()
    }

    fun clearSession() {
        prefs.edit().remove("user_id").apply()
    }
}
