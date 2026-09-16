# ChessPoints

**ChessPoints** is a simple, beautiful Android chess game that reimagines the opening of chess as a strategy game of buying and building your army before the first move is played. Instead of starting with the standard setup, both players receive the same credit budget, purchase a full 16-piece army using classic chess point values, and then take turns placing their pieces on the board. The result is a familiar game of chess with a fresh layer of planning, creativity, and mind games before play begins.

## Core Concept

ChessPoints keeps the core movement and win conditions of normal chess, but adds a draft-and-deploy phase before the game starts.

In standard chess, every player begins with the same fixed army in the same arrangement. In ChessPoints, each player still ends up with a full 16-piece army, but they choose how to spend their points. This means a player can trade away some of the usual balance of pawns and minor pieces in order to afford a different mix, such as two queens, extra rooks, or a heavier knight-focused setup.

After buying their army, players do not begin from a prebuilt layout. Instead, they alternate placing their purchased pieces on their own side of the board during a setup phase. This turns the opening of the match into a tactical contest of army design and board formation before traditional chess play begins.

## Rules

### Shared Budget and Piece Values

- Both players receive the **same credit budget**.
- Piece costs use **classic chess point values**:
  - **Pawn = 1**
  - **Knight = 3**
  - **Bishop = 3**
  - **Rook = 5**
  - **Queen = 9**
  - **King = free, but mandatory**
- The default budget is designed to match the value of a normal chess army:
  - **39 total points**, plus **1 mandatory king**

### Army Building Rules

- Each player must buy **exactly 16 total pieces**, matching the total number of pieces in standard chess.
- Each player must include **exactly 1 king**.
- The remaining 15 pieces can be any legal mix the player can afford within the shared budget.
- Players are **not required** to match the normal composition of 8 pawns, 2 rooks, 2 knights, 2 bishops, and 1 queen.
- Example: a player could choose **2 queens** and reduce other pieces to stay within the budget and still reach **16 total pieces**.

### Setup / Placement Phase

- After both players finish buying their armies, the game enters a **setup phase**.
- Players **take turns** placing pieces on the board.
- Each player places pieces only on **their own side of the board**.
- Placement continues until both full 16-piece armies have been deployed.
- Once placement is complete, the match proceeds as a normal game of chess using the chosen starting layout.

## Game Modes

### Local 2-Player

Two players share the same Android device and play against each other. They each complete the buy phase, alternate through setup, and then play the full game locally.

### Player vs Simple AI

The player can face a built-in AI opponent. The AI should be intentionally simple for v1, but capable of:

- buying a valid army within the rules,
- placing pieces during setup,
- and playing complete chess turns afterward.

## Platform / Tech

- **Platform:** Android
- **Product shape:** Mobile app
- **Design goal:** A **simple and beautiful** user experience
- **UX priority:** The game should feel approachable, readable, and elegant, with the custom draft-and-placement flow explained clearly to new players

## High-Level Features

- **Draft / shop screen** for buying pieces with a shared point budget
- **Placement screen** for alternating piece placement before the match begins
- **Game screen** for playing the actual chess match
- **Simple AI opponent** for solo play
- **Local 2-player mode** for pass-and-play matches on one device

## Out of Scope for v1 / Future Ideas

The following ideas are explicitly **not part of v1**, but could be explored later:

- **Online multiplayer**
- **Ranked or competitive play**
- **Custom point values or alternative budgets**
- **Additional AI difficulty levels**
- **Saved loadouts or army presets**
