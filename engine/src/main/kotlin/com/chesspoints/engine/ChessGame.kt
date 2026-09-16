package com.chesspoints.engine

data class GameRules(
    val draftRules: DraftRules = DraftRules(),
    val placementRules: PlacementRules = PlacementRules(),
)

sealed interface GameOutcome {
    data class Checkmate(val winner: Color) : GameOutcome
    data class Draw(val reason: DrawReason) : GameOutcome
}

sealed interface ChessGameState {
    data class Drafting(
        val draftRules: DraftRules,
        val submittedRosters: Map<Color, Roster>,
    ) : ChessGameState

    data class Placing(val placementState: PlacementState) : ChessGameState

    data class Playing(
        val position: GamePosition,
        val status: PositionStatus,
    ) : ChessGameState

    data class GameOver(
        val position: GamePosition,
        val outcome: GameOutcome,
    ) : ChessGameState
}

sealed interface DraftSubmissionError {
    val message: String

    data class DuplicateSubmission(val color: Color) : DraftSubmissionError {
        override val message: String = "$color has already submitted a draft"
    }

    data class WrongPhase(val phase: String) : DraftSubmissionError {
        override val message: String = "Cannot submit a draft during $phase"
    }

    data class InvalidDraft(val errors: List<DraftValidationError>) : DraftSubmissionError {
        override val message: String = errors.joinToString(separator = "; ") { it.message }
    }
}

sealed interface DraftSubmissionResult {
    data class Accepted(
        val roster: Roster,
        val gameState: ChessGameState,
    ) : DraftSubmissionResult

    data class Rejected(val reason: DraftSubmissionError) : DraftSubmissionResult
}

sealed interface GamePlacementError {
    val message: String

    data class WrongPhase(val phase: String) : GamePlacementError {
        override val message: String = "Cannot place pieces during $phase"
    }

    data class InvalidPlacement(val error: PlacementError) : GamePlacementError {
        override val message: String = error.message
    }
}

sealed interface GamePlacementResult {
    data class Accepted(
        val gameState: ChessGameState,
    ) : GamePlacementResult

    data class Rejected(val reason: GamePlacementError) : GamePlacementResult
}

sealed interface GameMoveError {
    val message: String

    data class WrongPhase(val phase: String) : GameMoveError {
        override val message: String = "Cannot make moves during $phase"
    }

    data class IllegalMove(val rejection: MoveRejection) : GameMoveError {
        override val message: String = rejection.message
    }
}

sealed interface GameMoveResult {
    data class Accepted(
        val move: Move,
        val gameState: ChessGameState,
    ) : GameMoveResult

    data class Rejected(val reason: GameMoveError) : GameMoveResult
}

class ChessGame(
    private val rules: GameRules = GameRules(),
) {
    private val drafts = mutableMapOf<Color, Roster>()
    private val placementValidator = PlacementValidator(rules.placementRules)

    private var placementState: PlacementState? = null
    private var position: GamePosition? = null
    private var outcome: GameOutcome? = null

    fun submitDraft(
        color: Color,
        pieceCounts: Map<PieceType, Int>,
    ): DraftSubmissionResult {
        if (placementState != null || position != null || outcome != null) {
            return DraftSubmissionResult.Rejected(DraftSubmissionError.WrongPhase(currentPhaseName()))
        }
        if (drafts.containsKey(color)) {
            return DraftSubmissionResult.Rejected(DraftSubmissionError.DuplicateSubmission(color))
        }

        return when (val validation = DraftValidator.validate(color, pieceCounts, rules.draftRules)) {
            is DraftValidationResult.Invalid -> {
                DraftSubmissionResult.Rejected(DraftSubmissionError.InvalidDraft(validation.errors))
            }

            is DraftValidationResult.Valid -> {
                drafts[color] = validation.roster
                if (drafts.keys.containsAll(Color.entries)) {
                    placementState = placementValidator.start(
                        whiteRoster = drafts.getValue(Color.WHITE),
                        blackRoster = drafts.getValue(Color.BLACK),
                    )
                }
                DraftSubmissionResult.Accepted(validation.roster, getGameState())
            }
        }
    }

    fun placePiece(
        color: Color,
        pieceType: PieceType,
        square: Square,
    ): GamePlacementResult {
        val currentPlacementState = placementState
            ?: return GamePlacementResult.Rejected(GamePlacementError.WrongPhase(currentPhaseName()))
        return when (val result = placementValidator.place(currentPlacementState, color, pieceType, square)) {
            is PlacementResult.Failure -> {
                GamePlacementResult.Rejected(GamePlacementError.InvalidPlacement(result.error))
            }

            is PlacementResult.Success -> {
                placementState = result.state
                if (result.readyToPlay) {
                    enterPlay(result.state)
                }
                GamePlacementResult.Accepted(getGameState())
            }
        }
    }

    fun getLegalMoves(from: Square? = null): List<Move> =
        position?.takeIf { outcome == null }?.let { MoveEngine.legalMoves(it, from) } ?: emptyList()

    fun makeMove(request: MoveRequest): GameMoveResult {
        val currentPosition = position ?: return GameMoveResult.Rejected(GameMoveError.WrongPhase(currentPhaseName()))
        if (outcome != null) {
            return GameMoveResult.Rejected(GameMoveError.WrongPhase(currentPhaseName()))
        }

        return when (val result = MoveEngine.apply(currentPosition, request)) {
            is MoveApplicationResult.Illegal -> GameMoveResult.Rejected(GameMoveError.IllegalMove(result.reason))
            is MoveApplicationResult.Success -> {
                position = result.position
                updateOutcome(result.status)
                GameMoveResult.Accepted(result.move, getGameState())
            }
        }
    }

    fun getBoard(): Board =
        position?.board ?: placementState?.board ?: Board.empty()

    fun getGameState(): ChessGameState {
        val currentPosition = position
        val currentOutcome = outcome
        return when {
            currentPosition != null && currentOutcome != null -> ChessGameState.GameOver(currentPosition, currentOutcome)
            currentPosition != null -> ChessGameState.Playing(currentPosition, MoveEngine.evaluate(currentPosition))
            placementState != null -> ChessGameState.Placing(placementState!!)
            else -> ChessGameState.Drafting(rules.draftRules, drafts.toMap())
        }
    }

    fun getRoster(color: Color): Roster? = drafts[color]

    private fun enterPlay(readyPlacement: PlacementState) {
        placementState = null
        position = GamePosition(
            board = readyPlacement.board,
            sideToMove = readyPlacement.rules.firstMoverInPlay,
            castlingRights = MoveEngine.standardCastlingRights(readyPlacement.board),
        )
        updateOutcome(MoveEngine.evaluate(position!!))
    }

    private fun updateOutcome(status: PositionStatus) {
        outcome = when (status) {
            is PositionStatus.Checkmate -> GameOutcome.Checkmate(status.winner)
            is PositionStatus.Draw -> GameOutcome.Draw(status.reason)
            PositionStatus.Active,
            is PositionStatus.Check,
            -> null
        }
    }

    private fun currentPhaseName(): String =
        when {
            outcome != null -> "game over"
            position != null -> "play phase"
            placementState != null -> "placement phase"
            else -> "draft phase"
        }
}
