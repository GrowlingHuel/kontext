package com.kontext.ui.story

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.kontext.data.local.StoryLoader
import com.kontext.ui.theme.KontextTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class StoryActivity : ComponentActivity() {

    @Inject
    lateinit var storyLoader: StoryLoader

    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KontextTheme {
                // State hoisted here to bridge Activity implementation with Compose
                var currentlyPlayingIndex by remember { mutableIntStateOf(-1) }
                var isAutoPlaying by remember { mutableStateOf(false) }
                // Listener trigger bridge
                val ttsListenerTrigger = remember { mutableStateOf<String?>(null) }

                // Retrieve User Name for "speak" logic
                val prefs = remember { getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
                val userName = remember { prefs.getString("user_name", "Du") ?: "Du" }

                // Initialize TTS
                DisposableEffect(Unit) {
                    tts = TextToSpeech(this@StoryActivity) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            val result = tts.setLanguage(Locale.GERMAN)
                            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                Toast.makeText(this@StoryActivity, "German language data missing. Please check TTS settings.", Toast.LENGTH_LONG).show()
                            }
                            
                            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) {
                                    // Generally we set index when calling speak, 
                                    // but this confirms it started.
                                }

                                override fun onDone(utteranceId: String?) {
                                    // Signal UI to update
                                    runOnUiThread {
                                        ttsListenerTrigger.value = utteranceId
                                    }
                                }

                                override fun onError(utteranceId: String?) {
                                    runOnUiThread {
                                        currentlyPlayingIndex = -1
                                        isAutoPlaying = false
                                    }
                                }
                            })
                        } else {
                            Toast.makeText(this@StoryActivity, "TTS Initialization Failed!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    onDispose {
                        tts.stop()
                        tts.shutdown()
                    }
                }
                
                // Helper function to call TTS
                fun speak(text: String, index: Int) {
                    val cleanText = text.replace("{{HERO}}", userName)
                    val params = Bundle()
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, index.toString())
                    
                    currentlyPlayingIndex = index
                    tts.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, index.toString())
                }

                fun stop() {
                    tts.stop()
                    currentlyPlayingIndex = -1
                    isAutoPlaying = false
                }
                
                // Handle TTS Done Event to Trigger Next if AutoPlaying
                LaunchedEffect(ttsListenerTrigger.value) {
                    val id = ttsListenerTrigger.value
                    if (id != null) {
                         val finishedIndex = id.toIntOrNull() ?: -1
                         // Check strictly if the finished one is the current one before proceeding? 
                         // Or just rely on state.
                         
                         if (isAutoPlaying) {
                              // We can't determine "Next" here without the list of sentences.
                              // So we signal 'StoryScreen' to play next via a callback or observing 'currentlyPlayingIndex' change?
                              // Actually, simpler: passing `currentlyPlayingIndex` as state to StoryScreen is done. 
                              // But logic for "Next" is inside StoryScreen or here?
                              // Ideally here if we had the list. But we don't.
                              // So we rely on StoryScreen to observe 'ttsListenerTrigger' passed down?
                              // No, let's pass a generic "onPlaybackFinished" event to StoryScreen.
                         } else {
                             if (currentlyPlayingIndex == finishedIndex) {
                                 currentlyPlayingIndex = -1
                             }
                         }
                    }
                }

                StoryScreen(
                    storyLoader = storyLoader,
                    currentlyPlayingIndex = currentlyPlayingIndex,
                    isAutoPlaying = isAutoPlaying,
                    lastFinishedUtteranceId = ttsListenerTrigger.value,
                    onPlayAudio = { text, index -> 
                        isAutoPlaying = false
                        speak(text, index) 
                    },
                    onToggleAutoPlay = { shouldPlay ->
                        isAutoPlaying = shouldPlay
                        if (!shouldPlay) stop()
                    },
                    onStopAudio = { stop() },
                    onAutoPlayNext = { nextIndex, text ->
                        if (isAutoPlaying) {
                            speak(text, nextIndex)
                        } else {
                            // Logic for manual finish
                            currentlyPlayingIndex = -1
                        }
                    },
                    onFinished = { finish() }
                )
            }
        }
    }
}
