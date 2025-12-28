# Kontext: German Learning App - Architecture Plan

**Project Type**: Native Android Application  
**Language**: Kotlin  
**UI Framework**: Jetpack Compose (Material 3)  
**Architecture**: MVVM + Clean Architecture  
**Dependency Injection**: Hilt (Dagger)  
**Local Database**: Room (SQLite)  
**Remote Backend**: Supabase (PostgreSQL + GoTrue Auth)  

---

## 🎯 Executive Summary

**Goal**: Build a multi-user German vocabulary learning app with scientifically-backed spaced repetition.

**Tracer Bullet Strategy**: The fastest path to validate the entire stack is:
1. Create Room database with `VocabCard` entity
2. Seed database from CSV on first launch
3. Display card count in Compose UI via ViewModel
4. Verify Hilt DI wiring and Supabase dependencies don't conflict

**Success Criteria**: Launch app → See bottom navigation → Tap "Drill" → See "4000 cards loaded"

---

## 🏛️ Architectural Principles (Pragmatic Programmer Applied to Android)

### 1. **Orthogonality** 🔷
> *"Eliminate effects between unrelated things"*

**Android Mapping**:
- **UI Layer** (Jetpack Compose): Pure presentation logic, no business rules
- **ViewModel Layer**: State management and UI event handling, no direct DB/network calls
- **Domain Layer**: Business logic (spaced repetition algorithms), no Android dependencies
- **Data Layer**: Room DAOs, Repositories, Supabase clients - no UI awareness

**Verification**: 
- Can I change the spaced repetition algorithm without touching Compose code? ✅
- Can I swap Room for Realm without changing ViewModels? ✅
- Can I replace Supabase with Firebase without touching UI? ✅

**Anti-Pattern to Avoid**:
```kotlin
// ❌ BAD: ViewModel directly accessing Room DAO
class DrillViewModel @Inject constructor(
    private val dao: VocabCardDao // WRONG - breaks orthogonality
)

// ✅ GOOD: ViewModel depends on abstraction
class DrillViewModel @Inject constructor(
    private val repository: VocabRepository // Interface - can swap implementations
)
```

---

### 2. **Combat Entropy** 🧹
> *"Don't live with broken windows"*

**Identified "Broken Windows" in Typical Android Apps**:

| Broken Window | Our Solution |
|--------------|--------------|
| Hardcoded API keys in code | Use `BuildConfig` + `local.properties` for secrets |
| Blocking main thread for I/O | Strict use of `Dispatchers.IO` for DB/network |
| Callback hell | Kotlin Coroutines + Flow exclusively |
| No error handling | Sealed class `Result<T>` wrapper for all data operations |
| ViewModels with business logic | Extract to Use Cases in domain layer |
| Magic strings everywhere | Centralized `Constants.kt` + string resources |

**Code Quality Gates**:
- All database operations must use `suspend` functions
- All network calls wrapped in `try-catch` with logging
- No `!!` (force unwrap) - use safe calls `?.` or `?:` with defaults
- Every ViewModel must have a corresponding test

**Example - Robust CSV Parsing**:
```kotlin
// ✅ Entropy-resistant implementation
suspend fun seedDatabase(): Result<Int> = withContext(Dispatchers.IO) {
    try {
        val existingCount = dao.getCount()
        if (existingCount > 0) {
            return@withContext Result.Success(existingCount) // Idempotent
        }
        
        val cards = parseCsv() // Can throw IOException
        dao.insertAll(cards)
        Result.Success(cards.size)
    } catch (e: IOException) {
        Log.e("SeedManager", "CSV read failed", e)
        Result.Error(e)
    } catch (e: Exception) {
        Log.e("SeedManager", "Unexpected error", e)
        Result.Error(e)
    }
}
```

---

### 3. **Design for Reentrancy** 🔄
> *"Make functions pure and stateless where possible"*

**Android-Specific Challenges**:
- Configuration changes (rotation) destroy Activities
- Process death can kill app in background
- Multiple users means session state must be isolated

**Our Solutions**:

#### A. ViewModel Survival
```kotlin
// ✅ Survives rotation via ViewModelScope
@HiltViewModel
class DrillViewModel @Inject constructor(
    private val repository: VocabRepository,
    private val savedStateHandle: SavedStateHandle // Survives process death
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DrillUiState())
    val uiState: StateFlow<DrillUiState> = _uiState.asStateFlow()
    
    init {
        // Restore state from process death
        val lastCardId = savedStateHandle.get<Int>("last_card_id")
        if (lastCardId != null) {
            loadCard(lastCardId)
        }
    }
    
    fun onCardReviewed(rating: Int) {
        savedStateHandle["last_card_id"] = _uiState.value.currentCard?.id
        // Pure state transformation
        viewModelScope.launch {
            repository.updateMasteryLevel(/* ... */)
        }
    }
}
```

#### B. Idempotent Database Operations
```kotlin
// ✅ Can be called multiple times safely
@Dao
interface VocabCardDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(cards: List<VocabCard>): List<Long>
    
    @Query("SELECT COUNT(*) FROM vocab_cards")
    suspend fun getCount(): Int
}
```

#### C. Multi-User Session Isolation
```kotlin
// ✅ User-scoped repository
@Singleton
class VocabRepositoryImpl @Inject constructor(
    private val dao: VocabCardDao,
    private val supabase: SupabaseClient,
    private val sessionManager: SessionManager // Tracks current user
) : VocabRepository {
    
    override fun getCardsForReview(): Flow<List<VocabCard>> = flow {
        val userId = sessionManager.getCurrentUserId() ?: throw AuthException()
        val now = System.currentTimeMillis()
        emit(dao.getCardsDueForUser(userId, now))
    }.flowOn(Dispatchers.IO)
}
```

---

### 4. **ETC (Easier To Change)** 🛠️
> *"Choose the implementation that makes future changes easiest"*

**Key Design Decisions**:

#### Decision 1: Spaced Repetition Algorithm
**Options**:
- A) Hardcode SM-2 algorithm in ViewModel
- B) Create `SpacedRepetitionEngine` interface

**Choice**: **B** - Interface-based design

**Rationale**: 
- Easy to A/B test different algorithms (SM-2 vs. Anki vs. custom)
- Can inject mock for testing
- Algorithm can evolve without touching UI

```kotlin
interface SpacedRepetitionEngine {
    fun calculateNextReview(
        currentLevel: Int,
        rating: Int,
        lastReview: Long
    ): ReviewResult
}

data class ReviewResult(
    val newLevel: Int,
    val nextReviewTimestamp: Long,
    val interval: Duration
)

// Easy to swap implementations
class SM2Algorithm @Inject constructor() : SpacedRepetitionEngine { /* ... */ }
class AnkiAlgorithm @Inject constructor() : SpacedRepetitionEngine { /* ... */ }
```

#### Decision 2: Navigation Architecture
**Options**:
- A) Activity-per-screen (legacy)
- B) Single Activity + Jetpack Navigation Compose

**Choice**: **B** - Navigation Compose with sealed class routes

**Rationale**:
- Easy to add deep links later
- Type-safe navigation arguments
- Shared ViewModels between destinations

```kotlin
sealed class Screen(val route: String) {
    object Immerse : Screen("immerse")
    object Drill : Screen("drill")
    object Profile : Screen("profile")
    data class CardDetail(val cardId: Int) : Screen("card/{cardId}") {
        fun createRoute(cardId: Int) = "card/$cardId"
    }
}
```

#### Decision 3: CSV Data Format
**Options**:
- A) Parse CSV directly into Room entities
- B) Create intermediate `CsvRow` data class

**Choice**: **B** - Separate parsing from persistence

**Rationale**:
- CSV format can change without affecting database schema
- Easy to add validation/transformation layer
- Can reuse parser for future imports

```kotlin
data class CsvRow(
    val german: String,
    val english: String,
    val exampleDe: String,
    val exampleEn: String
)

fun CsvRow.toVocabCard(userId: String): VocabCard = VocabCard(
    id = 0, // Auto-generated
    germanTerm = german.trim(),
    englishTerm = english.trim(),
    exampleSentenceGerman = exampleDe,
    exampleSentenceEnglish = exampleEn,
    masteryLevel = 0,
    nextReviewTimestamp = System.currentTimeMillis(),
    audioPath = null,
    userId = userId
)
```

---

## 📐 Layer Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        UI LAYER                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ ImmerseScreen│  │  DrillScreen │  │ ProfileScreen│      │
│  │  (Compose)   │  │  (Compose)   │  │  (Compose)   │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                  │                  │              │
│  ┌──────▼───────┐  ┌──────▼───────┐  ┌──────▼───────┐      │
│  │ImmerseVM     │  │  DrillVM     │  │  ProfileVM   │      │
│  │(StateFlow)   │  │(StateFlow)   │  │(StateFlow)   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      DOMAIN LAYER                            │
│  ┌────────────────────────────────────────────────────┐     │
│  │              Use Cases (Business Logic)            │     │
│  ├────────────────────────────────────────────────────┤     │
│  │ • GetNextReviewCardUseCase                         │     │
│  │ • UpdateCardMasteryUseCase                         │     │
│  │ • SyncWithSupabaseUseCase                          │     │
│  └────────────────────────────────────────────────────┘     │
│  ┌────────────────────────────────────────────────────┐     │
│  │         Spaced Repetition Engine (Interface)       │     │
│  └────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                       DATA LAYER                             │
│  ┌────────────────────────────────────────────────────┐     │
│  │          Repository (Interface)                    │     │
│  │  • VocabRepository                                 │     │
│  │  • UserRepository                                  │     │
│  └────────────────────────────────────────────────────┘     │
│         │                                  │                 │
│         ▼                                  ▼                 │
│  ┌─────────────┐                   ┌─────────────┐          │
│  │ Room (Local)│                   │  Supabase   │          │
│  ├─────────────┤                   │  (Remote)   │          │
│  │ • VocabCard │                   ├─────────────┤          │
│  │ • UserPrefs │                   │ • GoTrue    │          │
│  │ • SyncLog   │                   │ • Postgrest │          │
│  └─────────────┘                   └─────────────┘          │
└─────────────────────────────────────────────────────────────┘
```

---

## 📦 Project Structure

```
app/
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/kontext/
│   │   ├── KontextApplication.kt          # Hilt entry point
│   │   │
│   │   ├── di/                             # Dependency Injection
│   │   │   ├── AppModule.kt               # App-level dependencies
│   │   │   ├── DatabaseModule.kt          # Room database provision
│   │   │   ├── SupabaseModule.kt          # Supabase client setup
│   │   │   └── RepositoryModule.kt        # Repository bindings
│   │   │
│   │   ├── data/                           # DATA LAYER
│   │   │   ├── local/
│   │   │   │   ├── KontextDatabase.kt     # Room database definition
│   │   │   │   ├── dao/
│   │   │   │   │   ├── VocabCardDao.kt    # CRUD operations
│   │   │   │   │   └── UserDao.kt
│   │   │   │   └── entity/
│   │   │   │       ├── VocabCard.kt       # Room entity (Task 1)
│   │   │   │       └── User.kt
│   │   │   │
│   │   │   ├── remote/
│   │   │   │   ├── SupabaseClient.kt      # Supabase initialization
│   │   │   │   └── dto/                   # Network DTOs
│   │   │   │       └── VocabCardDto.kt
│   │   │   │
│   │   │   └── repository/
│   │   │       ├── VocabRepository.kt     # Interface
│   │   │       ├── VocabRepositoryImpl.kt # Implementation
│   │   │       ├── UserRepository.kt
│   │   │       └── UserRepositoryImpl.kt
│   │   │
│   │   ├── domain/                         # DOMAIN LAYER
│   │   │   ├── model/
│   │   │   │   ├── ReviewSession.kt       # Business models
│   │   │   │   └── UserProfile.kt
│   │   │   │
│   │   │   ├── algorithm/
│   │   │   │   ├── SpacedRepetitionEngine.kt  # Interface
│   │   │   │   └── SM2Algorithm.kt            # SM-2 implementation
│   │   │   │
│   │   │   └── usecase/
│   │   │       ├── GetNextReviewCardUseCase.kt
│   │   │       ├── UpdateCardMasteryUseCase.kt
│   │   │       └── SyncCardsUseCase.kt
│   │   │
│   │   ├── ui/                             # UI LAYER
│   │   │   ├── MainActivity.kt            # Single activity (Task 3)
│   │   │   │
│   │   │   ├── navigation/
│   │   │   │   ├── KontextNavigation.kt   # NavHost setup
│   │   │   │   └── Screen.kt              # Sealed class routes
│   │   │   │
│   │   │   ├── screens/
│   │   │   │   ├── immerse/
│   │   │   │   │   ├── ImmerseScreen.kt
│   │   │   │   │   └── ImmerseViewModel.kt
│   │   │   │   │
│   │   │   │   ├── drill/
│   │   │   │   │   ├── DrillScreen.kt
│   │   │   │   │   ├── DrillViewModel.kt
│   │   │   │   │   └── components/
│   │   │   │   │       ├── FlashCard.kt
│   │   │   │   │       └── RatingButtons.kt
│   │   │   │   │
│   │   │   │   └── profile/
│   │   │   │       ├── ProfileScreen.kt
│   │   │   │       └── ProfileViewModel.kt
│   │   │   │
│   │   │   ├── theme/
│   │   │   │   ├── Color.kt
│   │   │   │   ├── Theme.kt
│   │   │   │   └── Type.kt
│   │   │   │
│   │   │   └── common/                    # Reusable components
│   │   │       ├── LoadingIndicator.kt
│   │   │       └── ErrorMessage.kt
│   │   │
│   │   └── util/                           # UTILITIES
│   │       ├── SeedManager.kt             # CSV seeding (Task 2)
│   │       ├── CsvParser.kt
│   │       ├── Constants.kt
│   │       └── Result.kt                  # Sealed class for errors
│   │
│   └── res/
│       ├── raw/
│       │   └── german_4000.csv            # Seed data
│       ├── values/
│       │   ├── strings.xml
│       │   └── themes.xml
│       └── drawable/
│
├── build.gradle.kts                        # Module dependencies
└── proguard-rules.pro
```

---

## 🗄️ Database Schema

### VocabCard Entity (Task 1)

```kotlin
@Entity(tableName = "vocab_cards")
data class VocabCard(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "german_term")
    val germanTerm: String,
    
    @ColumnInfo(name = "english_term")
    val englishTerm: String,
    
    @ColumnInfo(name = "example_sentence_german")
    val exampleSentenceGerman: String,
    
    @ColumnInfo(name = "example_sentence_english")
    val exampleSentenceEnglish: String,
    
    @ColumnInfo(name = "mastery_level")
    val masteryLevel: Int = 0, // 0-5 scale
    
    @ColumnInfo(name = "next_review_timestamp")
    val nextReviewTimestamp: Long,
    
    @ColumnInfo(name = "audio_path")
    val audioPath: String? = null,
    
    @ColumnInfo(name = "user_id")
    val userId: String, // Supabase user ID
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "last_reviewed_at")
    val lastReviewedAt: Long? = null,
    
    @ColumnInfo(name = "review_count")
    val reviewCount: Int = 0
)
```

**Design Rationale**:
- `autoGenerate = true`: Room handles ID assignment (idempotent inserts)
- `userId`: Multi-user support from day 1
- `nextReviewTimestamp`: Enables efficient "due cards" queries
- `reviewCount`: Analytics for user progress
- Nullable `audioPath`: Future feature, doesn't block MVP

---

## 🌱 Seeding Strategy (Task 2)

### Requirements
- Parse `german_4000.csv` from `res/raw`
- Insert 4000 rows on first launch only
- Must not block main thread
- Must be idempotent (safe to call multiple times)

### Implementation Plan

```kotlin
@Singleton
class SeedManager @Inject constructor(
    private val database: KontextDatabase,
    private val context: Application,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val prefs = context.getSharedPreferences("kontext", Context.MODE_PRIVATE)
    
    suspend fun seedIfNeeded(userId: String): Result<Int> = withContext(ioDispatcher) {
        try {
            // Check if already seeded for this user
            val key = "seeded_$userId"
            if (prefs.getBoolean(key, false)) {
                val count = database.vocabCardDao().getCountForUser(userId)
                return@withContext Result.Success(count)
            }
            
            // Parse CSV
            val inputStream = context.resources.openRawResource(R.raw.german_4000)
            val cards = CsvParser.parse(inputStream, userId)
            
            // Batch insert (500 at a time for performance)
            cards.chunked(500).forEach { chunk ->
                database.vocabCardDao().insertAll(chunk)
            }
            
            // Mark as seeded
            prefs.edit().putBoolean(key, true).apply()
            
            Result.Success(cards.size)
        } catch (e: Exception) {
            Log.e("SeedManager", "Seeding failed", e)
            Result.Error(e)
        }
    }
}
```

**CSV Parser**:
```kotlin
object CsvParser {
    fun parse(inputStream: InputStream, userId: String): List<VocabCard> {
        return inputStream.bufferedReader().useLines { lines ->
            lines.drop(1) // Skip header
                .mapNotNull { line ->
                    parseLine(line, userId)
                }
                .toList()
        }
    }
    
    private fun parseLine(line: String, userId: String): VocabCard? {
        val parts = line.split(",")
        if (parts.size < 4) return null
        
        return VocabCard(
            germanTerm = parts[0].trim(),
            englishTerm = parts[1].trim(),
            exampleSentenceGerman = parts[2].trim(),
            exampleSentenceEnglish = parts[3].trim(),
            masteryLevel = 0,
            nextReviewTimestamp = System.currentTimeMillis(),
            userId = userId
        )
    }
}
```

**Entropy Prevention**:
- ✅ Idempotent: Checks `SharedPreferences` before seeding
- ✅ Non-blocking: Uses `Dispatchers.IO`
- ✅ Chunked inserts: Prevents memory spikes
- ✅ Error handling: Returns `Result` type

---

## 🎨 UI Architecture (Task 3)

### MainActivity with Bottom Navigation

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KontextTheme {
                KontextApp()
            }
        }
    }
}

@Composable
fun KontextApp() {
    val navController = rememberNavController()
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Book, "Immerse") },
                    label = { Text("Immerse") },
                    selected = false,
                    onClick = { navController.navigate(Screen.Immerse.route) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.School, "Drill") },
                    label = { Text("Drill") },
                    selected = false,
                    onClick = { navController.navigate(Screen.Drill.route) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, "Profile") },
                    label = { Text("Profile") },
                    selected = false,
                    onClick = { navController.navigate(Screen.Profile.route) }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Drill.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Immerse.route) { ImmerseScreen() }
            composable(Screen.Drill.route) { DrillScreen() }
            composable(Screen.Profile.route) { ProfileScreen() }
        }
    }
}
```

---

## 📚 Dependencies (build.gradle.kts)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.kontext"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kontext"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Supabase configuration (from local.properties)
        buildConfigField("String", "SUPABASE_URL", "\"${project.findProperty("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${project.findProperty("SUPABASE_KEY")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose BOM (Bill of Materials)
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Hilt Dependency Injection
    val hiltVersion = "2.50"
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    ksp("com.google.dagger:hilt-compiler:$hiltVersion")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Supabase
    implementation(platform("io.github.jan-tennert.supabase:bom:2.0.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.ktor:ktor-client-android:2.3.7")
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-utils:2.3.7")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    
    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

---

## 🔐 Configuration Management

### local.properties (NOT committed to Git)
```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_KEY=your-anon-key-here
```

### .gitignore
```
local.properties
*.keystore
```

---

## 🧪 Testing Strategy

### Unit Tests
- `VocabCardDaoTest`: Room database operations
- `CsvParserTest`: CSV parsing edge cases
- `SM2AlgorithmTest`: Spaced repetition calculations
- `DrillViewModelTest`: State management

### Integration Tests
- `SeedManagerTest`: End-to-end seeding
- `VocabRepositoryTest`: Local + Remote sync

### UI Tests
- `DrillScreenTest`: Flashcard interactions
- `NavigationTest`: Bottom nav flow

---

## 🚀 Implementation Phases

### Phase 1: Foundation (Tracer Bullet) ⚡
**Goal**: Prove the entire stack works end-to-end

1. ✅ Create `VocabCard` entity
2. ✅ Create `VocabCardDao` with basic queries
3. ✅ Create `KontextDatabase`
4. ✅ Setup Hilt modules (`DatabaseModule`, `AppModule`)
5. ✅ Create `SeedManager` (test with 10 CSV rows first)
6. ✅ Create `MainActivity` with bottom nav shell
7. ✅ Create `DrillScreen` that displays card count

**Verification**: Launch app → See "4000 cards loaded"

---

### Phase 2: Spaced Repetition Logic 🧠
**Goal**: Implement scientifically-backed review algorithm

1. Create `SpacedRepetitionEngine` interface
2. Implement `SM2Algorithm`
3. Create `GetNextReviewCardUseCase`
4. Create `UpdateCardMasteryUseCase`
5. Wire into `DrillViewModel`
6. Build flashcard UI with rating buttons

**Verification**: Review 10 cards → Verify intervals increase correctly

---

### Phase 3: Supabase Integration 🌐
**Goal**: Multi-user support with cloud sync

1. Setup `SupabaseClient` in Hilt
2. Implement GoTrue authentication
3. Create `UserRepository`
4. Build `ProfileScreen` with login/logout
5. Implement background sync logic
6. Add conflict resolution (local-first strategy)

**Verification**: Login → Seed data → Logout → Login as different user → See separate data

---

### Phase 4: Polish & Optimization ✨
**Goal**: Production-ready app

1. Add audio playback for `audioPath`
2. Implement progress charts in `ProfileScreen`
3. Add animations to flashcard flips
4. Optimize database queries with indices
5. Add ProGuard rules
6. Write comprehensive tests

---

## 🎯 Success Metrics

| Metric | Target | Verification |
|--------|--------|--------------|
| App launch time | < 2 seconds | Cold start profiling |
| CSV seeding time | < 5 seconds for 4000 rows | Background task logging |
| Card review latency | < 100ms | ViewModel state updates |
| Memory usage | < 100MB | Android Profiler |
| Test coverage | > 80% | JaCoCo report |

---

## 🚨 Risk Mitigation

| Risk | Mitigation |
|------|------------|
| CSV parsing fails | Robust error handling + fallback to empty DB |
| Supabase conflicts with Room | Test integration early in Phase 1 |
| Process death loses state | Use `SavedStateHandle` in ViewModels |
| Large dataset performance | Implement pagination with Paging 3 library |
| Multi-user data leakage | Add `userId` to all queries + integration tests |

---

## 📖 References

- [Pragmatic Programmer Principles](https://pragprog.com/titles/tpp20/)
- [Android Architecture Guide](https://developer.android.com/topic/architecture)
- [Jetpack Compose Best Practices](https://developer.android.com/jetpack/compose/mental-model)
- [SM-2 Spaced Repetition Algorithm](https://www.supermemo.com/en/archives1990-2015/english/ol/sm2)
- [Supabase Kotlin Documentation](https://supabase.com/docs/reference/kotlin)

---

**Document Version**: 1.0  
**Last Updated**: 2025-12-23  
**Author**: Principal Android Architect  
**Status**: Ready for Implementation
