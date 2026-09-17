package com.chesspoints.ui.shared

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesspoints.app.ui.theme.LocalBoardPalette
import com.chesspoints.engine.Board
import com.chesspoints.engine.Color as PieceColor
import com.chesspoints.engine.PieceType
import com.chesspoints.engine.PlacementZone
import com.chesspoints.engine.Square

private val BoardShape = RoundedCornerShape(22.dp)
private val SquareShape = RoundedCornerShape(3.dp)

@Composable
fun ChessBoard(
    board: Board,
    selectedSquare: Square?,
    highlightedSquares: Set<Square>,
    placementZone: PlacementZone?,
    onSquareTap: (Square) -> Unit,
    modifier: Modifier = Modifier,
    perspective: PieceColor = PieceColor.WHITE,
    showCoordinates: Boolean = true,
    dropTargets: Set<Square> = emptySet(),
    hoveredSquare: Square? = null,
    hoveredIsLegal: Boolean = true,
    checkedKingSquare: Square? = null,
    checkIsFatal: Boolean = false,
    onGridBoundsChanged: ((Rect) -> Unit)? = null,
) {
    val palette = LocalBoardPalette.current
    // One shared pulse so the king in check throbs in sync with the alert card.
    val checkPulse by rememberInfiniteTransition(label = "checkPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "checkPulseValue",
    )

    Surface(
        modifier = modifier.fillMaxWidth().aspectRatio(1f),
        shape = BoardShape,
        color = Color.Transparent,
        shadowElevation = 14.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(BoardShape)
                .background(Brush.verticalGradient(listOf(palette.frameTop, palette.frameBottom)))
                .drawBehind {
                    // Inner bevel highlight on the wooden frame.
                    drawRoundRect(
                        color = palette.frameEdge.copy(alpha = .55f),
                        style = Stroke(width = 2.dp.toPx()),
                        cornerRadius = CornerRadius(22.dp.toPx()),
                    )
                }
                .padding(10.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (onGridBoundsChanged != null) {
                            Modifier.onGloballyPositioned { onGridBoundsChanged(it.boundsInRoot()) }
                        } else {
                            Modifier
                        },
                    ),
                verticalArrangement = Arrangement.Top,
            ) {
                val displayedRanks = if (perspective == PieceColor.WHITE) 7 downTo 0 else 0..7
                val displayedFiles = if (perspective == PieceColor.WHITE) 0..7 else 7 downTo 0
                val bottomRank = if (perspective == PieceColor.WHITE) 0 else 7
                val leftFile = if (perspective == PieceColor.WHITE) 0 else 7
                for (rank in displayedRanks) {
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        for (file in displayedFiles) {
                            val square = Square(file, rank)
                            val piece = board[square]
                            BoardSquare(
                                square = square,
                                isLight = (file + rank) % 2 != 0,
                                pieceType = piece?.type,
                                pieceIsWhite = piece?.color == PieceColor.WHITE,
                                selected = selectedSquare == square,
                                highlighted = square in highlightedSquares,
                                inZone = placementZone?.contains(square) == true,
                                isDropTarget = square in dropTargets,
                                isHovered = hoveredSquare == square,
                                hoverIsLegal = hoveredIsLegal,
                                checkGlow = if (checkedKingSquare == square) checkPulse else null,
                                checkIsFatal = checkIsFatal,
                                showFileLabel = showCoordinates && rank == bottomRank,
                                showRankLabel = showCoordinates && file == leftFile,
                                onTap = { onSquareTap(square) },
                                modifier = Modifier.weight(1f).fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardSquare(
    square: Square,
    isLight: Boolean,
    pieceType: PieceType?,
    pieceIsWhite: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    inZone: Boolean,
    isDropTarget: Boolean,
    isHovered: Boolean,
    hoverIsLegal: Boolean,
    checkGlow: Float?,
    checkIsFatal: Boolean,
    showFileLabel: Boolean,
    showRankLabel: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalBoardPalette.current
    val baseColor = if (isLight) palette.lightSquare else palette.darkSquare
    val labelColor = if (isLight) palette.lightSquareLabel else palette.darkSquareLabel
    val isCaptureTarget = highlighted && pieceType != null
    val hoverColor = if (hoverIsLegal) palette.selection else palette.captureTarget

    val squareColor by animateColorAsState(
        targetValue = when {
            isHovered -> baseColor.blendTowards(hoverColor, if (hoverIsLegal) .5f else .38f)
            selected -> baseColor.blendTowards(palette.selection, .55f)
            isDropTarget -> baseColor.blendTowards(palette.zoneBorder, .3f)
            inZone -> baseColor.blendTowards(palette.zoneBorder, .16f)
            else -> baseColor
        },
        animationSpec = tween(140),
        label = "squareColor",
    )
    val pieceScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = tween(140),
        label = "pieceScale",
    )
    val dropDotScale by animateFloatAsState(
        targetValue = if (isHovered && hoverIsLegal) 1f else if (isDropTarget) .42f else 0f,
        animationSpec = tween(140),
        label = "dropDotScale",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clip(SquareShape)
            .background(squareColor)
            .drawBehind {
                // Soft diagonal sheen so squares read as polished wood.
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = .07f), Color.Black.copy(alpha = .06f)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
                if (inZone) {
                    drawRect(palette.zoneTint)
                    drawRoundRect(
                        color = palette.zoneBorder.copy(alpha = .8f),
                        style = Stroke(width = 1.5.dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
                if (dropDotScale > 0.01f && pieceType == null) {
                    drawCircle(
                        color = palette.moveTarget.copy(alpha = .35f * dropDotScale.coerceAtMost(1f)),
                        radius = size.minDimension * .30f * dropDotScale,
                    )
                }
                if (isDropTarget && !isHovered) {
                    drawRoundRect(
                        color = palette.moveTarget.copy(alpha = .75f),
                        style = Stroke(width = 1.8.dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
                if (isHovered) {
                    drawRoundRect(
                        color = hoverColor,
                        style = Stroke(width = 3.dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                    if (!hoverIsLegal) {
                        drawLine(
                            color = hoverColor,
                            start = Offset(size.width * .28f, size.height * .28f),
                            end = Offset(size.width * .72f, size.height * .72f),
                            strokeWidth = 3.dp.toPx(),
                        )
                        drawLine(
                            color = hoverColor,
                            start = Offset(size.width * .72f, size.height * .28f),
                            end = Offset(size.width * .28f, size.height * .72f),
                            strokeWidth = 3.dp.toPx(),
                        )
                    }
                }
                if (checkGlow != null) {
                    val pulse = if (checkIsFatal) 1f else checkGlow
                    drawRect(palette.captureTarget.copy(alpha = .22f + .3f * pulse))
                    drawRoundRect(
                        color = palette.captureTarget.copy(alpha = .65f + .35f * pulse),
                        style = Stroke(width = (2.5f + 2f * pulse).dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                    drawCircle(
                        color = palette.captureTarget.copy(alpha = .30f + .25f * pulse),
                        radius = size.minDimension * (.46f + .08f * pulse),
                        style = Stroke(width = size.minDimension * .07f),
                    )
                }
                if (selected) {
                    drawRoundRect(
                        color = palette.selection,
                        style = Stroke(width = 2.5.dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
                if (isCaptureTarget) {
                    drawCircle(
                        color = palette.captureTarget.copy(alpha = .9f),
                        radius = size.minDimension * .42f,
                        style = Stroke(width = size.minDimension * .09f),
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (showRankLabel) {
            SquareLabel(
                text = "${square.rank + 1}",
                color = labelColor,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = 1.dp),
            )
        }
        if (showFileLabel) {
            SquareLabel(
                text = "${'a' + square.file}",
                color = labelColor,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp, bottom = 1.dp),
            )
        }

        if (pieceType != null) {
            ChessPiece(
                pieceType = pieceType,
                isWhite = pieceIsWhite,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1.dp)
                    .scale(pieceScale),
            )
        } else if (highlighted) {
            Box(
                modifier = Modifier
                    .fillMaxSize(.3f)
                    .clip(RoundedCornerShape(50))
                    .background(palette.moveTarget.copy(alpha = .78f)),
            )
        }
    }
}

@Composable
private fun SquareLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = color.copy(alpha = .8f),
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
    )
}

private fun Color.blendTowards(other: Color, fraction: Float): Color = Color(
    red = red + (other.red - red) * fraction,
    green = green + (other.green - green) * fraction,
    blue = blue + (other.blue - blue) * fraction,
    alpha = alpha,
)
