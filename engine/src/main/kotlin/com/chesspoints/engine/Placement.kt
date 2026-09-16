package com.chesspoints.engine

data class PlacementZone(val ranks: IntRange) {
    init {
        require(!ranks.isEmpty()) { "placement zone cannot be empty" }
        require(ranks.first in 0..7 && ranks.last in 0..7) { "placement zone ranks must stay within 0..7" }
    }

    fun contains(square: Square): Boolean = square.rank in ranks
}

data class PlacementRules(
    val whiteZone: PlacementZone = PlacementZone(0..1),
    val blackZone: PlacementZone = PlacementZone(6..7),
    val firstPlayer: Color = Color.WHITE,
    val firstMoverInPlay: Color = Color.WHITE,
) {
    fun zoneFor(color: Color): PlacementZone = if (color == Color.WHITE) whiteZone else blackZone
}

data class PlacementState(
    val board: Board,
    val rosters: Map<Color, Roster>,
    private val remainingByColor: Map<Color, Map<PieceType, Int>>,
    val sideToPlace: Color,
    val rules: PlacementRules,
) {
    fun remainingPieces(color: Color): Map<PieceType, Int> = remainingByColor.getValue(color)

    fun remainingCount(color: Color, pieceType: PieceType): Int = remainingPieces(color).getValue(pieceType)

    fun isReadyToPlay(): Boolean = Color.entries.all { color ->
        remainingPieces(color).values.sum() == 0
    }
}

sealed interface PlacementError {
    val message: String

    data class WrongTurn(
        val expected: Color,
        val actual: Color,
    ) : PlacementError {
        override val message: String = "It is $expected's placement turn, not $actual's"
    }

    data class PieceNotAvailable(
        val color: Color,
        val pieceType: PieceType,
    ) : PlacementError {
        override val message: String = "$color has no remaining $pieceType to place"
    }

    data class OccupiedSquare(val square: Square) : PlacementError {
        override val message: String = "Square ${square.algebraic} is already occupied"
    }

    data class OutsidePlacementZone(
        val color: Color,
        val square: Square,
        val allowedRanks: IntRange,
    ) : PlacementError {
        override val message: String =
            "Square ${square.algebraic} is outside $color's placement zone (ranks ${allowedRanks.first + 1}-${allowedRanks.last + 1})"
    }
}

sealed interface PlacementResult {
    data class Success(
        val state: PlacementState,
        val readyToPlay: Boolean,
    ) : PlacementResult

    data class Failure(val error: PlacementError) : PlacementResult
}

class PlacementValidator(
    private val rules: PlacementRules = PlacementRules(),
) {
    fun start(whiteRoster: Roster, blackRoster: Roster): PlacementState =
        PlacementState(
            board = Board.empty(),
            rosters = mapOf(Color.WHITE to whiteRoster, Color.BLACK to blackRoster),
            remainingByColor = mapOf(
                Color.WHITE to whiteRoster.counts,
                Color.BLACK to blackRoster.counts,
            ),
            sideToPlace = rules.firstPlayer,
            rules = rules,
        )

    fun place(
        state: PlacementState,
        color: Color,
        pieceType: PieceType,
        square: Square,
    ): PlacementResult {
        if (color != state.sideToPlace) {
            return PlacementResult.Failure(PlacementError.WrongTurn(state.sideToPlace, color))
        }
        if (state.remainingCount(color, pieceType) <= 0) {
            return PlacementResult.Failure(PlacementError.PieceNotAvailable(color, pieceType))
        }
        if (!state.board.isEmpty(square)) {
            return PlacementResult.Failure(PlacementError.OccupiedSquare(square))
        }
        if (!state.rules.zoneFor(color).contains(square)) {
            return PlacementResult.Failure(
                PlacementError.OutsidePlacementZone(color, square, state.rules.zoneFor(color).ranks),
            )
        }

        val remaining = state.remainingPieces(color).toMutableMap()
        remaining[pieceType] = remaining.getValue(pieceType) - 1
        val nextRemainingByColor = state.rosters.keys.associateWith { currentColor ->
            if (currentColor == color) {
                remaining.toMap()
            } else {
                state.remainingPieces(currentColor)
            }
        }
        val nextState = PlacementState(
            board = state.board.place(square, Piece(pieceType, color)),
            rosters = state.rosters,
            remainingByColor = nextRemainingByColor,
            sideToPlace = color.opposite(),
            rules = state.rules,
        )
        return PlacementResult.Success(nextState, nextState.isReadyToPlay())
    }
}
