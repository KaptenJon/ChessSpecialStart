# ChessPoints

ChessPoints is a simple, beautiful Android chess game where players buy a 16-piece army with a shared point budget, place those pieces on the board, and then play standard chess from the custom setup.

See `PROJECT_DESCRIPTION.md` for the product overview, rules, and v1 scope.

## Open in Android Studio

1. Open Android Studio.
2. Choose **Open** and select `C:\repos\ChessPoints`.
3. Let Android Studio sync the Gradle project.
4. Build the app with **Build > Make Project** or run `assembleDebug` from the Gradle tool window.

Android Studio can also install or configure the required Android SDK packages automatically during project sync if they are missing.

## Building Locally

Prerequisites:
- JDK 17 or newer
- Android SDK with platform 34+ and build-tools 34.0.0
- Or just open the project in Android Studio and let it provision what is missing automatically

Build and test:
- `./gradlew :engine:test :ai:test`
- `./gradlew :app:assembleDebug`

On Windows, use `gradlew.bat` instead of `./gradlew`.

Debug APK output:
- `app/build/outputs/apk/debug/app-debug.apk`

## Project Layout

- `:app` — Android application shell, `MainActivity`, app theme, and Compose entry point
- `:engine` — pure Kotlin chess rules and game-state module, owned by **Engine Dev**
- `:ai` — pure Kotlin opponent and decision-making module, owned by **AI Dev**

## Package Ownership Map

- `com.chesspoints.app` — Android app bootstrap and application wiring
- `com.chesspoints.app.ui.theme` — shared Compose theme for the app shell
- `com.chesspoints.ui.draft` — **Android UI Dev** owns the draft/shop screen flow
- `com.chesspoints.ui.placement` — **Android UI Dev** owns the placement screen flow
- `com.chesspoints.ui.game` — **Android UI Dev** owns the in-game board screen flow
- `com.chesspoints.engine` — **Engine Dev** owns rules, validation, board state, and move generation
- `com.chesspoints.ai` — **AI Dev** owns army-buying, placement, and move-selection logic

## Build Targets

- Android app package: `com.chesspoints.app`
- Minimum SDK: 26
- Compile / target SDK: 35
