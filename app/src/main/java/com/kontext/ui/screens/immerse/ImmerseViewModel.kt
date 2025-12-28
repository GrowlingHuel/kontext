package com.kontext.ui.screens.immerse

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kontext.data.local.FileStorageHelper
import com.kontext.data.repository.StoryRepository
import com.kontext.data.repository.VocabRepository
import com.kontext.data.repository.StorySentence
import com.kontext.data.local.entity.StoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.graphics.Bitmap
import com.kontext.data.repository.StoryResponse
import org.json.JSONObject

sealed class ImmerseUiState {
    object Initial : ImmerseUiState()
    data class Loading(val message: String) : ImmerseUiState()
    data class Success(
        val storySegments: List<StorySentence>,
        val image: Bitmap? = null,
        val selectedWordTranslation: String? = null
    ) : ImmerseUiState()
    data class Error(val message: String) : ImmerseUiState()
}

@HiltViewModel
class ImmerseViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val storyRepository: StoryRepository,
    private val fileStorageHelper: FileStorageHelper
) : ViewModel() {

    private val _uiState = mutableStateOf<ImmerseUiState>(ImmerseUiState.Initial)
    val uiState: State<ImmerseUiState> = _uiState

    val history = storyRepository.getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun generateStory() {
        viewModelScope.launch {
            _uiState.value = ImmerseUiState.Loading("Crafting your story...")
            try {
                // Get 10 random words
                val cards = vocabRepository.getRandomCards(10)
                if (cards.isEmpty()) {
                    _uiState.value = ImmerseUiState.Error("No vocabulary found. Please seed the database first.")
                    return@launch
                }

                val words = cards.map { it.germanTerm }
                
                // Repo handles generation AND caching AND image generation now
                val storyResponse = storyRepository.generateStory(words)
                
                _uiState.value = ImmerseUiState.Success(storyResponse.sentences, storyResponse.image)
                
            } catch (e: Exception) {
                _uiState.value = ImmerseUiState.Error("Failed to generate story: ${e.message}")
            }
        }
    }

    fun loadStoryFromLibrary(entity: StoryEntity) {
        viewModelScope.launch {
            _uiState.value = ImmerseUiState.Loading("Loading from library...")
            try {
                val image = fileStorageHelper.loadBitmapFromPath(entity.imagePath)
                
                // Parse JSON
                val jsonObject = JSONObject(entity.jsonContent)
                val jsonSentences = jsonObject.optJSONArray("sentences")
                val sentences = mutableListOf<StorySentence>()
                if (jsonSentences != null) {
                    for (i in 0 until jsonSentences.length()) {
                        val item = jsonSentences.getJSONObject(i)
                        sentences.add(
                            StorySentence(
                                de = item.optString("de", ""),
                                en = item.optString("en", "")
                            )
                        )
                    }
                }

                _uiState.value = ImmerseUiState.Success(sentences, image)
            } catch (e: Exception) {
                _uiState.value = ImmerseUiState.Error("Failed to load story: ${e.message}")
            }
        }
    }

    val toastMessage = mutableStateOf<String?>(null)

    fun lookupWord(word: String) {
        viewModelScope.launch {
            val currentState = uiState.value
            if (currentState is ImmerseUiState.Success) {
                // Strip punctuation and lowercase
                val cleanWord = word.replace(Regex("[^A-Za-z0-9äöüÄÖÜß]"), "").lowercase()
                
                val translation = vocabRepository.findEnglishForGerman(cleanWord)
                
                if (translation != null) {
                    _uiState.value = currentState.copy(selectedWordTranslation = "$cleanWord: $translation")
                } else {
                    toastMessage.value = "Word '$cleanWord' not in your 5k study list."
                }
            }
        }
    }

    fun clearToast() {
        toastMessage.value = null
    }

    fun clearTranslation() {
        val currentState = uiState.value
        if (currentState is ImmerseUiState.Success) {
            _uiState.value = currentState.copy(selectedWordTranslation = null)
        }
    }
}
