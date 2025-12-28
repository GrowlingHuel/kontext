package com.kontext.ui.screens.drill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kontext.data.local.entity.VocabCard
import com.kontext.data.repository.VocabRepository
import com.kontext.domain.usecase.GetNextReviewCardUseCase
import com.kontext.domain.usecase.UpdateCardMasteryUseCase
import com.kontext.util.SeedManager
import com.kontext.util.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import javax.inject.Inject

sealed class DrillUiState {
    object Initial : DrillUiState()
    object Loading : DrillUiState()
    object Empty : DrillUiState()
    data class Review(
        val stats: String, // e.g. "10 cards due"
        val totalCount: Int,
        val currentCard: VocabCard,
        val isFlipped: Boolean = false
    ) : DrillUiState()
}

@HiltViewModel
class DrillViewModel @Inject constructor(
    private val repository: VocabRepository, // Kept for count/basic info if needed, but ideally UseCase too
    private val getNextReviewCardUseCase: GetNextReviewCardUseCase,
    private val updateCardMasteryUseCase: UpdateCardMasteryUseCase,
    private val seedManager: SeedManager,
    private val ttsManager: TtsManager
) : ViewModel() {

    private val _uiState = mutableStateOf<DrillUiState>(DrillUiState.Initial)
    val uiState: State<DrillUiState> = _uiState

    // Queue of cards for the session
    private var sessionQueue = ArrayDeque<VocabCard>()
    private var totalCardsCount = 0

    init {
        loadSession()
    }

    fun playAudio(text: String) {
        ttsManager.speak(text)
    }

    private fun loadSession() {
        viewModelScope.launch {
            _uiState.value = DrillUiState.Loading
            
            // Ensure seeded
            seedManager.seedIfNeeded()
            
            // basic stats
            totalCardsCount = repository.getCount()
            val dueCards = getNextReviewCardUseCase()
            sessionQueue.clear()
            sessionQueue.addAll(dueCards)

            showNextCard()
        }
    }

    private fun showNextCard() {
        val nextCard = sessionQueue.firstOrNull()
        if (nextCard != null) {
            val count = sessionQueue.size
            _uiState.value = DrillUiState.Review(
                stats = "$count cards due",
                totalCount = totalCardsCount,
                currentCard = nextCard,
                isFlipped = false
            )
        } else {
            _uiState.value = DrillUiState.Empty
        }
    }

    fun flipCard() {
        val currentState = _uiState.value
        if (currentState is DrillUiState.Review) {
            _uiState.value = currentState.copy(isFlipped = !currentState.isFlipped)
        }
    }

    fun gradeCard(rating: Int) {
        val currentState = _uiState.value
        if (currentState is DrillUiState.Review) {
            val card = currentState.currentCard
            
            viewModelScope.launch {
                // Use Case handles logic + persistence
                updateCardMasteryUseCase(card, rating)
                
                // Remove from head of queue
                sessionQueue.removeFirst()
                
                // If rating was "Again" (1), strictly re-queue it at the end of this session?
                // For this sprint, let's keep it simple: updated -> removed from *current* queue -> Done for now.
                // Re-queueing logic can be added if we want strictly "Until correct".
                
                showNextCard()
            }
        }
    }
}
