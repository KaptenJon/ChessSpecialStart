package com.chesspoints.engine

data class DraftRules(
    val budget: Int = DEFAULT_BUDGET,
    val requiredPieceCount: Int = DEFAULT_ARMY_SIZE,
) {
    init {
        require(budget >= 0) { "budget must be non-negative" }
        require(requiredPieceCount > 0) { "requiredPieceCount must be positive" }
    }
}

data class Roster(
    val color: Color,
    private val countsByType: Map<PieceType, Int>,
) {
    val counts: Map<PieceType, Int> = PieceType.entries.associateWith { countsByType[it] ?: 0 }

    fun countOf(type: PieceType): Int = counts.getValue(type)

    val totalPieces: Int
        get() = counts.values.sum()

    val totalPointCost: Int
        get() = counts.entries.sumOf { (type, count) -> type.pointValue * count }

    fun contains(type: PieceType): Boolean = countOf(type) > 0

    internal fun mutableCounts(): MutableMap<PieceType, Int> = counts.toMutableMap()
}

sealed interface DraftValidationError {
    val message: String

    data class NegativePieceCount(
        val pieceType: PieceType,
        val count: Int,
    ) : DraftValidationError {
        override val message: String = "${pieceType.name} count cannot be negative ($count)"
    }

    data class PieceCountMismatch(
        val expected: Int,
        val actual: Int,
    ) : DraftValidationError {
        override val message: String = "Expected exactly $expected total pieces but received $actual"
    }

    data class KingCountMismatch(
        val expected: Int,
        val actual: Int,
    ) : DraftValidationError {
        override val message: String = "Expected exactly $expected king but received $actual"
    }

    data class BudgetExceeded(
        val budget: Int,
        val actualCost: Int,
    ) : DraftValidationError {
        override val message: String = "Roster costs $actualCost points which exceeds the $budget point budget"
    }
}

sealed interface DraftValidationResult {
    data class Valid(val roster: Roster) : DraftValidationResult
    data class Invalid(val errors: List<DraftValidationError>) : DraftValidationResult
}

sealed interface DraftPurchaseValidation {
    data object Allowed : DraftPurchaseValidation

    sealed interface Rejected : DraftPurchaseValidation {
        val message: String
    }

    data class PieceLimitReached(
        val requiredPieceCount: Int,
    ) : Rejected {
        override val message: String = "The army already has $requiredPieceCount pieces."
    }

    data object KingAlreadySelected : Rejected {
        override val message: String = "Only one king is allowed."
    }

    data class CannotCompleteArmy(
        val remainingSlots: Int,
        val remainingBudget: Int,
        val minimumRequiredBudget: Int,
    ) : Rejected {
        override val message: String =
            if (remainingBudget < 0) {
                "This purchase exceeds the budget by ${-remainingBudget} points; " +
                    "$remainingSlots slots would still need at least $minimumRequiredBudget points."
            } else {
                "This purchase would leave $remainingSlots slots but only $remainingBudget points; " +
                    "at least $minimumRequiredBudget points are needed to complete the army."
            }
    }
}

object DraftValidator {
    fun validateAddition(
        pieceCounts: Map<PieceType, Int>,
        pieceType: PieceType,
        rules: DraftRules = DraftRules(),
    ): DraftPurchaseValidation {
        val normalizedCounts = PieceType.entries.associateWith { pieceCounts[it] ?: 0 }
        val currentTotal = normalizedCounts.values.sum()
        if (currentTotal >= rules.requiredPieceCount) {
            return DraftPurchaseValidation.PieceLimitReached(rules.requiredPieceCount)
        }
        if (pieceType == PieceType.KING && normalizedCounts.getValue(PieceType.KING) >= 1) {
            return DraftPurchaseValidation.KingAlreadySelected
        }

        val proposedCounts = normalizedCounts.toMutableMap().apply {
            this[pieceType] = getValue(pieceType) + 1
        }
        val remainingSlots = rules.requiredPieceCount - currentTotal - 1
        val proposedCost = proposedCounts.entries.sumOf { (type, count) -> type.pointValue * count }
        val kingStillNeeded = proposedCounts.getValue(PieceType.KING) == 0
        if (kingStillNeeded && remainingSlots == 0) {
            return DraftPurchaseValidation.CannotCompleteArmy(
                remainingSlots = remainingSlots,
                remainingBudget = rules.budget - proposedCost,
                minimumRequiredBudget = 0,
            )
        }

        val cheapestNonKingCost = PieceType.entries
            .filter { it != PieceType.KING }
            .minOf { it.pointValue }
        val minimumCompletionCost =
            if (kingStillNeeded) {
                (remainingSlots - 1).coerceAtLeast(0) * cheapestNonKingCost
            } else {
                remainingSlots * cheapestNonKingCost
            }
        val remainingBudget = rules.budget - proposedCost
        if (remainingBudget < minimumCompletionCost) {
            return DraftPurchaseValidation.CannotCompleteArmy(
                remainingSlots = remainingSlots,
                remainingBudget = remainingBudget,
                minimumRequiredBudget = minimumCompletionCost,
            )
        }
        return DraftPurchaseValidation.Allowed
    }

    /**
     * Returns whether adding one piece still leaves a path to a valid roster.
     *
     * The remaining slots are conservatively priced at the cheapest legal
     * pieces, while reserving the mandatory king slot when it has not yet
     * been bought.
     */
    fun canAddPiece(
        pieceCounts: Map<PieceType, Int>,
        pieceType: PieceType,
        rules: DraftRules = DraftRules(),
    ): Boolean {
        return validateAddition(pieceCounts, pieceType, rules) is DraftPurchaseValidation.Allowed
    }

    fun validate(
        color: Color,
        pieceCounts: Map<PieceType, Int>,
        rules: DraftRules = DraftRules(),
    ): DraftValidationResult {
        val normalizedCounts = PieceType.entries.associateWith { pieceCounts[it] ?: 0 }
        val errors = mutableListOf<DraftValidationError>()

        normalizedCounts.forEach { (type, count) ->
            if (count < 0) {
                errors += DraftValidationError.NegativePieceCount(type, count)
            }
        }

        val totalPieces = normalizedCounts.values.sum()
        if (totalPieces != rules.requiredPieceCount) {
            errors += DraftValidationError.PieceCountMismatch(rules.requiredPieceCount, totalPieces)
        }

        val kingCount = normalizedCounts.getValue(PieceType.KING)
        if (kingCount != 1) {
            errors += DraftValidationError.KingCountMismatch(1, kingCount)
        }

        val totalCost = normalizedCounts.entries.sumOf { (type, count) -> type.pointValue * count }
        if (totalCost > rules.budget) {
            errors += DraftValidationError.BudgetExceeded(rules.budget, totalCost)
        }

        return if (errors.isEmpty()) {
            DraftValidationResult.Valid(Roster(color, normalizedCounts))
        } else {
            DraftValidationResult.Invalid(errors)
        }
    }
}
