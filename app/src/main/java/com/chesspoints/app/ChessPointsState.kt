package com.chesspoints.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.chesspoints.ai.AiDrafter
import com.chesspoints.ai.AiMoveEngine
import com.chesspoints.ai.AiPlacer
import com.chesspoints.engine.Board
import com.chesspoints.engine.ChessGame
import com.chesspoints.engine.ChessGameState
import com.chesspoints.engine.Color
import com.chesspoints.engine.DraftRules
import com.chesspoints.engine.DraftValidationResult
import com.chesspoints.engine.DraftValidator
import com.chesspoints.engine.GameOutcome
import com.chesspoints.engine.GamePlacementResult
import com.chesspoints.engine.GamePosition
import com.chesspoints.engine.GameRules
import com.chesspoints.engine.Move
import com.chesspoints.engine.MoveRequest
import com.chesspoints.engine.Piece
import com.chesspoints.engine.PieceType
import com.chesspoints.engine.PlacementState
import com.chesspoints.engine.PlacementZone
import com.chesspoints.engine.PositionStatus
import com.chesspoints.engine.Square
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun rememberChessPointsState(): ChessPointsState {
    val coroutineScope = rememberCoroutineScope()
    return remember(coroutineScope) { ChessPointsState(appScope = coroutineScope) }
}

enum class GameMode {
    TwoPlayer,
    VersusAi,
}

enum class AppScreen {
    Home,
    Draft,
    Placement,
    Game,
}

enum class ColorPerspective {
    White,
    Black,
}

data class PlacementChoice(
    val pieceType: PieceType,
    val square: Square,
)

sealed interface AiStepResult<out T> {
    data class Success<T>(val value: T) : AiStepResult<T>
    data class Unavailable(val message: String) : AiStepResult<Nothing>
}

interface AiOpponentGateway {
    fun buildDraft(
        draftState: ChessGameState.Drafting,
        color: Color,
    ): AiStepResult<Map<PieceType, Int>>

    fun choosePlacement(
        placementState: PlacementState,
        color: Color,
    ): AiStepResult<PlacementChoice>

    fun chooseMove(position: GamePosition): AiStepResult<MoveRequest>
}

private class EngineBackedAiOpponentGateway(
    private val drafter: AiDrafter = AiDrafter(),
    private val placer: AiPlacer = AiPlacer(),
    private val moveEngine: AiMoveEngine = AiMoveEngine(),
) : AiOpponentGateway {
    private val noPlacementMessage = "The AI could not find a legal placement."
    private val noMoveMessage = "The AI could not find a legal move."

    override fun buildDraft(
        draftState: ChessGameState.Drafting,
        color: Color,
    ): AiStepResult<Map<PieceType, Int>> =
        AiStepResult.Success(
            drafter.draft(
                color = color,
                rules = draftState.draftRules,
            ).counts,
        )

    override fun choosePlacement(
        placementState: PlacementState,
        color: Color,
    ): AiStepResult<PlacementChoice> {
        val choice = placer.nextPlacement(
            state = placementState,
            color = color,
        ) ?: return AiStepResult.Unavailable(noPlacementMessage)

        return AiStepResult.Success(
            PlacementChoice(
                pieceType = choice.pieceType,
                square = choice.square,
            ),
        )
    }

    override fun chooseMove(position: GamePosition): AiStepResult<MoveRequest> {
        val move = moveEngine.chooseMove(position).move ?: return AiStepResult.Unavailable(noMoveMessage)
        return AiStepResult.Success(
            MoveRequest(
                from = move.from,
                to = move.to,
                promotion = move.promotion,
            ),
        )
    }
}

@Stable
class ChessPointsState(
    private val appScope: CoroutineScope,
    private val aiOpponentGateway: AiOpponentGateway = EngineBackedAiOpponentGateway(),
) {
    var gameMode by mutableStateOf(GameMode.TwoPlayer)
        private set

    var currentScreen by mutableStateOf(AppScreen.Home)
        private set

    var bannerMessage by mutableStateOf<String?>(null)
        private set

    var gameState by mutableStateOf<ChessGameState>(ChessGame().getGameState())
        private set

    var draftPieceCounts by mutableStateOf(defaultDraftCounts())
        private set

    var currentDraftColor by mutableStateOf(Color.WHITE)
        private set

    var selectedPlacementPieceType by mutableStateOf<PieceType?>(null)
        private set

    var selectedMoveSquare by mutableStateOf<Square?>(null)
        private set

    var selectedLegalMoves by mutableStateOf<List<Move>>(emptyList())
        private set

    var pendingPromotionMoves by mutableStateOf<List<Move>>(emptyList())
        private set

    private var awaitingAiDraft by mutableStateOf(false)
    private var awaitingAiPlacement by mutableStateOf(false)
    private var awaitingAiMove by mutableStateOf(false)

    private var game = ChessGame()
    private var gameSessionId = 0

    val draftRules: DraftRules
        get() = when (val state = gameState) {
            is ChessGameState.Drafting -> state.draftRules
            else -> GameRules().draftRules
        }

    val draftValidation: DraftValidationResult
        get() = DraftValidator.validate(currentDraftColor, draftPieceCounts, draftRules)

    val isDraftEditable: Boolean
        get() = !awaitingAiDraft && !(gameMode == GameMode.VersusAi && currentDraftColor == Color.BLACK)

    val draftHelperMessage: String
        get() = when {
            awaitingAiDraft -> "Waiting for the AI roster."
            gameMode == GameMode.VersusAi && currentDraftColor == Color.BLACK ->
                "The AI is drafting its roster."

            else -> "Build a 16-piece army with one mandatory king."
        }

    val placementSideToPlace: Color
        get() = (gameState as? ChessGameState.Placing)?.placementState?.sideToPlace ?: Color.WHITE

    val placementSideToPlaceLabel: String
        get() = colorLabel(placementSideToPlace)

    val placementZone: PlacementZone?
        get() = (gameState as? ChessGameState.Placing)?.placementState?.rules?.zoneFor(placementSideToPlace)

    val remainingPlacementPieces: Map<PieceType, Int>
        get() = (gameState as? ChessGameState.Placing)?.placementState?.remainingPieces(placementSideToPlace).orEmpty()

    val isPlacementInteractive: Boolean
        get() = !awaitingAiPlacement && !isAiColor(placementSideToPlace)

    val placementHelperMessage: String
        get() = when {
            awaitingAiPlacement -> "Waiting for the AI placement move."
            isAiColor(placementSideToPlace) -> "The AI is choosing a placement."
            selectedPlacementPieceType == null -> "Pick a piece from the tray, then tap a highlighted square."
            else -> "Tap a square inside ${colorLabel(placementSideToPlace)}'s placement zone."
        }

    val playSideToMove: Color
        get() = when (val state = gameState) {
            is ChessGameState.Playing -> state.position.sideToMove
            is ChessGameState.GameOver -> state.position.sideToMove
            else -> Color.WHITE
        }

    val playSideToMoveLabel: String
        get() = colorLabel(playSideToMove)

    val isGameInteractive: Boolean
        get() = gameState is ChessGameState.Playing && !awaitingAiMove && !isAiColor(playSideToMove)

    val gameStatusMessage: String
        get() = when (val state = gameState) {
            is ChessGameState.Playing -> describePlayingStatus(state.status)
            is ChessGameState.GameOver -> describeGameOver(state.outcome)
            else -> ""
        }

    val currentBoard: Board
        get() = when (val state = gameState) {
            is ChessGameState.Placing -> state.placementState.board
            is ChessGameState.Playing -> state.position.board
            is ChessGameState.GameOver -> state.position.board
            is ChessGameState.Drafting -> Board.empty()
        }

    val currentDraftColorLabel: String
        get() = colorLabel(currentDraftColor)

    val screenTitle: String
        get() = when (currentScreen) {
            AppScreen.Home -> "Choose a mode"
            AppScreen.Draft -> "Draft your armies"
            AppScreen.Placement -> "Place your pieces"
            AppScreen.Game -> "Play the match"
        }

    fun selectMode(mode: GameMode) {
        gameMode = mode
    }

    fun startGame() {
        gameSessionId += 1
        game = ChessGame()
        gameState = game.getGameState()
        currentDraftColor = Color.WHITE
        draftPieceCounts = defaultDraftCounts()
        selectedPlacementPieceType = null
        selectedMoveSquare = null
        selectedLegalMoves = emptyList()
        pendingPromotionMoves = emptyList()
        awaitingAiDraft = false
        awaitingAiPlacement = false
        awaitingAiMove = false
        bannerMessage = null
        currentScreen = AppScreen.Draft
    }

    fun incrementDraftPiece(pieceType: PieceType) {
        if (!isDraftEditable || pieceType == PieceType.KING) return
        draftPieceCounts = draftPieceCounts.toMutableMap().apply {
            this[pieceType] = getValue(pieceType) + 1
        }.toMap()
    }

    fun decrementDraftPiece(pieceType: PieceType) {
        if (!isDraftEditable || pieceType == PieceType.KING) return
        draftPieceCounts = draftPieceCounts.toMutableMap().apply {
            this[pieceType] = maxOf(0, getValue(pieceType) - 1)
        }.toMap()
    }

    fun confirmDraft() {
        val submission = game.submitDraft(currentDraftColor, draftPieceCounts)
        when (submission) {
            is com.chesspoints.engine.DraftSubmissionResult.Accepted -> {
                refreshGameState()
                if (gameMode == GameMode.VersusAi && currentDraftColor == Color.WHITE) {
                    currentDraftColor = Color.BLACK
                    draftPieceCounts = defaultDraftCounts()
                    requestAiDraft()
                } else {
                    advanceDraftFlow()
                }
            }

            is com.chesspoints.engine.DraftSubmissionResult.Rejected -> bannerMessage = submission.reason.message
        }
    }

    fun selectPlacementPiece(pieceType: PieceType) {
        if (!isPlacementInteractive || remainingPlacementPieces.getOrDefault(pieceType, 0) <= 0) return
        selectedPlacementPieceType = if (selectedPlacementPieceType == pieceType) null else pieceType
    }

    fun placeSelectedPieceAt(square: Square) {
        val pieceType = selectedPlacementPieceType
        val placingState = gameState as? ChessGameState.Placing ?: return
        if (!isPlacementInteractive) return
        if (pieceType == null) {
            bannerMessage = "Select a piece first."
            return
        }

        when (val result = game.placePiece(placingState.placementState.sideToPlace, pieceType, square)) {
            is GamePlacementResult.Accepted -> {
                refreshGameState()
                val remaining = (gameState as? ChessGameState.Placing)?.placementState?.remainingCount(
                    placingState.placementState.sideToPlace,
                    pieceType,
                ) ?: 0
                if (remaining <= 0) {
                    selectedPlacementPieceType = null
                }
                if (gameState is ChessGameState.Playing) {
                    currentScreen = AppScreen.Game
                    requestAiMoveIfNeeded()
                } else {
                    requestAiPlacementIfNeeded()
                }
            }

            is GamePlacementResult.Rejected -> bannerMessage = result.reason.message
        }
    }

    fun handleGameSquareTap(square: Square) {
        val playingState = gameState as? ChessGameState.Playing ?: return
        if (!isGameInteractive) return

        val board = playingState.position.board
        val selectedFrom = selectedMoveSquare
        if (selectedFrom == null) {
            val piece = board[square]
            if (piece?.color == playingState.position.sideToMove) {
                selectMoveSquare(square)
            }
            return
        }

        if (square == selectedFrom) {
            clearMoveSelection()
            return
        }

        val candidateMoves = selectedLegalMoves.filter { it.to == square }
        when {
            candidateMoves.isEmpty() -> {
                val piece = board[square]
                if (piece?.color == playingState.position.sideToMove) {
                    selectMoveSquare(square)
                } else {
                    bannerMessage = "That destination is not legal for the selected piece."
                }
            }

            candidateMoves.size == 1 && candidateMoves.first().promotion == null -> {
                submitMove(MoveRequest(selectedFrom, square))
            }

            else -> pendingPromotionMoves = candidateMoves
        }
    }

    fun completePromotion(pieceType: PieceType) {
        val from = selectedMoveSquare ?: return
        val target = pendingPromotionMoves.firstOrNull()?.to ?: return
        pendingPromotionMoves = emptyList()
        submitMove(MoveRequest(from, target, pieceType))
    }

    fun dismissPromotionPrompt() {
        pendingPromotionMoves = emptyList()
    }

    fun clearBanner() {
        bannerMessage = null
    }

    fun capturedPieces(perspective: ColorPerspective): List<Piece> {
        val stateRoster = when (perspective) {
            ColorPerspective.White -> game.getRoster(Color.BLACK)
            ColorPerspective.Black -> game.getRoster(Color.WHITE)
        } ?: return emptyList()
        val boardPieces = currentBoard.pieces(
            when (perspective) {
                ColorPerspective.White -> Color.BLACK
                ColorPerspective.Black -> Color.WHITE
            },
        ).groupingBy { it.second.type }.eachCount()

        return PieceType.entries.flatMap { pieceType ->
            val capturedCount = stateRoster.countOf(pieceType) - boardPieces.getOrDefault(pieceType, 0)
            List(maxOf(0, capturedCount)) {
                Piece(
                    type = pieceType,
                    color = when (perspective) {
                        ColorPerspective.White -> Color.BLACK
                        ColorPerspective.Black -> Color.WHITE
                    },
                )
            }
        }
    }

    private fun submitMove(request: MoveRequest) {
        when (val result = game.makeMove(request)) {
            is com.chesspoints.engine.GameMoveResult.Accepted -> {
                refreshGameState()
                clearMoveSelection()
                requestAiMoveIfNeeded()
            }

            is com.chesspoints.engine.GameMoveResult.Rejected -> bannerMessage = result.reason.message
        }
    }

    private fun selectMoveSquare(square: Square) {
        selectedMoveSquare = square
        selectedLegalMoves = game.getLegalMoves(square)
    }

    private fun clearMoveSelection() {
        selectedMoveSquare = null
        selectedLegalMoves = emptyList()
    }

    private fun advanceDraftFlow() {
        when (val refreshed = gameState) {
            is ChessGameState.Drafting -> {
                currentDraftColor = if (refreshed.submittedRosters.containsKey(Color.WHITE)) Color.BLACK else Color.WHITE
                draftPieceCounts = defaultDraftCounts()
            }

            is ChessGameState.Placing -> {
                currentScreen = AppScreen.Placement
                selectedPlacementPieceType = null
                requestAiPlacementIfNeeded()
            }

            else -> Unit
        }
    }

    private fun requestAiDraft() {
        val draftState = gameState as? ChessGameState.Drafting ?: return
        val sessionId = gameSessionId
        awaitingAiDraft = true
        appScope.launch {
            val result = withContext(Dispatchers.Default) {
                aiOpponentGateway.buildDraft(draftState, Color.BLACK)
            }
            if (sessionId != gameSessionId) return@launch
            when (result) {
                is AiStepResult.Success -> {
                    awaitingAiDraft = false
                    val submission = game.submitDraft(Color.BLACK, result.value)
                    if (submission is com.chesspoints.engine.DraftSubmissionResult.Rejected) {
                        bannerMessage = submission.reason.message
                    }
                    refreshGameState()
                    advanceDraftFlow()
                }

                is AiStepResult.Unavailable -> {
                    awaitingAiDraft = false
                    bannerMessage = result.message
                }
            }
        }
    }

    private fun requestAiPlacementIfNeeded() {
        val placementState = (gameState as? ChessGameState.Placing)?.placementState ?: return
        if (!isAiColor(placementState.sideToPlace)) return
        val sessionId = gameSessionId
        awaitingAiPlacement = true
        selectedPlacementPieceType = null
        appScope.launch {
            val result = withContext(Dispatchers.Default) {
                aiOpponentGateway.choosePlacement(placementState, placementState.sideToPlace)
            }
            if (sessionId != gameSessionId) return@launch
            when (result) {
                is AiStepResult.Success -> {
                    awaitingAiPlacement = false
                    when (val placementResult = game.placePiece(placementState.sideToPlace, result.value.pieceType, result.value.square)) {
                        is GamePlacementResult.Accepted -> {
                            refreshGameState()
                            if (gameState is ChessGameState.Playing) {
                                currentScreen = AppScreen.Game
                                requestAiMoveIfNeeded()
                            }
                        }

                        is GamePlacementResult.Rejected -> bannerMessage = placementResult.reason.message
                    }
                }

                is AiStepResult.Unavailable -> {
                    awaitingAiPlacement = false
                    bannerMessage = result.message
                }
            }
        }
    }

    private fun requestAiMoveIfNeeded() {
        val playingState = gameState as? ChessGameState.Playing ?: return
        if (!isAiColor(playingState.position.sideToMove)) return
        val sessionId = gameSessionId
        awaitingAiMove = true
        clearMoveSelection()
        appScope.launch {
            val result = withContext(Dispatchers.Default) {
                aiOpponentGateway.chooseMove(playingState.position)
            }
            if (sessionId != gameSessionId) return@launch
            when (result) {
                is AiStepResult.Success -> {
                    awaitingAiMove = false
                    submitMove(result.value)
                }

                is AiStepResult.Unavailable -> {
                    awaitingAiMove = false
                    bannerMessage = result.message
                }
            }
        }
    }

    private fun refreshGameState() {
        gameState = game.getGameState()
    }

    private fun isAiColor(color: Color): Boolean = gameMode == GameMode.VersusAi && color == Color.BLACK

    private fun colorLabel(color: Color): String =
        when {
            gameMode == GameMode.VersusAi && color == Color.WHITE -> "You"
            gameMode == GameMode.VersusAi && color == Color.BLACK -> "AI"
            color == Color.WHITE -> "White"
            else -> "Black"
        }

    private fun describePlayingStatus(status: PositionStatus): String =
        when (status) {
            PositionStatus.Active -> "${colorLabel(playSideToMove)} to move."
            is PositionStatus.Check -> "${colorLabel(status.checkedColor)} is in check."
            is PositionStatus.Checkmate -> "${colorLabel(status.winner)} delivered checkmate."
            is PositionStatus.Draw -> "Draw: ${status.reason.name.lowercase().replace('_', ' ')}."
        }

    private fun describeGameOver(outcome: GameOutcome): String =
        when (outcome) {
            is GameOutcome.Checkmate -> "${colorLabel(outcome.winner)} wins by checkmate."
            is GameOutcome.Draw -> "Draw: ${outcome.reason.name.lowercase().replace('_', ' ')}."
        }

    private fun defaultDraftCounts(): Map<PieceType, Int> =
        PieceType.entries.associateWith { if (it == PieceType.KING) 1 else 0 }
}
