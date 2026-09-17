package com.chesspoints.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DraftPurchaseValidationTest {
    @Test
    fun allowsAnAffordableAdditionThatLeavesACompletableRoster() {
        val counts = counts(PieceType.KING to 1, PieceType.PAWN to 14)

        assertEquals(
            DraftPurchaseValidation.Allowed,
            DraftValidator.validateAddition(counts, PieceType.QUEEN),
        )
        assertTrue(DraftValidator.canAddPiece(counts, PieceType.QUEEN))
    }

    @Test
    fun rejectsAddingASecondKingWithAnActionableResult() {
        val result = DraftValidator.validateAddition(
            counts(PieceType.KING to 1, PieceType.PAWN to 1),
            PieceType.KING,
        )

        assertIs<DraftPurchaseValidation.KingAlreadySelected>(result)
        assertEquals("Only one king is allowed.", result.message)
    }

    @Test
    fun rejectsAnyAdditionOnceTheArmyIsFull() {
        val result = DraftValidator.validateAddition(
            counts(PieceType.KING to 1, PieceType.PAWN to 15),
            PieceType.QUEEN,
        )

        val rejected = assertIs<DraftPurchaseValidation.PieceLimitReached>(result)
        assertEquals(DEFAULT_ARMY_SIZE, rejected.requiredPieceCount)
        assertEquals("The army already has 16 pieces.", rejected.message)
    }

    @Test
    fun explainsWhenAnExpensiveAdditionMakesCompletionImpossible() {
        val result = DraftValidator.validateAddition(
            counts(
                PieceType.KING to 1,
                PieceType.QUEEN to 2,
                PieceType.ROOK to 1,
                PieceType.PAWN to 11,
            ),
            PieceType.QUEEN,
        )

        val rejected = assertIs<DraftPurchaseValidation.CannotCompleteArmy>(result)
        assertEquals(0, rejected.remainingSlots)
        assertEquals(-4, rejected.remainingBudget)
        assertEquals(0, rejected.minimumRequiredBudget)
        assertTrue(rejected.message.contains("exceeds the budget by 4 points"))
        assertTrue(!DraftValidator.canAddPiece(
            counts(
                PieceType.KING to 1,
                PieceType.QUEEN to 2,
                PieceType.ROOK to 1,
                PieceType.PAWN to 11,
            ),
            PieceType.QUEEN,
        ))
    }

    private fun counts(vararg entries: Pair<PieceType, Int>): Map<PieceType, Int> =
        entries.toMap()
}
