# Tracer Bullet: Codebase Refactoring Plan

## 🎯 Goal
Align the codebase with the architectural principles defined in `ARCHITECTURE_PLAN.md`, specifically addressing "Broken Windows" in the Data Layer and ensuring robust, testable, and orthogonal code.

## 🔍 Analysis Summary
| Component | Status | Issues Identified | Risk |
|-----------|--------|-------------------|------|
| **ImageRepository** | 🔴 Critical | Uses raw `HttpURLConnection`, manual JSON parsing, weak error handling. | High (Security/Maintainability) |
| **VocabRepository** | 🟠 Warning | Hardcoded `"local_user"`. | Medium (Scalability) |
| **SupabaseClient** | 🟠 Warning | Singleton `object`, hard to test/mock. | Medium (Testability) |
| **StoryRepository** | 🟡 Monitor | Direct Gemini API usage; functional but could be more decoupled. | Low |
| **UI Layer** | 🟢 Good | `MainActivity` & Navigation adhere to best practices. | Low |

## 🛠️ Proposed Changes

### 1. Fix "Broken Window": Modernize ImageRepository
**Objective**: Combat Entropy & Improve Efficiency.
- **Action**: Replace `HttpURLConnection` with **Ktor** (already in dependencies) or `Retrofit`.
- **Why**: Reduces boilerplate, improves safety, enables proper interceptors/logging.
- **Detail**:
    - Create `ImageService` interface (if using Retrofit) or Ktor Client setup.
    - Inject into `ImageRepositoryImpl`.
    - Return `Result<Bitmap>` instead of nullable `Bitmap?`.

### 2. Dependency Injection Refactor
**Objective**: Orthogonality & Reentrancy.
- **Action**: Convert `SupabaseClient` object to a Hilt-provided class.
- **Why**: Allows injecting mock clients for testing; prevents static state leakage.
- **Detail**:
    - Update `SupabaseModule` (or create if missing) to provide `SupabaseClient`.
    - Inject `SupabaseClient` into Repositories.

### 3. Session Management
**Objective**: Decoupling & Security.
- **Action**: Implement `SessionManager` to handle User IDs.
- **Why**: Removes hardcoded `"local_user"` from `VocabRepository`.
- **Detail**:
    - Create `SessionManager` class (backed by DataStore or SharedPreferences).
    - Inject `SessionManager` into `VocabRepository`.
    - Update `getCardsForReview` to use dynamic `userId`.

### 4. Safety & Error Handling
**Objective**: Validity & Safety.
- **Action**: Standardize Error Handling.
- **Detail**:
    - Ensure all Repository methods return `Result<T>` or throw consistent exceptions that the ViewModel handles.
    - Remove `printStackTrace()`.

## 📝 Implementation Steps

1.  **[DI]** Convert `SupabaseClient` to proper Hilt Module.
2.  **[Data]** Refactor `ImageRepositoryImpl` to use Ktor/Retrofit.
3.  **[Domain]** Create `SessionManager` and inject into `VocabRepositoryImpl`.
4.  **[Verify]** Run unit tests (if any) or verify via "Tracer Bullet" manual test.

## 🧪 Verification Plan

### Automated
- **Unit Tests**:
    - Create `ImageRepositoryTest` mocking the network service.
    - Create `VocabRepositoryTest` mocking `SessionManager`.

### Manual (Tracer Bullet)
1.  **Launch App**: Ensure app still compiles and runs.
2.  **Generate Image**: Trigger image generation (if accessible) and verify no network crash.
3.  **Review Cards**: Verify card loading uses the correct (mocked or real) user ID.
