package com.chesspoints.app

import com.chesspoints.app.i18n.UiText
import com.chesspoints.engine.PieceType
import com.chesspoints.engine.Color
import com.chesspoints.engine.Piece
import com.chesspoints.engine.Square
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessPointsStateTest {
    @Test
    fun startingANewGameOpensTheCombinedBuyAndPlaceFlow() {
        val state = ChessPointsState(CoroutineScope(Dispatchers.Unconfined))

        state.startGame()

        assertEquals(AppScreen.Placement, state.currentScreen)
        assertTrue(state.isDrafting)
        assertTrue(state.isDraftEditable)
        assertEquals(UiText.Res(R.string.title_build_and_place), state.screenTitle)
    }

    @Test
    fun legalPiecePurchaseIsEnabledAndUpdatesTheDraft() {
        val state = ChessPointsState(CoroutineScope(Dispatchers.Unconfined))
        state.startGame()

        assertTrue(state.canIncrementDraftPiece(PieceType.PAWN))

        state.incrementDraftPiece(PieceType.PAWN)

        assertEquals(PieceType.PAWN, state.selectedPlacementPieceType)
        assertEquals(0, state.draftPieceCounts.getValue(PieceType.PAWN))
        assertEquals(null, state.bannerMessage)
    }

    @Test
    fun buyingAndPlacingOnePieceUpdatesTheBoardAndAlternatesTheSetupTurn() {
        val state = ChessPointsState(CoroutineScope(Dispatchers.Unconfined))
        state.startGame()

        state.incrementDraftPiece(PieceType.PAWN)
        state.placeSelectedPieceAt(sq("a2"))

        assertEquals(Piece(PieceType.PAWN, Color.WHITE), state.currentBoard[sq("a2")])
        assertEquals(Color.BLACK, state.currentDraftColor)
        assertEquals(null, state.selectedPlacementPieceType)
        assertTrue(state.isDrafting)
    }

    @Test
    fun invalidDraftPlacementDoesNotConsumeThePurchaseOrChangeTheTurn() {
        val state = ChessPointsState(CoroutineScope(Dispatchers.Unconfined))
        state.startGame()

        state.incrementDraftPiece(PieceType.KNIGHT)
        state.placeSelectedPieceAt(sq("a4"))

        assertEquals(null, state.currentBoard[sq("a4")])
        assertEquals(Color.WHITE, state.currentDraftColor)
        assertEquals(PieceType.KNIGHT, state.selectedPlacementPieceType)
        assertEquals(
            R.string.error_outside_zone,
            (state.bannerMessage as UiText.Res).id,
        )
    }

    @Test
    fun startingANewGameResetsSetupBoardAndSelection() {
        val state = ChessPointsState(CoroutineScope(Dispatchers.Unconfined))
        state.startGame()
        state.incrementDraftPiece(PieceType.PAWN)
        state.placeSelectedPieceAt(sq("a2"))

        state.startGame()

        assertEquals(AppScreen.Placement, state.currentScreen)
        assertTrue(state.isDrafting)
        assertEquals(Color.WHITE, state.currentDraftColor)
        assertEquals(null, state.currentBoard[sq("a2")])
        assertEquals(null, state.selectedPlacementPieceType)
    }

    private fun sq(algebraic: String): Square = Square.fromAlgebraic(algebraic)!!
}
