package com.chesspoints.app.i18n

import com.chesspoints.app.R
import com.chesspoints.engine.Color
import com.chesspoints.engine.DrawReason
import com.chesspoints.engine.DraftPurchaseValidation
import com.chesspoints.engine.DraftSubmissionError
import com.chesspoints.engine.DraftValidationError
import com.chesspoints.engine.GameMoveError
import com.chesspoints.engine.GamePlacementError
import com.chesspoints.engine.MoveRejection
import com.chesspoints.engine.PieceType
import com.chesspoints.engine.PlacementError
import com.chesspoints.engine.SetupError

/**
 * Maps engine results onto localisable text.
 *
 * The engine intentionally keeps its own English `message` strings for logs and tests;
 * the UI never shows those directly. Instead every rules outcome is translated here so
 * the player always reads rules feedback in the device language.
 */

val PieceType.nameRes: Int
    get() = when (this) {
        PieceType.PAWN -> R.string.piece_pawn
        PieceType.KNIGHT -> R.string.piece_knight
        PieceType.BISHOP -> R.string.piece_bishop
        PieceType.ROOK -> R.string.piece_rook
        PieceType.QUEEN -> R.string.piece_queen
        PieceType.KING -> R.string.piece_king
    }

fun PieceType.displayText(): UiText = UiText.Res(nameRes)

fun Color.displayText(): UiText = UiText.Res(
    when (this) {
        Color.WHITE -> R.string.color_white
        Color.BLACK -> R.string.color_black
    },
)

fun DrawReason.displayText(): UiText = UiText.Res(
    when (this) {
        DrawReason.STALEMATE -> R.string.draw_stalemate
        DrawReason.INSUFFICIENT_MATERIAL -> R.string.draw_insufficient_material
    },
)

fun DraftValidationError.displayText(): UiText = when (this) {
    is DraftValidationError.NegativePieceCount ->
        UiText.of(R.string.error_negative_piece_count, pieceType.displayText(), count)

    is DraftValidationError.PieceCountMismatch ->
        UiText.of(R.string.error_piece_count_mismatch, expected, actual)

    is DraftValidationError.KingCountMismatch ->
        UiText.of(R.string.error_king_count_mismatch, expected, actual)

    is DraftValidationError.BudgetExceeded ->
        UiText.of(R.string.error_budget_exceeded, actualCost, budget)
}

fun DraftPurchaseValidation.Rejected.displayText(): UiText = when (this) {
    is DraftPurchaseValidation.PieceLimitReached ->
        UiText.of(R.string.error_piece_limit_reached, requiredPieceCount)

    DraftPurchaseValidation.KingAlreadySelected ->
        UiText.Res(R.string.error_king_already_selected)

    is DraftPurchaseValidation.CannotCompleteArmy -> if (remainingBudget < 0) {
        UiText.of(
            R.string.error_purchase_over_budget,
            -remainingBudget,
            remainingSlots,
            minimumRequiredBudget,
        )
    } else {
        UiText.of(
            R.string.error_cannot_complete_army,
            remainingSlots,
            remainingBudget,
            minimumRequiredBudget,
        )
    }

    else -> UiText.Res(R.string.error_unknown)
}

fun PlacementError.displayText(): UiText = when (this) {
    is PlacementError.WrongTurn ->
        UiText.of(R.string.error_wrong_placement_turn, expected.displayText(), actual.displayText())

    is PlacementError.PieceNotAvailable ->
        UiText.of(R.string.error_piece_not_available, color.displayText(), pieceType.displayText())

    is PlacementError.OccupiedSquare ->
        UiText.of(R.string.error_square_occupied, square.algebraic)

    is PlacementError.OutsidePlacementZone ->
        UiText.of(
            R.string.error_outside_zone,
            square.algebraic,
            color.displayText(),
            allowedRanks.first + 1,
            allowedRanks.last + 1,
        )

    else -> UiText.Res(R.string.error_unknown)
}

fun DraftSubmissionError.displayText(): UiText = when (this) {
    is DraftSubmissionError.DuplicateSubmission ->
        UiText.of(R.string.error_duplicate_draft, color.displayText())

    is DraftSubmissionError.WrongPhase -> UiText.Res(R.string.error_wrong_phase)

    is DraftSubmissionError.InvalidDraft ->
        UiText.Joined(errors.map { it.displayText() }, separator = " ")

    else -> UiText.Res(R.string.error_unknown)
}

fun GamePlacementError.displayText(): UiText = when (this) {
    is GamePlacementError.WrongPhase -> UiText.Res(R.string.error_wrong_phase)
    is GamePlacementError.InvalidPlacement -> error.displayText()
}

fun MoveRejection.displayText(): UiText = when (this) {
    is MoveRejection.NoPieceAtSource ->
        UiText.of(R.string.error_no_piece_at_source, square.algebraic)

    is MoveRejection.WrongColorTurn ->
        UiText.of(R.string.error_wrong_color_turn, expected.displayText(), actual.displayText())

    is MoveRejection.IllegalMove ->
        UiText.of(R.string.error_illegal_move, request.from.algebraic, request.to.algebraic)
}

fun GameMoveError.displayText(): UiText = when (this) {
    is GameMoveError.WrongPhase -> UiText.Res(R.string.error_wrong_phase)
    is GameMoveError.IllegalMove -> rejection.displayText()
}

fun SetupError.displayText(): UiText = when (this) {
    is SetupError.WrongTurn ->
        UiText.of(R.string.error_wrong_placement_turn, expected.displayText(), actual.displayText())

    is SetupError.InvalidPurchase -> reason.displayText()
    is SetupError.InvalidSquare -> error.displayText()
    is SetupError.WrongPhase -> UiText.Res(R.string.error_wrong_phase)
}
