package com.chesspoints.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chesspoints.app.GameMode

@Composable
fun HomeScreen(
    selectedMode: GameMode,
    onModeSelected: (GameMode) -> Unit,
    onStartClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "Buy your army. Build the board. Then play.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Start with a 39-point budget, draft 16 pieces, place them on your side, and let the game begin.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        ModeOptionCard(
            title = "2 Player",
            subtitle = "Pass-and-play on one device for drafting, placement, and the match.",
            selected = selectedMode == GameMode.TwoPlayer,
            onClick = { onModeSelected(GameMode.TwoPlayer) },
        )

        ModeOptionCard(
            title = "vs AI",
            subtitle = "UI flow is ready now; the AI gameplay bridge will plug into the app state layer.",
            selected = selectedMode == GameMode.VersusAi,
            onClick = { onModeSelected(GameMode.VersusAi) },
        )

        Button(
            onClick = onStartClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Start drafting")
        }
    }
}

@Composable
private fun ModeOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = if (selected) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
        CardDefaults.cardColors()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = colors,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
            )
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
