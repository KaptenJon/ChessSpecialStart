package com.chesspoints.ui.draft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chesspoints.engine.DraftValidationResult
import com.chesspoints.engine.PieceType

@Composable
fun DraftScreen(
    currentColor: com.chesspoints.engine.Color,
    currentColorLabel: String,
    pieceCounts: Map<PieceType, Int>,
    budget: Int,
    requiredPieceCount: Int,
    validationResult: DraftValidationResult,
    controlsEnabled: Boolean,
    isAiMode: Boolean,
    helperMessage: String,
    onIncrement: (PieceType) -> Unit,
    onDecrement: (PieceType) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spent = pieceCounts.entries.sumOf { (type, count) -> type.pointValue * count }
    val totalPieces = pieceCounts.values.sum()
    val isValid = validationResult is DraftValidationResult.Valid

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeadlineCard(
            title = "$currentColorLabel drafts next",
            body = if (isAiMode && currentColor == com.chesspoints.engine.Color.BLACK) {
                helperMessage
            } else {
                "Spend up to $budget points and finish with exactly $requiredPieceCount pieces."
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                label = "Budget",
                value = "$spent / $budget",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                label = "Army size",
                value = "$totalPieces / $requiredPieceCount",
                modifier = Modifier.weight(1f),
            )
        }

        ValidationCard(validationResult = validationResult, helperMessage = helperMessage)

        PieceType.entries.forEach { pieceType ->
            PieceCounterRow(
                pieceType = pieceType,
                count = pieceCounts.getValue(pieceType),
                enabled = controlsEnabled && pieceType != PieceType.KING,
                onIncrement = { onIncrement(pieceType) },
                onDecrement = { onDecrement(pieceType) },
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onConfirm,
            enabled = controlsEnabled && isValid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (currentColor == com.chesspoints.engine.Color.BLACK && isAiMode) {
                    "Await AI"
                } else {
                    "Confirm roster"
                },
            )
        }
    }
}

@Composable
private fun HeadlineCard(
    title: String,
    body: String,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ValidationCard(
    validationResult: DraftValidationResult,
    helperMessage: String,
) {
    val isValid = validationResult is DraftValidationResult.Valid
    val containerColor = if (isValid) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isValid) "Roster is valid" else "Keep tuning the roster",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (isValid) {
                Text(text = helperMessage)
            } else {
                val errors = (validationResult as DraftValidationResult.Invalid).errors
                errors.forEach { error ->
                    Text(text = "• ${error.message}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PieceCounterRow(
    pieceType: PieceType,
    count: Int,
    enabled: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = pieceType.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (pieceType == PieceType.KING) "Free and mandatory" else "${pieceType.pointValue} points",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (pieceType == PieceType.KING) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = count.toString(),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDecrement, enabled = enabled && count > 0) {
                        Text(text = "−")
                    }
                    Text(text = count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Button(onClick = onIncrement, enabled = enabled) {
                        Text(text = "+")
                    }
                }
            }
        }
    }
}

private val PieceType.displayName: String
    get() = name.lowercase().replaceFirstChar { it.titlecase() }
