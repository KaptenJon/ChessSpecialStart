package com.chesspoints.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoveEngineTest {
    @Test
    fun generatesExpectedRookMovesOnASparseBoard() {
        val position = positionWith(
            sideToMove = Color.WHITE,
            "b1" to Piece(PieceType.KING, Color.WHITE),
            "a8" to Piece(PieceType.KING, Color.BLACK),
            "d4" to Piece(PieceType.ROOK, Color.WHITE),
        )

        assertMoveTargets(
            position = position,
            from = "d4",
            expected = setOf("a4", "b4", "c4", "e4", "f4", "g4", "h4", "d1", "d2", "d3", "d5", "d6", "d7", "d8"),
        )
    }

    @Test
    fun generatesExpectedBishopMovesOnASparseBoard() {
        val position = positionWith(
            sideToMove = Color.WHITE,
            "b1" to Piece(PieceType.KING, Color.WHITE),
            "a8" to Piece(PieceType.KING, Color.BLACK),
            "d4" to Piece(PieceType.BISHOP, Color.WHITE),
        )

        assertMoveTargets(
            position = position,
            from = "d4",
            expected = setOf("a1", "b2", "c3", "e5", "f6", "g7", "h8", "a7", "b6", "c5", "e3", "f2", "g1"),
        )
    }

    @Test
    fun generatesExpectedQueenMovesOnASparseBoard() {
        val position = positionWith(
            sideToMove = Color.WHITE,
            "b1" to Piece(PieceType.KING, Color.WHITE),
            "a8" to Piece(PieceType.KING, Color.BLACK),
            "d4" to Piece(PieceType.QUEEN, Color.WHITE),
        )

        assertMoveTargets(
            position = position,
            from = "d4",
            expected = setOf(
                "a4", "b4", "c4", "e4", "f4", "g4", "h4",
                "d1", "d2", "d3", "d5", "d6", "d7", "d8",
                "a1", "b2", "c3", "e5", "f6", "g7", "h8",
                "a7", "b6", "c5", "e3", "f2", "g1",
            ),
        )
    }

    @Test
    fun generatesExpectedKnightMovesOnASparseBoard() {
        val position = positionWith(
            sideToMove = Color.WHITE,
            "a1" to Piece(PieceType.KING, Color.WHITE),
            "h8" to Piece(PieceType.KING, Color.BLACK),
            "d4" to Piece(PieceType.KNIGHT, Color.WHITE),
        )

        assertMoveTargets(
            position = position,
            from = "d4",
            expected = setOf("b3", "b5", "c2", "c6", "e2", "e6", "f3", "f5"),
        )
    }

    @Test
    fun generatesExpectedKingMovesOnASparseBoard() {
        val position = positionWith(
            sideToMove = Color.WHITE,
            "d4" to Piece(PieceType.KING, Color.WHITE),
            "h8" to Piece(PieceType.KING, Color.BLACK),
        )

        assertMoveTargets(
            position = position,
            from = "d4",
            expected = setOf("c3", "c4", "c5", "d3", "d5", "e3", "e4", "e5"),
        )
    }

    @Test
    fun kingCannotMoveIntoAnAttackedSquare() {
        val position = positionWith(
            sideToMove = Color.WHITE,
            "e1" to Piece(PieceType.KING, Color.WHITE),
            "a8" to Piece(PieceType.KING, Color.BLACK),
            "e8" to Piece(PieceType.ROOK, Color.BLACK),
        )

        val kingMoves = MoveEngine.legalMoves(position, sq("e1")).map { it.to.algebraic }.toSet()

        assertFalse("e2" in kingMoves)
        assertTrue("f2" in kingMoves)
        assertTrue("d2" in kingMoves)
    }

    @Test
    fun aMoveThatLeavesTheMovingSideKingInCheckIsNotLegal() {
        val position = positionWith(
            sideToMove = Color.BLACK,
            "e8" to Piece(PieceType.KING, Color.BLACK),
            "a7" to Piece(PieceType.PAWN, Color.BLACK),
            "e1" to Piece(PieceType.ROOK, Color.WHITE),
            "a1" to Piece(PieceType.KING, Color.WHITE),
        )

        val illegal = MoveEngine.apply(position, MoveRequest(from = sq("a8"), to = sq("a7")))
        assertIs<MoveApplicationResult.Illegal>(illegal)
    }

    @Test
    fun generatesExpectedPawnMovesOnASparseBoard() {
        val position = positionWith(
            sideToMove = Color.WHITE,
            "h1" to Piece(PieceType.KING, Color.WHITE),
            "h8" to Piece(PieceType.KING, Color.BLACK),
            "d2" to Piece(PieceType.PAWN, Color.WHITE),
            "c3" to Piece(PieceType.BISHOP, Color.BLACK),
            "e3" to Piece(PieceType.KNIGHT, Color.BLACK),
        )

        assertMoveTargets(
            position = position,
            from = "d2",
            expected = setOf("c3", "d3", "d4", "e3"),
        )
    }

    @Test
    fun detectsCheckWithoutMisclassifyingItAsMate() {
        val position = positionWith(
            sideToMove = Color.BLACK,
            "a1" to Piece(PieceType.KING, Color.WHITE),
            "e7" to Piece(PieceType.QUEEN, Color.WHITE),
            "e8" to Piece(PieceType.KING, Color.BLACK),
        )

        val status = MoveEngine.evaluate(position)

        val check = assertIs<PositionStatus.Check>(status)
        assertEquals(Color.BLACK, check.checkedColor)
        assertTrue(MoveEngine.isInCheck(position, Color.BLACK))
    }

    @Test
    fun whileInCheckOnlyMovesThatResolveCheckAreLegal() {
        val position = positionWith(
            sideToMove = Color.BLACK,
            "a1" to Piece(PieceType.KING, Color.WHITE),
            "e1" to Piece(PieceType.ROOK, Color.WHITE),
            "e8" to Piece(PieceType.KING, Color.BLACK),
            "a7" to Piece(PieceType.PAWN, Color.BLACK),
        )

        assertTrue(MoveEngine.isInCheck(position, Color.BLACK))
        assertTrue(
            MoveEngine.legalMoves(position).none {
                it.piece.type == PieceType.PAWN && it.from == sq("a7")
            },
        )
        assertIs<MoveApplicationResult.Illegal>(
            MoveEngine.apply(position, MoveRequest(from = sq("a7"), to = sq("a6"))),
        )
    }

    @Test
    fun detectsCheckmate() {
        val position = positionWith(
            sideToMove = Color.BLACK,
            "f6" to Piece(PieceType.KING, Color.WHITE),
            "g7" to Piece(PieceType.QUEEN, Color.WHITE),
            "h8" to Piece(PieceType.KING, Color.BLACK),
        )

        val status = MoveEngine.evaluate(position)

        val checkmate = assertIs<PositionStatus.Checkmate>(status)
        assertEquals(Color.WHITE, checkmate.winner)
    }

    @Test
    fun applyingTheMatingMoveReportsTheWinningOutcome() {
        val position = positionWith(
            sideToMove = Color.WHITE,
            "f6" to Piece(PieceType.KING, Color.WHITE),
            "g6" to Piece(PieceType.QUEEN, Color.WHITE),
            "h8" to Piece(PieceType.KING, Color.BLACK),
        )

        val result = assertIs<MoveApplicationResult.Success>(
            MoveEngine.apply(position, MoveRequest(from = sq("g6"), to = sq("g7"))),
        )

        val checkmate = assertIs<PositionStatus.Checkmate>(result.status)
        assertEquals(Color.WHITE, checkmate.winner)
        assertTrue(MoveEngine.legalMoves(result.position).isEmpty())
    }

    @Test
    fun checkmateLeavesTheCheckedSideWithNoLegalMoves() {
        val position = positionWith(
            sideToMove = Color.BLACK,
            "f6" to Piece(PieceType.KING, Color.WHITE),
            "g7" to Piece(PieceType.QUEEN, Color.WHITE),
            "h8" to Piece(PieceType.KING, Color.BLACK),
        )

        assertIs<PositionStatus.Checkmate>(MoveEngine.evaluate(position))
        assertTrue(MoveEngine.legalMoves(position).isEmpty())
    }

    @Test
    fun detectsStalemate() {
        val position = positionWith(
            sideToMove = Color.BLACK,
            "f7" to Piece(PieceType.KING, Color.WHITE),
            "g6" to Piece(PieceType.QUEEN, Color.WHITE),
            "h8" to Piece(PieceType.KING, Color.BLACK),
        )

        val status = MoveEngine.evaluate(position)

        val draw = assertIs<PositionStatus.Draw>(status)
        assertEquals(DrawReason.STALEMATE, draw.reason)
        assertFalse(MoveEngine.isInCheck(position, Color.BLACK))
    }

    @Test
    fun allowsCastlingOnlyWhenStandardLayoutRightsExistAndPathIsSafe() {
        val castleEligibleBoard = Board.fromPieces(
            sq("e1") to Piece(PieceType.KING, Color.WHITE),
            sq("h1") to Piece(PieceType.ROOK, Color.WHITE),
            sq("e8") to Piece(PieceType.KING, Color.BLACK),
        )
        val eligiblePosition = GamePosition(
            board = castleEligibleBoard,
            sideToMove = Color.WHITE,
            castlingRights = MoveEngine.standardCastlingRights(castleEligibleBoard),
        )

        val standardHomeMoves = MoveEngine.legalMoves(eligiblePosition, sq("e1"))
        assertTrue(standardHomeMoves.any { it.type == MoveType.KING_SIDE_CASTLE && it.to == sq("g1") })

        val customLayoutBoard = Board.fromPieces(
            sq("d1") to Piece(PieceType.KING, Color.WHITE),
            sq("h1") to Piece(PieceType.ROOK, Color.WHITE),
            sq("e8") to Piece(PieceType.KING, Color.BLACK),
        )
        val customLayoutPosition = GamePosition(
            board = customLayoutBoard,
            sideToMove = Color.WHITE,
            castlingRights = MoveEngine.standardCastlingRights(customLayoutBoard),
        )

        assertTrue(MoveEngine.legalMoves(customLayoutPosition, sq("d1")).none { it.type == MoveType.KING_SIDE_CASTLE })
    }

    @Test
    fun enPassantDependsOnTheCurrentTargetSquare() {
        val basePieces = arrayOf(
            sq("e1") to Piece(PieceType.KING, Color.WHITE),
            sq("e8") to Piece(PieceType.KING, Color.BLACK),
            sq("e5") to Piece(PieceType.PAWN, Color.WHITE),
            sq("d5") to Piece(PieceType.PAWN, Color.BLACK),
        )
        val withTarget = GamePosition(
            board = Board.fromPieces(*basePieces),
            sideToMove = Color.WHITE,
            enPassantTarget = sq("d6"),
        )
        val withoutTarget = withTarget.copy(enPassantTarget = null)

        assertTrue(MoveEngine.legalMoves(withTarget, sq("e5")).any { it.type == MoveType.EN_PASSANT && it.to == sq("d6") })
        assertTrue(MoveEngine.legalMoves(withoutTarget, sq("e5")).none { it.type == MoveType.EN_PASSANT })
    }

    @Test
    fun promotionMoveRequiresCallerToSpecifyPromotionPiece() {
        val position = positionWith(
            sideToMove = Color.WHITE,
            "e1" to Piece(PieceType.KING, Color.WHITE),
            "e8" to Piece(PieceType.KING, Color.BLACK),
            "a7" to Piece(PieceType.PAWN, Color.WHITE),
        )

        val legalMoves = MoveEngine.legalMoves(position, sq("a7"))
        assertEquals(setOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT), legalMoves.map { it.promotion }.toSet())

        val missingPromotion = MoveEngine.apply(position, MoveRequest(from = sq("a7"), to = sq("a8")))
        assertIs<MoveApplicationResult.Illegal>(missingPromotion)

        val promoted = assertIs<MoveApplicationResult.Success>(
            MoveEngine.apply(position, MoveRequest(from = sq("a7"), to = sq("a8"), promotion = PieceType.ROOK)),
        )
        assertEquals(Piece(PieceType.ROOK, Color.WHITE), promoted.position.board[sq("a8")])
        assertNull(promoted.position.board[sq("a7")])
    }

    @Test
    fun aKingCannotMoveIntoCheckFromAnEnemyRook() {
        val position = positionWith(
            sideToMove = Color.WHITE,
            "e1" to Piece(PieceType.KING, Color.WHITE),
            "e8" to Piece(PieceType.KING, Color.BLACK),
            "a2" to Piece(PieceType.ROOK, Color.BLACK),
        )

        assertTrue(MoveEngine.legalMoves(position, sq("e1")).none { it.to == sq("e2") })
    }

    @Test
    fun capturingTheKingIsNeverA_LegalMove() {
        val position = positionWith(
            sideToMove = Color.WHITE,
            "e1" to Piece(PieceType.KING, Color.WHITE),
            "e8" to Piece(PieceType.KING, Color.BLACK),
            "e7" to Piece(PieceType.QUEEN, Color.WHITE),
        )

        val result = assertIs<MoveApplicationResult.Illegal>(
            MoveEngine.apply(position, MoveRequest(sq("e7"), sq("e8"))),
        )

        assertIs<MoveRejection.IllegalMove>(result.reason)
        assertEquals(Piece(PieceType.KING, Color.BLACK), position.board[sq("e8")])
    }

    @Test
    fun aKingCannotCaptureAnAdjacentKing() {
        val position = positionWith(
            sideToMove = Color.WHITE,
            "e1" to Piece(PieceType.KING, Color.WHITE),
            "e2" to Piece(PieceType.KING, Color.BLACK),
        )

        assertTrue(MoveEngine.legalMoves(position, sq("e1")).none { it.to == sq("e2") })
    }

}

private fun assertMoveTargets(position: GamePosition, from: String, expected: Set<String>) {
    assertEquals(expected, MoveEngine.legalMoves(position, sq(from)).map { it.to.algebraic }.toSet())
}

private fun positionWith(
    sideToMove: Color,
    vararg placements: Pair<String, Piece>,
): GamePosition = GamePosition(
    board = Board.fromPieces(*placements.map { (square, piece) -> sq(square) to piece }.toTypedArray()),
    sideToMove = sideToMove,
)

private fun sq(algebraic: String): Square = Square.fromAlgebraic(algebraic)!!
