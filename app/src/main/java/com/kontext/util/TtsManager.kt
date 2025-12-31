package com.kontext.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.kontext.domain.model.LanguageConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val languageConfig: LanguageConfig
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = languageConfig.targetLanguage.locale
            val result = tts?.setLanguage(locale)
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(
                    "TtsManager", 
                    "${languageConfig.targetLanguage.displayName} language not supported or missing data"
                )
            } else {
                isInitialized = true
                Log.d(
                    "TtsManager", 
                    "TTS initialized for ${languageConfig.targetLanguage.displayName}"
                )
            }
        } else {
            Log.e("TtsManager", "TTS initialization failed")
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            Log.w(
                "TtsManager", 
                "TTS not initialized for ${languageConfig.targetLanguage.displayName}"
            )
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
