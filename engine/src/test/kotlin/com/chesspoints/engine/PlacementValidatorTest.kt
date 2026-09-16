package com.chesspoints.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlacementValidatorTest {
    private val validator = PlacementValidator()

    @Test
    fun rejectsPlacementsThatAreOutOfTurnOutsideZoneOrUseUnavailablePieces() {
        val state = validator.start(
            whiteRoster = roster(Color.WHITE, PieceType.KING to 1),
            blackRoster = roster(Color.BLACK, PieceType.KING to 1),
        )

        val wrongTurn = validator.place(state, Color.BLACK, PieceType.KING, sq("e8"))
        val unavailablePiece = validator.place(state, Color.WHITE, PieceType.QUEEN, sq("d1"))
        val whiteKingPlaced = assertIs<PlacementResult.Success>(
            validator.place(state, Color.WHITE, PieceType.KING, sq("e1")),
        )
        val outsideZone = validator.place(whiteKingPlaced.state, Color.BLACK, PieceType.KING, sq("e5"))

        assertIs<PlacementResult.Failure>(wrongTurn).also {
            assertIs<PlacementError.WrongTurn>(it.error)
        }
        assertIs<PlacementResult.Failure>(unavailablePiece).also {
            assertIs<PlacementError.PieceNotAvailable>(it.error)
        }
        assertIs<PlacementResult.Failure>(outsideZone).also {
            assertIs<PlacementError.OutsidePlacementZone>(it.error)
        }
    }

    @Test
    fun rejectsPlacementOnAnOccupiedSquare() {
        val state = validator.start(
            whiteRoster = roster(Color.WHITE, PieceType.KING to 1, PieceType.PAWN to 1),
            blackRoster = roster(Color.BLACK, PieceType.KING to 1),
        )

        val whiteKingPlaced = assertIs<PlacementResult.Success>(
            validator.place(state, Color.WHITE, PieceType.KING, sq("e1")),
        )
        val blackKingPlaced = assertIs<PlacementResult.Success>(
            validator.place(whiteKingPlaced.state, Color.BLACK, PieceType.KING, sq("e8")),
        )
        val occupied = validator.place(blackKingPlaced.state, Color.WHITE, PieceType.PAWN, sq("e1"))

        assertIs<PlacementResult.Failure>(occupied).also {
            assertIs<PlacementError.OccupiedSquare>(it.error)
        }
    }

    @Test
    fun becomesReadyToPlayOnlyAfterBothRostersAreFullyPlaced() {
        val state = validator.start(
            whiteRoster = roster(Color.WHITE, PieceType.KING to 1, PieceType.PAWN to 1),
            blackRoster = roster(Color.BLACK, PieceType.KING to 1, PieceType.PAWN to 1),
        )

        val whiteKingPlaced = assertIs<PlacementResult.Success>(
            validator.place(state, Color.WHITE, PieceType.KING, sq("e1")),
        )
        val blackKingPlaced = assertIs<PlacementResult.Success>(
            validator.place(whiteKingPlaced.state, Color.BLACK, PieceType.KING, sq("e8")),
        )
        val whitePawnPlaced = assertIs<PlacementResult.Success>(
            validator.place(blackKingPlaced.state, Color.WHITE, PieceType.PAWN, sq("a2")),
        )
        val blackPawnPlaced = assertIs<PlacementResult.Success>(
            validator.place(whitePawnPlaced.state, Color.BLACK, PieceType.PAWN, sq("a7")),
        )

        assertFalse(whiteKingPlaced.readyToPlay)
        assertFalse(blackKingPlaced.readyToPlay)
        assertFalse(whitePawnPlaced.readyToPlay)
        assertTrue(blackPawnPlaced.readyToPlay)
        assertEquals(0, blackPawnPlaced.state.remainingPieces(Color.WHITE).values.sum())
        assertEquals(0, blackPawnPlaced.state.remainingPieces(Color.BLACK).values.sum())
    }

    @Test
    fun chessGameOnlyTransitionsToPlayingAfterFinalPlacement() {
        val game = ChessGame(
            GameRules(
                draftRules = DraftRules(
                    budget = 1,
                    requiredPieceCount = 2,
                ),
            ),
        )
        val roster = mapOf(PieceType.KING to 1, PieceType.PAWN to 1)

        assertIs<DraftSubmissionResult.Accepted>(game.submitDraft(Color.WHITE, roster))
        val blackDraft = assertIs<DraftSubmissionResult.Accepted>(game.submitDraft(Color.BLACK, roster))
        assertIs<ChessGameState.Placing>(blackDraft.gameState)

        val firstPlacement = assertIs<GamePlacementResult.Accepted>(game.placePiece(Color.WHITE, PieceType.KING, sq("e1")))
        val secondPlacement = assertIs<GamePlacementResult.Accepted>(game.placePiece(Color.BLACK, PieceType.KING, sq("e8")))
        val thirdPlacement = assertIs<GamePlacementResult.Accepted>(game.placePiece(Color.WHITE, PieceType.PAWN, sq("a2")))
        val fourthPlacement = assertIs<GamePlacementResult.Accepted>(game.placePiece(Color.BLACK, PieceType.PAWN, sq("a7")))

        assertIs<ChessGameState.Placing>(firstPlacement.gameState)
        assertIs<ChessGameState.Placing>(secondPlacement.gameState)
        assertIs<ChessGameState.Placing>(thirdPlacement.gameState)
        val playing = assertIs<ChessGameState.Playing>(fourthPlacement.gameState)
        assertEquals(Color.WHITE, playing.position.sideToMove)
    }
}

private fun roster(color: Color, vararg counts: Pair<PieceType, Int>): Roster =
    Roster(color, mapOf(*counts))

private fun sq(algebraic: String): Square = Square.fromAlgebraic(algebraic)!!
