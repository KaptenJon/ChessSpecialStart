package com.chesspoints.ui.placement

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.chesspoints.app.R
import com.chesspoints.app.i18n.UiText
import com.chesspoints.app.i18n.displayText
import com.chesspoints.app.i18n.nameRes
import com.chesspoints.app.i18n.resolve
import com.chesspoints.app.ui.theme.LocalBoardPalette
import com.chesspoints.engine.Board
import com.chesspoints.engine.DraftValidationResult
import com.chesspoints.engine.PieceType
import com.chesspoints.engine.PlacementZone
import com.chesspoints.engine.Square
import com.chesspoints.ui.shared.ChessBoard
import com.chesspoints.ui.shared.ChessPiece
import com.chesspoints.ui.shared.PieceBadge
import kotlin.math.roundToInt

/**
 * Setup screen. Buying and placing are a single atomic gesture: the player drags a piece from
 * the shop straight onto a legal square. There is no intermediate "selected piece" state - a
 * drop either buys and places the piece, or the engine rejects it with a localised reason.
 */
@Composable
fun PlacementScreen(
    board: Board,
    sideToPlace: com.chesspoints.engine.Color,
    boardPerspective: com.chesspoints.engine.Color = com.chesspoints.engine.Color.WHITE,
    sideToPlaceLabel: UiText,
    placementZone: PlacementZone?,
    remainingPieces: Map<PieceType, Int>,
    interactionEnabled: Boolean,
    helperMessage: UiText,
    onPieceDropped: (PieceType, Square) -> Unit,
    placeableSquares: (PieceType) -> Set<Square>,
    isDrafting: Boolean = false,
    currentDraftColorLabel: UiText = UiText.Raw(""),
    draftPieceCounts: Map<PieceType, Int> = emptyMap(),
    draftBudget: Int = 0,
    draftRequiredPieceCount: Int = 16,
    draftValidation: DraftValidationResult? = null,
    draftControlsEnabled: Boolean = false,
    draftPurchaseWarning: (PieceType) -> UiText? = { null },
    isAiMode: Boolean = false,
    aiThinking: Boolean = false,
    draftHelperMessage: UiText = UiText.Raw(""),
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val isWhiteSide = sideToPlace == com.chesspoints.engine.Color.WHITE
    val shopEnabled = interactionEnabled && (!isDrafting || draftControlsEnabled)
    val showTrayAboveBoard = !isWhiteSide && !isAiMode
    val shopTypes = if (isDrafting) {
        PieceType.entries.toList()
    } else {
        PieceType.entries.filter { remainingPieces.getOrDefault(it, 0) > 0 }
    }

    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var gridBounds by remember { mutableStateOf<Rect?>(null) }
    var dragPiece by remember { mutableStateOf<PieceType?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var blockedPiece by remember { mutableStateOf<PieceType?>(null) }

    // Any shop change (successful drop, side switch) invalidates a stale affordability warning.
    LaunchedEffect(draftPieceCounts, remainingPieces, sideToPlace) { blockedPiece = null }
    LaunchedEffect(shopEnabled) { if (!shopEnabled) dragPiece = null }

    val dropTargets = dragPiece?.let(placeableSquares).orEmpty()
    val hoveredSquare = dragPiece?.let { gridBounds?.squareAt(dragPosition, boardPerspective) }
    val hoverIsLegal = hoveredSquare != null && hoveredSquare in dropTargets
    val shopContent: @Composable () -> Unit = {
        if (isDrafting) {
            DraftMetrics(
                pieceCounts = draftPieceCounts,
                budget = draftBudget,
                requiredPieceCount = draftRequiredPieceCount,
            )
        }

        SectionLabel(
            text = if (isDrafting) {
                stringResource(R.string.draft_section_drag)
            } else {
                stringResource(R.string.placement_section_unplaced)
            },
        )
        Text(
            text = stringResource(R.string.draft_drop_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            shopTypes.forEach { pieceType ->
                val available = placeableSquares(pieceType).isNotEmpty()
                val count = if (isDrafting) {
                    draftPieceCounts.getOrDefault(pieceType, 0)
                } else {
                    remainingPieces.getOrDefault(pieceType, 0)
                }
                PieceShopChip(
                    pieceType = pieceType,
                    isWhite = isWhiteSide,
                    count = count,
                    showCost = isDrafting,
                    enabled = shopEnabled && available,
                    dragging = dragPiece == pieceType,
                    onBlockedTap = { blockedPiece = pieceType },
                    onDragStart = { root ->
                        blockedPiece = null
                        dragPiece = pieceType
                        dragPosition = root
                    },
                    onDragMove = { root -> dragPosition = root },
                    onDragEnd = {
                        val target = gridBounds?.squareAt(dragPosition, boardPerspective)
                        dragPiece = null
                        if (target != null) onPieceDropped(pieceType, target)
                    },
                    onDragCancel = { dragPiece = null },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val warning = blockedPiece?.let(draftPurchaseWarning)
        if (warning != null) {
            Text(
                text = warning.resolve(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        val validation = draftValidation
        if (validation is DraftValidationResult.Invalid) {
            val errorText = validation.errors.map { it.displayText().resolve() }
                .joinToString("\n") { "• $it" }
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(
                    text = errorText,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (isDrafting && validation is DraftValidationResult.Valid) {
            Text(
                text = stringResource(R.string.draft_auto_finish),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.boundsInRoot().topLeft },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhaseHeaderCard(
                title = if (isDrafting) {
                    stringResource(R.string.placement_header_build, currentDraftColorLabel.resolve())
                } else {
                    stringResource(R.string.placement_header_place, sideToPlaceLabel.resolve())
                },
                message = if (isDrafting) draftHelperMessage.resolve() else helperMessage.resolve(),
                zoneHint = placementZone?.let { zone ->
                    stringResource(
                        R.string.placement_zone_hint,
                        zone.ranks.first + 1,
                        zone.ranks.last + 1,
                    )
                },
                phaseLabel = if (isDrafting) {
                    stringResource(R.string.phase_step_setup)
                } else {
                    stringResource(R.string.phase_step_place)
                },
                aiBadge = isAiMode,
                aiThinking = aiThinking,
                isWhiteSide = isWhiteSide,
            )

            if (showTrayAboveBoard) {
                shopContent()
            }

            ChessBoard(
                board = board,
                selectedSquare = null,
                highlightedSquares = emptySet(),
                placementZone = placementZone,
                onSquareTap = { },
                perspective = boardPerspective,
                dropTargets = dropTargets,
                hoveredSquare = hoveredSquare,
                hoveredIsLegal = hoverIsLegal,
                onGridBoundsChanged = { gridBounds = it },
                modifier = Modifier.fillMaxWidth(),
            )

            if (!showTrayAboveBoard) {
                shopContent()
            }
        }

        // Ghost piece that follows the finger, sized to a board cell so the drop reads 1:1.
        val dragged = dragPiece
        if (dragged != null) {
            val cellPx = gridBounds?.let { it.width / 8f } ?: with(density) { 56.dp.toPx() }
            val ghostPx = cellPx * 1.15f
            val local = dragPosition - rootOrigin
            ChessPiece(
                pieceType = dragged,
                isWhite = isWhiteSide,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (local.x - ghostPx / 2f).roundToInt(),
                            (local.y - ghostPx * 0.72f).roundToInt(),
                        )
                    }
                    .size(with(density) { ghostPx.toDp() })
                    .alpha(0.95f),
            )
        }
    }
}

private fun Rect.squareAt(
    position: Offset,
    perspective: com.chesspoints.engine.Color,
): Square? {
    if (!contains(position)) return null
    val cellWidth = width / 8f
    val cellHeight = height / 8f
    if (cellWidth <= 0f || cellHeight <= 0f) return null
    val displayFile = ((position.x - left) / cellWidth).toInt().coerceIn(0, 7)
    val rankFromTop = ((position.y - top) / cellHeight).toInt().coerceIn(0, 7)
    val file = if (perspective == com.chesspoints.engine.Color.WHITE) displayFile else 7 - displayFile
    val rank = if (perspective == com.chesspoints.engine.Color.WHITE) 7 - rankFromTop else rankFromTop
    return Square(file, rank)
}

@Composable
private fun PhaseHeaderCard(
    title: String,
    message: String,
    zoneHint: String?,
    phaseLabel: String,
    aiBadge: Boolean,
    aiThinking: Boolean,
    isWhiteSide: Boolean,
) {
    val boardPalette = LocalBoardPalette.current
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = phaseLabel.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .8f),
                    modifier = Modifier.weight(1f),
                )
                if (aiBadge) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = stringResource(R.string.badge_vs_ai),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isWhiteSide) boardPalette.lightSquare else boardPalette.darkSquare),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .85f),
            )
            if (aiThinking) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .clearAndSetSemantics { },
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .15f),
                )
            }
            if (zoneHint != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = zoneHint,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun DraftMetrics(
    pieceCounts: Map<PieceType, Int>,
    budget: Int,
    requiredPieceCount: Int,
) {
    val spent = pieceCounts.entries.sumOf { (type, count) -> type.pointValue * count }
    val total = pieceCounts.values.sum()

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        DraftMetric(
            label = stringResource(R.string.draft_metric_budget),
            value = stringResource(R.string.draft_metric_value, spent, budget),
            caption = pluralStringResource(
                R.plurals.draft_points_left,
                (budget - spent).coerceAtLeast(0),
                (budget - spent).coerceAtLeast(0),
            ),
            progress = if (budget > 0) spent.toFloat() / budget else 0f,
            modifier = Modifier.weight(1f),
        )
        DraftMetric(
            label = stringResource(R.string.draft_metric_army),
            value = stringResource(R.string.draft_metric_value, total, requiredPieceCount),
            caption = pluralStringResource(
                R.plurals.draft_slots_left,
                (requiredPieceCount - total).coerceAtLeast(0),
                (requiredPieceCount - total).coerceAtLeast(0),
            ),
            progress = if (requiredPieceCount > 0) total.toFloat() / requiredPieceCount else 0f,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DraftMetric(
    label: String,
    value: String,
    caption: String,
    progress: Float,
    modifier: Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PieceShopChip(
    pieceType: PieceType,
    isWhite: Boolean,
    count: Int,
    showCost: Boolean,
    enabled: Boolean,
    dragging: Boolean,
    onBlockedTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val pieceName = stringResource(pieceType.nameRes)
    val dragDescription = stringResource(R.string.cd_drag_piece, pieceName)
    val lift by animateFloatAsState(
        targetValue = if (dragging) 1.08f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "chipLift",
    )

    Card(
        modifier = modifier
            .scale(lift)
            .alpha(if (enabled) 1f else .45f)
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .semantics { contentDescription = dragDescription }
            .then(
                if (enabled) {
                    Modifier.pointerInput(pieceType) {
                        detectDragGestures(
                            onDragStart = { local -> onDragStart(origin + local) },
                            onDrag = { change, _ ->
                                change.consume()
                                onDragMove(origin + change.position)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                        )
                    }
                } else {
                    Modifier.clickable(onClick = onBlockedTap)
                },
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (dragging) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PieceBadge(
                pieceType = pieceType,
                isWhite = isWhite,
                size = 36.dp,
                modifier = Modifier.alpha(if (dragging) .35f else 1f),
            )
            Text(
                text = when {
                    showCost && pieceType == PieceType.KING -> stringResource(R.string.draft_king_free)
                    showCost -> stringResource(R.string.shop_piece_cost, pieceType.pointValue)
                    else -> stringResource(R.string.piece_count_short, count)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (showCost && count > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                ) {
                    Text(
                        text = stringResource(R.string.piece_count_short, count),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
