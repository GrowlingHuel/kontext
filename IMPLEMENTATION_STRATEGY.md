# Kontext: Implementation Strategy
## Refactoring to Language-Agnostic Architecture

**Created:** 2025-12-31  
**Based on:** CODEBASE_ANALYSIS_AND_MVP_ROADMAP.md  
**Goal:** Transform Kontext into a language-agnostic learning platform

---

## 🎯 Strategy Overview

This document provides a **step-by-step implementation plan** to address all issues identified in the codebase analysis. The strategy is organized into **4 phases** that can be executed sequentially, with each phase delivering working, testable improvements.

### Implementation Principles

1. **Non-Breaking First:** Fix broken windows without changing public APIs
2. **Test As We Go:** Add unit tests for each refactored component
3. **Feature Flags:** Use configuration to toggle between old/new behavior during migration
4. **Single Responsibility:** Each PR should address one concern

### Time Estimate: **3-4 Weeks** (60-80 hours)

---

## 📋 Phase 1: Foundation Hardening (Week 1)
**Goal:** Fix broken windows and standardize error handling  
**Estimated Effort:** 15-20 hours

### 1.1 Standardize Repository Error Handling

#### Problem
- `ImageRepository` returns `Bitmap?`
- `VocabRepository` returns direct types
- `StoryRepository` returns domain models with embedded errors

#### Solution: Introduce Result Wrapper

**Step 1.1.1:** Create `Result.kt` sealed class

```kotlin
// util/Result.kt
package com.kontext.util

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(
        val exception: Exception,
        val message: String = exception.message ?: "Unknown error"
    ) : Result<Nothing>()
    
    object Loading : Result<Nothing>()
}

// Extension functions for ViewModels
fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) action(data)
    return this
}

fun <T> Result<T>.onError(action: (Exception, String) -> Unit): Result<T> {
    if (this is Result.Error) action(exception, message)
    return this
}
```

**Step 1.1.2:** Refactor `ImageRepository`

```kotlin
// data/repository/ImageRepository.kt
interface ImageRepository {
    suspend fun generateScene(prompt: String): Result<Bitmap>  // ← Changed
}

@Singleton
class ImageRepositoryImpl @Inject constructor(
    private val client: HttpClient
) : ImageRepository {

    override suspend fun generateScene(prompt: String): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            val url = "https://generativelanguage.googleapis.com/v1beta/models/imagen-4.0-generate-001:predict?key=$apiKey"

            val requestBody = GenerationRequest(
                instances = listOf(PromptInstance(prompt)),
                parameters = GenerationParameters(sampleCount = 1)
            )

            val response: JsonObject = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()

            val predictions = response["predictions"]?.jsonArray
            if (predictions != null && predictions.isNotEmpty()) {
                val firstPrediction = predictions[0].jsonObject
                val base64String = firstPrediction["bytesBase64Encoded"]?.jsonPrimitive?.content
                
                if (!base64String.isNullOrEmpty()) {
                    val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    return@withContext Result.Success(bitmap)
                }
            }
            
            Result.Error(Exception("No image data in response"))
        } catch (e: Exception) {
            Log.e("ImageRepository", "Image generation failed", e)
            Result.Error(e)
        }
    }
}
```

**Step 1.1.3:** Update `StoryRepositoryImpl` to use Result wrapper

```kotlin
// data/repository/StoryRepository.kt
interface StoryRepository {
    suspend fun generateStory(vocabList: List<String>): Result<StoryResponse>  // ← Changed
    fun getHistory(): Flow<List<StoryEntity>>
}

// In StoryRepositoryImpl.kt
override suspend fun generateStory(vocabList: List<String>): Result<StoryResponse> = withContext(Dispatchers.IO) {
    try {
        // ... existing logic ...
        
        // Generate Image with Result handling
        val generatedImage = when (val imageResult = imageRepository.generateScene(imagePrompt)) {
            is Result.Success -> imageResult.data
            is Result.Error -> {
                Log.w("StoryRepository", "Image generation failed, continuing without image", imageResult.exception)
                null
            }
            Result.Loading -> null
        }
        
        // ... rest of logic ...
        
        Result.Success(parseJsonToResponse(jsonString, generatedImage))
    } catch (e: Exception) {
        Log.e("StoryRepository", "Story generation failed", e)
        Result.Error(e)
    }
}
```

**Verification:**
- [ ] Unit test: `ImageRepositoryTest.kt` - mock Ktor client, verify Result.Success/Error
- [ ] Integration test: Generate story with failing image API, verify story still succeeds

---

### 1.2 Remove Dead Code

**Step 1.2.1:** Decide on story caching strategy

**Option A (Recommended):** Delete unused caching for MVP
```kotlin
// Delete StoryEntity.kt, StoryDao.kt
// Remove from KontextDatabase.kt:
// abstract fun storyDao(): StoryDao

// Simplify StoryRepositoryImpl:
class StoryRepositoryImpl @Inject constructor(
    private val imageRepository: ImageRepository
    // Remove: database, fileStorageHelper
) : StoryRepository {
    
    override suspend fun generateStory(vocabList: List<String>): Result<StoryResponse> {
        // Remove lines 44-50 (cache lookup)
        // Remove lines 91-98 (cache persistence)
        
        // Just generate and return - no caching
    }
    
    override fun getHistory(): Flow<List<StoryEntity>> {
        // Delete this method entirely for MVP
    }
}
```

**Option B:** Keep caching, but fix it properly
```kotlin
// Re-enable cache with prompt versioning
private val PROMPT_VERSION = "v2" // Increment when prompt template changes

override suspend fun generateStory(vocabList: List<String>): Result<StoryResponse> = withContext(IO) {
    try {
        val sortedWords = vocabList.map { it.lowercase().trim() }.sorted()
        val signature = "$PROMPT_VERSION-${sortedWords.joinToString("-")}"
        
        // Check cache ← UNCOMMENTED
        val cachedStory = storyDao.getStoryBySignature(signature)
        if (cachedStory != null) {
            val image = fileStorageHelper.loadBitmapFromPath(cachedStory.imagePath)
            return@withContext Result.Success(parseJsonToResponse(cachedStory.jsonContent, image))
        }
        
        // ... network call ...
    }
}
```

**Decision Required:** Choose Option A or B based on MVP requirements

**Verification:**
- [ ] If Option A: Verify app compiles after removing StoryEntity
- [ ] If Option B: Add test verifying cache hit with same vocab signature

---

### 1.3 Consolidate CSV Parsing

**Step 1.3.1:** Extract parsing logic to `CsvParser`

```kotlin
// util/CsvParser.kt
@Singleton
class CsvParser @Inject constructor() {
    
    fun parseVocabCsv(
        inputStream: InputStream,
        userId: String,
        delimiter: String = "|"
    ): List<VocabCard> {
        val cards = mutableListOf<VocabCard>()
        
        inputStream.bufferedReader().use { reader ->
            var line = reader.readLine()
            
            // Skip header if present
            if (line != null && isHeaderRow(line)) {
                line = reader.readLine()
            }
            
            while (line != null) {
                parseVocabLine(line, userId, delimiter)?.let { cards.add(it) }
                line = reader.readLine()
            }
        }
        
        return cards
    }
    
    private fun isHeaderRow(line: String): Boolean {
        return line.contains("German", ignoreCase = true) || 
               line.contains("English", ignoreCase = true)
    }
    
    private fun parseVocabLine(line: String, userId: String, delimiter: String): VocabCard? {
        val parts = line.split(delimiter)
        if (parts.size < 4) return null
        
        return VocabCard(
            germanTerm = parts[0].trim(),
            englishTerm = parts[1].trim(),
            exampleSentenceGerman = parts[2].trim(),
            exampleSentenceEnglish = parts[3].trim(),
            nextReviewTimestamp = System.currentTimeMillis(),
            userId = userId
        )
    }
}
```

**Step 1.3.2:** Simplify `SeedManager`

```kotlin
// util/SeedManager.kt
@Singleton
class SeedManager @Inject constructor(
    private val database: KontextDatabase,
    private val context: Context,
    private val sessionManager: SessionManager,
    private val csvParser: CsvParser  // ← NEW
) {
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        try {
            val count = database.vocabCardDao().getCount()
            if (count > 4500) {
                Log.d("SeedManager", "Database already seeded with $count cards")
                return@withContext
            }
            
            if (count > 0) {
                Log.d("SeedManager", "Partial seed detected, clearing...")
                database.vocabCardDao().deleteAll()
            }
            
            val inputStream = context.resources.openRawResource(R.raw.german_4000)
            val cards = csvParser.parseVocabCsv(inputStream, sessionManager.getCurrentUserId())
            
            cards.chunked(500).forEach { chunk ->
                database.vocabCardDao().insertAll(chunk)
            }
            
            Log.d("SeedManager", "Seeded ${cards.size} cards")
        } catch (e: Exception) {
            Log.e("SeedManager", "Seeding failed", e)
        }
    }
    
    // DELETE the private parseCsv() method - now handled by CsvParser
}
```

**Verification:**
- [ ] Unit test: `CsvParserTest.kt` - test header detection, malformed rows
- [ ] Integration test: Seed DB, verify 4000+ cards inserted

---

## 📋 Phase 2: Language Abstraction Layer (Week 2)
**Goal:** Make codebase language-agnostic without breaking existing functionality  
**Estimated Effort:** 20-25 hours

### 2.1 Create Language Configuration System

**Step 2.1.1:** Define language domain models

```kotlin
// domain/model/Language.kt
package com.kontext.domain.model

import java.util.Locale

enum class Language(
    val code: String,
    val displayName: String,
    val locale: Locale
) {
    GERMAN("de", "German", Locale.GERMAN),
    SPANISH("es", "Spanish", Locale("es", "ES")),
    FRENCH("fr", "French", Locale.FRENCH);
    
    companion object {
        fun fromCode(code: String): Language? = values().find { it.code == code }
    }
}

enum class CEFRLevel(val displayName: String, val topWordCount: Int) {
    A1("Beginner", 500),
    A2("Elementary", 1000),
    B1("Intermediate", 2000),
    B2("Upper Intermediate", 3000),
    C1("Advanced", 5000)
}
```

**Step 2.1.2:** Create LanguageConfig data class

```kotlin
// domain/model/LanguageConfig.kt
package com.kontext.domain.model

import androidx.annotation.RawRes

data class LanguageConfig(
    val targetLanguage: Language,
    val nativeLanguage: Language = Language.GERMAN, // User's L1 (English for most users, but configurable)
    val level: CEFRLevel = CEFRLevel.A1,
    @RawRes val csvResourceId: Int
) {
    companion object {
        // Default configurations for each language
        fun forGerman() = LanguageConfig(
            targetLanguage = Language.GERMAN,
            nativeLanguage = Language.GERMAN, // English translation in CSV
            csvResourceId = R.raw.german_4000
        )
        
        fun forSpanish() = LanguageConfig(
            targetLanguage = Language.SPANISH,
            nativeLanguage = Language.GERMAN, // Will be R.raw.spanish_5000
            csvResourceId = 0 // Placeholder until Spanish CSV added
        )
    }
}
```

**Step 2.1.3:** Create Hilt module for language configuration

```kotlin
// di/LanguageModule.kt
package com.kontext.di

import com.kontext.domain.model.LanguageConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LanguageModule {
    
    @Provides
    @Singleton
    fun provideLanguageConfig(): LanguageConfig {
        // For MVP: Hardcoded German
        // Post-MVP: Read from SessionManager or user profile
        return LanguageConfig.forGerman()
    }
}
```

**Verification:**
- [ ] App compiles with new module
- [ ] Can inject `LanguageConfig` into existing components

---

### 2.2 Refactor TtsManager for Language Injection

**Step 2.2.1:** Update TtsManager to use LanguageConfig

```kotlin
// util/TtsManager.kt
@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val languageConfig: LanguageConfig  // ← NEW
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = languageConfig.targetLanguage.locale  // ← DYNAMIC
            val result = tts?.setLanguage(locale)
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TtsManager", "${languageConfig.targetLanguage.displayName} not supported")
            } else {
                isInitialized = true
                Log.d("TtsManager", "TTS initialized for ${languageConfig.targetLanguage.displayName}")
            }
        } else {
            Log.e("TtsManager", "TTS initialization failed")
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            Log.w("TtsManager", "TTS not initialized for ${languageConfig.targetLanguage.displayName}")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
```

**Verification:**
- [ ] Change `LanguageModule` to provide Spanish config
- [ ] Verify TTS initializes with Spanish locale
- [ ] Change back to German, verify German TTS works

---

### 2.3 Parameterize Story Generation

**Step 2.3.1:** Extract story prompt builder

```kotlin
// domain/usecase/StoryPromptBuilder.kt
package com.kontext.domain.usecase

import com.kontext.domain.model.LanguageConfig
import javax.inject.Inject

interface StoryPromptBuilder {
    fun buildPrompt(vocabWords: List<String>, config: LanguageConfig): String
}

class CEFRStoryPromptBuilder @Inject constructor() : StoryPromptBuilder {
    
    override fun buildPrompt(vocabWords: List<String>, config: LanguageConfig): String {
        val languageName = config.targetLanguage.displayName
        val level = config.level.displayName
        val topN = config.level.topWordCount
        val wordsJoined = vocabWords.sorted().joinToString(", ")
        
        return """
            You are a conservative $level $languageName teacher. 
            95% of your story MUST use the top $topN most common $languageName words. 
            You may use exactly TWO 'imagination' words per story, but they must be clear from context. 
            Do NOT use bizarre, rare, or complex compound words. 
            Keep sentences under 10 words each.
            
            Write a story using these target words: [$wordsJoined].
            
            Format the response as a valid JSON object with no markdown formatting. 
            The JSON should have this structure:
            { 
                "image_description": "(A vivid English prompt for an AI image generator describing the scene)", 
                "sentences": [ 
                    {"de": "$languageName sentence", "en": "English translation"}, 
                    ... 
                ] 
            }
        """.trimIndent()
    }
}
```

**Step 2.3.2:** Inject prompt builder into StoryRepository

```kotlin
// data/repository/StoryRepositoryImpl.kt
class StoryRepositoryImpl @Inject constructor(
    private val imageRepository: ImageRepository,
    private val promptBuilder: StoryPromptBuilder,  // ← NEW
    private val languageConfig: LanguageConfig       // ← NEW
) : StoryRepository {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash", 
        apiKey = BuildConfig.GEMINI_API_KEY,
        safetySettings = listOf(/* ... */)
    )

    override suspend fun generateStory(vocabList: List<String>): Result<StoryResponse> = withContext(IO) {
        try {
            // Use prompt builder instead of inline string
            val prompt = promptBuilder.buildPrompt(vocabList, languageConfig)
            
            val response = generativeModel.generateContent(prompt)
            val text = response.text ?: "{}"
            
            // ... rest of logic unchanged ...
            
            Result.Success(parseJsonToResponse(jsonString, generatedImage))
        } catch (e: Exception) {
            Log.e("StoryRepository", "Story generation failed", e)
            Result.Error(e)
        }
    }
}
```

**Step 2.3.3:** Bind interface in Hilt module

```kotlin
// di/RepositoryModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    abstract fun bindStoryPromptBuilder(
        impl: CEFRStoryPromptBuilder
    ): StoryPromptBuilder
    
    // ... existing bindings ...
}
```

**Verification:**
- [ ] Generate story, verify prompt uses correct language name
- [ ] Change language config to Spanish, verify prompt updates

---

### 2.4 Parameterize Seeding

**Step 2.4.1:** Update SeedManager to use LanguageConfig

```kotlin
// util/SeedManager.kt
@Singleton
class SeedManager @Inject constructor(
    private val database: KontextDatabase,
    private val context: Context,
    private val sessionManager: SessionManager,
    private val csvParser: CsvParser,
    private val languageConfig: LanguageConfig  // ← NEW
) {
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        try {
            val count = database.vocabCardDao().getCount()
            if (count > 4500) {
                Log.d("SeedManager", "Database already seeded")
                return@withContext
            }
            
            if (count > 0) {
                database.vocabCardDao().deleteAll()
            }
            
            // Use dynamic resource ID from config
            val inputStream = context.resources.openRawResource(languageConfig.csvResourceId)
            val cards = csvParser.parseVocabCsv(inputStream, sessionManager.getCurrentUserId())
            
            cards.chunked(500).forEach { chunk ->
                database.vocabCardDao().insertAll(chunk)
            }
            
            Log.d("SeedManager", "Seeded ${cards.size} ${languageConfig.targetLanguage.displayName} cards")
        } catch (e: Exception) {
            Log.e("SeedManager", "Seeding failed for ${languageConfig.targetLanguage.displayName}", e)
        }
    }
}
```

**Verification:**
- [ ] Seed succeeds with German CSV
- [ ] (After Phase 3) Seed succeeds with Spanish CSV

---

## 📋 Phase 3: Database Schema Migration (Week 3)
**Goal:** Make VocabCard language-agnostic  
**Estimated Effort:** 15-20 hours

### 3.1 Create New VocabCard Schema

**Step 3.1.1:** Define new entity structure

```kotlin
// data/local/entity/VocabCard.kt (Version 2)
@Entity(
    tableName = "vocab_cards",
    indices = [
        Index(value = ["language_code", "user_id"]),
        Index(value = ["next_review_timestamp"])
    ]
)
data class VocabCard(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    // Language-agnostic fields
    @ColumnInfo(name = "target_language_term")
    val targetLanguageTerm: String,  // ← Was germanTerm
    
    @ColumnInfo(name = "native_language_term")
    val nativeLanguageTerm: String,  // ← Was englishTerm
    
    @ColumnInfo(name = "example_sentence_target")
    val exampleSentenceTarget: String,  // ← Was exampleSentenceGerman
    
    @ColumnInfo(name = "example_sentence_native")
    val exampleSentenceNative: String,  // ← Was exampleSentenceEnglish
    
    @ColumnInfo(name = "language_code")
    val languageCode: String,  // ← NEW: "de", "es", "fr"
    
    // Existing fields (unchanged)
    @ColumnInfo(name = "mastery_level")
    val masteryLevel: Int = 0,
    
    @ColumnInfo(name = "next_review_timestamp")
    val nextReviewTimestamp: Long,
    
    @ColumnInfo(name = "audio_path")
    val audioPath: String? = null,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "last_reviewed_at")
    val lastReviewedAt: Long? = null,
    
    @ColumnInfo(name = "review_count")
    val reviewCount: Int = 0
)
```

**Step 3.1.2:** Create Room migration

```kotlin
// data/local/KontextDatabase.kt
@Database(
    entities = [VocabCard::class],
    version = 2,  // ← INCREMENT
    exportSchema = true
)
abstract class KontextDatabase : RoomDatabase() {
    abstract fun vocabCardDao(): VocabCardDao
}

// di/DatabaseModule.kt
@Provides
@Singleton
fun provideKontextDatabase(@ApplicationContext context: Context): KontextDatabase {
    return Room.databaseBuilder(
        context,
        KontextDatabase::class.java,
        "kontext_database"
    )
    .addMigrations(MIGRATION_1_2)  // ← ADD
    .build()
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create temporary table with new schema
        database.execSQL("""
            CREATE TABLE vocab_cards_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                target_language_term TEXT NOT NULL,
                native_language_term TEXT NOT NULL,
                example_sentence_target TEXT NOT NULL,
                example_sentence_native TEXT NOT NULL,
                language_code TEXT NOT NULL,
                mastery_level INTEGER NOT NULL,
                next_review_timestamp INTEGER NOT NULL,
                audio_path TEXT,
                user_id TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                last_reviewed_at INTEGER,
                review_count INTEGER NOT NULL
            )
        """)
        
        // Copy data from old table, adding language_code = 'de'
        database.execSQL("""
            INSERT INTO vocab_cards_new 
            (id, target_language_term, native_language_term, 
             example_sentence_target, example_sentence_native, 
             language_code, mastery_level, next_review_timestamp, 
             audio_path, user_id, created_at, last_reviewed_at, review_count)
            SELECT 
                id, german_term, english_term,
                example_sentence_german, example_sentence_english,
                'de', mastery_level, next_review_timestamp,
                audio_path, user_id, created_at, last_reviewed_at, review_count
            FROM vocab_cards
        """)
        
        // Drop old table
        database.execSQL("DROP TABLE vocab_cards")
        
        // Rename new table
        database.execSQL("ALTER TABLE vocab_cards_new RENAME TO vocab_cards")
        
        // Create indices
        database.execSQL("CREATE INDEX index_vocab_cards_language_code_user_id ON vocab_cards(language_code, user_id)")
        database.execSQL("CREATE INDEX index_vocab_cards_next_review_timestamp ON vocab_cards(next_review_timestamp)")
    }
}
```

**Step 3.1.3:** Update DAO queries

```kotlin
// data/local/dao/VocabCardDao.kt
@Dao
interface VocabCardDao {
    @Query("SELECT COUNT(*) FROM vocab_cards WHERE user_id = :userId AND language_code = :languageCode")
    suspend fun getCountForUserAndLanguage(userId: String, languageCode: String): Int
    
    @Query("""
        SELECT * FROM vocab_cards 
        WHERE user_id = :userId 
        AND language_code = :languageCode
        AND next_review_timestamp <= :now 
        ORDER BY next_review_timestamp ASC 
        LIMIT :limit
    """)
    suspend fun getCardsDueForUserAndLanguage(
        userId: String, 
        languageCode: String,
        now: Long, 
        limit: Int = 20
    ): List<VocabCard>
    
    @Query("""
        SELECT * FROM vocab_cards 
        WHERE user_id = :userId 
        AND language_code = :languageCode
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    suspend fun getRandomCardsForLanguage(
        userId: String,
        languageCode: String,
        limit: Int
    ): List<VocabCard>
    
    @Query("SELECT native_language_term FROM vocab_cards WHERE target_language_term = :targetTerm AND language_code = :languageCode LIMIT 1")
    suspend fun findTranslation(targetTerm: String, languageCode: String): String?
    
    // ... other methods unchanged ...
}
```

**Step 3.1.4:** Update Repository to use language-aware queries

```kotlin
// data/repository/VocabRepositoryImpl.kt
@Singleton
class VocabRepositoryImpl @Inject constructor(
    private val dao: VocabCardDao,
    private val sessionManager: SessionManager,
    private val languageConfig: LanguageConfig  // ← NEW
) : VocabRepository {
    
    override suspend fun getCount(): Int {
        val userId = sessionManager.getCurrentUserId()
        val languageCode = languageConfig.targetLanguage.code
        return dao.getCountForUserAndLanguage(userId, languageCode)
    }
    
    override suspend fun getCardsForReview(): List<VocabCard> {
        val userId = sessionManager.getCurrentUserId()
        val languageCode = languageConfig.targetLanguage.code
        val now = System.currentTimeMillis()
        return dao.getCardsDueForUserAndLanguage(userId, languageCode, now)
    }
    
    override suspend fun getRandomCards(limit: Int): List<VocabCard> {
        val userId = sessionManager.getCurrentUserId()
        val languageCode = languageConfig.targetLanguage.code
        return dao.getRandomCardsForLanguage(userId, languageCode, limit)
    }
    
    override suspend fun findEnglishForGerman(germanTerm: String): String? {
        val languageCode = languageConfig.targetLanguage.code
        return dao.findTranslation(germanTerm, languageCode)
    }
}
```

**Step 3.1.5:** Update CsvParser to populate language code

```kotlin
// util/CsvParser.kt
@Singleton
class CsvParser @Inject constructor(
    private val languageConfig: LanguageConfig  // ← NEW
) {
    
    private fun parseVocabLine(line: String, userId: String, delimiter: String): VocabCard? {
        val parts = line.split(delimiter)
        if (parts.size < 4) return null
        
        return VocabCard(
            targetLanguageTerm = parts[0].trim(),      // ← Renamed
            nativeLanguageTerm = parts[1].trim(),      // ← Renamed
            exampleSentenceTarget = parts[2].trim(),   // ← Renamed
            exampleSentenceNative = parts[3].trim(),   // ← Renamed
            languageCode = languageConfig.targetLanguage.code,  // ← NEW
            nextReviewTimestamp = System.currentTimeMillis(),
            userId = userId
        )
    }
}
```

**Verification:**
- [ ] Clean install app → Migration runs → Data preserved with `language_code = 'de'`
- [ ] Seeding inserts cards with correct language code
- [ ] Drill screen loads German cards correctly

---

### 3.2 Update UI to Use New Field Names

**Step 3.2.1:** Update DrillScreen composables

```kotlin
// ui/screens/drill/DrillScreen.kt

// Find all references to:
// - card.germanTerm → card.targetLanguageTerm
// - card.englishTerm → card.nativeLanguageTerm
// - card.exampleSentenceGerman → card.exampleSentenceTarget
// - card.exampleSentenceEnglish → card.exampleSentenceNative

// Example:
@Composable
fun FlashCard(card: VocabCard, isFlipped: Boolean) {
    Card {
        if (!isFlipped) {
            Text(text = card.targetLanguageTerm)  // ← Changed
        } else {
            Column {
                Text(text = card.targetLanguageTerm)
                Text(text = card.nativeLanguageTerm)  // ← Changed
                Text(text = card.exampleSentenceTarget)  // ← Changed
                Text(text = card.exampleSentenceNative)  // ← Changed
            }
        }
    }
}
```

**Verification:**
- [ ] Visual regression test: Flashcard displays correct terms
- [ ] Audio playback works with new field names

---

## 📋 Phase 4: Multi-Language Enablement (Week 4)
**Goal:** Add language picker and prepare for Spanish MVP  
**Estimated Effort:** 10-15 hours

### 4.1 Persist Language Selection

**Step 4.1.1:** Extend SessionManager

```kotlin
// data/local/SessionManager.kt
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
    
    // NEW: Language persistence
    fun getSelectedLanguageCode(): String {
        return prefs.getString("language_code", "de") ?: "de"
    }
    
    fun setSelectedLanguage(languageCode: String) {
        prefs.edit().putString("language_code", languageCode).apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
```

**Step 4.1.2:** Update LanguageModule to read from SessionManager

```kotlin
// di/LanguageModule.kt
@Module
@InstallIn(SingletonComponent::class)
object LanguageModule {
    
    @Provides
    @Singleton
    fun provideLanguageConfig(sessionManager: SessionManager): LanguageConfig {
        val languageCode = sessionManager.getSelectedLanguageCode()
        
        return when (languageCode) {
            "de" -> LanguageConfig.forGerman()
            "es" -> LanguageConfig.forSpanish()
            "fr" -> LanguageConfig(
                targetLanguage = Language.FRENCH,
                csvResourceId = 0  // TODO: Add French CSV
            )
            else -> LanguageConfig.forGerman()  // Default fallback
        }
    }
}
```

**Verification:**
- [ ] Change language via SessionManager
- [ ] Restart app → Verify language persists

---

### 4.2 Create Language Picker UI

**Step 4.2.1:** Add to ProfileScreen

```kotlin
// ui/screens/profile/ProfileScreen.kt

@Composable
fun ProfileScreen(
    onLanguageChanged: (Language) -> Unit = {}
) {
    Column {
        Text("Profile", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Language Selector
        LanguageSelector(onLanguageChanged = onLanguageChanged)
        
        // ... rest of profile UI ...
    }
}

@Composable
fun LanguageSelector(onLanguageChanged: (Language) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val sessionManager = LocalContext.current.getSystemService(SessionManager::class.java)
    val currentCode = sessionManager.getSelectedLanguageCode()
    val currentLanguage = Language.values().find { it.code == currentCode } ?: Language.GERMAN
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Learning Language", style = MaterialTheme.typography.titleMedium)
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = currentLanguage.displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor()
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    Language.values().forEach { language ->
                        DropdownMenuItem(
                            text = { Text(language.displayName) },
                            onClick = {
                                sessionManager.setSelectedLanguage(language.code)
                                onLanguageChanged(language)
                                expanded = false
                            }
                        )
                    }
                }
            }
            
            Text(
                text = "⚠️ Changing language will clear your current progress",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
```

**Step 4.2.2:** Handle language change in MainActivity

```kotlin
// ui/MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var database: KontextDatabase
    
    @Inject
    lateinit var seedManager: SeedManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            KontextTheme {
                KontextApp(
                    onLanguageChanged = { newLanguage ->
                        handleLanguageChange(newLanguage)
                    }
                )
            }
        }
    }
    
    private fun handleLanguageChange(newLanguage: Language) {
        lifecycleScope.launch {
            // Clear existing vocabulary
            database.vocabCardDao().deleteAll()
            
            // Re-seed with new language
            // NOTE: Requires app restart to reload LanguageConfig from Hilt
            Toast.makeText(
                this@MainActivity,
                "Please restart the app to load ${newLanguage.displayName} vocabulary",
                Toast.LENGTH_LONG
            ).show()
            
            // Alternative: Trigger app restart programmatically
            // val intent = Intent(this@MainActivity, MainActivity::class.java)
            // intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            // startActivity(intent)
            // finish()
        }
    }
}
```

**Verification:**
- [ ] Language picker displays all languages
- [ ] Selecting Spanish → Shows restart prompt
- [ ] After restart → App loads with Spanish config (once CSV added)

---

### 4.3 Prepare for Spanish CSV

**Step 4.3.1:** Create placeholder Spanish resource

```kotlin
// res/raw/spanish_5000.csv (placeholder)
// Header: Spanish|English|ExampleES|ExampleEN
hola|hello|Hola, ¿cómo estás?|Hello, how are you?
casa|house|Mi casa es grande.|My house is big.
... (2000+ entries)
```

**Step 4.3.2:** Update LanguageConfig

```kotlin
// domain/model/LanguageConfig.kt
companion object {
    fun forSpanish() = LanguageConfig(
        targetLanguage = Language.SPANISH,
        csvResourceId = R.raw.spanish_5000  // ← Update when CSV ready
    )
}
```

**Verification:**
- [ ] CSV file validated (correct delimiter, 4 columns)
- [ ] Seeding works with Spanish CSV
- [ ] TTS speaks Spanish sentences

---

## 🧪 Testing Strategy

### Unit Tests (Per Phase)

**Phase 1:**
```kotlin
// ImageRepositoryTest.kt
@Test
fun `generateScene returns Success with valid response`() = runTest {
    val mockClient = mockk<HttpClient>()
    val repository = ImageRepositoryImpl(mockClient)
    
    // ... mock Ktor response ...
    
    val result = repository.generateScene("test prompt")
    assertTrue(result is Result.Success)
}

@Test
fun `generateScene returns Error on network failure`() = runTest {
    val mockClient = mockk<HttpClient>()
    coEvery { mockClient.post<Any>(any()) } throws IOException()
    
    val repository = ImageRepositoryImpl(mockClient)
    val result = repository.generateScene("test")
    
    assertTrue(result is Result.Error)
}
```

**Phase 2:**
```kotlin
// CEFRStoryPromptBuilderTest.kt
@Test
fun `buildPrompt generates correct German A1 prompt`() {
    val builder = CEFRStoryPromptBuilder()
    val config = LanguageConfig.forGerman()
    
    val prompt = builder.buildPrompt(listOf("Haus", "Katze"), config)
    
    assertTrue(prompt.contains("German teacher"))
    assertTrue(prompt.contains("top 500"))
}
```

**Phase 3:**
```kotlin
// MigrationTest.kt
@Test
fun migration1To2_preservesData() {
    // Use Room's MigrationTestHelper
    val testHelper = MigrationTestHelper(...)
    
    val db = testHelper.createDatabase(TEST_DB, 1)
    db.execSQL("INSERT INTO vocab_cards (german_term, ...) VALUES ('Haus', ...)")
    db.close()
    
    testHelper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
    
    val migratedDb = testHelper.runMigrationsAndValidate(TEST_DB, 2, true)
    val cursor = migratedDb.query("SELECT target_language_term, language_code FROM vocab_cards")
    
    cursor.moveToFirst()
    assertEquals("Haus", cursor.getString(0))
    assertEquals("de", cursor.getString(1))
}
```

### Integration Tests

```kotlin
// EndToEndLanguageSwitchTest.kt
@Test
fun switchingLanguage_clearsOldData_seedsNewData() = runTest {
    // 1. Seed German
    sessionManager.setSelectedLanguage("de")
    seedManager.seedIfNeeded()
    val germanCount = dao.getCountForUserAndLanguage("local_user", "de")
    assertTrue(germanCount > 4000)
    
    // 2. Switch to Spanish
    sessionManager.setSelectedLanguage("es")
    dao.deleteAll()
    seedManager.seedIfNeeded()
    
    // 3. Verify Spanish data, no German data
    val spanishCount = dao.getCountForUserAndLanguage("local_user", "es")
    val remainingGerman = dao.getCountForUserAndLanguage("local_user", "de")
    
    assertTrue(spanishCount > 2000)
    assertEquals(0, remainingGerman)
}
```

---

## 📊 Success Criteria

### Phase Completion Gates

| Phase | Gate Criteria |
|-------|---------------|
| **Phase 1** | All repositories return `Result<T>`, dead code removed, >80% test coverage on refactored code |
| **Phase 2** | TtsManager uses injected locale, story prompts parameterized, German app still works |
| **Phase 3** | Migration runs successfully, drill screen shows cards with new schema |
| **Phase 4** | Language picker functional, Spanish CSV loads, app works in both German and Spanish |

### Final Validation

- [ ] App launches with German (default)
- [ ] Switch to Spanish via Profile → Restart → Spanish cards load
- [ ] Generate story in Spanish → Prompt uses "Spanish teacher"
- [ ] TTS speaks Spanish sentences
- [ ] Switch back to German → All German functionality intact

---

## 🎯 Post-Implementation: Adding New Languages

**Time Estimate: <8 hours per language** (after Phase 4 complete)

### Checklist for Adding French

1. **Create CSV:** `res/raw/french_4000.csv` (4-6 hours to source/format data)
2. **Update LanguageConfig:** Add French case in `forFrench()` (5 minutes)
3. **Add String Resources:** `res/values-fr/strings.xml` for UI labels (30 minutes)
4. **Test:** Verify seeding, TTS, story generation (1 hour)

**Total:** ~7.5 hours

---

## 📝 Notes & Considerations

### Backward Compatibility

- Room migration preserves existing user data
- Language defaults to German for existing users
- No breaking changes to public APIs until Phase 3

### Performance

- CSV parsing happens once on first launch (acceptable for MVP)
- Consider replacing CSV with SQLite bundle for 10k+ words
- Story caching disabled for MVP (re-evaluate if generation latency >3s)

### Future Enhancements (Post-MVP)

- [ ] Server-side vocabulary management (Supabase)
- [ ] User-uploaded custom word lists
- [ ] A/B test different prompt templates
- [ ] Multi-language UI (currently English-only interface)
- [ ] Cloud sync for multi-device support

---

## 🚀 Getting Started

1. **Review this document** with the team
2. **Create branch:** `refactor/language-agnostic-architecture`
3. **Start with Phase 1.1:** Implement `Result<T>` wrapper
4. **Write tests first** for each component before refactoring
5. **Review after each phase** before proceeding to next

**Questions? Issues?** Reference line numbers in this document when discussing implementation details.
