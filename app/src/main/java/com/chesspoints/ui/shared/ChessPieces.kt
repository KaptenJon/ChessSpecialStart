package com.chesspoints.ui.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesspoints.app.R
import com.chesspoints.app.i18n.nameRes
import com.chesspoints.engine.PieceType

/**
 * Colour ramp used to fake a sculpted, three-dimensional carved piece:
 * a specular top-left highlight, two mid tones for the turned body, a
 * shaded bottom-right side, plus an outline and a warm accent for
 * engraved details.
 */
@Immutable
data class PiecePalette(
    val specular: Color,
    val light: Color,
    val mid: Color,
    val dark: Color,
    val outline: Color,
    val accent: Color,
) {
    companion object {
        val Ivory = PiecePalette(
            specular = Color(0xFFFFFDF6),
            light = Color(0xFFF7E8CB),
            mid = Color(0xFFE0C79C),
            dark = Color(0xFFB2906A),
            outline = Color(0xFF5B402A),
            accent = Color(0xFF8A6A47),
        )
        val Ebony = PiecePalette(
            specular = Color(0xFF8D7C6E),
            light = Color(0xFF574A41),
            mid = Color(0xFF322A25),
            dark = Color(0xFF17120F),
            outline = Color(0xFF0A0706),
            accent = Color(0xFFA98A64),
        )

        fun of(isWhite: Boolean): PiecePalette = if (isWhite) Ivory else Ebony
    }
}

/**
 * Fixed-size piece chip used by trays, catalogues and captured rows.
 */
@Composable
fun PieceBadge(
    pieceType: PieceType,
    isWhite: Boolean,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    ChessPiece(
        pieceType = pieceType,
        isWhite = isWhite,
        modifier = modifier.size(size),
    )
}

/**
 * Renders a single chess piece, scaled to fill whatever bounds it is given.
 */
@Composable
fun ChessPiece(
    pieceType: PieceType,
    isWhite: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(
        R.string.cd_chess_piece,
        stringResource(if (isWhite) R.string.color_white else R.string.color_black),
        stringResource(pieceType.nameRes),
    )
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription = description },
    ) {
        drawChessPiece(pieceType, PiecePalette.of(isWhite))
    }
}


/* --------------------------------------------------------------------------
 * Drawing
 *
 * Every piece is authored in a 0f..1f unit square so the same geometry scales
 * from a 24dp captured-piece chip up to a full board square. Shapes are
 * layered back-to-front: cast shadow, plinth, body, then engraved detail and
 * specular highlights on top.
 * ----------------------------------------------------------------------- */

internal fun DrawScope.drawChessPiece(pieceType: PieceType, palette: PiecePalette) {
    val s = size.minDimension
    val ox = (size.width - s) / 2f
    val oy = (size.height - s) / 2f

    fun px(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    fun u(value: Float) = value * s

    val body = Brush.linearGradient(
        colorStops = arrayOf(
            0f to palette.specular,
            0.26f to palette.light,
            0.62f to palette.mid,
            1f to palette.dark,
        ),
        start = px(0.16f, 0.04f),
        end = px(0.92f, 0.96f),
    )
    val outlineWidth = u(0.022f)
    val outlineStroke = Stroke(width = outlineWidth, join = StrokeJoin.Round, cap = StrokeCap.Round)

    fun sculpt(path: Path) {
        drawPath(path, body)
        drawPath(path, palette.outline, style = outlineStroke)
    }

    fun roundedPath(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
    ): Path = Path().apply {
        addRoundRect(
            RoundRect(
                Rect(px(left, top), px(right, bottom)),
                CornerRadius(u(radius), u(radius)),
            ),
        )
    }

    // Contact shadow on the square underneath the piece.
    drawOval(
        color = Color.Black.copy(alpha = 0.22f),
        topLeft = px(0.14f, 0.855f),
        size = Size(u(0.72f), u(0.11f)),
    )

    // Plinth and collar are shared by every piece so the set reads as one family.
    val plinth = Path().apply {
        moveTo(px(0.16f, 0.90f).x, px(0.16f, 0.90f).y)
        lineTo(px(0.84f, 0.90f).x, px(0.84f, 0.90f).y)
        lineTo(px(0.78f, 0.815f).x, px(0.78f, 0.815f).y)
        lineTo(px(0.22f, 0.815f).x, px(0.22f, 0.815f).y)
        close()
    }
    val collar = roundedPath(0.255f, 0.755f, 0.745f, 0.825f, 0.025f)

    when (pieceType) {
        PieceType.PAWN -> {
            val stem = Path().apply {
                moveTo(px(0.375f, 0.765f).x, px(0.375f, 0.765f).y)
                cubicTo(
                    px(0.40f, 0.65f).x, px(0.40f, 0.65f).y,
                    px(0.415f, 0.56f).x, px(0.415f, 0.56f).y,
                    px(0.42f, 0.485f).x, px(0.42f, 0.485f).y,
                )
                lineTo(px(0.58f, 0.485f).x, px(0.58f, 0.485f).y)
                cubicTo(
                    px(0.585f, 0.56f).x, px(0.585f, 0.56f).y,
                    px(0.60f, 0.65f).x, px(0.60f, 0.65f).y,
                    px(0.625f, 0.765f).x, px(0.625f, 0.765f).y,
                )
                close()
            }
            sculpt(stem)
            sculpt(plinth)
            sculpt(collar)
            sculpt(roundedPath(0.335f, 0.425f, 0.665f, 0.495f, 0.03f))
            val head = Path().apply { addOval(Rect(px(0.335f, 0.155f), px(0.665f, 0.44f))) }
            sculpt(head)
            drawCircle(palette.specular.copy(alpha = 0.55f), u(0.045f), px(0.435f, 0.235f))
        }

        PieceType.KNIGHT -> {
            val head = Path().apply {
                moveTo(px(0.285f, 0.775f).x, px(0.285f, 0.775f).y)
                lineTo(px(0.30f, 0.60f).x, px(0.30f, 0.60f).y)
                cubicTo(
                    px(0.255f, 0.45f).x, px(0.255f, 0.45f).y,
                    px(0.30f, 0.29f).x, px(0.30f, 0.29f).y,
                    px(0.415f, 0.215f).x, px(0.415f, 0.215f).y,
                )
                lineTo(px(0.375f, 0.095f).x, px(0.375f, 0.095f).y)
                lineTo(px(0.485f, 0.175f).x, px(0.485f, 0.175f).y)
                lineTo(px(0.525f, 0.075f).x, px(0.525f, 0.075f).y)
                cubicTo(
                    px(0.68f, 0.13f).x, px(0.68f, 0.13f).y,
                    px(0.785f, 0.255f).x, px(0.785f, 0.255f).y,
                    px(0.80f, 0.365f).x, px(0.80f, 0.365f).y,
                )
                lineTo(px(0.655f, 0.425f).x, px(0.655f, 0.425f).y)
                cubicTo(
                    px(0.575f, 0.465f).x, px(0.575f, 0.465f).y,
                    px(0.555f, 0.52f).x, px(0.555f, 0.52f).y,
                    px(0.595f, 0.60f).x, px(0.595f, 0.60f).y,
                )
                lineTo(px(0.665f, 0.775f).x, px(0.665f, 0.775f).y)
                close()
            }
            sculpt(head)
            sculpt(plinth)
            sculpt(collar)
            // Mane, eye and nostril.
            drawPath(
                Path().apply {
                    moveTo(px(0.44f, 0.20f).x, px(0.44f, 0.20f).y)
                    cubicTo(
                        px(0.355f, 0.30f).x, px(0.355f, 0.30f).y,
                        px(0.325f, 0.44f).x, px(0.325f, 0.44f).y,
                        px(0.345f, 0.585f).x, px(0.345f, 0.585f).y,
                    )
                },
                palette.accent.copy(alpha = 0.75f),
                style = Stroke(width = u(0.03f), cap = StrokeCap.Round),
            )
            drawCircle(palette.outline, u(0.032f), px(0.565f, 0.315f))
            drawCircle(palette.specular.copy(alpha = 0.7f), u(0.012f), px(0.575f, 0.305f))
            drawCircle(palette.outline.copy(alpha = 0.8f), u(0.022f), px(0.745f, 0.355f))
        }

        PieceType.BISHOP -> {
            val mitre = Path().apply {
                moveTo(px(0.33f, 0.765f).x, px(0.33f, 0.765f).y)
                cubicTo(
                    px(0.30f, 0.60f).x, px(0.30f, 0.60f).y,
                    px(0.34f, 0.40f).x, px(0.34f, 0.40f).y,
                    px(0.50f, 0.135f).x, px(0.50f, 0.135f).y,
                )
                cubicTo(
                    px(0.66f, 0.40f).x, px(0.66f, 0.40f).y,
                    px(0.70f, 0.60f).x, px(0.70f, 0.60f).y,
                    px(0.67f, 0.765f).x, px(0.67f, 0.765f).y,
                )
                close()
            }
            sculpt(mitre)
            sculpt(plinth)
            sculpt(collar)
            sculpt(roundedPath(0.305f, 0.50f, 0.695f, 0.565f, 0.028f))
            val finial = Path().apply { addOval(Rect(px(0.445f, 0.045f), px(0.555f, 0.155f))) }
            sculpt(finial)
            // Mitre slit.
            drawLine(
                palette.outline.copy(alpha = 0.8f),
                px(0.485f, 0.27f),
                px(0.60f, 0.40f),
                strokeWidth = u(0.028f),
                cap = StrokeCap.Round,
            )
            drawLine(
                palette.specular.copy(alpha = 0.45f),
                px(0.405f, 0.33f),
                px(0.385f, 0.60f),
                strokeWidth = u(0.035f),
                cap = StrokeCap.Round,
            )
        }

        PieceType.ROOK -> {
            val tower = Path().apply {
                moveTo(px(0.295f, 0.765f).x, px(0.295f, 0.765f).y)
                cubicTo(
                    px(0.325f, 0.60f).x, px(0.325f, 0.60f).y,
                    px(0.335f, 0.46f).x, px(0.335f, 0.46f).y,
                    px(0.335f, 0.335f).x, px(0.335f, 0.335f).y,
                )
                lineTo(px(0.665f, 0.335f).x, px(0.665f, 0.335f).y)
                cubicTo(
                    px(0.665f, 0.46f).x, px(0.665f, 0.46f).y,
                    px(0.675f, 0.60f).x, px(0.675f, 0.60f).y,
                    px(0.705f, 0.765f).x, px(0.705f, 0.765f).y,
                )
                close()
            }
            sculpt(tower)
            sculpt(plinth)
            sculpt(collar)
            // Battlement: lintel plus four merlons.
            sculpt(roundedPath(0.26f, 0.275f, 0.74f, 0.35f, 0.025f))
            val merlonTops = listOf(0.265f, 0.395f, 0.525f, 0.655f)
            merlonTops.forEach { left ->
                sculpt(roundedPath(left, 0.145f, left + 0.08f, 0.285f, 0.02f))
            }
            drawLine(
                palette.specular.copy(alpha = 0.4f),
                px(0.385f, 0.375f),
                px(0.375f, 0.70f),
                strokeWidth = u(0.035f),
                cap = StrokeCap.Round,
            )
        }

        PieceType.QUEEN -> {
            val gown = Path().apply {
                moveTo(px(0.275f, 0.765f).x, px(0.275f, 0.765f).y)
                cubicTo(
                    px(0.335f, 0.63f).x, px(0.335f, 0.63f).y,
                    px(0.375f, 0.52f).x, px(0.375f, 0.52f).y,
                    px(0.385f, 0.425f).x, px(0.385f, 0.425f).y,
                )
                lineTo(px(0.615f, 0.425f).x, px(0.615f, 0.425f).y)
                cubicTo(
                    px(0.625f, 0.52f).x, px(0.625f, 0.52f).y,
                    px(0.665f, 0.63f).x, px(0.665f, 0.63f).y,
                    px(0.725f, 0.765f).x, px(0.725f, 0.765f).y,
                )
                close()
            }
            sculpt(gown)
            sculpt(plinth)
            sculpt(collar)
            sculpt(roundedPath(0.325f, 0.375f, 0.675f, 0.44f, 0.028f))
            // Five-point crown.
            val crown = Path().apply {
                moveTo(px(0.315f, 0.385f).x, px(0.315f, 0.385f).y)
                lineTo(px(0.255f, 0.185f).x, px(0.255f, 0.185f).y)
                lineTo(px(0.385f, 0.285f).x, px(0.385f, 0.285f).y)
                lineTo(px(0.50f, 0.115f).x, px(0.50f, 0.115f).y)
                lineTo(px(0.615f, 0.285f).x, px(0.615f, 0.285f).y)
                lineTo(px(0.745f, 0.185f).x, px(0.745f, 0.185f).y)
                lineTo(px(0.685f, 0.385f).x, px(0.685f, 0.385f).y)
                close()
            }
            sculpt(crown)
            listOf(
                Offset(0.255f, 0.165f),
                Offset(0.50f, 0.095f),
                Offset(0.745f, 0.165f),
            ).forEach { point ->
                val pearl = Path().apply {
                    addOval(
                        Rect(
                            px(point.x - 0.055f, point.y - 0.055f),
                            px(point.x + 0.055f, point.y + 0.055f),
                        ),
                    )
                }
                sculpt(pearl)
            }
            drawLine(
                palette.specular.copy(alpha = 0.4f),
                px(0.40f, 0.47f),
                px(0.355f, 0.71f),
                strokeWidth = u(0.035f),
                cap = StrokeCap.Round,
            )
        }

        PieceType.KING -> {
            val gown = Path().apply {
                moveTo(px(0.275f, 0.765f).x, px(0.275f, 0.765f).y)
                cubicTo(
                    px(0.335f, 0.63f).x, px(0.335f, 0.63f).y,
                    px(0.375f, 0.52f).x, px(0.375f, 0.52f).y,
                    px(0.385f, 0.44f).x, px(0.385f, 0.44f).y,
                )
                lineTo(px(0.615f, 0.44f).x, px(0.615f, 0.44f).y)
                cubicTo(
                    px(0.625f, 0.52f).x, px(0.625f, 0.52f).y,
                    px(0.665f, 0.63f).x, px(0.665f, 0.63f).y,
                    px(0.725f, 0.765f).x, px(0.725f, 0.765f).y,
                )
                close()
            }
            sculpt(gown)
            sculpt(plinth)
            sculpt(collar)
            sculpt(roundedPath(0.325f, 0.385f, 0.675f, 0.45f, 0.028f))
            // Crown band with a gentle scoop.
            val band = Path().apply {
                moveTo(px(0.325f, 0.39f).x, px(0.325f, 0.39f).y)
                lineTo(px(0.30f, 0.245f).x, px(0.30f, 0.245f).y)
                cubicTo(
                    px(0.39f, 0.305f).x, px(0.39f, 0.305f).y,
                    px(0.61f, 0.305f).x, px(0.61f, 0.305f).y,
                    px(0.70f, 0.245f).x, px(0.70f, 0.245f).y,
                )
                lineTo(px(0.675f, 0.39f).x, px(0.675f, 0.39f).y)
                close()
            }
            sculpt(band)
            // Cross finial.
            sculpt(roundedPath(0.462f, 0.04f, 0.538f, 0.265f, 0.025f))
            sculpt(roundedPath(0.375f, 0.10f, 0.625f, 0.172f, 0.025f))
            drawLine(
                palette.specular.copy(alpha = 0.4f),
                px(0.40f, 0.48f),
                px(0.355f, 0.71f),
                strokeWidth = u(0.035f),
                cap = StrokeCap.Round,
            )
        }
    }

    // Ambient occlusion where the plinth meets the board.
    drawLine(
        Color.Black.copy(alpha = 0.18f),
        px(0.20f, 0.885f),
        px(0.80f, 0.885f),
        strokeWidth = u(0.03f),
        cap = StrokeCap.Round,
    )
}
