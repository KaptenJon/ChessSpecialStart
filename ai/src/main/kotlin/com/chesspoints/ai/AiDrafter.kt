package com.chesspoints.ai

import com.chesspoints.engine.Color
import com.chesspoints.engine.DEFAULT_BUDGET
import com.chesspoints.engine.DraftRules
import com.chesspoints.engine.DraftValidationResult
import com.chesspoints.engine.DraftValidator
import com.chesspoints.engine.PieceType
import com.chesspoints.engine.Roster
import kotlin.random.Random

class AiDrafter(
    private val defaultBudget: Int = DEFAULT_BUDGET,
) {
    fun draft(
        color: Color,
        rules: DraftRules = DraftRules(budget = defaultBudget),
        random: Random = Random.Default,
    ): Roster {
        val orderedTemplates = draftTemplates.shuffled(random)
        for (template in orderedTemplates) {
            val candidate = template.toRoster(color)
            val validation = DraftValidator.validate(color, candidate.counts, rules)
            if (validation is DraftValidationResult.Valid) {
                return validation.roster
            }
        }

        val generatedFallback = buildAdaptiveFallback(color, rules, random)
        val generatedValidation = DraftValidator.validate(color, generatedFallback.counts, rules)
        if (generatedValidation is DraftValidationResult.Valid) {
            return generatedValidation.roster
        }

        val standardFallback = standardTemplate.toRoster(color)
        val standardValidation = DraftValidator.validate(color, standardFallback.counts, rules)
        if (standardValidation is DraftValidationResult.Valid) {
            return standardValidation.roster
        }

        error("Unable to generate a valid AI roster for budget=${rules.budget} and size=${rules.requiredPieceCount}")
    }

    private fun buildAdaptiveFallback(
        color: Color,
        rules: DraftRules,
        random: Random,
    ): Roster {
        val counts = PieceType.entries.associateWith { 0 }.toMutableMap()
        counts[PieceType.KING] = 1
        counts[PieceType.PAWN] = rules.requiredPieceCount - 1

        var spareBudget = rules.budget - (rules.requiredPieceCount - 1)
        val upgrades = listOf(
            Upgrade(PieceType.PAWN, PieceType.KNIGHT, 2),
            Upgrade(PieceType.PAWN, PieceType.BISHOP, 2),
            Upgrade(PieceType.PAWN, PieceType.ROOK, 4),
            Upgrade(PieceType.PAWN, PieceType.QUEEN, 8),
        ).shuffled(random)

        while (counts.getValue(PieceType.PAWN) > 0) {
            val upgrade = upgrades.firstOrNull { it.deltaCost <= spareBudget } ?: break
            counts[upgrade.from] = counts.getValue(upgrade.from) - 1
            counts[upgrade.to] = counts.getValue(upgrade.to) + 1
            spareBudget -= upgrade.deltaCost
        }

        return Roster(color, counts)
    }

    private data class DraftTemplate(
        val counts: Map<PieceType, Int>,
    ) {
        fun toRoster(color: Color): Roster = Roster(color, PieceType.entries.associateWith { counts[it] ?: 0 })
    }

    private data class Upgrade(
        val from: PieceType,
        val to: PieceType,
        val deltaCost: Int,
    )

    companion object {
        private val standardTemplate = DraftTemplate(
            counts = mapOf(
                PieceType.KING to 1,
                PieceType.QUEEN to 1,
                PieceType.ROOK to 2,
                PieceType.BISHOP to 2,
                PieceType.KNIGHT to 2,
                PieceType.PAWN to 8,
            ),
        )

        private val draftTemplates = listOf(
            standardTemplate,
            DraftTemplate(
                counts = mapOf(
                    PieceType.KING to 1,
                    PieceType.QUEEN to 2,
                    PieceType.ROOK to 1,
                    PieceType.BISHOP to 1,
                    PieceType.KNIGHT to 1,
                    PieceType.PAWN to 10,
                ),
            ),
            DraftTemplate(
                counts = mapOf(
                    PieceType.KING to 1,
                    PieceType.QUEEN to 1,
                    PieceType.ROOK to 3,
                    PieceType.BISHOP to 1,
                    PieceType.KNIGHT to 1,
                    PieceType.PAWN to 9,
                ),
            ),
            DraftTemplate(
                counts = mapOf(
                    PieceType.KING to 1,
                    PieceType.QUEEN to 1,
                    PieceType.ROOK to 1,
                    PieceType.BISHOP to 2,
                    PieceType.KNIGHT to 4,
                    PieceType.PAWN to 7,
                ),
            ),
            DraftTemplate(
                counts = mapOf(
                    PieceType.KING to 1,
                    PieceType.ROOK to 4,
                    PieceType.BISHOP to 2,
                    PieceType.KNIGHT to 1,
                    PieceType.PAWN to 8,
                ),
            ),
            DraftTemplate(
                counts = mapOf(
                    PieceType.KING to 1,
                    PieceType.QUEEN to 1,
                    PieceType.ROOK to 2,
                    PieceType.BISHOP to 4,
                    PieceType.PAWN to 8,
                ),
            ),
        )
    }
}
