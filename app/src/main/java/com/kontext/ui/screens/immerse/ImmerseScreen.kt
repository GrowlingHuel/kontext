package com.kontext.ui.screens.immerse

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight // Added import
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState // Added
import androidx.hilt.navigation.compose.hiltViewModel
import com.kontext.data.repository.StorySentence
import com.kontext.ui.screens.drill.DrillViewModel

import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext // Needed for Toast

@Composable
fun ImmerseScreen(
    viewModel: ImmerseViewModel = hiltViewModel(),
    drillViewModel: DrillViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsState()
    val uiState by viewModel.uiState
    val context = LocalContext.current
    val toastMessage by viewModel.toastMessage
    var showLibrary by remember { mutableStateOf(false) }

    // Library Dialog
    if (showLibrary) {
        AlertDialog(
            onDismissRequest = { showLibrary = false },
            title = { Text("Story Library") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    if (history.isEmpty()) {
                        item { Text("No saved stories yet.") }
                    } else {
                        items(history) { story ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.loadStoryFromLibrary(story)
                                        showLibrary = false
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = story.vocabSignature.replace("-", ", "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Saved: ${java.text.SimpleDateFormat("MMM dd, HH:mm").format(java.util.Date(story.createdAt))}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLibrary = false }) { Text("Close") }
            }
        )
    }

    // Handle Toast
    if (toastMessage != null) {
        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
        viewModel.clearToast()
    }

    // Translation Dialog
    if (uiState is ImmerseUiState.Success) {
        val successState = uiState as ImmerseUiState.Success
        if (successState.selectedWordTranslation != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearTranslation() },
                title = { Text("Dictionary") },
                text = { Text(successState.selectedWordTranslation) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearTranslation() }) {
                        Text("Close")
                    }
                }
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val state = uiState) {
            is ImmerseUiState.Initial -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Text("Immerse Mode", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Generate a story based on your vocabulary.", textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = { viewModel.generateStory() }) { Text("Generate Story") }
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { showLibrary = true }) { Text("View Library") }
                    }
                }
            }
            is ImmerseUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(state.message)
                    }
                }
            }
            is ImmerseUiState.Success -> {
                // Sticky Header (Fixed Image - 30% Height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.3f)
                ) {
                    if (state.image != null) {
                        Image(
                            bitmap = state.image.asImageBitmap(),
                            contentDescription = "Story Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No Image")
                        }
                    }
                }

                // Scrollable Story Content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Takes remaining space
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item { Spacer(modifier = Modifier.height(16.dp)) }

                    items(state.storySegments) { segment ->
                        StorySegmentRow(
                            segment = segment,
                            onPlayAudio = { drillViewModel.playAudio(it) },
                            onWordClick = { word -> viewModel.lookupWord(word) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = { viewModel.generateStory() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Generate Another")
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
            is ImmerseUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error", color = MaterialTheme.colorScheme.error)
                        Text(state.message)
                        Button(onClick = { viewModel.generateStory() }) { Text("Retry") }
                    }
                }
            }
        }
    }
}

@Composable
fun StorySegmentRow(
    segment: StorySentence,
    onPlayAudio: (String) -> Unit,
    onWordClick: (String) -> Unit
) {
    var isTranslated by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isTranslated = !isTranslated }, // Toggle full translation
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconButton(
                onClick = { onPlayAudio(segment.de) },
                modifier = Modifier.size(32.dp).align(Alignment.CenterVertically)
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = "Play Audio")
            }
            
            Column(modifier = Modifier.padding(start = 12.dp)) {
                // Interactive German Text
                val annotatedString = buildAnnotatedString {
                    val words = segment.de.split(" ")
                    words.forEachIndexed { index, word ->
                        pushStringAnnotation(tag = "WORD", annotation = word)
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(word)
                        }
                        pop()
                        if (index < words.size - 1) append(" ")
                    }
                }

                ClickableText(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(tag = "WORD", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                onWordClick(annotation.item)
                            }
                    }
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                if (isTranslated) {
                    Text(
                        text = segment.en,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Tap card to translate sentences",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
