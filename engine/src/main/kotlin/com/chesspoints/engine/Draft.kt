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

object DraftValidator {
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
