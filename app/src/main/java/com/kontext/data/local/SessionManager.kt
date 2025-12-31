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
    
    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_LANGUAGE_CODE = "language_code"
        private const val DEFAULT_USER_ID = "local_user"
        private const val DEFAULT_LANGUAGE_CODE = "de" // German as default
    }

    fun getCurrentUserId(): String {
        return prefs.getString(KEY_USER_ID, DEFAULT_USER_ID) ?: DEFAULT_USER_ID
    }
    
    fun setUserId(userId: String) {
        prefs.edit().putString(KEY_USER_ID, userId).apply()
    }

    fun clearSession() {
        prefs.edit().remove(KEY_USER_ID).apply()
    }
    
    /**
     * Get the currently selected language code.
     * @return ISO 639-1 language code ("de", "es", "fr")
     */
    fun getLanguageCode(): String {
        return prefs.getString(KEY_LANGUAGE_CODE, DEFAULT_LANGUAGE_CODE) ?: DEFAULT_LANGUAGE_CODE
    }
    
    /**
     * Set the selected language code.
     * Note: Changing language requires app restart to reinitialize dependencies.
     */
    fun setLanguageCode(languageCode: String) {
        prefs.edit().putString(KEY_LANGUAGE_CODE, languageCode).apply()
    }
}
