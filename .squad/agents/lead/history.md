# Lead — History

## Project Context (seeded)

- **Project:** ChessPoints — an Android chess game.
- **Key twist:** Before playing, each player spends a shared point budget (classic chess piece values) to "buy" their army, must end up with the standard total of 16 pieces (including 1 mandatory king), but composition is free-form (e.g., 2 queens, fewer pawns). Players then alternate placing bought pieces on the board themselves, taking turns, before the game starts.
- **Modes:** Local 2-player, and vs a simple built-in AI opponent.
- **Design goal:** Simple and beautiful UI.
- **Stack:** Android (Kotlin), likely Jetpack Compose — to be confirmed with Android UI Dev.
- **Requested by:** repo owner.


📌 Team update (2026-09-16T13:35:00+02:00): First full development pass landed across scaffold, engine, AI, UI, and tests; local end-to-end Gradle verification remains incomplete on this machine because Gradle wrapper execution / Android SDK Build-Tools 34 were unavailable.

📌 Team update (2026-09-16T13:55:00+02:00): Local Android bring-up is now complete: JDK 17, Android SDK tooling, and the real Gradle wrapper were verified; README/.gitignore were updated for local builds; and `:engine:test :ai:test :app:assembleDebug` now passes with `app/build/outputs/apk/debug/app-debug.apk` produced.

📌 Team update (2026-09-16T22:10:00+02:00): GitHub Actions CI and tagged-release automation are now in place, with `scripts/run-on-device.ps1` / `scripts/run-on-device.sh` added for local install-and-launch flows; release signing is driven by the four GitHub Actions secrets instead of any committed keystore material.

📌 Team update (2026-09-16T22:10:00+02:00): The initial CI failure was fixed by removing `android-actions/setup-android@v3`, CI is green, and `KaptenJon/ChessSpecialStart` now has a successful `v0.1.0` GitHub Release with a signed APK asset. Operationally, losing the current release keystore would force a new signing key and break upgrade continuity for installs signed with the original key.

📌 Team update (2026-09-16T22:20:00+02:00): Added `GETSTARTED.md`, updated `README.md` cross-links, and introduced `scripts/setup-release-secrets.ps1` so new maintainers can generate or reuse a release keystore and set the four required GitHub Actions secrets without exposing secret values; validation stayed in `-DryRun` mode against a throwaway keystore only.
📌 Team update (2026-09-16T22:28:54.297+02:00): Fixed `scripts/setup-release-secrets.ps1` for Windows PowerShell by adding a Windows argument-escaping fallback when `ProcessStartInfo.ArgumentList` is unavailable, while retaining modern `ArgumentList` behavior; Windows PowerShell and PowerShell 7 parsing both passed, with no established automated tests found.
