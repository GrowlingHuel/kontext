📂 Kontext: Project Master Context
Last Updated: Sprint 3 Complete (Flashcard Game Loop Functional)
Mission: Build "The Anti-Duolingo"—a scientific, high-efficiency German learning app using Vibe Coding.

1. 🏗️ The Tech Stack (Strict Constraints)
Language: Kotlin (100% pure).

UI Toolkit: Jetpack Compose (Material 3).

Architecture: MVVM (Model-View-ViewModel) + Clean Architecture.

Dependency Injection: Hilt.

Local Database: Room (SQLite).

Remote Backend: Supabase (PostgreSQL + Auth).

AI/Voice: OpenAI Whisper API (via Retrofit).

Build System: Gradle (Kotlin DSL).

2. 📜 The "Clean Code" Constitution
Any AI working on this project must adhere to these rules:

Orthogonality: UI (@Composable) must be strictly decoupled from Logic (ViewModel) and Data (Repository).

No XML: Use only Jetpack Compose for UI.

Modern Concurrency: Use Coroutines and Flow. No legacy Java callbacks or AsyncTask.

Configuration Survival: ViewModels must handle process death.

AndroidX Enforcement: The project uses android.useAndroidX=true.

3. 💾 Data Schema (Current Reality)
Local: Room Database (AppDatabase)
Entity: VocabCard

id (Int, PK, AutoGen)

germanTerm (String)

englishTerm (String)

exampleSentenceGerman (String)

exampleSentenceEnglish (String)

masteryLevel (Int) - SM-2 Level (0-5)

nextReviewTimestamp (Long) - Epoch millis

audioPath (String?) - Local path to cached audio

reviewCount (Int) - Number of times reviewed

lastReviewedAt (Long?) - Timestamp of last review

4. 📍 Current State (The "Truth")
✅ Completed
Sprint 1 (Skeleton): Project initialized, Hilt wired up, Navigation Graph set up (Drill, Immerse, Profile tabs).

Sprint 2 (Seeding):
SeedManager created.
Parses german_4000.csv from res/raw.
Auto-populates DB on first launch (Idempotent).
Verified: App launches and Drill Screen reports "10 cards".
Infrastructure Fixes: Added gradle.properties with JVM memory boost and AndroidX flags.

Sprint 3 (The Game Loop):
DrillViewModel: Implements the loop (Load -> Flip -> Grade -> Update).
UI: Interactive Flashcard with Flip animation and Grading Buttons (Again/Hard/Good/Easy).
Algorithm: SM-2 logic implemented (`SpacedRepetitionEngine`) to calculate next review intervals.
Data: `updateCard` functionality added to Repository/DAO.

🚧 In Progress
Refining UI transitions and animations.

5. 🗺️ Future Roadmap
Sprint 4 (Supabase Integration): Fix Auth dependency and implement User Profile / Cloud Sync.

Sprint 5 (Voice): Integrate OpenAI Whisper to allow "Spoken Answers."

Sprint 6 (AI Storyteller): Connect Gemini API to generate dynamic stories based on only the user's "Known Words".

6. 🐛 Known Quirks & Workarounds
Gradle Warnings: We are ignoring "Deprecated Feature" warnings regarding Gradle 10.0.

Emulator: Requires "Always On Top" mode for efficient Vibe Coding.

Supabase Auth: Dependency temporarily disabled (`implementation("...auth-kt")` commented out) in `app/build.gradle.kts` to unblock compilation. Needs resolution in Sprint 4.
