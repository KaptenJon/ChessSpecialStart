package com.chesspoints.ui.game

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chesspoints.app.R
import com.chesspoints.app.i18n.UiText
import com.chesspoints.app.i18n.nameRes
import com.chesspoints.app.i18n.resolve
import com.chesspoints.app.ui.theme.LocalBoardPalette
import com.chesspoints.engine.Board
import com.chesspoints.engine.Color as PieceColor
import com.chesspoints.engine.Move
import com.chesspoints.engine.Piece
import com.chesspoints.engine.PieceType
import com.chesspoints.engine.Square
import com.chesspoints.ui.shared.ChessBoard
import com.chesspoints.ui.shared.PieceBadge

@Composable
fun GameScreen(
    board: Board,
    currentTurnLabel: UiText,
    whiteToMove: Boolean,
    modifier: Modifier = Modifier,
    boardPerspective: PieceColor = PieceColor.WHITE,
    selectedSquare: Square?,
    legalMoves: List<Move>,
    capturedByWhite: List<Piece>,
    capturedByBlack: List<Piece>,
    statusMessage: UiText,
    promotionChoices: List<Move>,
    interactionEnabled: Boolean,
    onSquareSelected: (Square) -> Unit,
    onPromotionSelected: (PieceType) -> Unit,
    onPromotionDismissed: () -> Unit,
    checkAlert: CheckAlert? = null,
    gameOverMessage: UiText? = null,
    onNewGame: () -> Unit = {},
    onBackToHome: () -> Unit = {},
) {
    var gameOverDismissed by rememberSaveable(gameOverMessage) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TurnCard(
            currentTurnLabel = currentTurnLabel.resolve(),
            statusMessage = statusMessage.resolve(),
            whiteToMove = whiteToMove,
            thinking = !interactionEnabled && promotionChoices.isEmpty() && gameOverMessage == null,
        )

        if (checkAlert != null) {
            CheckAlertCard(alert = checkAlert)
        }

        CapturedPiecesRow(
            label = stringResource(R.string.game_captured_by_white),
            pieces = capturedByWhite,
        )

        ChessBoard(
            board = board,
            selectedSquare = selectedSquare,
            highlightedSquares = legalMoves.mapTo(linkedSetOf()) { it.to },
            placementZone = null,
            perspective = boardPerspective,
            checkedKingSquare = checkAlert?.kingSquare,
            checkIsFatal = checkAlert?.isFatal == true,
            onSquareTap = { square ->
                if (interactionEnabled) onSquareSelected(square)
            },
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (interactionEnabled) 1f else .88f),
        )

        CapturedPiecesRow(
            label = stringResource(R.string.game_captured_by_black),
            pieces = capturedByBlack,
        )

        if (gameOverMessage != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onNewGame,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(R.string.action_new_game))
                }
                OutlinedButton(
                    onClick = onBackToHome,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(R.string.action_home))
                }
            }
        }

        if (promotionChoices.isNotEmpty()) {
            PromotionDialog(
                choices = promotionChoices.mapNotNull { it.promotion }.distinct(),
                promotingWhite = whiteToMove,
                onSelected = onPromotionSelected,
                onDismiss = onPromotionDismissed,
            )
        }

        if (gameOverMessage != null && !gameOverDismissed) {
            GameOverDialog(
                message = gameOverMessage.resolve(),
                onNewGame = {
                    gameOverDismissed = true
                    onNewGame()
                },
                onBackToHome = {
                    gameOverDismissed = true
                    onBackToHome()
                },
                onDismiss = { gameOverDismissed = true },
            )
        }
    }
}

/** Everything the UI needs to shout "the king is under attack". */
data class CheckAlert(
    val kingSquare: Square?,
    val isFatal: Boolean,
    val title: UiText,
    val body: UiText,
)

@Composable
private fun CheckAlertCard(alert: CheckAlert) {
    val pulse by rememberInfiniteTransition(label = "checkCardPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "checkCardPulseValue",
    )
    val emphasis = if (alert.isFatal) 1f else pulse

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp + 4.dp * emphasis),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = .25f + .35f * emphasis)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = alert.title.resolve(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = alert.body.resolve(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun GameOverDialog(
    message: String,
    onNewGame: () -> Unit,
    onBackToHome: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(
                text = stringResource(R.string.game_over_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            TextButton(onClick = onNewGame) { Text(stringResource(R.string.action_new_game)) }
        },
        dismissButton = {
            TextButton(onClick = onBackToHome) { Text(stringResource(R.string.action_home)) }
        },
    )
}

@Composable
private fun TurnCard(
    currentTurnLabel: String,
    statusMessage: String,
    whiteToMove: Boolean,
    thinking: Boolean,
) {
    val boardPalette = LocalBoardPalette.current
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        if (whiteToMove) boardPalette.lightSquare else boardPalette.darkSquare,
                    ),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.game_turn_title, currentTurnLabel),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .85f),
                )
            }
            if (thinking) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = stringResource(R.string.game_badge_waiting),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CapturedPiecesRow(
    label: String,
    pieces: List<Piece>,
) {
    val points = pieces.sumOf { it.type.pointValue }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.game_captured_points, points),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (pieces.isEmpty()) {
                Text(
                    text = stringResource(R.string.game_no_captures),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(pieces) { piece ->
                        PieceBadge(
                            pieceType = piece.type,
                            isWhite = piece.color == PieceColor.WHITE,
                            size = 30.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PromotionDialog(
    choices: List<PieceType>,
    promotingWhite: Boolean,
    onSelected: (PieceType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(text = stringResource(R.string.promotion_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.promotion_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                choices.forEach { pieceType ->
                    TextButton(
                        onClick = { onSelected(pieceType) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PieceBadge(pieceType = pieceType, isWhite = promotingWhite, size = 36.dp)
                            Text(
                                text = stringResource(pieceType.nameRes),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}
