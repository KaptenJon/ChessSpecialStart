package com.chesspoints.app

import com.chesspoints.ai.AiMoveEngine
import com.chesspoints.engine.Board
import com.chesspoints.engine.ChessGameState
import com.chesspoints.engine.Color
import com.chesspoints.engine.DraftRules
import com.chesspoints.engine.GamePosition
import com.chesspoints.engine.GameRules
import com.chesspoints.engine.MoveRequest
import com.chesspoints.engine.PieceType
import com.chesspoints.engine.PlacementRules
import com.chesspoints.engine.PlacementState
import com.chesspoints.engine.Square
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the AI setup turn loop: after every human buy-and-place the AI has to
 * answer with its own piece, all the way until both armies are on the board.
 */
class AiSetupFlowTest {
    private val placementRules = GameRules().placementRules
    private val draftRules = GameRules().draftRules

    @Test
    fun aiPlacesAPieceAfterTheFirstHumanPlacement() {
        val state = newAiGame()

        state.dropPieceOn(PieceType.PAWN, whiteSquares()[0])

        awaitUntil("AI never placed a piece") { blackPieceCount(state) == 1 }
        assertEquals(Color.WHITE, state.currentDraftColor)
        assertTrue(state.isPlacementInteractive)
        assertNull(state.bannerMessage)
    }

    @Test
    fun aiAnswersEveryHumanPlacementUntilBothArmiesAreReady() {
        val state = newAiGame()
        val squares = whiteSquares()

        standardArmy().forEachIndexed { index, pieceType ->
            awaitUntil("Setup never came back to the human at move $index") {
                state.isPlacementInteractive && state.currentDraftColor == Color.WHITE
            }
            state.dropPieceOn(pieceType, squares[index])
            awaitUntil("AI did not answer human placement $index") {
                blackPieceCount(state) == index + 1 || state.gameState is ChessGameState.Playing
            }
            assertNull(state.bannerMessage)
        }

        awaitUntil("Setup never reached the playing phase") { state.gameState is ChessGameState.Playing }
        assertEquals(16, blackPieceCount(state))
        assertEquals(16, whitePieceCount(state))
        assertEquals(AppScreen.Game, state.currentScreen)
    }

    @Test
    fun realAiOpponentAnswersEveryHumanPlacementUntilTheMatchStarts() {
        val state = ChessPointsState(CoroutineScope(Dispatchers.Unconfined))
        state.selectMode(GameMode.VersusAi)
        state.startGame()
        val squares = whiteSquares()

        standardArmy().forEachIndexed { index, pieceType ->
            awaitUntil("Setup never came back to the human at move $index") {
                state.isPlacementInteractive && state.currentDraftColor == Color.WHITE
            }
            state.dropPieceOn(pieceType, squares[index])
            awaitUntil("Real AI did not answer human placement $index") {
                blackPieceCount(state) == index + 1 || state.gameState is ChessGameState.Playing
            }
            assertNull(state.bannerMessage)
        }

        awaitUntil("Setup never reached the playing phase") { state.gameState is ChessGameState.Playing }
        assertEquals(16, blackPieceCount(state))
        assertEquals(16, whitePieceCount(state))
        assertEquals(AppScreen.Game, state.currentScreen)
    }

    @Test
    fun twoPlayerSetupDoesNotAutoPlaceForBlack() {
        val state = ChessPointsState(CoroutineScope(Dispatchers.Unconfined), ScriptedAiGateway())
        state.selectMode(GameMode.TwoPlayer)
        state.startGame()

        state.dropPieceOn(PieceType.PAWN, whiteSquares()[0])

        assertEquals(Color.BLACK, state.currentDraftColor)
        assertEquals(0, blackPieceCount(state))
        assertTrue(state.isPlacementInteractive)
    }

    private fun newAiGame(): ChessPointsState {
        val state = ChessPointsState(CoroutineScope(Dispatchers.Unconfined), ScriptedAiGateway())
        state.selectMode(GameMode.VersusAi)
        state.startGame()
        return state
    }

    private fun whiteSquares(): List<Square> {
        val ranks = placementRules.zoneFor(Color.WHITE).ranks
        return ranks.flatMap { rank -> (0..7).map { file -> Square(file, rank) } }
    }

    private fun standardArmy(): List<PieceType> = buildList {
        add(PieceType.KING)
        add(PieceType.QUEEN)
        repeat(2) { add(PieceType.ROOK) }
        repeat(2) { add(PieceType.BISHOP) }
        repeat(2) { add(PieceType.KNIGHT) }
        repeat(8) { add(PieceType.PAWN) }
    }

    private fun blackPieceCount(state: ChessPointsState): Int = state.currentBoard.pieces(Color.BLACK).size

    private fun whitePieceCount(state: ChessPointsState): Int = state.currentBoard.pieces(Color.WHITE).size

    private fun awaitUntil(message: String, timeoutMs: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(5)
        }
        throw AssertionError(message)
    }

    /** Deterministic stand-in for the real AI so the turn loop itself is what is under test. */
    private inner class ScriptedAiGateway : AiOpponentGateway {
        private val moveEngine = AiMoveEngine()

        override fun buildDraft(
            draftState: ChessGameState.Drafting,
            color: Color,
        ): AiStepResult<Map<PieceType, Int>> = AiStepResult.Success(
            PieceType.entries.associateWith { type -> standardArmy().count { it == type } },
        )

        override fun choosePlacement(
            placementState: PlacementState,
            color: Color,
        ): AiStepResult<PlacementChoice> {
            val pieceType = placementState.remainingPieces(color).entries
                .firstOrNull { it.value > 0 }?.key
                ?: return AiStepResult.Unavailable(com.chesspoints.app.i18n.UiText.Raw("no piece"))
            val square = freeSquare(color, placementState.board)
                ?: return AiStepResult.Unavailable(com.chesspoints.app.i18n.UiText.Raw("no square"))
            return AiStepResult.Success(PlacementChoice(pieceType, square))
        }

        override fun chooseSetupPlacement(
            color: Color,
            remainingPieces: Map<PieceType, Int>,
            board: Board,
            rules: PlacementRules,
            purchasedCounts: Map<PieceType, Int>,
            draftRules: DraftRules,
        ): AiStepResult<PlacementChoice> {
            val pieceType = PieceType.entries.firstOrNull { remainingPieces.getOrDefault(it, 0) > 0 }
                ?: return AiStepResult.Unavailable(com.chesspoints.app.i18n.UiText.Raw("no piece"))
            val square = freeSquare(color, board, rules)
                ?: return AiStepResult.Unavailable(com.chesspoints.app.i18n.UiText.Raw("no square"))
            return AiStepResult.Success(PlacementChoice(pieceType, square))
        }

        override fun chooseMove(position: GamePosition): AiStepResult<MoveRequest> {
            val move = moveEngine.chooseMove(position).move
                ?: return AiStepResult.Unavailable(com.chesspoints.app.i18n.UiText.Raw("no move"))
            return AiStepResult.Success(MoveRequest(move.from, move.to, move.promotion))
        }

        private fun freeSquare(
            color: Color,
            board: Board,
            rules: PlacementRules = placementRules,
        ): Square? = rules.zoneFor(color).ranks
            .flatMap { rank -> (0..7).map { file -> Square(file, rank) } }
            .firstOrNull { board[it] == null }
    }
}
