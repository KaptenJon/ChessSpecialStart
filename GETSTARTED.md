# ChessPoints — Get Started

New to ChessPoints? This guide gets you from clone to local builds, device runs, and signed release setup with as little friction as possible.

## 1. Prerequisites

Install these first:

- **JDK 17 or newer**
- **Android SDK**  
  Easiest path: install **Android Studio** and let it manage the SDK for you.
- **git**
- **GitHub CLI (`gh`)**

Then authenticate GitHub CLI:

```powershell
gh auth login
```

If you prefer working from the command line, Gradle can build the project directly. If you prefer a GUI, Android Studio is the easiest way to get a working Android environment.

## 2. Clone the repo

```powershell
git clone https://github.com/KaptenJon/ChessSpecialStart.git ChessPoints
cd ChessPoints
```

## 3. Open the project

### Android Studio

1. Open **Android Studio**
2. Choose **Open**
3. Select the `ChessPoints` folder
4. Let Gradle sync
5. Let Android Studio install any missing SDK packages

### Gradle directly

From the repo root:

```bash
./gradlew :app:assembleDebug
```

On Windows, use:

```powershell
.\gradlew.bat :app:assembleDebug
```

## 4. Run tests

Core JVM tests live in the pure Kotlin modules:

```bash
./gradlew :engine:test :ai:test
```

On Windows:

```powershell
.\gradlew.bat :engine:test :ai:test
```

## 5. Run on a connected device

ChessPoints includes helper scripts that build, install, and launch the debug APK on a USB-connected Android device.

### Windows

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-on-device.ps1
```

### macOS / Linux

```bash
bash ./scripts/run-on-device.sh
```

These scripts:

- verify Java
- locate `adb`
- confirm a connected device
- build `:app:assembleDebug`
- install the debug APK
- launch `com.chesspoints.app/.MainActivity`

## 6. Understand release signing in this repo

Before setting up releases, it helps to know the exact flow:

- `app/build.gradle.kts` looks for release signing values from environment variables:
  - `RELEASE_STORE_FILE`
  - `RELEASE_STORE_PASSWORD`
  - `RELEASE_KEY_ALIAS`
  - `RELEASE_KEY_PASSWORD`
- It also supports a local `keystore.properties` file with:
  - `storeFile`
  - `storePassword`
  - `keyAlias`
  - `keyPassword`
- `.github/workflows/release.yml` does **not** store a keystore file in the repo. Instead it expects these **GitHub Actions secrets**:
  - `RELEASE_KEYSTORE_BASE64`
  - `RELEASE_KEYSTORE_PASSWORD`
  - `RELEASE_KEY_ALIAS`
  - `RELEASE_KEY_PASSWORD`
- During the release workflow, GitHub Actions:
  1. decodes `RELEASE_KEYSTORE_BASE64` into a temporary `release.keystore`
  2. exports that file path as `RELEASE_STORE_FILE`
  3. exports the other three values as environment variables
  4. runs `:app:assembleRelease`

That is how CI produces a **signed** release APK without committing any signing files.

## 7. Generate a release signing keystore

If you do not already have a release keystore, generate one locally.

> Important: **never commit this file**. The repo already ignores `*.jks` and `*.keystore`, but you should still store the real keystore safely: password manager notes, encrypted backup, secure team vault, or another trusted backup location.

Example command:

```powershell
keytool -genkeypair -v -keystore release.keystore -alias chesspoints -keyalg RSA -keysize 2048 -validity 10000
```

What the flags mean:

- `-genkeypair` — create a public/private key pair for signing
- `-v` — show verbose progress
- `-keystore release.keystore` — write the keystore to `release.keystore`
- `-alias chesspoints` — name of the key inside the keystore
- `-keyalg RSA` — use RSA
- `-keysize 2048` — use a 2048-bit key
- `-validity 10000` — keep the certificate valid for 10,000 days

### Where to store it

Good options:

- outside the repo in an encrypted folder
- in a secure team vault
- in an encrypted backup

Bad options:

- committed to git
- emailed around casually
- left as the only copy on one machine

Losing the original release keystore means future APKs cannot update installs signed with the old one.

## 8. Set the 4 GitHub release secrets

### Easy path: use the setup script

The repo includes a Windows-first helper:

```powershell
.\scripts\setup-release-secrets.ps1
```

It will:

- verify `gh` is installed and authenticated
- detect the target repo (or let you pass `-Repo owner/repo`)
- use an existing keystore, or help generate one
- base64-encode the keystore
- set these four GitHub Actions secrets:
  - `RELEASE_KEYSTORE_BASE64`
  - `RELEASE_KEYSTORE_PASSWORD`
  - `RELEASE_KEY_ALIAS`
  - `RELEASE_KEY_PASSWORD`

Useful examples:

```powershell
.\scripts\setup-release-secrets.ps1
.\scripts\setup-release-secrets.ps1 -KeystorePath .\my-release.keystore
.\scripts\setup-release-secrets.ps1 -Repo KaptenJon/ChessSpecialStart -DryRun
```

### Manual fallback: what the script does under the hood

If you want to set the secrets yourself:

```powershell
$repo = "KaptenJon/ChessSpecialStart"
$keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes(".\release.keystore"))

gh secret set RELEASE_KEYSTORE_BASE64 --repo $repo --body $keystoreBase64
gh secret set RELEASE_KEYSTORE_PASSWORD --repo $repo --body "<store-password>"
gh secret set RELEASE_KEY_ALIAS --repo $repo --body "chesspoints"
gh secret set RELEASE_KEY_PASSWORD --repo $repo --body "<key-password>"
```

Use the PowerShell expression above (or the setup script) for
`RELEASE_KEYSTORE_BASE64`. The workflow accepts wrapped Windows Base64 by
removing whitespace before decoding, but the secret must contain raw standard
Base64; do not upload `certutil -encode` output with its header/footer lines.

Those secret names must match exactly, because `release.yml` reads them directly.

## 9. Cut a release

When the code is ready:

```bash
git tag vX.Y.Z
git push origin vX.Y.Z
```

Example:

```bash
git tag v0.2.0
git push origin v0.2.0
```

That tag triggers `.github/workflows/release.yml`.

After the workflow finishes:

- open the repo on GitHub
- go to **Releases**
- find the release for your tag
- download the attached signed APK asset

The APK is uploaded from:

- `app/build/outputs/apk/release/app-release.apk`

## 10. Quick checklist

If you are setting up a new maintainer machine or fork:

1. Install JDK 17+, Android Studio, git, and `gh`
2. Run `gh auth login`
3. Clone the repo
4. Run `./gradlew :engine:test :ai:test`
5. Use `scripts/run-on-device.ps1` or `.sh` for a real device
6. Generate or recover the release keystore
7. Run `scripts/setup-release-secrets.ps1`
8. Push a `vX.Y.Z` tag when you are ready to publish

Simple setup, safe signing, beautiful chess.
