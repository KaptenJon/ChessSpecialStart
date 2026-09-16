package com.chesspoints.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChessGameTest {
    @Test
    fun draftValidatorRejectsInvalidCompositions() {
        val result = DraftValidator.validate(
            color = Color.WHITE,
            pieceCounts = mapOf(
                PieceType.KING to 2,
                PieceType.QUEEN to 5,
            ),
        )

        val invalid = assertIs<DraftValidationResult.Invalid>(result)
        assertTrue(invalid.errors.any { it is DraftValidationError.PieceCountMismatch })
        assertTrue(invalid.errors.any { it is DraftValidationError.KingCountMismatch })
        assertTrue(invalid.errors.any { it is DraftValidationError.BudgetExceeded })
    }

    @Test
    fun placementValidatorEnforcesTurnOrderAndZones() {
        val placementValidator = PlacementValidator()
        val whiteRoster = Roster(Color.WHITE, mapOf(PieceType.KING to 1, PieceType.PAWN to 1))
        val blackRoster = Roster(Color.BLACK, mapOf(PieceType.KING to 1, PieceType.PAWN to 1))
        val state = placementValidator.start(whiteRoster, blackRoster)

        val wrongTurn = placementValidator.place(state, Color.BLACK, PieceType.KING, Square.fromAlgebraic("e8")!!)
        assertIs<PlacementResult.Failure>(wrongTurn)

        val whitePlacement = assertIs<PlacementResult.Success>(
            placementValidator.place(state, Color.WHITE, PieceType.KING, Square.fromAlgebraic("e1")!!),
        )
        val outsideZone = placementValidator.place(
            whitePlacement.state,
            Color.BLACK,
            PieceType.KING,
            Square.fromAlgebraic("e5")!!,
        )
        assertIs<PlacementResult.Failure>(outsideZone)
    }

    @Test
    fun castlingRightsOnlyExistFromStandardHomeSquares() {
        val eligibleBoard = Board.fromPieces(
            Square.fromAlgebraic("e1")!! to Piece(PieceType.KING, Color.WHITE),
            Square.fromAlgebraic("h1")!! to Piece(PieceType.ROOK, Color.WHITE),
            Square.fromAlgebraic("a1")!! to Piece(PieceType.ROOK, Color.WHITE),
            Square.fromAlgebraic("e8")!! to Piece(PieceType.KING, Color.BLACK),
        )
        val eligibleRights = MoveEngine.standardCastlingRights(eligibleBoard)
        assertTrue(eligibleRights.whiteKingSide)
        assertTrue(eligibleRights.whiteQueenSide)

        val customBoard = Board.fromPieces(
            Square.fromAlgebraic("d1")!! to Piece(PieceType.KING, Color.WHITE),
            Square.fromAlgebraic("h1")!! to Piece(PieceType.ROOK, Color.WHITE),
            Square.fromAlgebraic("e8")!! to Piece(PieceType.KING, Color.BLACK),
        )
        val customRights = MoveEngine.standardCastlingRights(customBoard)
        assertFalse(customRights.whiteKingSide)
        assertFalse(customRights.whiteQueenSide)
    }

    @Test
    fun moveEngineSupportsEnPassantAndPromotion() {
        val enPassantPosition = GamePosition(
            board = Board.fromPieces(
                Square.fromAlgebraic("e1")!! to Piece(PieceType.KING, Color.WHITE),
                Square.fromAlgebraic("e8")!! to Piece(PieceType.KING, Color.BLACK),
                Square.fromAlgebraic("e5")!! to Piece(PieceType.PAWN, Color.WHITE),
                Square.fromAlgebraic("d5")!! to Piece(PieceType.PAWN, Color.BLACK),
            ),
            sideToMove = Color.WHITE,
            enPassantTarget = Square.fromAlgebraic("d6"),
        )

        val enPassantMove = MoveEngine.legalMoves(enPassantPosition, Square.fromAlgebraic("e5")!!)
            .firstOrNull { it.type == MoveType.EN_PASSANT }
        assertNotNull(enPassantMove)

        val enPassantResult = assertIs<MoveApplicationResult.Success>(
            MoveEngine.apply(enPassantPosition, MoveRequest(enPassantMove.from, enPassantMove.to)),
        )
        assertEquals(Piece(PieceType.PAWN, Color.WHITE), enPassantResult.position.board[Square.fromAlgebraic("d6")!!])
        assertEquals(null, enPassantResult.position.board[Square.fromAlgebraic("d5")!!])

        val promotionPosition = GamePosition(
            board = Board.fromPieces(
                Square.fromAlgebraic("e1")!! to Piece(PieceType.KING, Color.WHITE),
                Square.fromAlgebraic("e8")!! to Piece(PieceType.KING, Color.BLACK),
                Square.fromAlgebraic("a7")!! to Piece(PieceType.PAWN, Color.WHITE),
            ),
            sideToMove = Color.WHITE,
        )

        val promotionMoves = MoveEngine.legalMoves(promotionPosition, Square.fromAlgebraic("a7")!!)
        assertEquals(4, promotionMoves.size)
        assertTrue(promotionMoves.all { it.promotion != null })

        val promotionResult = assertIs<MoveApplicationResult.Success>(
            MoveEngine.apply(
                promotionPosition,
                MoveRequest(
                    from = Square.fromAlgebraic("a7")!!,
                    to = Square.fromAlgebraic("a8")!!,
                    promotion = PieceType.QUEEN,
                ),
            ),
        )
        assertEquals(Piece(PieceType.QUEEN, Color.WHITE), promotionResult.position.board[Square.fromAlgebraic("a8")!!])
    }

    @Test
    fun chessGameTransitionsFromDraftToPlacementToPlay() {
        val game = ChessGame()
        val roster = mapOf(
            PieceType.KING to 1,
            PieceType.PAWN to 15,
        )

        assertIs<DraftSubmissionResult.Accepted>(game.submitDraft(Color.WHITE, roster))
        val secondDraft = assertIs<DraftSubmissionResult.Accepted>(game.submitDraft(Color.BLACK, roster))
        assertIs<ChessGameState.Placing>(secondDraft.gameState)

        val whiteSquares = listOf("e1", "a1", "b1", "c1", "d1", "f1", "g1", "h1", "a2", "b2", "c2", "d2", "e2", "f2", "g2", "h2")
        val blackSquares = listOf("e8", "a8", "b8", "c8", "d8", "f8", "g8", "h8", "a7", "b7", "c7", "d7", "e7", "f7", "g7", "h7")

        for (index in 0 until 16) {
            val whiteType = if (index == 0) PieceType.KING else PieceType.PAWN
            val blackType = if (index == 0) PieceType.KING else PieceType.PAWN
            assertIs<GamePlacementResult.Accepted>(
                game.placePiece(Color.WHITE, whiteType, Square.fromAlgebraic(whiteSquares[index])!!),
            )
            val blackResult = game.placePiece(Color.BLACK, blackType, Square.fromAlgebraic(blackSquares[index])!!)
            assertIs<GamePlacementResult.Accepted>(blackResult)
        }

        val state = game.getGameState()
        val playing = assertIs<ChessGameState.Playing>(state)
        assertEquals(Color.WHITE, playing.position.sideToMove)
        assertTrue(game.getLegalMoves().isNotEmpty())
    }
}
