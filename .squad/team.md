# Squad Team

> ChessPoints

## Coordinator

| Name | Role | Notes |
|------|------|-------|
| Squad | Coordinator | Routes work, enforces handoffs and reviewer gates. |

## Members

| Name | Role | Charter | Status |
|------|------|---------|--------|
| Lead | Lead | `.squad/agents/lead/charter.md` | ✅ Active |
| Android UI Dev | Android UI Dev | `.squad/agents/android-ui/charter.md` | ✅ Active |
| Engine Dev | Engine Dev | `.squad/agents/engine-dev/charter.md` | ✅ Active |
| AI Dev | AI Dev | `.squad/agents/ai-dev/charter.md` | ✅ Active |
| Tester | Tester | `.squad/agents/tester/charter.md` | ✅ Active |
| Scribe | Session Logger | `.squad/agents/scribe/charter.md` | 📋 Silent |
| Ralph | Work Monitor | `.squad/agents/ralph/charter.md` | 🔄 Monitor |
| Rai | RAI Reviewer | `.squad/agents/Rai/charter.md` | 🛡️ RAI |
| Fact Checker | Fact Checker | `.squad/agents/fact-checker/charter.md` | 🔍 Verifier |

## Coding Agent

<!-- copilot-auto-assign: false -->

| Name | Role | Charter | Status |
|------|------|---------|--------|
| @copilot | Coding Agent | — | 🤖 Coding Agent |

### Capabilities

**🟢 Good fit — auto-route when enabled:**
- Bug fixes with clear reproduction steps
- Test coverage (adding missing tests, fixing flaky tests)
- Lint/format fixes and code style cleanup
- Dependency updates and version bumps
- Small isolated features with clear specs
- Boilerplate/scaffolding generation
- Documentation fixes and README updates

**🟡 Needs review — route to @copilot but flag for squad member PR review:**
- Medium features with clear specs and acceptance criteria
- Refactoring with existing test coverage
- API endpoint additions following established patterns
- Migration scripts with well-defined schemas

**🔴 Not suitable — route to squad member instead:**
- Architecture decisions and system design
- Multi-system integration requiring coordination
- Ambiguous requirements needing clarification
- Security-critical changes (auth, encryption, access control)
- Performance-critical paths requiring benchmarking
- Changes requiring cross-team discussion

## Project Context

- **Owner:** repo owner
- **Stack:** Android (Kotlin, likely Jetpack Compose)
- **Description:** A simple, beautiful Android chess game where each player first spends a shared point budget (classic chess piece values) to buy a 16-piece army (any composition, e.g. 2 queens), then alternates placing pieces on the board before play begins. Supports local 2-player and a simple built-in AI opponent.
- **Project:** ChessPoints
- **Created:** 2026-09-16
