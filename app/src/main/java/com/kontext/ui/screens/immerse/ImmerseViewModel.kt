package com.kontext.ui.screens.immerse

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kontext.data.repository.StoryRepository
import com.kontext.data.repository.VocabRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.graphics.Bitmap
import com.kontext.data.repository.ImageRepository
import com.kontext.data.repository.StorySentence
// ... other imports

sealed class ImmerseUiState {
    object Initial : ImmerseUiState()
    data class Loading(val message: String) : ImmerseUiState()
    data class Success(
        val storySegments: List<StorySentence>,
        val image: Bitmap? = null,
        val selectedWordTranslation: String? = null // For Dictionary Lookup
    ) : ImmerseUiState()
    data class Error(val message: String) : ImmerseUiState()
}

@HiltViewModel
class ImmerseViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val storyRepository: StoryRepository,
    private val imageRepository: ImageRepository
) : ViewModel() {

    private val _uiState = mutableStateOf<ImmerseUiState>(ImmerseUiState.Initial)
    val uiState: State<ImmerseUiState> = _uiState

    fun generateStory() {
        viewModelScope.launch {
            _uiState.value = ImmerseUiState.Loading("Writing your story...")
            try {
                // Get 10 random words
                val cards = vocabRepository.getRandomCards(10)
                if (cards.isEmpty()) {
                    _uiState.value = ImmerseUiState.Error("No vocabulary found. Please seed the database first.")
                    return@launch
                }

                val words = cards.map { it.germanTerm }
                
                // 1. Generate Story
                val storyResponse = storyRepository.generateStory(words)
                
                // Show intermediate state with text while loading image
                _uiState.value = ImmerseUiState.Success(storyResponse.sentences, null)
                
                // 2. Generate Image
                _uiState.value = ImmerseUiState.Loading("Painting the scene...")
                val image = imageRepository.generateScene(storyResponse.imageDescription)
                
                // 3. Final State
                _uiState.value = ImmerseUiState.Success(storyResponse.sentences, image)
                
            } catch (e: Exception) {
                _uiState.value = ImmerseUiState.Error("Failed to generate story: ${e.message}")
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
