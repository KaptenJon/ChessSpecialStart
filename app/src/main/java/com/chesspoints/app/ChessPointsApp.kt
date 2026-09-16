package com.chesspoints.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chesspoints.app.ui.theme.ChessPointsTheme
import com.chesspoints.ui.draft.DraftScreen
import com.chesspoints.ui.game.GameScreen
import com.chesspoints.ui.home.HomeScreen
import com.chesspoints.ui.placement.PlacementScreen

@Composable
fun ChessPointsApp() {
    ChessPointsTheme {
        val appState = rememberChessPointsState()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(appState.bannerMessage) {
            val message = appState.bannerMessage ?: return@LaunchedEffect
            snackbarHostState.showSnackbar(message)
            appState.clearBanner()
        }

        ChessPointsScaffold(
            title = appState.screenTitle,
            snackbarHostState = snackbarHostState,
        ) { innerPadding ->
            when (appState.currentScreen) {
                AppScreen.Home -> HomeScreen(
                    selectedMode = appState.gameMode,
                    onModeSelected = appState::selectMode,
                    onStartClick = appState::startGame,
                )

                AppScreen.Draft -> DraftScreen(
                    currentColor = appState.currentDraftColor,
                    currentColorLabel = appState.currentDraftColorLabel,
                    pieceCounts = appState.draftPieceCounts,
                    budget = appState.draftRules.budget,
                    requiredPieceCount = appState.draftRules.requiredPieceCount,
                    validationResult = appState.draftValidation,
                    controlsEnabled = appState.isDraftEditable,
                    isAiMode = appState.gameMode == GameMode.VersusAi,
                    helperMessage = appState.draftHelperMessage,
                    onIncrement = appState::incrementDraftPiece,
                    onDecrement = appState::decrementDraftPiece,
                    onConfirm = appState::confirmDraft,
                    modifier = Modifier.padding(innerPadding),
                )

                AppScreen.Placement -> PlacementScreen(
                    board = appState.currentBoard,
                    sideToPlace = appState.placementSideToPlace,
                    sideToPlaceLabel = appState.placementSideToPlaceLabel,
                    placementZone = appState.placementZone,
                    remainingPieces = appState.remainingPlacementPieces,
                    selectedPieceType = appState.selectedPlacementPieceType,
                    interactionEnabled = appState.isPlacementInteractive,
                    helperMessage = appState.placementHelperMessage,
                    onPieceSelected = appState::selectPlacementPiece,
                    onSquareSelected = appState::placeSelectedPieceAt,
                    modifier = Modifier.padding(innerPadding),
                )

                AppScreen.Game -> GameScreen(
                    board = appState.currentBoard,
                    currentTurnLabel = appState.playSideToMoveLabel,
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
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChessPointsScaffold(
    title: String,
    snackbarHostState: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "ChessPoints")
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.surface,
            content = { content(PaddingValues(16.dp)) },
        )
    }
}
