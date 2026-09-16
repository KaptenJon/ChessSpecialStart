# Squad Decisions

## Active Decisions

### 2026-09-16: Default v1 economy framing for project description
**By:** Lead
**What:** The project description uses classic chess point values (P=1, N=3, B=3, R=5, Q=9, King free/mandatory) and defines the default shared budget as 39 points plus one mandatory king, with exactly 16 total pieces per side.
**Why:** This gives the team a concrete baseline that maps directly to standard chess material value, preserves budget parity, and removes ambiguity for UI, engine, AI, and test planning.

### 2026-09-16: ChessPoints initial Android project scaffold
**By:** Lead
**What:** Scaffolded ChessPoints as a multi-module Gradle project with `:app` for the Android shell, `:engine` for pure Kotlin chess logic, and `:ai` for pure Kotlin AI logic. Set `minSdk` to 26 and `compileSdk` / `targetSdk` to 35. Established ownership packages for `com.chesspoints.ui.*`, `com.chesspoints.engine`, and `com.chesspoints.ai`.
**Why:** Splitting `:engine` and `:ai` out now keeps rules and AI Android-free from day one, reduces future refactors, gives the Android UI Dev a clean app shell to build on, and makes package ownership explicit for each teammate before feature work starts.

### 2026-09-16: ChessPoints rules engine public API
**By:** Engine Dev
**What:** Implemented the `:engine` pure Kotlin JVM rules engine around `PieceType`, `Color`, `Square`, immutable `Board`, `Roster`, `DraftValidator`, `PlacementValidator`, `GamePosition`, `MoveEngine`, and `ChessGame`. Public entry points for other modules are `ChessGame.submitDraft(...)`, `ChessGame.placePiece(...)`, `ChessGame.makeMove(...)`, `ChessGame.getLegalMoves(...)`, `ChessGame.getGameState()`, plus direct stateless helpers on `DraftValidator` and `MoveEngine`.
**Why:** `:ai` and Android UI both need one authoritative rules surface for draft validation, placement flow, legal move generation, and game outcome detection.

### 2026-09-16: Castling rule under custom starting layouts
**By:** Engine Dev
**What:** Castling rights are created only if the final placed starting layout has the king on the standard home square (`e1`/`e8`) and the specific rook on the standard matching rook square (`a1`/`h1`/`a8`/`h8`). Standard no-check/no-through-check/no-through-pieces rules still apply, and rights are lost once that king or rook moves or the rook is captured on its home square.
**Why:** ChessPoints allows arbitrary purchased armies and arbitrary placement inside each side's zone, so castling needs a deterministic rule that remains compatible with standard chess expectations without inventing per-rook custom castle lanes.

### 2026-09-16: Default engine constants and integration assumptions
**By:** Engine Dev
**What:** Default draft constants are `DEFAULT_BUDGET = 39` and `DEFAULT_ARMY_SIZE = 16`. Default placement zones are white ranks `0..1` (`1st-2nd` ranks) and black ranks `6..7` (`7th-8th` ranks). `Board` is immutable, so callers should replace board references instead of mutating in place. `Square.rank` is zero-based (`0 == rank 1`), `Square.file` is zero-based (`0 == file a`), and `Square.algebraic` / `Square.fromAlgebraic(...)` are the safest boundary helpers for UI and AI. Promotion requires the caller to specify the target piece in `MoveRequest`.
**Why:** These defaults match the product spec, keep the public API deterministic, and give Android UI Dev / AI Dev / Tester a stable contract to build against.

### 2026-09-16: Expanded engine edge-case coverage and seeded minimal AI test stub
**By:** Tester
**What:** Added focused `:engine` tests for draft validation boundaries (exact budget, over-budget via tighter rules, king-count and piece-count failures, unusual legal composition), placement validation failures (wrong turn, unavailable piece, occupied square, outside zone) plus readiness/phase-transition checks, and move-engine coverage for sparse-board move generation by piece type, check, checkmate, stalemate, castling eligibility under the custom-layout rule, en passant target handling, and promotion request requirements. Also added a minimal `:ai` placeholder test file with TODO coverage notes and a safe smoke test that only verifies the AI test source set compiles against `:engine`.
**Why:** Engine Dev exposed a stable rules API and initial tests, but several requirement-level edge cases and outcome scenarios were still uncovered. Strengthening these tests improves regression protection without changing engine behavior. No engine bugs were found in the exercised cases; `:engine:test` and `:ai:test` passed locally.

### 2026-09-16: AI opponent public API
**By:** AI Dev
**What:** Added the `:ai` module AI surface as three pure Kotlin entry points: `AiDrafter.draft(color, rules = DraftRules(), random = Random.Default): Roster`, `AiPlacer.nextPlacement(state, color = state.sideToPlace): AiPlacementChoice?`, and `AiMoveEngine.chooseMove(position, config = AiSearchConfig(depth = 2, timeBudgetMillis = null)): AiMoveChoice`. `AiMoveChoice` includes the selected legal move plus search diagnostics (`searchedDepth`, `evaluatedNodes`, `completedRequestedDepth`) for difficulty tuning and UI telemetry.
**Why:** Android UI Dev needs a small, engine-backed API for solo play that can draft a legal army, choose legal placement turns, and search legal game moves without depending on Android threading APIs.

### 2026-09-16: AI opponent defaults and assumptions
**By:** AI Dev
**What:** The default move-search depth is `2` plies for a fast v1 baseline. Drafting uses several validated 16-piece templates for the default 39-point budget, then falls back to an adaptive pawn-upgrade builder if templates do not fit the active rules. Placement heuristics favor a back-rank, corner-ish king; edge-oriented rooks; central minor pieces; and front-rank pawns, while always asking the engine validator to confirm legality.
**Why:** These defaults keep the AI simple, deterministic to integrate, and cheap enough for the UI to run from a background coroutine while still producing varied armies and sensible opening setups.

### 2026-09-16: Core Compose screen flow for ChessPoints
**By:** Android UI Dev
**What:** Implemented a state-driven Compose flow of Home → Draft → Placement → Game in the app module, backed by a shared `ChessPointsState` that owns `ChessGame` state and exposes a small `AiOpponentGateway` seam for future `:ai` integration.
**Why:** The app needed its first playable UI shell around the engine API, plus a clean boundary so AI Dev can plug draft/placement/move decisions into the existing screens without reworking navigation or composables.

### 2026-09-16: Sequential draft UX on one device
**By:** Android UI Dev
**What:** Chose sequential drafting (White/Player first, then Black/AI) for the v1 draft screen rather than simultaneous entry.
**Why:** On a shared Android device, sequential drafting keeps each roster private, matches pass-and-play expectations, and maps cleanly onto `ChessGame.submitDraft(...)`, which already accepts one roster per color before transitioning into placement.

### 2026-09-16: Tap-to-select, tap-to-place placement interaction
**By:** Android UI Dev
**What:** Implemented placement as selecting a piece from the tray and then tapping a legal square, instead of drag-and-drop.
**Why:** Tap placement is simpler to ship, easier to validate against `ChessGame.placePiece(...)`, and keeps the board interaction consistent with the game screen's piece-then-destination move flow. Drag-and-drop can be layered on later without changing engine contracts.

### 2026-09-16: AI integration seam lives in app state
**By:** Android UI Dev
**What:** Added `AiOpponentGateway` with methods for roster creation, placement choice, and move choice, plus a placeholder implementation that reports the AI bridge as pending.
**Why:** The `:ai` module does not yet expose a consumable gameplay API, so the UI needed a stable ViewModel/state-layer seam now. AI Dev can later implement the gateway against the real module without changing screen structure or user-facing navigation.

### 2026-09-16: Wired app-state AI gateway to the real :ai module
**By:** Android UI Dev
**What:** Replaced the placeholder `AiOpponentGateway` with an implementation backed by `AiDrafter`, `AiPlacer`, and `AiMoveEngine`, and moved AI draft, placement, and move requests onto background coroutines before applying results back into `ChessPointsState`.
**Why:** Solo play now needs the finished AI Dev API to drive Black's roster, alternating setup turns, and live moves without blocking the Compose UI thread or changing the existing screen/navigation flow.

### 2026-09-16: Local Android build environment verified
**By:** Lead
**What:** Established and verified a working local Android build toolchain on the repo owner's machine:
- Installed JDK 17 (Eclipse Temurin) at `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` because Android Gradle Plugin `8.7.3` requires JVM 11+ and the machine default Java was `1.8`.
- Installed Android SDK command-line tools at `C:\android-sdk`, accepted all SDK licenses, and installed `platform-tools`, `platforms;android-34`, and `build-tools;34.0.0`. Gradle also auto-resolved and installed platform `35` during build resolution.
- Generated the real Gradle wrapper artifacts locally via Gradle `8.9`: `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`. The wrapper JAR had previously been absent because it is not created by a normal `git init`.
- Created `local.properties` with `sdk.dir=C\:\\android-sdk` for this machine's Android SDK location. This file is intentionally local-only and remains ignored.
- Verified the full local build with `gradlew.bat :engine:test :ai:test :app:assembleDebug`, which completed with `BUILD SUCCESSFUL`. All `:engine` and `:ai` unit tests passed, and the debug APK was produced at `app/build/outputs/apk/debug/app-debug.apk`.
- Recorded two real fixes discovered during verification:
  1. Added `com.google.android.material:material` to `app/build.gradle.kts` so the XML parent theme `Theme.Material3.DayNight.NoActionBar` in `res/values/themes.xml` resolves correctly.
  2. Fixed Android UI Dev screen imports in `DraftScreen.kt`, `GameScreen.kt`, `PlacementScreen.kt`, and `ChessBoard.kt` by importing `verticalScroll` from `androidx.compose.foundation` instead of `androidx.compose.foundation.layout`, and by removing the incorrect explicit `weight` import that shadowed the scoped `Row`/`Column` extension. The fix was verified by a passing `:app:compileDebugKotlin`.
- Decision on source control:
  - `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` **should be committed** so every machine and CI environment can use the same reproducible Gradle wrapper.
  - `local.properties`, the installed Android SDK, and the installed JDK **must not be committed**, because they are machine-specific environment state.
**Why:** Reproducible local builds now depend on a documented Java/Android toolchain baseline plus committed Gradle wrapper artifacts, while machine-specific SDK pointers remain local. Capturing the two bugs found during bring-up prevents other teammates from re-discovering the same failures.

### 2026-09-16: Compose scroll/weight import build fix
**By:** Android UI Dev
**What:** Fixed Compose import mistakes in `DraftScreen`, `GameScreen`, and `PlacementScreen` by switching `verticalScroll` to `androidx.compose.foundation.verticalScroll`, and removed explicit `weight` imports from `DraftScreen` and `ChessBoard` so `Modifier.weight(...)` resolves from the surrounding `Row`/`Column` scope receivers. Also checked the rest of `app/src/main/java/com/chesspoints/` for the same Compose foundation import mistake pattern and found no other instances.
**Why:** The first real Gradle compile surfaced imports that were accepted in earlier stubbed work but are invalid against the actual Compose APIs. `verticalScroll` lives in `foundation`, not `foundation.layout`, and explicit `weight` imports were binding to the wrong symbol instead of the scoped Compose extension used inside `Row`/`Column`.

## Governance

- All meaningful changes require team consensus
- Document architectural decisions here
- Keep history focused on work, decisions focused on direction