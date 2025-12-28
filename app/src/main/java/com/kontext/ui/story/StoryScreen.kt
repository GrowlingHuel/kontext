package com.kontext.ui.story

import android.content.Context
import android.graphics.BitmapFactory
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kontext.data.local.StoryLoader
import com.kontext.domain.model.StoryLevel
import com.kontext.domain.model.UserGender
import java.util.Locale

@Composable
fun StoryScreen(
    storyLoader: StoryLoader,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    // Initialize TTS
    DisposableEffect(context) {
        val ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.GERMAN
            }
        }
        tts = ttsInstance
        onDispose {
            ttsInstance.shutdown()
        }
    }

    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    
    // Load saved level (Default to 0: Prologue)
    val savedLevel = remember { prefs.getInt("last_level", 0) }
    
    // State
    var currentLevelIndex by remember { mutableIntStateOf(savedLevel) }
    var lastChoiceSide by remember { mutableStateOf(prefs.getString("last_choice", "A") ?: "A") } 
    
    // Persist Level Change
    LaunchedEffect(currentLevelIndex, lastChoiceSide) {
        prefs.edit()
            .putInt("last_level", currentLevelIndex)
            .putString("last_choice", lastChoiceSide)
            .apply()
    }

    val stories = remember { storyLoader.loadStories(context) } 
    
    // Logging for Debugging
    LaunchedEffect(currentLevelIndex, lastChoiceSide) {
        android.util.Log.d("StoryFlow", "Loading Level: $currentLevelIndex, Last Choice: $lastChoiceSide")
    }

    // TTS Safety Wrapper
    val safeExample = { text: String ->
        if (tts != null) {
             tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
             android.util.Log.w("StoryFlow", "TTS not initialized")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        
        if (currentLevelIndex == 0) {
            // PROLOGUE SCREEN
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Placeholder image or Text
                Text("PROLOGUE", style = MaterialTheme.typography.headlineLarge)
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .padding(16.dp)
            ) {
                Text(
                    "Arrival in Leipzig...",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    "You stand at the station. The air smells of coal and cheap coffee. A stranger watches you.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Prologue Choices -> Level 1
                 if (stories.isNotEmpty()) {
                    val firstLevel = stories[0]
                    Text("What do you do?", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { currentLevelIndex = 1; lastChoiceSide = "A" }, modifier = Modifier.fillMaxWidth()) {
                        Text(firstLevel.choiceA.prompt.ifBlank { "Choice A" })
                    }
                    Button(onClick = { currentLevelIndex = 1; lastChoiceSide = "B" }, modifier = Modifier.fillMaxWidth()) {
                        Text(firstLevel.choiceB.prompt.ifBlank { "Choice B" })
                    }
                } else {
                    Text("No stories loaded.")
                }
            }

        } else {
            // STORY CONTENT (Level 1+)
            // Map LevelIndex 1 -> Stories Index 0
            val storyIndex = currentLevelIndex - 1
            val currentLevel = stories.getOrNull(storyIndex)
            
            if (currentLevel != null) {
                val currentChoiceData = if (lastChoiceSide == "A") currentLevel.choiceA else currentLevel.choiceB
                
                // User Gender Logic
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val genderStr = prefs.getString("user_gender", "MALE")
                val sentences = if (genderStr == "FEMALE") currentChoiceData.sentencesF else currentChoiceData.sentencesM
                val enSentences = currentChoiceData.sentencesEn

                // Image Loading
                val imageBitmap = remember(currentLevel.level, lastChoiceSide) {
                    try {
                        val filename = "story_${currentLevel.level}_$lastChoiceSide.jpg"
                        val stream = context.assets.open(filename)
                        BitmapFactory.decodeStream(stream).asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                }

                // UI
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Story Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.4f),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.weight(0.4f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Image Missing") }
                }

                val listState = rememberLazyListState() // Restored
                
                // Logic: Show choices only when at end of list
                val isAtEnd by remember {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val visibleItemsInfo = layoutInfo.visibleItemsInfo
                        if (visibleItemsInfo.isEmpty()) {
                            false
                        } else {
                            val lastVisibleItem = visibleItemsInfo.last()
                            // If last visible item is the last index in data (totalItems - 1)
                            lastVisibleItem.index == layoutInfo.totalItemsCount - 1
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(sentences) { index, deText ->
                         val enText = enSentences.getOrElse(index) { "" }
                         SentenceItem(deText, enText) { safeExample(deText) }
                    }
                    
                    // NEXT LEVEL CHOICES (Visible only when scrolled to end)
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        AnimatedVisibility(
                            visible = isAtEnd,
                            enter = fadeIn()
                        ) {
                            val nextStoryIndex = storyIndex + 1
                            if (nextStoryIndex < stories.size) {
                                val nextStory = stories[nextStoryIndex]
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("What do you do?", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { currentLevelIndex++; lastChoiceSide = "A" }, modifier = Modifier.fillMaxWidth()) {
                                        Text(nextStory.choiceA.prompt.ifBlank { "Option A" })
                                    }
                                    OutlinedButton(onClick = { currentLevelIndex++; lastChoiceSide = "B" }, modifier = Modifier.fillMaxWidth()) {
                                        Text(nextStory.choiceB.prompt.ifBlank { "Option B" })
                                    }
                                }
                            } else {
                                Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text("Finish") }
                            }
                        }
                    }
                }
            } else {
                 // Error or End
                 Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Story Ended") }
            }
        }
    }
}

@Composable
fun SentenceItem(
    germanText: String,
    englishText: String,
    onPlay: () -> Unit
) {
    var showEnglish by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { showEnglish = !showEnglish },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = germanText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onPlay) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            AnimatedVisibility(visible = showEnglish) {
                Text(
                    text = englishText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
