### 2026-09-16: Android CI/CD and local device-run setup
**By:** Lead
**What:** Added GitHub Actions workflows for CI (`.github/workflows/ci.yml`) and tagged releases (`.github/workflows/release.yml`), plus `scripts/run-on-device.ps1` and `scripts/run-on-device.sh` for local debug install/launch flows. The release pipeline expects four GitHub secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`.
**Why:** The team now has a reproducible Android validation path for pushes, pull requests, and version tags, along with a simple local device deployment script. Release signing is configured to use CI-provided secrets when present, while local `assembleRelease` gracefully falls back without requiring any committed keystore material.

### 2026-09-16: Removed broken setup-android action from GitHub workflows
**By:** Lead
**What:** Removed `android-actions/setup-android@v3` from both `.github/workflows/ci.yml` and `.github/workflows/release.yml`, and kept SDK package installation by accepting licenses non-interactively and invoking the runner's existing `sdkmanager` from the preinstalled Android SDK under `ANDROID_HOME` (falling back to the hosted-runner default SDK path if needed).
**Why:** GitHub-hosted `ubuntu-latest` runners already provide an Android SDK, while `setup-android@v3` currently fails by trying to install the legacy `tools` package that Google removed from the SDK repository. Resolving `sdkmanager` from the preinstalled runner SDK avoids that broken dependency and is more robust than assuming the tool is already on `PATH`.
