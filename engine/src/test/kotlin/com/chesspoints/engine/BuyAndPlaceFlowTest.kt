package com.chesspoints.engine

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BuyAndPlaceFlowTest {
    private val rules = GameRules(
        draftRules = DraftRules(budget = 5, requiredPieceCount = 3),
    )

    @Test
    fun rejectsAnUnaffordableDraftWithAUsefulValidationMessage() {
        val game = ChessGame(rules)

        val result = game.submitDraft(
            color = Color.WHITE,
            pieceCounts = mapOf(
                PieceType.KING to 1,
                PieceType.QUEEN to 1,
                PieceType.PAWN to 1,
            ),
        )

        val rejected = assertIs<DraftSubmissionResult.Rejected>(result)
        val invalid = assertIs<DraftSubmissionError.InvalidDraft>(rejected.reason)
        val budgetError = assertIs<DraftValidationError.BudgetExceeded>(
            invalid.errors.single { it is DraftValidationError.BudgetExceeded },
        )
        assertEquals(5, budgetError.budget)
        assertEquals(10, budgetError.actualCost)
        assertContains(invalid.message, "exceeds the 5 point budget")
    }

    @Test
    fun validPurchasesCanBePlacedAndInvalidPlacementDoesNotAdvanceTheFlow() {
        val game = ChessGame(rules)
        val affordableRoster = mapOf(
            PieceType.KING to 1,
            PieceType.PAWN to 2,
        )

        assertIs<DraftSubmissionResult.Accepted>(game.submitDraft(Color.WHITE, affordableRoster))
        val placing = assertIs<DraftSubmissionResult.Accepted>(
            game.submitDraft(Color.BLACK, affordableRoster),
        ).gameState
        assertIs<ChessGameState.Placing>(placing)

        val wrongPiece = assertIs<GamePlacementResult.Rejected>(
            game.placePiece(Color.WHITE, PieceType.QUEEN, sq("d1")),
        )
        assertContains(wrongPiece.reason.message, "no remaining QUEEN")
        assertIs<ChessGameState.Placing>(game.getGameState())

        assertIs<GamePlacementResult.Accepted>(
            game.placePiece(Color.WHITE, PieceType.KING, sq("e1")),
        )
        val outsideZone = assertIs<GamePlacementResult.Rejected>(
            game.placePiece(Color.BLACK, PieceType.KING, sq("e5")),
        )
        assertContains(outsideZone.reason.message, "outside BLACK's placement zone")
        assertIs<ChessGameState.Placing>(game.getGameState())

        val occupied = assertIs<GamePlacementResult.Rejected>(
            game.placePiece(Color.BLACK, PieceType.KING, sq("e1")),
        )
        assertContains(occupied.reason.message, "already occupied")
        assertIs<ChessGameState.Placing>(game.getGameState())
    }

    @Test
    fun buyAndPlaceIsAtomicAndTransitionsDirectlyToPlay() {
        val game = ChessGame(
            GameRules(draftRules = DraftRules(budget = 1, requiredPieceCount = 2)),
        )

        val wrongTurn = assertIs<SetupResult.Rejected>(
            game.buyAndPlacePiece(Color.BLACK, PieceType.KING, sq("e8")),
        )
        assertIs<SetupError.WrongTurn>(wrongTurn.reason)

        assertIs<SetupResult.Accepted>(game.buyAndPlacePiece(Color.WHITE, PieceType.KING, sq("e1")))
        val invalid = assertIs<SetupResult.Rejected>(
            game.buyAndPlacePiece(Color.BLACK, PieceType.KING, sq("e1")),
        )
        assertIs<SetupError.InvalidSquare>(invalid.reason)

        assertIs<SetupResult.Accepted>(game.buyAndPlacePiece(Color.BLACK, PieceType.KING, sq("e8")))
        assertIs<SetupResult.Accepted>(game.buyAndPlacePiece(Color.WHITE, PieceType.PAWN, sq("a1")))
        val final = assertIs<SetupResult.Accepted>(
            game.buyAndPlacePiece(Color.BLACK, PieceType.PAWN, sq("a8")),
        )

        val playing = assertIs<ChessGameState.Playing>(final.gameState)
        assertEquals(Piece(PieceType.KING, Color.WHITE), playing.position.board[sq("e1")])
        assertEquals(Piece(PieceType.PAWN, Color.BLACK), playing.position.board[sq("a8")])
    }

    @Test
    fun invalidSetupDropsPreserveTurnBoardAndUnpurchasedState() {
        val game = ChessGame(
            GameRules(draftRules = DraftRules(budget = 1, requiredPieceCount = 2)),
        )

        val outsideZone = assertIs<SetupResult.Rejected>(
            game.buyAndPlacePiece(Color.WHITE, PieceType.PAWN, sq("a3")),
        )
        assertIs<SetupError.InvalidSquare>(outsideZone.reason)
        assertEquals(Color.WHITE, game.getSetupSideToPlace())
        assertEquals(0, game.getSetupCounts(Color.WHITE).getValue(PieceType.PAWN))
        assertEquals(null, game.getSetupBoard()[sq("a3")])

        assertIs<SetupResult.Accepted>(
            game.buyAndPlacePiece(Color.WHITE, PieceType.KING, sq("e1")),
        )

        val occupied = assertIs<SetupResult.Rejected>(
            game.buyAndPlacePiece(Color.BLACK, PieceType.KING, sq("e1")),
        )
        assertIs<SetupError.InvalidSquare>(occupied.reason)
        assertEquals(Color.BLACK, game.getSetupSideToPlace())
        assertEquals(0, game.getSetupCounts(Color.BLACK).getValue(PieceType.KING))

        val wrongTurn = assertIs<SetupResult.Rejected>(
            game.buyAndPlacePiece(Color.WHITE, PieceType.PAWN, sq("a2")),
        )
        assertIs<SetupError.WrongTurn>(wrongTurn.reason)
        assertEquals(Color.BLACK, game.getSetupSideToPlace())
        assertEquals(null, game.getSetupBoard()[sq("a2")])
    }

    @Test
    fun eachAcceptedSetupMoveAlternatesUntilTheFourthPieceStartsPlay() {
        val game = ChessGame(
            GameRules(draftRules = DraftRules(budget = 1, requiredPieceCount = 2)),
        )

        assertIs<SetupResult.Accepted>(game.buyAndPlacePiece(Color.WHITE, PieceType.KING, sq("e1")))
        assertEquals(Color.BLACK, game.getSetupSideToPlace())
        assertIs<SetupResult.Accepted>(game.buyAndPlacePiece(Color.BLACK, PieceType.KING, sq("e8")))
        assertEquals(Color.WHITE, game.getSetupSideToPlace())
        assertIs<SetupResult.Accepted>(game.buyAndPlacePiece(Color.WHITE, PieceType.PAWN, sq("a2")))
        assertEquals(Color.BLACK, game.getSetupSideToPlace())

        val final = assertIs<SetupResult.Accepted>(
            game.buyAndPlacePiece(Color.BLACK, PieceType.PAWN, sq("a7")),
        )

        val playing = assertIs<ChessGameState.Playing>(final.gameState)
        assertEquals(Piece(PieceType.KING, Color.WHITE), playing.position.board[sq("e1")])
        assertEquals(Piece(PieceType.KING, Color.BLACK), playing.position.board[sq("e8")])
        assertEquals(Piece(PieceType.PAWN, Color.WHITE), playing.position.board[sq("a2")])
        assertEquals(Piece(PieceType.PAWN, Color.BLACK), playing.position.board[sq("a7")])
    }

    private fun sq(algebraic: String): Square = Square.fromAlgebraic(algebraic)!!
}
