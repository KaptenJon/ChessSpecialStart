package com.chesspoints.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DraftValidatorTest {
    @Test
    fun acceptsRostersThatExactlyMeetBudgetPieceCountAndKingRequirements() {
        val standardArmy = mapOf(
            PieceType.KING to 1,
            PieceType.QUEEN to 1,
            PieceType.ROOK to 2,
            PieceType.BISHOP to 2,
            PieceType.KNIGHT to 2,
            PieceType.PAWN to 8,
        )

        val result = DraftValidator.validate(Color.WHITE, standardArmy)

        val valid = assertIs<DraftValidationResult.Valid>(result)
        assertEquals(DEFAULT_ARMY_SIZE, valid.roster.totalPieces)
        assertEquals(DEFAULT_BUDGET, valid.roster.totalPointCost)
        assertEquals(1, valid.roster.countOf(PieceType.KING))
    }

    @Test
    fun rejectsRosterThatIsExactlyOnePointOverBudgetWhenRulesBudgetIsLoweredByOne() {
        val standardArmy = mapOf(
            PieceType.KING to 1,
            PieceType.QUEEN to 1,
            PieceType.ROOK to 2,
            PieceType.BISHOP to 2,
            PieceType.KNIGHT to 2,
            PieceType.PAWN to 8,
        )

        val result = DraftValidator.validate(
            color = Color.BLACK,
            pieceCounts = standardArmy,
            rules = DraftRules(budget = DEFAULT_BUDGET - 1),
        )

        val invalid = assertIs<DraftValidationResult.Invalid>(result)
        val budgetError = assertIs<DraftValidationError.BudgetExceeded>(
            invalid.errors.single { it is DraftValidationError.BudgetExceeded },
        )
        assertEquals(DEFAULT_BUDGET - 1, budgetError.budget)
        assertEquals(DEFAULT_BUDGET, budgetError.actualCost)
    }

    @Test
    fun rejectsWrongKingCountsAndWrongTotalPieceCounts() {
        val zeroKings = DraftValidator.validate(
            color = Color.WHITE,
            pieceCounts = mapOf(
                PieceType.QUEEN to 1,
                PieceType.ROOK to 2,
                PieceType.BISHOP to 2,
                PieceType.KNIGHT to 2,
                PieceType.PAWN to 9,
            ),
        )
        val twoKings = DraftValidator.validate(
            color = Color.WHITE,
            pieceCounts = mapOf(
                PieceType.KING to 2,
                PieceType.QUEEN to 1,
                PieceType.ROOK to 2,
                PieceType.BISHOP to 2,
                PieceType.KNIGHT to 2,
                PieceType.PAWN to 7,
            ),
        )
        val fifteenPieces = DraftValidator.validate(
            color = Color.WHITE,
            pieceCounts = mapOf(
                PieceType.KING to 1,
                PieceType.QUEEN to 1,
                PieceType.ROOK to 2,
                PieceType.BISHOP to 2,
                PieceType.KNIGHT to 2,
                PieceType.PAWN to 7,
            ),
        )
        val seventeenPieces = DraftValidator.validate(
            color = Color.WHITE,
            pieceCounts = mapOf(
                PieceType.KING to 1,
                PieceType.QUEEN to 1,
                PieceType.ROOK to 2,
                PieceType.BISHOP to 2,
                PieceType.KNIGHT to 2,
                PieceType.PAWN to 9,
            ),
        )

        assertInvalidWith< DraftValidationError.KingCountMismatch>(zeroKings)
        assertInvalidWith< DraftValidationError.KingCountMismatch>(twoKings)
        assertInvalidWith< DraftValidationError.PieceCountMismatch>(fifteenPieces)
        assertInvalidWith< DraftValidationError.PieceCountMismatch>(seventeenPieces)
    }

    @Test
    fun acceptsUnusualButLegalCompositionWhenItStillFitsBudgetAndArmySize() {
        val doubleQueenArmy = mapOf(
            PieceType.KING to 1,
            PieceType.QUEEN to 2,
            PieceType.ROOK to 1,
            PieceType.BISHOP to 1,
            PieceType.KNIGHT to 1,
            PieceType.PAWN to 10,
        )

        val result = DraftValidator.validate(Color.BLACK, doubleQueenArmy)

        val valid = assertIs<DraftValidationResult.Valid>(result)
        assertEquals(DEFAULT_ARMY_SIZE, valid.roster.totalPieces)
        assertEquals(DEFAULT_BUDGET, valid.roster.totalPointCost)
        assertEquals(2, valid.roster.countOf(PieceType.QUEEN))
        assertEquals(10, valid.roster.countOf(PieceType.PAWN))
    }

    @Test
    fun candidateGuardReservesEnoughBudgetForRemainingSlots() {
        val nearlyFull = mapOf(
            PieceType.KING to 1,
            PieceType.QUEEN to 2,
            PieceType.ROOK to 1,
            PieceType.BISHOP to 0,
            PieceType.KNIGHT to 0,
            PieceType.PAWN to 11,
        )

        assertTrue(DraftValidator.canAddPiece(nearlyFull, PieceType.PAWN))
        assertTrue(!DraftValidator.canAddPiece(nearlyFull, PieceType.QUEEN))
    }

    @Test
    fun candidateGuardAccountsForAnUnboughtMandatoryKing() {
        val fifteenNonKings = mapOf(
            PieceType.KING to 0,
            PieceType.QUEEN to 0,
            PieceType.ROOK to 0,
            PieceType.BISHOP to 0,
            PieceType.KNIGHT to 0,
            PieceType.PAWN to 15,
        )

        assertTrue(!DraftValidator.canAddPiece(fifteenNonKings, PieceType.PAWN))
        assertTrue(DraftValidator.canAddPiece(fifteenNonKings, PieceType.KING))
    }

    @Test
    fun additionValidationExplainsWhyAHighValueCandidateIsRejected() {
        val nearlyFull = mapOf(
            PieceType.KING to 1,
            PieceType.QUEEN to 2,
            PieceType.ROOK to 1,
            PieceType.BISHOP to 0,
            PieceType.KNIGHT to 0,
            PieceType.PAWN to 11,
        )

        val rejected = assertIs<DraftPurchaseValidation.CannotCompleteArmy>(
            DraftValidator.validateAddition(nearlyFull, PieceType.QUEEN),
        )
        assertEquals(0, rejected.remainingSlots)
        assertEquals(-4, rejected.remainingBudget)
        assertEquals(0, rejected.minimumRequiredBudget)
        assertTrue(rejected.message.contains("exceeds the budget by 4 points"))
    }
}

private inline fun <reified T : DraftValidationError> assertInvalidWith(
    result: DraftValidationResult,
) {
    val invalid = assertIs<DraftValidationResult.Invalid>(result)
    assertTrue(invalid.errors.any { it is T })
}
