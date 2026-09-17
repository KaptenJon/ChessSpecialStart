package com.chesspoints.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chesspoints.app.i18n.UiText
import com.chesspoints.app.i18n.resolve
import com.chesspoints.app.ui.theme.ChessPointsTheme
import com.chesspoints.ui.game.CheckAlert
import com.chesspoints.ui.game.GameScreen
import com.chesspoints.ui.home.HomeScreen
import com.chesspoints.ui.placement.PlacementScreen

@Composable
fun ChessPointsApp() {
    ChessPointsTheme {
        val appState = rememberChessPointsState()
        val snackbarHostState = remember { SnackbarHostState() }
        val resources = LocalResources.current

        LaunchedEffect(appState.bannerMessage) {
            val message = appState.bannerMessage ?: return@LaunchedEffect
            snackbarHostState.showSnackbar(message.resolve(resources))
            appState.clearBanner()
        }

        ChessPointsScaffold(
            title = appState.screenTitle,
            snackbarHostState = snackbarHostState,
            canNavigateBack = appState.currentScreen != AppScreen.Home,
            confirmBeforeLeaving = appState.hasMatchInProgress,
            onNavigateHome = appState::navigateHome,
            onNewGame = appState::startGame,
        ) { innerPadding ->
            when (appState.currentScreen) {
                AppScreen.Home -> HomeScreen(
                    selectedMode = appState.gameMode,
                    onModeSelected = appState::selectMode,
                    onStartClick = appState::startGame,
                    modifier = Modifier.padding(innerPadding),
                )

                AppScreen.Placement -> PlacementScreen(
                    board = appState.currentBoard,
                    sideToPlace = appState.placementSideToPlace,
                    boardPerspective = com.chesspoints.engine.Color.WHITE,
                    sideToPlaceLabel = appState.placementSideToPlaceLabel,
                    placementZone = appState.placementZone,
                    remainingPieces = appState.remainingPlacementPieces,
                    interactionEnabled = appState.isPlacementInteractive,
                    helperMessage = appState.placementHelperMessage,
                    onPieceDropped = appState::dropPieceOn,
                    placeableSquares = appState::placeableSquares,
                    isDrafting = appState.isDrafting,
                    currentDraftColorLabel = appState.currentDraftColorLabel,
                    draftPieceCounts = appState.draftPieceCounts,
                    draftBudget = appState.draftRules.budget,
                    draftRequiredPieceCount = appState.draftRules.requiredPieceCount,
                    draftValidation = appState.draftValidation,
                    draftControlsEnabled = appState.isDraftEditable,
                    draftPurchaseWarning = appState::draftPurchaseWarning,
                    isAiMode = appState.gameMode == GameMode.VersusAi,
                    aiThinking = appState.gameMode == GameMode.VersusAi &&
                        (!appState.isPlacementInteractive || (appState.isDrafting && !appState.isDraftEditable)),
                    draftHelperMessage = appState.draftHelperMessage,
                    modifier = Modifier.padding(innerPadding),
                )

                AppScreen.Game -> GameScreen(
                    board = appState.currentBoard,
                    currentTurnLabel = appState.playSideToMoveLabel,
                    whiteToMove = appState.playSideToMove == com.chesspoints.engine.Color.WHITE,
                    boardPerspective = com.chesspoints.engine.Color.WHITE,
                    selectedSquare = appState.selectedMoveSquare,
                    legalMoves = appState.selectedLegalMoves,
                    capturedByWhite = appState.capturedPieces(ColorPerspective.White),
                    capturedByBlack = appState.capturedPieces(ColorPerspective.Black),
                    statusMessage = appState.gameStatusMessage,
                    promotionChoices = appState.pendingPromotionMoves,
                    interactionEnabled = appState.isGameInteractive,
                    onSquareSelected = appState::handleGameSquareTap,
                    onPromotionSelected = appState::completePromotion,
                    onPromotionDismissed = appState::dismissPromotionPrompt,
                    checkAlert = appState.checkedKingColor?.let { checkedColor ->
                        val isFatal = appState.isCheckFatal
                        CheckAlert(
                            kingSquare = appState.checkedKingSquare,
                            isFatal = isFatal,
                            title = UiText.Res(
                                if (isFatal) R.string.game_checkmate_title else R.string.game_check_title,
                            ),
                            body = UiText.of(
                                if (isFatal) R.string.game_checkmate_body else R.string.game_check_body,
                                appState.colorLabelFor(checkedColor),
                            ),
                        )
                    },
                    gameOverMessage = appState.gameOverMessage,
                    onNewGame = appState::startGame,
                    onBackToHome = appState::navigateHome,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChessPointsScaffold(
    title: UiText,
    snackbarHostState: SnackbarHostState,
    canNavigateBack: Boolean,
    confirmBeforeLeaving: Boolean,
    onNavigateHome: () -> Unit,
    onNewGame: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    var pendingLeaveAction by remember { mutableStateOf<LeaveAction?>(null) }

    fun requestLeave(action: LeaveAction) {
        if (confirmBeforeLeaving) {
            pendingLeaveAction = action
        } else {
            when (action) {
                LeaveAction.Home -> onNavigateHome()
                LeaveAction.NewGame -> onNewGame()
            }
        }
    }

    BackHandler(enabled = canNavigateBack) { requestLeave(LeaveAction.Home) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = { requestLeave(LeaveAction.Home) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_chesspoints_logo),
                            contentDescription = stringResource(R.string.cd_app_logo),
                            modifier = Modifier.padding(start = 12.dp).size(32.dp),
                        )
                    }
                },
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = title.resolve(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (canNavigateBack) {
                        TextButton(onClick = { requestLeave(LeaveAction.NewGame) }) {
                            Text(stringResource(R.string.action_new_game))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        ) {
            content(PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp))
        }

        val leaveAction = pendingLeaveAction
        if (leaveAction != null) {
            AlertDialog(
                onDismissRequest = { pendingLeaveAction = null },
                shape = MaterialTheme.shapes.large,
                title = { Text(stringResource(R.string.dialog_leave_title)) },
                text = { Text(stringResource(R.string.dialog_leave_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingLeaveAction = null
                            when (leaveAction) {
                                LeaveAction.Home -> onNavigateHome()
                                LeaveAction.NewGame -> onNewGame()
                            }
                        },
                    ) {
                        Text(
                            stringResource(
                                when (leaveAction) {
                                    LeaveAction.Home -> R.string.action_leave
                                    LeaveAction.NewGame -> R.string.action_new_game
                                },
                            ),
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingLeaveAction = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

private enum class LeaveAction { Home, NewGame }
