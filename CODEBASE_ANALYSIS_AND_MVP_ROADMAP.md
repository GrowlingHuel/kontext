# Kontext: Codebase Analysis & MVP Roadmap

**Analysis Date:** 2025-12-31  
**Project:** German Language Learning App (Kontext)  
**Analyst:** Architecture Review Team

---

## 🎯 Executive Summary

This analysis evaluates the Kontext codebase against **Pragmatic Programmer principles** (Orthogonality, Combat Entropy, Reentrancy, ETC) and assesses multi-language readiness for future iterations (Spanish, French, etc.). The project demonstrates **strong architectural foundations** with proper layering, dependency injection, and MVVM patterns. However, several **simplification opportunities** exist, particularly around eliminating redundant persistence layers and consolidating language-agnostic components.

### Readiness Score: **7.5/10**

| Category | Score | Status |
|----------|-------|--------|
| **Orthogonality** | 8/10 | ✅ Strong layer separation |
| **Combat Entropy** | 7/10 | 🟡 Mild code duplication detected |
| **Reentrancy** | 6/10 | 🟠 Singleton patterns need review |
| **Ease to Change** | 8/10 | ✅ Good abstraction, needs language parameterization |
| **Multi-Language Readiness** | 7/10 | 🟡 Requires refactoring for language-agnostic design |

---

## 📐 Architectural Assessment

### ✅ **What's Working Well**

#### 1. **Orthogonality: Clean Layer Separation**

The project adheres to **MVVM + Clean Architecture**:

```
UI Layer (Compose) → ViewModel → UseCase → Repository → DAO
```

**Evidence:**
- [`DrillViewModel`](/home/jesse/Projects/german-language/app/src/main/java/com/kontext/ui/screens/drill/DrillViewModel.kt) correctly depends on `VocabRepository` (abstraction) instead of directly accessing `VocabCardDao`
- [`VocabRepositoryImpl`](/home/jesse/Projects/german-language/app/src/main/java/com/kontext/data/repository/VocabRepositoryImpl.kt) properly injects `SessionManager` for user context
- UI state is managed via `StateFlow`, ensuring configuration change survival

**Verification:**
> **Can we swap Room for Realm without touching ViewModels?** ✅ YES  
> **Can we replace the spaced repetition algorithm without changing UI?** ✅ YES

---

#### 2. **Combat Entropy: Good Error Handling Patterns**

**Positive patterns identified:**

1. **Idempotent Seeding** ([`SeedManager.kt`](/home/jesse/Projects/german-language/app/src/main/java/com/kontext/util/SeedManager.kt)):
   ```kotlin
   val count = database.vocabCardDao().getCount()
   if (count > 4500) {
       Log.d("SeedManager", "Database already fully seeded...")
       return@withContext
   }
   ```
   - Prevents duplicate seeding on app restarts
   - Uses Room count as source of truth

2. **Proper Coroutine Usage**:
   - All database operations use `suspend` functions
   - Explicit `Dispatchers.IO` for blocking operations
   - `viewModelScope` ensures lifecycle-aware cancellation

3. **Dependency Injection with Hilt**:
   - No manual `new` instantiation in production code
   - Singletons properly scoped (`@Singleton`, `@HiltViewModel`)
   - Test-friendly (can inject mocks)

---

#### 3. **Design for Reentrancy**

**Configuration Change Handling:**
- ViewModels survive rotation via `ViewModelScope`
- `SavedStateHandle` placeholder exists in architecture plan (not yet implemented in all ViewModels)

**Multi-User Session Isolation:**
- [`SessionManager`](/home/jesse/Projects/german-language/app/src/main/java/com/kontext/data/local/SessionManager.kt) centralizes user context
- All queries filter by `userId` (see `VocabRepositoryImpl.getCardsForReview()`)

---

#### 4. **ETC (Easier To Change)**

**Interface-Based Design:**
- `SpacedRepetitionEngine` interface allows swapping algorithms (SM-2 → Anki)
- `VocabRepository` interface enables switching persistence layers
- Navigation uses sealed class routes (`Screen.kt`), making deep links easy to add

---

## 🟡 **Areas for Improvement**

### 1. ⚠️ **Language Hardcoding (Critical for Multi-Language Goal)**

**Current State: German-Specific Implementation**

Multiple files embed German language logic:

| File | Issue | Impact |
|------|-------|--------|
| [`TtsManager.kt`](file:///home/jesse/Projects/german-language/app/src/main/java/com/kontext/util/TtsManager.kt#L25) | Hardcoded `Locale.GERMAN` | **HIGH** - Breaks Spanish/French versions |
| [`StoryRepositoryImpl.kt`](file:///home/jesse/Projects/german-language/app/src/main/java/com/kontext/data/repository/StoryRepositoryImpl.kt#L68) | Prompt: "A1 German teacher" | **HIGH** - AI stories won't work for other languages |
| `VocabCard` schema | Fields: `germanTerm` | **MEDIUM** - Schema refactor needed for other languages |
| CSV Parser | Expects `german_4000.csv` | **MEDIUM** - Seeding logic not parameterized |

**Root Cause:** Language is baked into **domain models** and **utility classes** instead of being injected as configuration.

---

### 2. 🔴 **Broken Window: ImageRepository (TRACER_BULLET.md Identified)**

**Status:** Already flagged in [`TRACER_BULLET.md`](/home/jesse/Projects/german-language/docs/TRACER_BULLET.md#L8-L9)

**Issue:** 
- Uses manual JSON parsing (`org.json.JSONObject`)
- Returns nullable `Bitmap?` instead of `Result<Bitmap>`
- printStackTrace() instead of structured logging

**Fix Proposed (TRACER_BULLET):**
- Already using Ktor HttpClient ✅ (correctly implemented in current code)
- Still needs: `Result<Bitmap>` wrapper for consistent error handling

**Current Implementation:**
```kotlin
override suspend fun generateScene(prompt: String): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val response: JsonObject = client.post(url) { ... }.body()
        // ... manual parsing ...
    } catch (e: Exception) {
        e.printStackTrace()  // ❌ Broken window
    }
    return@withContext null
}
```

**Recommendation:** 
- Wrap in `sealed class Result<T>` pattern (see [ARCHITECTURE_PLAN.md line 68](/home/jesse/Projects/german-language/ARCHITECTURE_PLAN.md#L68))

---

### 3. 🟡 **TtsManager: Singleton with Context Dependency**

**Current Pattern:**
```kotlin
@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
```

**Issues:**
1. **Hardcoded Language:** `Locale.GERMAN` set in `onInit()` — **not parameterized**
2. **Resource Leak Risk:** TTS engine initialized in `init {}`, no lifecycle awareness
3. **Testing Challenge:** Hard to mock `TextToSpeech` for unit tests

**Recommendation:**
- Inject `LanguageConfig` object containing target locale
- Consider scoping to Activity/ViewModel lifecycle instead of `@Singleton`
- Add `@OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)` to call `shutdown()`

---

### 4. 🟡 **Story Generation: Inline Prompts & No Caching Strategy**

**Current Implementation ([`StoryRepositoryImpl.kt`](file:///home/jesse/Projects/german-language/app/src/main/java/com/kontext/data/repository/StoryRepositoryImpl.kt#L68-L81)):**

```kotlin
val prompt = """
    You are a conservative A1 German teacher. 95% of your story MUST use the top 500 most common German words...
""".trimIndent()
```

**Issues:**
1. **Hardcoded Prompt:** Language level (A1), language name ("German"), and word limits are inline strings
2. **Disabled Caching:** Lines 47-50 show commented-out cache lookup logic
3. **No Prompt Versioning:** If you improve the prompt template, old cached stories become inconsistent

**Recommendations:**
1. **Extract to PromptTemplate Interface:**
   ```kotlin
   interface StoryPromptTemplate {
       fun buildPrompt(vocab: List<String>, level: LanguageLevel): String
   }
   
   class GermanA1PromptTemplate : StoryPromptTemplate { ... }
   class SpanishA1PromptTemplate : StoryPromptTemplate { ... }
   ```

2. **Re-enable Caching with Signature:**
   - Current signature: `sortedWords.joinToString("-")` ✅ Good foundation
   - Add prompt version hash to signature: `"v2-${sortedWords.joinToString("-")}"`

3. **Parameterize Language Level:**
   ```kotlin
   data class StoryConfig(
       val targetLanguage: Language,
       val level: CEFRLevel,  // A1, A2, B1...
       val topNWords: Int = 500
   )
   ```

---

### 5. 🟡 **Duplicate User ID Logic**

**Current Pattern:**
```kotlin
// SessionManager.kt
fun getCurrentUserId(): String {
    return prefs.getString("user_id", "local_user") ?: "local_user"
}
```

**Issue:** "local_user" default appears in multiple places:
- Fallback in `SessionManager`
- Potentially referenced in seed logic

**Recommendation:**
- Consolidate to single constant: `object UserConstants { const val DEFAULT_USER = "local_user" }`
- Better: Use UUID generation on first launch for anonymous users

---

## 🛠️ **Simplification Opportunities (Without Losing Functionality)**

### 1. **Consolidate Seeding Logic**

**Current State:**
- `SeedManager` parses CSV manually using `BufferedReader`
- `CsvParser` utility exists but not used consistently

**Simplification:**
```kotlin
// BEFORE (SeedManager.kt Line 60-96)
private fun parseCsv(inputStream: InputStream): List<VocabCard> {
    val cards = mutableListOf<VocabCard>()
    val reader = BufferedReader(InputStreamReader(inputStream))
    // ... manual line parsing ...
}

// AFTER (Delegate to CsvParser)
class SeedManager @Inject constructor(
    private val csvParser: CsvParser,
    private val database: KontextDatabase
) {
    suspend fun seedIfNeeded() {
        val cards = csvParser.parseVocabCsv(R.raw.german_4000, sessionManager.getCurrentUserId())
        database.vocabCardDao().insertAll(cards)
    }
}
```

**Benefit:** 
- Single responsibility (SeedManager orchestrates, CsvParser parses)
- Easier to unit test CSV parsing separately
- Reusable for importing user-uploaded CSV files

---

### 2. **Eliminate Redundant Story Persistence**

**Current State:**
- `StoryEntity` persists:
  - `vocabSignature` (vocabulary hash)
  - `jsonContent` (full story JSON)
  - `imagePath` (file path to image)
- `FileStorageHelper` saves images to internal storage

**Issues:**
1. **Dual Storage:** Story JSON in Room + Image in filesystem = fragmented state
2. **Cache Disabled:** Lines 47-50 in `StoryRepositoryImpl` show cache lookup is commented out
3. **No Expiration:** Cached stories never expire, even if prompt template improves

**Simplification Options:**

#### Option A: **Simplify to In-Memory Cache Only (for MVP)**
- Remove `StoryEntity` and Room persistence
- Use `LruCache<String, StoryResponse>` in `StoryRepositoryImpl`
- Faster development, acceptable for MVP if users rarely regenerate same vocab set

#### Option B: **Keep Persistence, Fix Cache Hit Logic**
- Uncomment lines 47-50
- Add cache invalidation based on prompt version
- Add TTL (time-to-live) for cached stories (e.g., 30 days)

**Recommendation for MVP:** **Option A** — Defer complex caching until post-MVP when usage patterns are understood.

---

### 3. **Unify Repository Patterns**

**Current Inconsistency:**

| Repository | Return Type | Error Handling |
|------------|-------------|----------------|
| `VocabRepositoryImpl` | Direct types (`List<VocabCard>`) | Throws exceptions |
| `ImageRepositoryImpl` | Nullable (`Bitmap?`) | Returns `null` on error |
| `StoryRepositoryImpl` | Domain model (`StoryResponse`) | Returns error object in response |

**Simplification:**
- **Standardize to `Result<T>` sealed class** (already defined in architecture plan):
  ```kotlin
  sealed class Result<out T> {
      data class Success<T>(val data: T) : Result<T>()
      data class Error(val exception: Exception) : Result<Nothing>()
  }
  ```

**Benefits:**
- ViewModels can uniformly handle errors
- Consistent patterns = less cognitive load for developers
- Easier to add analytics for error rates

---

### 4. **Remove Dead Code & Commented Logic**

**Identified in Codebase:**

1. **StoryRepositoryImpl Lines 47-50** (Commented cache lookup)
   ```kotlin
   // val cachedStory = storyDao.getStoryBySignature(signature)
   // if (cachedStory != null) { ... }
   ```
   → **Decision:** Enable caching OR delete entity if not using

2. **TRACER_BULLET.md References `SupabaseClient` as `object`**
   → **Status:** Already fixed? (Need to verify [SupabaseModule.kt](file:///home/jesse/Projects/german-language/app/src/main/java/com/kontext/di/SupabaseModule.kt))

3. **Unused Dependencies?**
   - Check if `io.github.jan-tennert.supabase:gotrue-kt` is actually used (noted as disabled in PROJECT_CONTEXT line 91)

---

## 🌍 **Multi-Language Readiness Assessment**

### 🎯 **Goal:** Create Spanish, French, Italian versions using same codebase

### Current Blockers

| # | Component | Issue | Effort to Fix |
|---|-----------|-------|---------------|
| 1 | `TtsManager` | Hardcoded `Locale.GERMAN` | **LOW** - Inject `LanguageConfig` |
| 2 | `StoryRepositoryImpl` | Prompt templates German-specific | **MEDIUM** - Extract to strategy pattern |
| 3 | `VocabCard` Entity | Column names: `germanTerm` | **HIGH** - Schema migration required |
| 4 | CSV Seeding | Expects `german_4000.csv` | **LOW** - Parameterize filename |
| 5 | UI Strings | German labels in Compose? | **LOW** - Already uses `strings.xml`? (needs verification) |

---

### 🏗️ **Proposed Multi-Language Architecture**

#### 1. **Introduce LanguageConfig Abstraction**

```kotlin
// domain/model/LanguageConfig.kt
enum class Language(val code: String, val locale: Locale) {
    GERMAN("de", Locale.GERMAN),
    SPANISH("es", Locale("es")),
    FRENCH("fr", Locale.FRENCH)
}

data class LanguageConfig(
    val language: Language,
    val csvResourceId: Int,        // R.raw.german_4000, R.raw.spanish_5000
    val topNWords: Int = 500,
    val cefrLevel: CEFRLevel = CEFRLevel.A1
)
```

#### 2. **Inject Language Config via Hilt Module**

```kotlin
// di/LanguageModule.kt
@Module
@InstallIn(SingletonComponent::class)
object LanguageModule {
    
    @Provides
    @Singleton
    fun provideLanguageConfig(): LanguageConfig {
        // For MVP: Hardcoded German
        // Post-MVP: Read from SharedPreferences or user profile
        return LanguageConfig(
            language = Language.GERMAN,
            csvResourceId = R.raw.german_4000
        )
    }
}
```

#### 3. **Refactor VocabCard Schema (Breaking Change)**

**Current:**
```kotlin
data class VocabCard(
    val germanTerm: String,
    val englishTerm: String,
    // ...
)
```

**Multi-Language:**
```kotlin
data class VocabCard(
    val targetLanguageTerm: String,   // German/Spanish/French word
    val nativeLanguageTerm: String,   // English translation (or user's L1)
    val languageCode: String,          // "de", "es", "fr"
    // ...
)
```

**Migration Strategy:**
- Create `VocabCard_v2` entity
- Write migration script to copy `germanTerm → targetLanguageTerm`, add `languageCode = "de"`
- Delete old table after migration

---

#### 4. **Parameterize Story Prompts**

```kotlin
interface StoryPromptBuilder {
    fun build(vocab: List<String>, config: LanguageConfig): String
}

class CEFRPromptBuilder @Inject constructor() : StoryPromptBuilder {
    override fun build(vocab: List<String>, config: LanguageConfig): String {
        val languageName = config.language.name.lowercase().capitalize()
        return """
            You are a conservative ${config.cefrLevel} $languageName teacher.
            95% of your story MUST use the top ${config.topNWords} most common $languageName words.
            Write a story using these target words: [${vocab.joinToString(", ")}].
            ...
        """.trimIndent()
    }
}
```

---

#### 5. **TtsManager with Language Injection**

```kotlin
@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val languageConfig: LanguageConfig  // ← NEW
) : TextToSpeech.OnInitListener {

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(languageConfig.language.locale)  // ← DYNAMIC
            // ...
        }
    }
}
```

---

### 📦 **Recommended Project Structure for Multi-Language**

```
app/
├── src/main/
│   ├── res/
│   │   ├── raw/
│   │   │   ├── german_4000.csv
│   │   │   ├── spanish_5000.csv
│   │   │   └── french_4000.csv
│   │   └── values/
│   │       ├── strings.xml          # English (default)
│   │       ├── strings-de.xml       # German UI labels
│   │       └── strings-es.xml       # Spanish UI labels
│   │
│   └── java/com/kontext/
│       ├── di/
│       │   └── LanguageModule.kt    # ← NEW: Provides LanguageConfig
│       │
│       ├── domain/
│       │   └── model/
│       │       ├── Language.kt      # ← NEW: Enum of supported languages
│       │       └── LanguageConfig.kt
│       │
│       └── data/
│           └── local/
│               └── entity/
│                   └── VocabCard.kt  # ← REFACTOR: Rename fields
```

---

## 🚀 **MVP Next Steps (Prioritized)**

### Phase 1: **Foundation Hardening** (Week 1-2)

**Goal:** Fix broken windows, stabilize existing German app

| # | Task | Effort | Impact | Priority |
|---|------|--------|--------|----------|
| 1.1 | Wrap `ImageRepository.generateScene()` in `Result<Bitmap>` | 2h | High | **P0** |
| 1.2 | Re-enable story caching OR delete `StoryEntity` (decide) | 3h | Medium | **P1** |
| 1.3 | Add unit tests for `SeedManager` (idempotency test) | 4h | Medium | **P1** |
| 1.4 | Remove commented code in `StoryRepositoryImpl` | 1h | Low | **P2** |
| 1.5 | Consolidate CSV parsing to `CsvParser` utility | 2h | Low | **P2** |

**Verification:**
- Run existing flashcard flow: Seed DB → Drill → Grade cards → Verify SM-2 intervals
- Generate 3 stories with same vocab → Verify caching (if enabled)

---

### Phase 2: **Multi-Language Abstraction** (Week 3-4)

**Goal:** Make codebase language-agnostic

| # | Task | Effort | Impact | Priority |
|---|------|--------|--------|----------|
| 2.1 | Create `Language` enum + `LanguageConfig` data class | 2h | High | **P0** |
| 2.2 | Create `LanguageModule` in Hilt (provide German config) | 1h | High | **P0** |
| 2.3 | Refactor `TtsManager` to inject `LanguageConfig` | 3h | High | **P0** |
| 2.4 | Extract `StoryPromptBuilder` interface | 4h | High | **P0** |
| 2.5 | Parameterize `SeedManager` to accept CSV resource ID | 2h | Medium | **P1** |
| 2.6 | Rename `VocabCard` fields (requires Room migration) | 6h | High | **P0** |

**Verification:**
- Change `LanguageModule` to provide Spanish config → App should compile (but fail at runtime due to missing CSV)
- TTS engine should initialize with correct locale

---

### Phase 3: **Spanish MVP** (Week 5)

**Goal:** Validate multi-language architecture with second language

| # | Task | Effort | Impact | Priority |
|---|------|--------|--------|----------|
| 3.1 | Create `spanish_5000.csv` resource file | 8h | High | **P0** |
| 3.2 | Add Spanish string resources (`strings-es.xml`) | 2h | Medium | **P1** |
| 3.3 | Create `SpanishA1PromptTemplate` implementation | 2h | High | **P0** |
| 3.4 | Add language picker in Profile screen | 4h | Medium | **P1** |
| 3.5 | Persist selected language in `SessionManager` | 2h | Medium | **P1** |

**Verification:**
- Switch to Spanish → Seed 5000 cards → Drill shows Spanish/English pairs
- TTS speaks Spanish sentences
- Story generation uses Spanish prompt template

---

### Phase 4: **Polish & Launch** (Week 6)

| # | Task | Effort | Impact |
|---|------|--------|--------|
| 4.1 | Add analytics for error rates by language | 3h | Medium |
| 4.2 | Implement story cache expiration (if enabled in 1.2) | 4h | Low |
| 4.3 | Add user onboarding: "Choose your language" | 3h | High |
| 4.4 | Performance testing: Seed 5000 cards under 3 seconds | 4h | Medium |

---

## 📊 **Metrics for Success**

### Code Quality Metrics

| Metric | Current | Target (Post-MVP) |
|--------|---------|-------------------|
| Lines of code in largest file | 129 (StoryRepositoryImpl) | <150 |
| Number of `!!` (force unwrap) | 0 ✅ | 0 |
| Repository methods returning `Result<T>` | 0% | 100% |
| Test coverage (domain layer) | Unknown | >70% |

### Multi-Language Readiness

| Metric | Current | Target |
|--------|---------|--------|
| Hardcoded language references | ~8 files | 0 |
| Supported languages | German only | German, Spanish, French |
| Time to add new language | ~40 hours | <8 hours |

---

## 🎓 **Best Practices Alignment Summary**

### ✅ **Orthogonality**

**Grade: 8/10**

**Strengths:**
- Clean layer separation (UI/ViewModel/UseCase/Repository/DAO)
- ViewModels don't directly access DAOs
- Interfaces enable swapping implementations

**Improvements:**
- Standardize repository return types (`Result<T>`)
- Extract business logic from `StoryRepositoryImpl` into Use Cases

---

### 🟡 **Combat Entropy**

**Grade: 7/10**

**Strengths:**
- Idempotent seeding logic
- No callback hell (pure coroutines)
- Hilt DI prevents manual instantiation

**Broken Windows:**
- `ImageRepository` returns `null` on errors instead of `Result.Error`
- Commented-out cache logic in `StoryRepositoryImpl`
- `printStackTrace()` instead of structured logging

---

### 🟠 **Design for Reentrancy**

**Grade: 6/10**

**Strengths:**
- ViewModels use `StateFlow` for configuration survival
- Database operations are idempotent
- Session isolation via `SessionManager`

**Risks:**
- `TtsManager` is `@Singleton` with lifecycle callbacks — could leak Activity context
- No cleanup logic for TTS engine on app exit
- `SavedStateHandle` not used in all ViewModels (e.g., `ImmerseViewModel`)

---

### ✅ **ETC (Easier To Change)**

**Grade: 8/10**

**Strengths:**
- `SpacedRepetitionEngine` interface allows algorithm swaps
- Navigation routes use sealed classes
- CSV parsing separated from domain models

**Improvements:**
- Parameterize language configuration
- Extract story prompts to strategy pattern
- Refactor `VocabCard` schema for language-agnostic design

---

## 🔚 **Conclusion**

The Kontext codebase demonstrates **solid engineering practices** with clean architecture, proper dependency injection, and modern Jetpack Compose UI. The main challenge for multi-language expansion is **language hardcoding** at the domain level, particularly in:

1. TTS engine initialization
2. Story prompt templates
3. Database schema field names

**Recommended Path Forward:**

1. **Short-term (MVP):** Fix broken windows (`ImageRepository`, commented cache logic)
2. **Mid-term (Weeks 3-4):** Introduce `LanguageConfig` abstraction and refactor core components
3. **Long-term (Spanish launch):** Validate architecture with second language, iterate on onboarding

**Estimated Effort to Multi-Language Ready:** **60-80 hours** of focused development.

By addressing these issues systematically, the app will achieve **<8 hours to add new languages** post-refactor, enabling rapid expansion to Romance languages (Spanish, French, Italian) and beyond.

---

## 📎 Appendix: Reference Files

- [ARCHITECTURE_PLAN.md](file:///home/jesse/Projects/german-language/ARCHITECTURE_PLAN.md) - Original architectural blueprint
- [PROJECT_CONTEXT.md](file:///home/jesse/Projects/german-language/PROJECT_CONTEXT.md) - Current project state
- [TRACER_BULLET.md](file:///home/jesse/Projects/german-language/docs/TRACER_BULLET.md) - Identified refactoring needs
- [VocabRepositoryImpl.kt](file:///home/jesse/Projects/german-language/app/src/main/java/com/kontext/data/repository/VocabRepositoryImpl.kt)
- [TtsManager.kt](file:///home/jesse/Projects/german-language/app/src/main/java/com/kontext/util/TtsManager.kt)
- [StoryRepositoryImpl.kt](file:///home/jesse/Projects/german-language/app/src/main/java/com/kontext/data/repository/StoryRepositoryImpl.kt)

