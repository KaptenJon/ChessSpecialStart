package com.chesspoints.app

import com.chesspoints.engine.Color
import com.chesspoints.engine.PieceType
import com.chesspoints.engine.Square
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the in-game check notice and the back / new game navigation added for the match HUD.
 */
class GameNoticeAndNavigationTest {
    @Test
    fun checkIsReportedWithTheThreatenedKingSquare() {
        val state = playedOutSetup()

        // Qb2-b6 attacks the black king on a7.
        state.handleGameSquareTap(sq("b2"))
        state.handleGameSquareTap(sq("b6"))

        assertEquals(Color.BLACK, state.checkedKingColor)
        assertEquals(sq("a7"), state.checkedKingSquare)
        assertFalse(state.isCheckFatal)
        assertNull(state.gameOverMessage)
    }

    @Test
    fun noCheckNoticeWhileTheKingIsSafe() {
        val state = playedOutSetup()

        assertNull(state.checkedKingColor)
        assertNull(state.checkedKingSquare)
        assertFalse(state.isCheckFatal)
        assertNull(state.gameOverMessage)
    }

    @Test
    fun navigateHomeAbandonsTheMatchAndClearsTheBoard() {
        val state = ChessPointsState(CoroutineScope(Dispatchers.Unconfined))
        state.startGame()
        state.dropPieceOn(PieceType.PAWN, sq("a2"))
        assertTrue(state.hasMatchInProgress)

        state.navigateHome()

        assertEquals(AppScreen.Home, state.currentScreen)
        assertFalse(state.hasMatchInProgress)
        assertTrue(state.currentBoard.pieces(Color.WHITE).isEmpty())
        assertTrue(state.currentBoard.pieces(Color.BLACK).isEmpty())
        assertEquals(Color.WHITE, state.currentDraftColor)
    }

    @Test
    fun startingANewGameFromAMatchResetsTheBoard() {
        val state = playedOutSetup()
        assertTrue(state.currentBoard.pieces(Color.WHITE).isNotEmpty())

        state.startGame()

        assertEquals(AppScreen.Placement, state.currentScreen)
        assertTrue(state.isDrafting)
        assertTrue(state.currentBoard.pieces(Color.WHITE).isEmpty())
        assertNull(state.gameOverMessage)
    }

    @Test
    fun homeScreenHasNoMatchToLeave() {
        val state = ChessPointsState(CoroutineScope(Dispatchers.Unconfined))

        assertFalse(state.hasMatchInProgress)
        assertEquals(AppScreen.Home, state.currentScreen)
    }

    /** Plays a full two-player setup so the match starts with a known position. */
    private fun playedOutSetup(): ChessPointsState {
        val state = ChessPointsState(CoroutineScope(Dispatchers.Unconfined))
        state.selectMode(GameMode.TwoPlayer)
        state.startGame()

        whiteArmy().zip(blackArmy()).forEach { (white, black) ->
            state.dropPieceOn(white.first, white.second)
            assertNull("White placement ${white.second} was rejected", state.bannerMessage)
            state.dropPieceOn(black.first, black.second)
            assertNull("Black placement ${black.second} was rejected", state.bannerMessage)
        }

        assertEquals(AppScreen.Game, state.currentScreen)
        assertNotNull(state.currentBoard[sq("a7")])
        return state
    }

    private fun whiteArmy(): List<Pair<PieceType, Square>> = listOf(
        PieceType.KING to sq("e1"),
        PieceType.QUEEN to sq("b2"),
        PieceType.ROOK to sq("a1"),
        PieceType.ROOK to sq("h1"),
        PieceType.BISHOP to sq("c1"),
        PieceType.BISHOP to sq("f1"),
        PieceType.KNIGHT to sq("b1"),
        PieceType.KNIGHT to sq("g1"),
        PieceType.PAWN to sq("d1"),
        PieceType.PAWN to sq("a2"),
        PieceType.PAWN to sq("c2"),
        PieceType.PAWN to sq("d2"),
        PieceType.PAWN to sq("e2"),
        PieceType.PAWN to sq("f2"),
        PieceType.PAWN to sq("g2"),
        PieceType.PAWN to sq("h2"),
    )

    private fun blackArmy(): List<Pair<PieceType, Square>> = listOf(
        PieceType.KING to sq("a7"),
        PieceType.QUEEN to sq("d8"),
        PieceType.ROOK to sq("a8"),
        PieceType.ROOK to sq("h8"),
        PieceType.BISHOP to sq("c8"),
        PieceType.BISHOP to sq("f8"),
        PieceType.KNIGHT to sq("b8"),
        PieceType.KNIGHT to sq("g8"),
        PieceType.PAWN to sq("e8"),
        PieceType.PAWN to sq("b7"),
        PieceType.PAWN to sq("c7"),
        PieceType.PAWN to sq("d7"),
        PieceType.PAWN to sq("e7"),
        PieceType.PAWN to sq("f7"),
        PieceType.PAWN to sq("g7"),
        PieceType.PAWN to sq("h7"),
    )

    private fun sq(algebraic: String): Square = Square.fromAlgebraic(algebraic)!!
}
