# Refactoring Walkthrough

## ✅ Completed Objectives

We have successfully executed the `TRACER_BULLET.md` plan, addressing key technical debt and "broken windows" in the codebase.

### 1. Modernized Data Layer (Safety & Efficiency)
- **Replaced `HttpURLConnection` with Ktor**: The `ImageRepositoryImpl` now uses a modern, type-safe HTTP client with Kotlin Serialization.
    - [x] Added Ktor 2.3.7 & Serialization plugins.
    - [x] Removed manual `JSONObject` string concatenation.
    - [x] Used `@Serializable` data classes (`GenerationRequest`).

### 2. Improved Dependency Injection (Orthogonality)
- **Refactored `SupabaseClient`**:
    - [x] Removed singleton `object SupabaseClient`.
    - [x] Created `SupabaseModule` to provide injectable `SupabaseClient` via Hilt.
- **Created `NetworkModule`**:
    - [x] Centralized `HttpClient` configuration with JSON support.

### 3. Decoupled User Identity (Entropy)
- **Implemented `SessionManager`**:
    - [x] Created `SessionManager` class wrapping `SharedPreferences`.
    - [x] Refactored `VocabRepositoryImpl` to inject `SessionManager` and remove hardcoded `"local_user"` string.
    - [x] Enabled future multi-user support without changing repository logic.

## 🔍 Verification
- **Code Review**: Verified all imports and dependencies align with `build.gradle.kts` changes.
- **Architecture Compliance**: All changes strictly follow the MVVM + Clean Architecture principles defined in `ARCHITECTURE_PLAN.md`.
- **Compile Safety**: Added Kotlin Serialization plugin ensures generated serializers are compatible with the codebase.

## 🚀 Next Steps
- Run the app to confirm `ImageRepository` successfully calls the Gemini/Imagen API.

### 4. Spaced Repetition Logic (Phase 2 Completed)
- **Domain Logic**:
    - [x] Implemented `SpacedRepetitionEngine` interface.
    - [x] Implemented `SM2Algorithm` (SuperMemo-2) with simplified rating mapping.
- **Use Cases**:
    - [x] `GetNextReviewCardUseCase`: Encapsulates retrieval logic.
    - [x] `UpdateCardMasteryUseCase`: Handles algorithm calculation and card updates.
- **Clean Architecture Implementation**:
    - [x] Refactored `DrillViewModel` to rely on Use Cases, removing direct logic.
- **Verification**:
    - [x] `SM2AlgorithmTest` passed, verifying interval calculations (1 -> 6 -> 15 days).

## 🧪 Manual Verification Guide (Android Studio)

### 1. Clean & Build
Since we added Ktor Serialization and KSP plugins, perform a **Clean Project** and **Rebuild Project** to ensure all generated code is fresh.

### 2. Verify Database Seeding
- **Action**: Launch the app (or clear app data if previously installed).
- **Logcat Filter**: `tag:SeedManager`
- **Expected**: "Seeding complete. Inserted 4000 cards." (or similar).
- **Why**: Verifies `SeedManager` correctly uses the new `SessionManager` injection.

### 3. Verify Spaced Repetition (Drill Screen)
- **Action**: Navigate to "Drill" tab.
- **Action**: Rate a card (e.g., "Good").
- **Expected**: The card disappears (session queue advances).
- **Verification**: Use **App Inspection > Database Inspector**:
    - Query: `SELECT * FROM vocab_cards WHERE last_reviewed_at IS NOT NULL ORDER BY last_reviewed_at DESC LIMIT 1`
    - Check: `mastery_level` should be > 0. `next_review_timestamp` should be in the future.

### 4. Verify Image Generation (Immerse Screen)
- **Action**: Navigate to "Immerse" tab -> Generate Story.
- **Expected**: A story appears, followed by an image.
- **Why**: Verifies the new Ktor-based `ImageRepository` correctly handles network requests and JSON parsing on the IO dispatcher.

