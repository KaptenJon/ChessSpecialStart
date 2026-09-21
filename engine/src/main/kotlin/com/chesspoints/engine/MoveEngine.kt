package com.chesspoints.engine

enum class CastleSide {
    KING_SIDE,
    QUEEN_SIDE,
}

enum class MoveType {
    QUIET,
    CAPTURE,
    KING_SIDE_CASTLE,
    QUEEN_SIDE_CASTLE,
    EN_PASSANT,
    PROMOTION,
    PROMOTION_CAPTURE,
}

data class MoveRequest(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
)

data class Move(
    val from: Square,
    val to: Square,
    val piece: Piece,
    val type: MoveType,
    val capturedPiece: Piece? = null,
    val promotion: PieceType? = null,
) {
    val castleSide: CastleSide?
        get() = when (type) {
            MoveType.KING_SIDE_CASTLE -> CastleSide.KING_SIDE
            MoveType.QUEEN_SIDE_CASTLE -> CastleSide.QUEEN_SIDE
            else -> null
        }
}

data class CastlingRights(
    val whiteKingSide: Boolean = false,
    val whiteQueenSide: Boolean = false,
    val blackKingSide: Boolean = false,
    val blackQueenSide: Boolean = false,
) {
    fun canCastle(color: Color, side: CastleSide): Boolean =
        when (color) {
            Color.WHITE -> if (side == CastleSide.KING_SIDE) whiteKingSide else whiteQueenSide
            Color.BLACK -> if (side == CastleSide.KING_SIDE) blackKingSide else blackQueenSide
        }

    fun withoutKing(color: Color): CastlingRights =
        when (color) {
            Color.WHITE -> copy(whiteKingSide = false, whiteQueenSide = false)
            Color.BLACK -> copy(blackKingSide = false, blackQueenSide = false)
        }

    fun withoutRook(color: Color, side: CastleSide): CastlingRights =
        when (color) {
            Color.WHITE ->
                if (side == CastleSide.KING_SIDE) copy(whiteKingSide = false) else copy(whiteQueenSide = false)

            Color.BLACK ->
                if (side == CastleSide.KING_SIDE) copy(blackKingSide = false) else copy(blackQueenSide = false)
        }
}

data class GamePosition(
    val board: Board,
    val sideToMove: Color = Color.WHITE,
    val castlingRights: CastlingRights = CastlingRights(),
    val enPassantTarget: Square? = null,
    val halfmoveClock: Int = 0,
    val fullmoveNumber: Int = 1,
) {
    init {
        require(halfmoveClock >= 0) { "halfmoveClock must be non-negative" }
        require(fullmoveNumber >= 1) { "fullmoveNumber must be at least 1" }
    }
}

enum class DrawReason {
    STALEMATE,
    INSUFFICIENT_MATERIAL,
}

sealed interface PositionStatus {
    data object Active : PositionStatus
    data class Check(val checkedColor: Color) : PositionStatus
    data class Checkmate(val winner: Color) : PositionStatus
    data class KingCaptured(val winner: Color) : PositionStatus
    data class Draw(val reason: DrawReason) : PositionStatus
}

sealed interface MoveRejection {
    val message: String

    data class NoPieceAtSource(val square: Square) : MoveRejection {
        override val message: String = "No piece found at ${square.algebraic}"
    }

    data class WrongColorTurn(
        val expected: Color,
        val actual: Color,
    ) : MoveRejection {
        override val message: String = "It is $expected's turn, not $actual's"
    }

    data class IllegalMove(val request: MoveRequest) : MoveRejection {
        override val message: String = "Move ${request.from.algebraic}-${request.to.algebraic} is not legal"
    }
}

sealed interface MoveApplicationResult {
    data class Success(
        val move: Move,
        val position: GamePosition,
        val status: PositionStatus,
    ) : MoveApplicationResult

    data class Illegal(val reason: MoveRejection) : MoveApplicationResult
}

object MoveEngine {
    private val knightOffsets = listOf(
        -2 to -1,
        -2 to 1,
        -1 to -2,
        -1 to 2,
        1 to -2,
        1 to 2,
        2 to -1,
        2 to 1,
    )
    private val kingOffsets = listOf(
        -1 to -1,
        -1 to 0,
        -1 to 1,
        0 to -1,
        0 to 1,
        1 to -1,
        1 to 0,
        1 to 1,
    )
    private val rookDirections = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    private val bishopDirections = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)

    /**
     * Under ChessPoints custom layouts, castling is granted only when the
     * placed starting layout contains the same-color king on e1/e8 and the
     * relevant rook on a1/h1/a8/h8. If a king or rook starts anywhere else,
     * that side never gains the corresponding castling right.
     */
    fun standardCastlingRights(board: Board): CastlingRights =
        CastlingRights(
            whiteKingSide = board[Square.fromAlgebraic("e1")!!] == Piece(PieceType.KING, Color.WHITE) &&
                board[Square.fromAlgebraic("h1")!!] == Piece(PieceType.ROOK, Color.WHITE),
            whiteQueenSide = board[Square.fromAlgebraic("e1")!!] == Piece(PieceType.KING, Color.WHITE) &&
                board[Square.fromAlgebraic("a1")!!] == Piece(PieceType.ROOK, Color.WHITE),
            blackKingSide = board[Square.fromAlgebraic("e8")!!] == Piece(PieceType.KING, Color.BLACK) &&
                board[Square.fromAlgebraic("h8")!!] == Piece(PieceType.ROOK, Color.BLACK),
            blackQueenSide = board[Square.fromAlgebraic("e8")!!] == Piece(PieceType.KING, Color.BLACK) &&
                board[Square.fromAlgebraic("a8")!!] == Piece(PieceType.ROOK, Color.BLACK),
        )

    fun legalMoves(position: GamePosition, from: Square? = null): List<Move> {
        val pseudoMoves = if (from == null) {
            position.board.pieces(position.sideToMove).flatMap { (square, piece) -> pseudoMovesFrom(position, square, piece) }
        } else {
            val piece = position.board[from]
            if (piece == null || piece.color != position.sideToMove) emptyList() else pseudoMovesFrom(position, from, piece)
        }

        return pseudoMoves.filter { move ->
            move.capturedPiece?.type != PieceType.KING &&
                !isInCheck(applyUnchecked(position, move), move.piece.color)
        }
    }

    fun apply(position: GamePosition, request: MoveRequest): MoveApplicationResult {
        val piece = position.board[request.from] ?: return MoveApplicationResult.Illegal(MoveRejection.NoPieceAtSource(request.from))
        if (piece.color != position.sideToMove) {
            return MoveApplicationResult.Illegal(MoveRejection.WrongColorTurn(position.sideToMove, piece.color))
        }
        val move = legalMoves(position, request.from).firstOrNull { candidate ->
            candidate.to == request.to && candidate.promotion == request.promotion
        } ?: return MoveApplicationResult.Illegal(MoveRejection.IllegalMove(request))
        val nextPosition = applyUnchecked(position, move)
        return MoveApplicationResult.Success(move, nextPosition, evaluate(nextPosition))
    }

    fun isInCheck(position: GamePosition, color: Color = position.sideToMove): Boolean {
        val kingSquare = position.board.kingSquare(color) ?: return false
        return isSquareAttacked(position.board, kingSquare, color.opposite())
    }

    fun evaluate(position: GamePosition): PositionStatus {
        val whiteKingPresent = position.board.kingSquare(Color.WHITE) != null
        val blackKingPresent = position.board.kingSquare(Color.BLACK) != null
        if (!whiteKingPresent || !blackKingPresent) {
            return PositionStatus.KingCaptured(
                winner = if (whiteKingPresent) Color.WHITE else Color.BLACK,
            )
        }
        if (insufficientMaterial(position.board)) {
            return PositionStatus.Draw(DrawReason.INSUFFICIENT_MATERIAL)
        }

        val inCheck = isInCheck(position, position.sideToMove)
        val legalMoves = legalMoves(position)
        if (legalMoves.isEmpty()) {
            return if (inCheck) {
                PositionStatus.Checkmate(position.sideToMove.opposite())
            } else {
                PositionStatus.Draw(DrawReason.STALEMATE)
            }
        }
        return if (inCheck) PositionStatus.Check(position.sideToMove) else PositionStatus.Active
    }

    fun positionKey(position: GamePosition): String =
        buildString {
            for (square in Square.ALL) {
                append(
                    when (val piece = position.board[square]) {
                        null -> '.'
                        else -> pieceCode(piece)
                    },
                )
            }
            append('|')
            append(position.sideToMove.name)
            append('|')
            append(if (position.castlingRights.whiteKingSide) 'K' else '-')
            append(if (position.castlingRights.whiteQueenSide) 'Q' else '-')
            append(if (position.castlingRights.blackKingSide) 'k' else '-')
            append(if (position.castlingRights.blackQueenSide) 'q' else '-')
            append('|')
            append(position.enPassantTarget?.algebraic ?: "-")
        }

    private fun pseudoMovesFrom(position: GamePosition, square: Square, piece: Piece): List<Move> =
        when (piece.type) {
            PieceType.PAWN -> pawnMoves(position, square, piece)
            PieceType.KNIGHT -> knightMoves(position, square, piece)
            PieceType.BISHOP -> slidingMoves(position, square, piece, bishopDirections)
            PieceType.ROOK -> slidingMoves(position, square, piece, rookDirections)
            PieceType.QUEEN -> slidingMoves(position, square, piece, rookDirections + bishopDirections)
            PieceType.KING -> kingMoves(position, square, piece)
        }

    private fun pawnMoves(position: GamePosition, square: Square, piece: Piece): List<Move> {
        val direction = piece.color.pawnForwardStep
        val promotionRank = if (piece.color == Color.WHITE) 7 else 0
        val startRank = piece.color.pawnHomeRank
        val moves = mutableListOf<Move>()

        val oneForward = square.offset(0, direction)
        if (oneForward != null && position.board.isEmpty(oneForward)) {
            addPawnMove(moves, square, oneForward, piece, null, promotionRank)

            val twoForward = square.offset(0, direction * 2)
            if (square.rank == startRank && twoForward != null && position.board.isEmpty(twoForward)) {
                moves += Move(square, twoForward, piece, MoveType.QUIET)
            }
        }

        for (fileOffset in listOf(-1, 1)) {
            val target = square.offset(fileOffset, direction) ?: continue
            val occupant = position.board[target]
            if (occupant != null && occupant.color != piece.color) {
                addPawnMove(moves, square, target, piece, occupant, promotionRank)
            } else if (position.enPassantTarget == target) {
                val capturedSquare = Square(target.file, square.rank)
                val capturedPawn = position.board[capturedSquare]
                if (capturedPawn == Piece(PieceType.PAWN, piece.color.opposite())) {
                    moves += Move(square, target, piece, MoveType.EN_PASSANT, capturedPawn)
                }
            }
        }

        return moves
    }

    private fun addPawnMove(
        moves: MutableList<Move>,
        from: Square,
        to: Square,
        piece: Piece,
        capturedPiece: Piece?,
        promotionRank: Int,
    ) {
        if (to.rank == promotionRank) {
            for (promotion in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
                moves += Move(
                    from = from,
                    to = to,
                    piece = piece,
                    type = if (capturedPiece == null) MoveType.PROMOTION else MoveType.PROMOTION_CAPTURE,
                    capturedPiece = capturedPiece,
                    promotion = promotion,
                )
            }
        } else {
            moves += Move(
                from = from,
                to = to,
                piece = piece,
                type = if (capturedPiece == null) MoveType.QUIET else MoveType.CAPTURE,
                capturedPiece = capturedPiece,
            )
        }
    }

    private fun knightMoves(position: GamePosition, square: Square, piece: Piece): List<Move> =
        knightOffsets.mapNotNull { (fileOffset, rankOffset) ->
            val target = square.offset(fileOffset, rankOffset) ?: return@mapNotNull null
            val occupant = position.board[target]
            when {
                occupant == null -> Move(square, target, piece, MoveType.QUIET)
                occupant.color != piece.color -> Move(square, target, piece, MoveType.CAPTURE, occupant)
                else -> null
            }
        }

    private fun slidingMoves(
        position: GamePosition,
        square: Square,
        piece: Piece,
        directions: List<Pair<Int, Int>>,
    ): List<Move> {
        val moves = mutableListOf<Move>()
        for ((fileDirection, rankDirection) in directions) {
            var current = square.offset(fileDirection, rankDirection)
            while (current != null) {
                val occupant = position.board[current]
                if (occupant == null) {
                    moves += Move(square, current, piece, MoveType.QUIET)
                } else {
                    if (occupant.color != piece.color) {
                        moves += Move(square, current, piece, MoveType.CAPTURE, occupant)
                    }
                    break
                }
                current = current.offset(fileDirection, rankDirection)
            }
        }
        return moves
    }

    private fun kingMoves(position: GamePosition, square: Square, piece: Piece): List<Move> {
        val moves = kingOffsets.mapNotNull { (fileOffset, rankOffset) ->
            val target = square.offset(fileOffset, rankOffset) ?: return@mapNotNull null
            val occupant = position.board[target]
            when {
                occupant == null -> Move(square, target, piece, MoveType.QUIET)
                occupant.color != piece.color -> Move(square, target, piece, MoveType.CAPTURE, occupant)
                else -> null
            }
        }.toMutableList()

        moves += castleMoves(position, piece.color)
        return moves
    }

    private fun castleMoves(position: GamePosition, color: Color): List<Move> {
        val kingStart = if (color == Color.WHITE) Square.fromAlgebraic("e1")!! else Square.fromAlgebraic("e8")!!
        val king = position.board[kingStart]
        if (king != Piece(PieceType.KING, color) || isSquareAttacked(position.board, kingStart, color.opposite())) {
            return emptyList()
        }

        val moves = mutableListOf<Move>()
        if (position.castlingRights.canCastle(color, CastleSide.KING_SIDE)) {
            val rookSquare = if (color == Color.WHITE) Square.fromAlgebraic("h1")!! else Square.fromAlgebraic("h8")!!
            val path = listOf(
                if (color == Color.WHITE) Square.fromAlgebraic("f1")!! else Square.fromAlgebraic("f8")!!,
                if (color == Color.WHITE) Square.fromAlgebraic("g1")!! else Square.fromAlgebraic("g8")!!,
            )
            if (
                position.board[rookSquare] == Piece(PieceType.ROOK, color) &&
                path.all(position.board::isEmpty) &&
                path.none { square -> isSquareAttacked(position.board, square, color.opposite()) }
            ) {
                moves += Move(kingStart, path.last(), king, MoveType.KING_SIDE_CASTLE)
            }
        }

        if (position.castlingRights.canCastle(color, CastleSide.QUEEN_SIDE)) {
            val rookSquare = if (color == Color.WHITE) Square.fromAlgebraic("a1")!! else Square.fromAlgebraic("a8")!!
            val emptyPath = listOf(
                if (color == Color.WHITE) Square.fromAlgebraic("b1")!! else Square.fromAlgebraic("b8")!!,
                if (color == Color.WHITE) Square.fromAlgebraic("c1")!! else Square.fromAlgebraic("c8")!!,
                if (color == Color.WHITE) Square.fromAlgebraic("d1")!! else Square.fromAlgebraic("d8")!!,
            )
            val checkedPath = emptyPath.takeLast(2)
            if (
                position.board[rookSquare] == Piece(PieceType.ROOK, color) &&
                emptyPath.all(position.board::isEmpty) &&
                checkedPath.none { square -> isSquareAttacked(position.board, square, color.opposite()) }
            ) {
                moves += Move(
                    from = kingStart,
                    to = if (color == Color.WHITE) Square.fromAlgebraic("c1")!! else Square.fromAlgebraic("c8")!!,
                    piece = king,
                    type = MoveType.QUEEN_SIDE_CASTLE,
                )
            }
        }
        return moves
    }

    private fun applyUnchecked(position: GamePosition, move: Move): GamePosition {
        val moverColor = move.piece.color
        var nextBoard = position.board.remove(move.from)
        var capturedPiece = move.capturedPiece

        when (move.type) {
            MoveType.KING_SIDE_CASTLE -> {
                val rookFrom = if (moverColor == Color.WHITE) Square.fromAlgebraic("h1")!! else Square.fromAlgebraic("h8")!!
                val rookTo = if (moverColor == Color.WHITE) Square.fromAlgebraic("f1")!! else Square.fromAlgebraic("f8")!!
                nextBoard = nextBoard.place(move.to, move.piece).remove(rookFrom).place(rookTo, Piece(PieceType.ROOK, moverColor))
            }

            MoveType.QUEEN_SIDE_CASTLE -> {
                val rookFrom = if (moverColor == Color.WHITE) Square.fromAlgebraic("a1")!! else Square.fromAlgebraic("a8")!!
                val rookTo = if (moverColor == Color.WHITE) Square.fromAlgebraic("d1")!! else Square.fromAlgebraic("d8")!!
                nextBoard = nextBoard.place(move.to, move.piece).remove(rookFrom).place(rookTo, Piece(PieceType.ROOK, moverColor))
            }

            MoveType.EN_PASSANT -> {
                val capturedSquare = Square(move.to.file, move.from.rank)
                capturedPiece = position.board[capturedSquare]
                nextBoard = nextBoard.remove(capturedSquare).place(move.to, move.piece)
            }

            MoveType.PROMOTION,
            MoveType.PROMOTION_CAPTURE,
            MoveType.CAPTURE,
            MoveType.QUIET,
            -> {
                val promotedPiece = move.promotion?.let { Piece(it, moverColor) } ?: move.piece
                nextBoard = nextBoard.set(move.to, promotedPiece)
            }
        }

        var nextCastlingRights = position.castlingRights
        if (move.piece.type == PieceType.KING) {
            nextCastlingRights = nextCastlingRights.withoutKing(moverColor)
        }
        if (move.piece.type == PieceType.ROOK) {
            rookCastleSide(move.from)?.let { side ->
                nextCastlingRights = nextCastlingRights.withoutRook(moverColor, side)
            }
        }
        if (capturedPiece?.type == PieceType.ROOK) {
            rookCastleSide(move.to)?.let { side ->
                nextCastlingRights = nextCastlingRights.withoutRook(moverColor.opposite(), side)
            }
        }

        val enPassantTarget =
            if (move.piece.type == PieceType.PAWN && kotlin.math.abs(move.to.rank - move.from.rank) == 2) {
                Square(move.from.file, (move.from.rank + move.to.rank) / 2)
            } else {
                null
            }

        val halfmoveClock =
            if (move.piece.type == PieceType.PAWN || capturedPiece != null) {
                0
            } else {
                position.halfmoveClock + 1
            }

        return GamePosition(
            board = nextBoard,
            sideToMove = moverColor.opposite(),
            castlingRights = nextCastlingRights,
            enPassantTarget = enPassantTarget,
            halfmoveClock = halfmoveClock,
            fullmoveNumber = if (moverColor == Color.BLACK) position.fullmoveNumber + 1 else position.fullmoveNumber,
        )
    }

    private fun rookCastleSide(square: Square): CastleSide? =
        when (square.algebraic) {
            "a1", "a8" -> CastleSide.QUEEN_SIDE
            "h1", "h8" -> CastleSide.KING_SIDE
            else -> null
        }

    private fun isSquareAttacked(board: Board, square: Square, byColor: Color): Boolean {
        val pawnRankOffset = if (byColor == Color.WHITE) -1 else 1
        for (fileOffset in listOf(-1, 1)) {
            val attacker = square.offset(fileOffset, pawnRankOffset)
            if (attacker != null && board[attacker] == Piece(PieceType.PAWN, byColor)) {
                return true
            }
        }

        if (knightOffsets.any { (fileOffset, rankOffset) ->
                square.offset(fileOffset, rankOffset)?.let(board::get) == Piece(PieceType.KNIGHT, byColor)
            }) {
            return true
        }

        if (kingOffsets.any { (fileOffset, rankOffset) ->
                square.offset(fileOffset, rankOffset)?.let(board::get) == Piece(PieceType.KING, byColor)
            }) {
            return true
        }

        return attackedBySlidingPiece(board, square, byColor, rookDirections, setOf(PieceType.ROOK, PieceType.QUEEN)) ||
            attackedBySlidingPiece(board, square, byColor, bishopDirections, setOf(PieceType.BISHOP, PieceType.QUEEN))
    }

    private fun attackedBySlidingPiece(
        board: Board,
        square: Square,
        byColor: Color,
        directions: List<Pair<Int, Int>>,
        validAttackers: Set<PieceType>,
    ): Boolean {
        for ((fileDirection, rankDirection) in directions) {
            var current = square.offset(fileDirection, rankDirection)
            while (current != null) {
                val piece = board[current]
                if (piece != null) {
                    if (piece.color == byColor && piece.type in validAttackers) {
                        return true
                    }
                    break
                }
                current = current.offset(fileDirection, rankDirection)
            }
        }
        return false
    }

    private fun insufficientMaterial(board: Board): Boolean {
        val nonKings = board.pieces().map { it.second }.filter { it.type != PieceType.KING }
        if (nonKings.isEmpty()) {
            return true
        }
        if (nonKings.size == 1 && nonKings.single().type in setOf(PieceType.BISHOP, PieceType.KNIGHT)) {
            return true
        }
        if (nonKings.size == 2 && nonKings.all { it.type == PieceType.BISHOP }) {
            val bishopSquares = board.pieces().filter { (_, piece) -> piece.type == PieceType.BISHOP }.map { (square, _) -> square }
            return bishopSquares.map(::squareColor).distinct().size == 1
        }
        return false
    }

    private fun squareColor(square: Square): Int = (square.file + square.rank) % 2

    private fun pieceCode(piece: Piece): Char {
        val base = when (piece.type) {
            PieceType.PAWN -> 'p'
            PieceType.KNIGHT -> 'n'
            PieceType.BISHOP -> 'b'
            PieceType.ROOK -> 'r'
            PieceType.QUEEN -> 'q'
            PieceType.KING -> 'k'
        }
        return if (piece.color == Color.WHITE) base.uppercaseChar() else base
    }
}
