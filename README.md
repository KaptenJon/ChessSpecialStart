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
- Android SDK with platform 35 and build-tools 35.0.0
- Or just open the project in Android Studio and let it provision what is missing automatically

Build and test:
- `./gradlew :engine:test :ai:test`
- `./gradlew :app:assembleDebug`

On Windows, use `gradlew.bat` instead of `./gradlew`.

Debug APK output:
- `app/build/outputs/apk/debug/app-debug.apk`

`local.properties` is machine-local and gitignored. It can point to your Android SDK for local builds, but CI must provision its own SDK and does not rely on a committed `local.properties`.

## CI/CD

GitHub Actions workflows live in `.github/workflows/`:

- `ci.yml` runs on every push to `main` and on every pull request. It provisions JDK 17 and the Android SDK, runs `:engine:test` and `:ai:test`, builds `:app:assembleDebug`, and uploads the debug APK as a workflow artifact.
- `release.yml` runs when a version tag matching `v*.*.*` is pushed. It reruns the JVM tests, builds `:app:assembleRelease`, and creates a GitHub Release with the release APK attached.

Release builds require these repository secrets so CI can sign the APK:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

To create a release keystore, follow Android's official signing guidance:
https://developer.android.com/studio/publish/app-signing#generate-key

Example `keytool` command:

- `keytool -genkeypair -v -keystore release.keystore -alias chesspoints -keyalg RSA -keysize 2048 -validity 10000`

Base64-encode the keystore before uploading it as `RELEASE_KEYSTORE_BASE64`:

- Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))`
- Linux: `base64 -w0 release.keystore`

The release workflow decodes that secret during CI, signs the APK when all four values are present, and local builds continue to work without any committed keystore file.

## Run on a Connected Device

Use the helper scripts in `scripts/` to build, install, and launch the debug APK on a USB-connected Android device.

Prerequisites:

- USB debugging enabled on the device
- A connected device visible to `adb`
- JDK 17 available locally (`JAVA_HOME` preferred)
- Android SDK available via `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or `local.properties`

Windows:

- `powershell -ExecutionPolicy Bypass -File .\scripts\run-on-device.ps1`

macOS / Linux:

- `bash ./scripts/run-on-device.sh`

The scripts verify Java, locate `adb`, confirm a connected device, build `:app:assembleDebug`, install `app/build/outputs/apk/debug/app-debug.apk`, and launch `com.chesspoints.app/.MainActivity`.

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
