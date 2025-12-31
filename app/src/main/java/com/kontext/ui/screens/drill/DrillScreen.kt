package com.kontext.ui.screens.drill

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import com.kontext.data.local.entity.VocabCard

@Composable
fun DrillScreen(
    viewModel: DrillViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState

    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is DrillUiState.Initial, is DrillUiState.Loading -> {
                CircularProgressIndicator()
            }
            is DrillUiState.Empty -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "All caught up!",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Come back later for more reviews.")
                }
            }
            is DrillUiState.Review -> {
                ReviewContent(
                    state = state,
                    onFlip = viewModel::flipCard,
                    onGrade = viewModel::gradeCard,
                    onPlayAudio = viewModel::playAudio
                )
            }
        }
    }
}

@Composable
fun ReviewContent(
    state: DrillUiState.Review,
    onFlip: () -> Unit,
    onGrade: (Int) -> Unit,
    onPlayAudio: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Safe Area / Stats
        Text(
            text = "${state.stats} (${state.totalCount} Total)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        // Flashcard
        FlashCard(
            card = state.currentCard,
            isFlipped = state.isFlipped,
            onClick = onFlip,
            onPlayAudio = onPlayAudio,
            modifier = Modifier.weight(1f).padding(vertical = 32.dp)
        )

        // Controls
        if (state.isFlipped) {
            GradingControls(onGrade = onGrade)
        } else {
            // Placeholder to keep spacing or hint
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Tap card to flip", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun FlashCard(
    card: VocabCard,
    isFlipped: Boolean,
    onClick: () -> Unit,
    onPlayAudio: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto-play word on flip (User request: less overwhelming)
    LaunchedEffect(isFlipped) {
        if (isFlipped) {
            onPlayAudio(card.targetLanguageTerm)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                
                // Front (German)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = card.targetLanguageTerm,
                        style = MaterialTheme.typography.displayMedium,
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { onPlayAudio(card.targetLanguageTerm) }) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Play Term Audio"
                        )
                    }
                }
                
                // Example Sentence German
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = card.exampleSentenceTarget,
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    IconButton(onClick = { onPlayAudio(card.exampleSentenceTarget) }) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Play Sentence Audio"
                        )
                    }
                }

                if (isFlipped) {
                    Spacer(modifier = Modifier.height(32.dp))
                    // Divider or visual break
                    Text(
                        text = "---",
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Back (English)
                    Text(
                        text = card.nativeLanguageTerm,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = card.exampleSentenceNative,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun GradingControls(onGrade: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Again (1)
        Button(
            onClick = { onGrade(1) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Again")
        }
        
        // Hard (2)
        Button(
            onClick = { onGrade(2) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Hard")
        }
        
        // Good (3)
        Button(
            onClick = { onGrade(3) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Good")
        }
        
        // Easy (4)
        Button(
            onClick = { onGrade(4) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Text("Easy")
        }
    }
}
