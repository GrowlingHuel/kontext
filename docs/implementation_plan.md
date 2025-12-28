# Phase 2: Spaced Repetition Logic Implementation Plan

## 🎯 Goal
Implement a scientifically-backed Spaced Repetition System (SRS) using the SuperMemo-2 (SM-2) algorithm. This ensures users review words at optimal intervals to maximize retention.

## 🏗️ Proposed Changes

### 1. Domain Layer: Spaced Repetition Engine
- **Interface**: `SpacedRepetitionEngine`
    - Method: `calculateNextReview(currentMastery: Int, rating: Int, lastReview: Long): ReviewResult`
- **Implementation**: `SM2Algorithm`
    - Implements SM-2 logic:
        - Rating (0-5) determines next interval.
        - Updates `masteryLevel` (EF factor approximation).
        - Returns `nextReviewTimestamp`.

### 2. Domain Layer: Use Cases
- **`GetNextReviewCardUseCase`**:
    - Dependencies: `VocabRepository`, `SessionManager`.
    - Logic: Fetch cards where `nextReviewTimestamp <= now`.
- **`UpdateCardMasteryUseCase`**:
    - Dependencies: `VocabRepository`, `SpacedRepetitionEngine`, `SessionManager`.
    - Logic:
        1.  Take `card` and user `rating`.
        2.  Call `engine.calculateNextReview()`.
        3.  Update card entity with new `masteryLevel`, `nextReviewTimestamp`, `lastReviewedAt`, and increment `reviewCount`.
        4.  Save to Repository.

### 3. UI Layer: DrillViewModel
- **Refactor**:
    - Inject Use Cases instead of Repository directly (following Clean Architecture).
    - Handle `onCardRated(rating: Int)` event.
    - Expose `currentCard` and `sessionStats` via StateFlow.

### 4. Utility: SeedManager Fix
- **Refactor**:
    - Inject `SessionManager`.
    - Use `sessionManager.getCurrentUserId()` instead of hardcoded `"local_user"` when parsing CSV.

## 📝 Implementation Steps
1.  **[Domain]** Create `SpacedRepetitionEngine` interface & `SM2Algorithm`.
2.  **[Domain]** Create Use Cases.
3.  **[Util]** Fix `SeedManager`.
4.  **[UI]** Refactor `DrillViewModel` to use new Use Cases.
5.  **[DI]** Bind `SpacedRepetitionEngine` in `AlgorithmModule`.

## 🧪 Verification Plan

### Automated Tests
- **`SM2AlgorithmTest`**:
    - Verify interval growth (e.g., Rating 5 should increase interval significantly).
    - Verify regression (Rating 1 should reset/shorten interval).
- **`DrillViewModelTest`**:
    - Mock Use Cases.
    - Verify `onCardRated` triggers `UpdateCardMasteryUseCase`.

### User Verification
1.  **Drill Screen**:
    - Open "Drill" tab.
    - Verify a card appears.
    - Rate the card (e.g., "Hard").
    - Verify the next card appears.
    - (Optional) Check Database Inspector to see if `next_review_timestamp` updated correctly.
