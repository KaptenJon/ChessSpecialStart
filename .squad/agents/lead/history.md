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