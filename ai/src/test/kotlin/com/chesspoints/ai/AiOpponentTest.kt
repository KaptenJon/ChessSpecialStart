package com.chesspoints.ai

import com.chesspoints.engine.Board
import com.chesspoints.engine.ChessGame
import com.chesspoints.engine.ChessGameState
import com.chesspoints.engine.Color
import com.chesspoints.engine.DraftValidationResult
import com.chesspoints.engine.DraftValidator
import com.chesspoints.engine.GamePlacementResult
import com.chesspoints.engine.GamePosition
import com.chesspoints.engine.MoveEngine
import com.chesspoints.engine.Piece
import com.chesspoints.engine.PieceType
import com.chesspoints.engine.PlacementResult
import com.chesspoints.engine.PlacementValidator
import com.chesspoints.engine.Square
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiOpponentTest {
    private val drafter = AiDrafter()
    private val placer = AiPlacer()
    private val moveEngine = AiMoveEngine()

    @Test
    fun drafterAlwaysProducesValidDrafts() {
        val seenCompositions = mutableSetOf<Map<PieceType, Int>>()

        repeat(24) { seed ->
            val roster = drafter.draft(Color.WHITE, random = Random(seed))
            val validation = DraftValidator.validate(Color.WHITE, roster.counts)
            assertIs<DraftValidationResult.Valid>(validation)
            seenCompositions += roster.counts
        }

        assertTrue(seenCompositions.size > 1, "Expected the drafter to produce more than one composition.")
    }

    @Test
    fun placerAlwaysProducesLegalPlacements() {
        val whiteRoster = drafter.draft(Color.WHITE, random = Random(1))
        val blackRoster = drafter.draft(Color.BLACK, random = Random(2))
        val validator = PlacementValidator()
        var state = validator.start(whiteRoster, blackRoster)

        repeat(whiteRoster.totalPieces + blackRoster.totalPieces) {
            val choice = placer.nextPlacement(state)
            assertNotNull(choice)

            val result = validator.place(state, state.sideToPlace, choice.pieceType, choice.square)
            val success = assertIs<PlacementResult.Success>(result)
            state = success.state
        }

        assertTrue(state.isReadyToPlay())
    }

    @Test
    fun placerChoicesIntegrateWithChessGamePlacementApi() {
        val game = ChessGame()
        val whiteRoster = drafter.draft(Color.WHITE, random = Random(3))
        val blackRoster = drafter.draft(Color.BLACK, random = Random(4))

        assertIs<com.chesspoints.engine.DraftSubmissionResult.Accepted>(game.submitDraft(Color.WHITE, whiteRoster.counts))
        val secondDraft = assertIs<com.chesspoints.engine.DraftSubmissionResult.Accepted>(
            game.submitDraft(Color.BLACK, blackRoster.counts),
        )
        var state = assertIs<ChessGameState.Placing>(secondDraft.gameState).placementState

        repeat(whiteRoster.totalPieces + blackRoster.totalPieces) {
            val choice = placer.nextPlacement(state)
            assertNotNull(choice)
            val placement = game.placePiece(state.sideToPlace, choice.pieceType, choice.square)
            assertIs<GamePlacementResult.Accepted>(placement)
            val nextState = game.getGameState()
            if (nextState is ChessGameState.Placing) {
                state = nextState.placementState
            }
        }

        assertIs<ChessGameState.Playing>(game.getGameState())
    }

    @Test
    fun moveEngineReturnsLegalMovesForVariedPositions() {
        val positions = listOf(
            GamePosition(
                board = Board.fromPieces(
                    Square.fromAlgebraic("e1")!! to Piece(PieceType.KING, Color.WHITE),
                    Square.fromAlgebraic("d1")!! to Piece(PieceType.QUEEN, Color.WHITE),
                    Square.fromAlgebraic("a1")!! to Piece(PieceType.ROOK, Color.WHITE),
                    Square.fromAlgebraic("c3")!! to Piece(PieceType.KNIGHT, Color.WHITE),
                    Square.fromAlgebraic("e2")!! to Piece(PieceType.PAWN, Color.WHITE),
                    Square.fromAlgebraic("e8")!! to Piece(PieceType.KING, Color.BLACK),
                    Square.fromAlgebraic("d8")!! to Piece(PieceType.QUEEN, Color.BLACK),
                    Square.fromAlgebraic("a8")!! to Piece(PieceType.ROOK, Color.BLACK),
                    Square.fromAlgebraic("c6")!! to Piece(PieceType.KNIGHT, Color.BLACK),
                    Square.fromAlgebraic("e7")!! to Piece(PieceType.PAWN, Color.BLACK),
                ),
                sideToMove = Color.WHITE,
            ),
            GamePosition(
                board = Board.fromPieces(
                    Square.fromAlgebraic("g1")!! to Piece(PieceType.KING, Color.WHITE),
                    Square.fromAlgebraic("d4")!! to Piece(PieceType.QUEEN, Color.WHITE),
                    Square.fromAlgebraic("a2")!! to Piece(PieceType.PAWN, Color.WHITE),
                    Square.fromAlgebraic("g8")!! to Piece(PieceType.KING, Color.BLACK),
                    Square.fromAlgebraic("d6")!! to Piece(PieceType.ROOK, Color.BLACK),
                    Square.fromAlgebraic("f7")!! to Piece(PieceType.PAWN, Color.BLACK),
                ),
                sideToMove = Color.BLACK,
            ),
            GamePosition(
                board = Board.fromPieces(
                    Square.fromAlgebraic("e2")!! to Piece(PieceType.KING, Color.WHITE),
                    Square.fromAlgebraic("d5")!! to Piece(PieceType.PAWN, Color.WHITE),
                    Square.fromAlgebraic("e7")!! to Piece(PieceType.KING, Color.BLACK),
                    Square.fromAlgebraic("h7")!! to Piece(PieceType.PAWN, Color.BLACK),
                ),
                sideToMove = Color.WHITE,
            ),
        )

        positions.forEach { position ->
            val choice = moveEngine.chooseMove(position, AiSearchConfig(depth = 2))
            val legalMoves = MoveEngine.legalMoves(position)
            assertNotNull(choice.move)
            assertTrue(choice.move in legalMoves)
        }
    }

    @Test
    fun moveEngineRespectsDepthParameter() {
        val position = GamePosition(
            board = Board.fromPieces(
                Square.fromAlgebraic("e1")!! to Piece(PieceType.KING, Color.WHITE),
                Square.fromAlgebraic("d1")!! to Piece(PieceType.QUEEN, Color.WHITE),
                Square.fromAlgebraic("a1")!! to Piece(PieceType.ROOK, Color.WHITE),
                Square.fromAlgebraic("h1")!! to Piece(PieceType.ROOK, Color.WHITE),
                Square.fromAlgebraic("c3")!! to Piece(PieceType.KNIGHT, Color.WHITE),
                Square.fromAlgebraic("f3")!! to Piece(PieceType.BISHOP, Color.WHITE),
                Square.fromAlgebraic("d2")!! to Piece(PieceType.PAWN, Color.WHITE),
                Square.fromAlgebraic("e8")!! to Piece(PieceType.KING, Color.BLACK),
                Square.fromAlgebraic("d8")!! to Piece(PieceType.QUEEN, Color.BLACK),
                Square.fromAlgebraic("a8")!! to Piece(PieceType.ROOK, Color.BLACK),
                Square.fromAlgebraic("h8")!! to Piece(PieceType.ROOK, Color.BLACK),
                Square.fromAlgebraic("c6")!! to Piece(PieceType.KNIGHT, Color.BLACK),
                Square.fromAlgebraic("f6")!! to Piece(PieceType.BISHOP, Color.BLACK),
                Square.fromAlgebraic("d7")!! to Piece(PieceType.PAWN, Color.BLACK),
            ),
            sideToMove = Color.WHITE,
        )

        val shallow = moveEngine.chooseMove(position, AiSearchConfig(depth = 1))
        val deep = moveEngine.chooseMove(position, AiSearchConfig(depth = 3))

        assertEquals(1, shallow.searchedDepth)
        assertEquals(3, deep.searchedDepth)
        assertTrue(shallow.completedRequestedDepth)
        assertTrue(deep.completedRequestedDepth)
        assertTrue(deep.evaluatedNodes > shallow.evaluatedNodes)
        assertNotNull(shallow.move)
        assertNotNull(deep.move)
    }
}
