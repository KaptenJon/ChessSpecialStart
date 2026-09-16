package com.chesspoints.engine

const val DEFAULT_BUDGET = 39
const val DEFAULT_ARMY_SIZE = 16

enum class PieceType(val pointValue: Int) {
    PAWN(1),
    KNIGHT(3),
    BISHOP(3),
    ROOK(5),
    QUEEN(9),
    KING(0),
}

enum class Color {
    WHITE,
    BLACK,
    ;

    fun opposite(): Color = if (this == WHITE) BLACK else WHITE

    val pawnForwardStep: Int
        get() = if (this == WHITE) 1 else -1

    val pawnHomeRank: Int
        get() = if (this == WHITE) 1 else 6

    val backRank: Int
        get() = if (this == WHITE) 0 else 7
}

data class Square(val file: Int, val rank: Int) {
    init {
        require(file in 0..7) { "file must be between 0 and 7" }
        require(rank in 0..7) { "rank must be between 0 and 7" }
    }

    val index: Int
        get() = (rank * 8) + file

    val algebraic: String
        get() = "${'a' + file}${rank + 1}"

    fun offset(fileDelta: Int, rankDelta: Int): Square? {
        val nextFile = file + fileDelta
        val nextRank = rank + rankDelta
        return if (nextFile in 0..7 && nextRank in 0..7) {
            Square(nextFile, nextRank)
        } else {
            null
        }
    }

    companion object {
        val ALL: List<Square> = buildList {
            for (rank in 0..7) {
                for (file in 0..7) {
                    add(Square(file, rank))
                }
            }
        }

        fun fromAlgebraic(notation: String): Square? {
            if (notation.length != 2) {
                return null
            }
            val file = notation[0].lowercaseChar() - 'a'
            val rank = notation[1] - '1'
            return if (file in 0..7 && rank in 0..7) Square(file, rank) else null
        }
    }
}

data class Piece(val type: PieceType, val color: Color)

class Board private constructor(
    private val cells: Array<Piece?>,
) {
    constructor() : this(Array(64) { null })

    operator fun get(square: Square): Piece? = cells[square.index]

    fun set(square: Square, piece: Piece?): Board {
        val nextCells = cells.copyOf()
        nextCells[square.index] = piece
        return Board(nextCells)
    }

    fun place(square: Square, piece: Piece): Board = set(square, piece)

    fun remove(square: Square): Board = set(square, null)

    fun move(from: Square, to: Square): Board {
        val piece = this[from] ?: return this
        return remove(from).set(to, piece)
    }

    fun isEmpty(square: Square): Boolean = this[square] == null

    fun pieces(): List<Pair<Square, Piece>> =
        Square.ALL.mapNotNull { square ->
            this[square]?.let { piece -> square to piece }
        }

    fun pieces(color: Color): List<Pair<Square, Piece>> = pieces().filter { (_, piece) -> piece.color == color }

    fun kingSquare(color: Color): Square? =
        pieces(color).firstOrNull { (_, piece) -> piece.type == PieceType.KING }?.first

    override fun equals(other: Any?): Boolean = other is Board && cells.contentEquals(other.cells)

    override fun hashCode(): Int = cells.contentHashCode()

    override fun toString(): String =
        (7 downTo 0).joinToString(separator = "\n") { rank ->
            (0..7).joinToString(separator = " ") { file ->
                when (val piece = this[Square(file, rank)]) {
                    null -> "."
                    else -> {
                        val symbol = when (piece.type) {
                            PieceType.PAWN -> 'p'
                            PieceType.KNIGHT -> 'n'
                            PieceType.BISHOP -> 'b'
                            PieceType.ROOK -> 'r'
                            PieceType.QUEEN -> 'q'
                            PieceType.KING -> 'k'
                        }
                        if (piece.color == Color.WHITE) symbol.uppercaseChar().toString() else symbol.toString()
                    }
                }
            }
        }

    companion object {
        fun empty(): Board = Board()

        fun fromPieces(vararg placements: Pair<Square, Piece>): Board {
            var board = empty()
            for ((square, piece) in placements) {
                require(board[square] == null) { "Square ${square.algebraic} already occupied" }
                board = board.place(square, piece)
            }
            return board
        }
    }
}
