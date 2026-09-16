# Engine Dev — History

## Project Context (seeded)

- **Project:** ChessPoints — Android chess game.
- **My focus:** Pure Kotlin rules engine: custom-composition board model, legal move generation/validation, check/mate detection, and enforcing the draft economy (point budget + fixed 16-piece count incl. exactly one king) and alternating placement legality.
- **Point values (classic):** Pawn 1, Knight 3, Bishop 3, Rook 5, Queen 9, King free/mandatory.
- **Requested by:** repo owner.


📌 Team update (2026-09-16T13:35:00+02:00): Android UI and AI now integrate against the `ChessGame` / engine surface, and Tester expanded edge-case coverage without finding engine bugs in exercised cases.