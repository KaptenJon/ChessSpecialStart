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
import com.chesspoints.app.i18n.UiText
import com.chesspoints.app.i18n.displayText
import com.chesspoints.engine.Board
import com.chesspoints.engine.ChessGame
import com.chesspoints.engine.ChessGameState
import com.chesspoints.engine.Color
import com.chesspoints.engine.DraftRules
import com.chesspoints.engine.DraftValidationResult
import com.chesspoints.engine.DraftValidator
import com.chesspoints.engine.DraftPurchaseValidation
import com.chesspoints.engine.GameOutcome
import com.chesspoints.engine.GamePlacementResult
import com.chesspoints.engine.GamePosition
import com.chesspoints.engine.GameRules
import com.chesspoints.engine.Move
import com.chesspoints.engine.MoveRequest
import com.chesspoints.engine.Piece
import com.chesspoints.engine.PieceType
import com.chesspoints.engine.PlacementState
import com.chesspoints.engine.PlacementRules
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
    data class Unavailable(val message: UiText) : AiStepResult<Nothing>
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

    fun chooseSetupPlacement(
        color: Color,
        remainingPieces: Map<PieceType, Int>,
        board: Board,
        rules: PlacementRules,
        purchasedCounts: Map<PieceType, Int>,
        draftRules: DraftRules,
    ): AiStepResult<PlacementChoice>

    fun chooseMove(position: GamePosition): AiStepResult<MoveRequest>
}

private class EngineBackedAiOpponentGateway(
    private val drafter: AiDrafter = AiDrafter(),
    private val placer: AiPlacer = AiPlacer(),
    private val moveEngine: AiMoveEngine = AiMoveEngine(),
) : AiOpponentGateway {
    private val noPlacementMessage = UiText.Res(R.string.ai_no_placement)
    private val noMoveMessage = UiText.Res(R.string.ai_no_move)

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

    override fun chooseSetupPlacement(
        color: Color,
        remainingPieces: Map<PieceType, Int>,
        board: Board,
        rules: PlacementRules,
        purchasedCounts: Map<PieceType, Int>,
        draftRules: DraftRules,
    ): AiStepResult<PlacementChoice> {
        val choice = placer.nextSetupPlacement(
            color = color,
            remainingPieces = remainingPieces,
            board = board,
            rules = rules,
            purchasedCounts = purchasedCounts,
            draftRules = draftRules,
        ) ?: return AiStepResult.Unavailable(noPlacementMessage)
        return AiStepResult.Success(PlacementChoice(choice.pieceType, choice.square))
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

    var bannerMessage by mutableStateOf<UiText?>(null)
        private set

    var gameState by mutableStateOf<ChessGameState>(ChessGame().getGameState())
        private set

    var draftPieceCounts by mutableStateOf(defaultDraftCounts())
        private set

    var draftPlacementBoard by mutableStateOf(Board.empty())
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
    private var aiSetupCounts: Map<PieceType, Int>? = null

    private var game = ChessGame()
    private var gameSessionId = 0
    private val draftPlacements = mutableMapOf<Color, MutableList<PlacementChoice>>()

    val draftRules: DraftRules
        get() = when (val state = gameState) {
            is ChessGameState.Drafting -> state.draftRules
            else -> GameRules().draftRules
        }

    val draftValidation: DraftValidationResult
        get() = DraftValidator.validate(currentDraftColor, game.getSetupCounts(currentDraftColor), draftRules)

    val isDraftEditable: Boolean
        get() = !awaitingAiDraft && !(gameMode == GameMode.VersusAi && currentDraftColor == Color.BLACK)

    val draftHelperMessage: UiText
        get() = when {
            awaitingAiDraft -> UiText.Res(R.string.helper_waiting_ai_army)
            awaitingAiPlacement -> UiText.Res(R.string.helper_waiting_ai_placement)
            gameMode == GameMode.VersusAi && currentDraftColor == Color.BLACK ->
                UiText.Res(R.string.helper_ai_preparing_army)

            else -> UiText.of(R.string.helper_build_army, draftRules.requiredPieceCount)
        }

    val isDrafting: Boolean
        get() = gameState is ChessGameState.Drafting

    val placementSideToPlace: Color
        get() = (gameState as? ChessGameState.Placing)?.placementState?.sideToPlace
            ?: currentDraftColor

    val placementSideToPlaceLabel: UiText
        get() = colorLabel(placementSideToPlace)

    val placementZone: PlacementZone?
        get() = (gameState as? ChessGameState.Placing)?.placementState?.rules?.zoneFor(placementSideToPlace)
            ?: if (isDrafting) GameRules().placementRules.zoneFor(currentDraftColor) else null

    val remainingPlacementPieces: Map<PieceType, Int>
        get() = (gameState as? ChessGameState.Placing)?.placementState?.remainingPieces(placementSideToPlace).orEmpty()

    val isPlacementInteractive: Boolean
        get() = !awaitingAiPlacement && !isAiColor(placementSideToPlace)

    val placementHelperMessage: UiText
        get() = when {
            awaitingAiPlacement -> UiText.Res(R.string.helper_waiting_ai_placement)
            isAiColor(placementSideToPlace) -> UiText.Res(R.string.helper_ai_choosing_placement)
            selectedPlacementPieceType == null -> UiText.Res(R.string.helper_pick_piece)
            else -> UiText.of(R.string.helper_tap_zone, colorLabel(placementSideToPlace))
        }

    val playSideToMove: Color
        get() = when (val state = gameState) {
            is ChessGameState.Playing -> state.position.sideToMove
            is ChessGameState.GameOver -> state.position.sideToMove
            else -> Color.WHITE
        }

    val playSideToMoveLabel: UiText
        get() = colorLabel(playSideToMove)

    val isGameInteractive: Boolean
        get() = gameState is ChessGameState.Playing && !awaitingAiMove && !isAiColor(playSideToMove)

    val gameStatusMessage: UiText
        get() = when (val state = gameState) {
            is ChessGameState.Playing -> describePlayingStatus(state.status)
            is ChessGameState.GameOver -> describeGameOver(state.outcome)
            else -> UiText.Raw("")
        }

    val currentBoard: Board
        get() = when (val state = gameState) {
            is ChessGameState.Placing -> state.placementState.board
            is ChessGameState.Playing -> state.position.board
            is ChessGameState.GameOver -> state.position.board
            is ChessGameState.Drafting -> game.getSetupBoard()
        }

    val currentDraftColorLabel: UiText
        get() = colorLabel(currentDraftColor)

    val screenTitle: UiText
        get() = when (currentScreen) {
            AppScreen.Home -> UiText.Res(R.string.title_home)
            AppScreen.Placement ->
                if (isDrafting) {
                    UiText.Res(R.string.title_build_and_place)
                } else {
                    UiText.Res(R.string.title_place_pieces)
                }

            AppScreen.Game -> UiText.Res(R.string.title_play_match)
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
        draftPlacementBoard = Board.empty()
        draftPlacements.clear()
        selectedPlacementPieceType = null
        selectedMoveSquare = null
        selectedLegalMoves = emptyList()
        pendingPromotionMoves = emptyList()
        awaitingAiDraft = false
        awaitingAiPlacement = false
        awaitingAiMove = false
        aiSetupCounts = null
        bannerMessage = null
        currentScreen = AppScreen.Placement
        requestAiSetupPlacementIfNeeded()
    }

    /**
     * Leaves the current match and returns to the home screen. The session id is bumped so any
     * in-flight AI step is ignored when it completes.
     */
    fun navigateHome() {
        gameSessionId += 1
        game = ChessGame()
        gameState = game.getGameState()
        currentDraftColor = Color.WHITE
        draftPieceCounts = defaultDraftCounts()
        draftPlacementBoard = Board.empty()
        draftPlacements.clear()
        selectedPlacementPieceType = null
        selectedMoveSquare = null
        selectedLegalMoves = emptyList()
        pendingPromotionMoves = emptyList()
        awaitingAiDraft = false
        awaitingAiPlacement = false
        awaitingAiMove = false
        aiSetupCounts = null
        bannerMessage = null
        currentScreen = AppScreen.Home
    }

    /** True while a match (setup or play) is running and would be lost by leaving. */
    val hasMatchInProgress: Boolean
        get() = currentScreen != AppScreen.Home &&
            (currentBoard.pieces(Color.WHITE).isNotEmpty() || currentBoard.pieces(Color.BLACK).isNotEmpty())

    /** Square of the king that is currently in check or mated, if any. */
    val checkedKingSquare: Square?
        get() {
            val color = checkedKingColor ?: return null
            return currentBoard.pieces(color).firstOrNull { it.second.type == PieceType.KING }?.first
        }

    /** Colour whose king is in check or checkmated, or null when nobody is in check. */
    val checkedKingColor: Color?
        get() = when (val state = gameState) {
            is ChessGameState.Playing -> when (val status = state.status) {
                is PositionStatus.Check -> status.checkedColor
                is PositionStatus.Checkmate -> status.winner.opposite()
                else -> null
            }

            is ChessGameState.GameOver -> when (val outcome = state.outcome) {
                is GameOutcome.Checkmate -> outcome.winner.opposite()
                else -> null
            }

            else -> null
        }

    /** True when the check on the board is terminal (checkmate or a captured king). */
    val isCheckFatal: Boolean
        get() = when (val state = gameState) {
            is ChessGameState.Playing -> state.status is PositionStatus.Checkmate
            is ChessGameState.GameOver -> state.outcome is GameOutcome.Checkmate
            else -> false
        }

    /** Localised game-over sentence, or null while the match is still running. */
    val gameOverMessage: UiText?
        get() = when (val state = gameState) {
            is ChessGameState.GameOver -> describeGameOver(state.outcome)
            is ChessGameState.Playing -> when (val status = state.status) {
                is PositionStatus.Checkmate -> UiText.of(R.string.outcome_checkmate, colorLabel(status.winner))
                is PositionStatus.KingCaptured -> UiText.of(R.string.outcome_king_captured, colorLabel(status.winner))
                is PositionStatus.Draw -> UiText.of(R.string.status_draw, status.reason.displayText())
                else -> null
            }

            else -> null
        }

    /** Public access to the localised side label (handles the You/AI wording in AI mode). */
    fun colorLabelFor(color: Color): UiText = colorLabel(color)

    fun incrementDraftPiece(pieceType: PieceType) {
        if (!isDraftEditable) return
        selectedPlacementPieceType = pieceType
    }

    fun decrementDraftPiece(pieceType: PieceType) {
        // Setup purchases are atomic and cannot be undone after placement.
    }

    fun canIncrementDraftPiece(pieceType: PieceType): Boolean {
        return DraftValidator.canAddPiece(game.getSetupCounts(currentDraftColor), pieceType, draftRules)
    }

    fun draftPurchaseWarning(pieceType: PieceType): UiText? =
        (DraftValidator.validateAddition(game.getSetupCounts(currentDraftColor), pieceType, draftRules) as? com.chesspoints.engine.DraftPurchaseValidation.Rejected)?.displayText()

    fun draftPurchaseValidation(pieceType: PieceType): DraftPurchaseValidation =
        DraftValidator.validateAddition(game.getSetupCounts(currentDraftColor), pieceType, draftRules)

    fun confirmDraft() {
        // There is no separate confirmation step in atomic setup.
    }

    fun selectPlacementPiece(pieceType: PieceType) {
        if (!isPlacementInteractive || remainingPlacementPieces.getOrDefault(pieceType, 0) <= 0) return
        selectedPlacementPieceType = if (selectedPlacementPieceType == pieceType) null else pieceType
    }

    /**
     * True when [pieceType] may be dropped on [square] right now. Setup is atomic:
     * a legal drop both buys and places the piece, so affordability is part of the answer.
     */
    fun canPlaceAt(pieceType: PieceType, square: Square): Boolean {
        if (!isPlacementInteractive) return false
        if (currentBoard[square] != null) return false
        if (placementZone?.contains(square) != true) return false
        return if (isDrafting) {
            canIncrementDraftPiece(pieceType)
        } else {
            remainingPlacementPieces.getOrDefault(pieceType, 0) > 0
        }
    }

    /** Squares that would accept [pieceType] right now; used to light up drop targets while dragging. */
    fun placeableSquares(pieceType: PieceType): Set<Square> {
        val zone = placementZone ?: return emptySet()
        if (!isPlacementInteractive) return emptySet()
        val affordable = if (isDrafting) {
            canIncrementDraftPiece(pieceType)
        } else {
            remainingPlacementPieces.getOrDefault(pieceType, 0) > 0
        }
        if (!affordable) return emptySet()
        val board = currentBoard
        return buildSet {
            for (rank in zone.ranks) {
                for (file in 0..7) {
                    val square = Square(file, rank)
                    if (board[square] == null) add(square)
                }
            }
        }
    }

    /**
     * Atomic buy-and-place triggered by dropping a piece from the shop onto a square.
     * Rejections surface as a localised banner from the engine result.
     */
    fun dropPieceOn(pieceType: PieceType, square: Square) {
        if (!isPlacementInteractive) return
        selectedPlacementPieceType = pieceType
        placeSelectedPieceAt(square)
        selectedPlacementPieceType = null
    }

    fun placeSelectedPieceAt(square: Square) {
        val pieceType = selectedPlacementPieceType
        if (!isPlacementInteractive) return
        if (pieceType == null) {
            bannerMessage = UiText.Res(R.string.banner_select_piece_first)
            return
        }

        if (isDrafting) {
            when (val result = game.buyAndPlacePiece(currentDraftColor, pieceType, square)) {
                is com.chesspoints.engine.SetupResult.Accepted -> {
                    refreshGameState()
                    currentDraftColor = game.getSetupSideToPlace()
                    draftPieceCounts = game.getSetupCounts(currentDraftColor)
                    selectedPlacementPieceType = null
                    if (gameState is ChessGameState.Playing) {
                        currentScreen = AppScreen.Game
                        requestAiMoveIfNeeded()
                    } else {
                        requestAiSetupPlacementIfNeeded()
                    }
                }
                is com.chesspoints.engine.SetupResult.Rejected -> {
                    bannerMessage = result.reason.displayText()
                }
            }
            return
        }

        val placingState = gameState as? ChessGameState.Placing ?: return

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

            is GamePlacementResult.Rejected -> bannerMessage = result.reason.displayText()
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
                    bannerMessage = UiText.Res(R.string.banner_illegal_destination)
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

            is com.chesspoints.engine.GameMoveResult.Rejected -> bannerMessage = result.reason.displayText()
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
                draftPlacementBoard = Board.empty()
                selectedPlacementPieceType = null
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
                    aiSetupCounts = result.value
                    requestAiSetupPlacementIfNeeded()
                }

                is AiStepResult.Unavailable -> {
                    awaitingAiDraft = false
                    bannerMessage = result.message
                }
            }
        }
    }

    /**
     * Drives one AI buy-and-place turn during the atomic setup phase. The AI army is
     * drafted once per session and then placed one piece per AI turn until it is complete.
     */
    private fun requestAiSetupPlacementIfNeeded() {
        if (!isDrafting) return
        if (!isAiColor(game.getSetupSideToPlace())) return
        val aiColor = game.getSetupSideToPlace()
        val targetCounts = aiSetupCounts
        if (targetCounts == null) {
            requestAiDraft()
            return
        }
        val placedCounts = game.getSetupCounts(aiColor)
        val remaining = PieceType.entries.associateWith { type ->
            targetCounts.getOrDefault(type, 0) - placedCounts.getOrDefault(type, 0)
        }
        if (remaining.values.sum() <= 0) {
            awaitingAiPlacement = false
            return
        }
        val sessionId = gameSessionId
        awaitingAiPlacement = true
        selectedPlacementPieceType = null
        appScope.launch {
            val result = withContext(Dispatchers.Default) {
                aiOpponentGateway.chooseSetupPlacement(
                    color = aiColor,
                    remainingPieces = remaining,
                    board = game.getSetupBoard(),
                    rules = GameRules().placementRules,
                    purchasedCounts = placedCounts,
                    draftRules = draftRules,
                )
            }
            if (sessionId != gameSessionId) return@launch
            awaitingAiPlacement = false
            when (result) {
                is AiStepResult.Success -> {
                    when (
                        val setup = game.buyAndPlacePiece(aiColor, result.value.pieceType, result.value.square)
                    ) {
                        is com.chesspoints.engine.SetupResult.Accepted -> {
                            refreshGameState()
                            currentDraftColor = game.getSetupSideToPlace()
                            draftPieceCounts = game.getSetupCounts(currentDraftColor)
                            if (gameState is ChessGameState.Playing) {
                                currentScreen = AppScreen.Game
                                requestAiMoveIfNeeded()
                            } else {
                                // Setup alternates by piece. Keep driving the loop if the
                                // engine still reports the AI side, while leaving control
                                // with the human when the turn has switched.
                                requestAiSetupPlacementIfNeeded()
                            }
                        }

                        is com.chesspoints.engine.SetupResult.Rejected -> {
                            bannerMessage = setup.reason.displayText()
                        }
                    }
                }

                is AiStepResult.Unavailable -> bannerMessage = result.message
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

                        is GamePlacementResult.Rejected -> bannerMessage = placementResult.reason.displayText()
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

    private fun replayDraftPlacements() {
        if (gameState !is ChessGameState.Placing) return
        val nextIndex = mutableMapOf(Color.WHITE to 0, Color.BLACK to 0)
        while (true) {
            val placingState = gameState as? ChessGameState.Placing ?: return
            val color = placingState.placementState.sideToPlace
            val choices = draftPlacements[color].orEmpty()
            val index = nextIndex.getValue(color)
            val choice = choices.getOrNull(index) ?: return
            game.placePiece(color, choice.pieceType, choice.square)
            nextIndex[color] = index + 1
            refreshGameState()
        }
    }

    private fun isAiColor(color: Color): Boolean = gameMode == GameMode.VersusAi && color == Color.BLACK

    private fun colorLabel(color: Color): UiText =
        when {
            gameMode == GameMode.VersusAi && color == Color.WHITE -> UiText.Res(R.string.label_you)
            gameMode == GameMode.VersusAi && color == Color.BLACK -> UiText.Res(R.string.label_ai)
            color == Color.WHITE -> UiText.Res(R.string.color_white)
            else -> UiText.Res(R.string.color_black)
        }

    private fun describePlayingStatus(status: PositionStatus): UiText =
        when (status) {
            PositionStatus.Active ->
                UiText.of(R.string.status_to_move, colorLabel(playSideToMove))

            is PositionStatus.Check ->
                UiText.of(R.string.status_check, colorLabel(status.checkedColor))

            is PositionStatus.Checkmate ->
                UiText.of(R.string.status_checkmate, colorLabel(status.winner))

            is PositionStatus.KingCaptured ->
                UiText.of(R.string.status_king_captured, colorLabel(status.winner))

            is PositionStatus.Draw ->
                UiText.of(R.string.status_draw, status.reason.displayText())
        }

    private fun describeGameOver(outcome: GameOutcome): UiText =
        when (outcome) {
            is GameOutcome.Checkmate ->
                UiText.of(R.string.outcome_checkmate, colorLabel(outcome.winner))

            is GameOutcome.KingCaptured ->
                UiText.of(R.string.outcome_king_captured, colorLabel(outcome.winner))

            is GameOutcome.Draw ->
                UiText.of(R.string.status_draw, outcome.reason.displayText())
        }

    private fun defaultDraftCounts(): Map<PieceType, Int> =
        PieceType.entries.associateWith { 0 }

    private fun getValueOrZero(counts: Map<PieceType, Int>, pieceType: PieceType): Int =
        counts.getOrDefault(pieceType, 0)
}
