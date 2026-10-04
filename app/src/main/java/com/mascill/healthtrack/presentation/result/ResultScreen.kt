package com.mascill.healthtrack.presentation.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mascill.healthtrack.presentation.common.component.LoadingIndicator
import com.mascill.healthtrack.presentation.result.model.ResultRow
import com.mascill.healthtrack.presentation.result.model.ResultUIState
import com.mascill.healthtrack.presentation.theme.HealthTrackTheme

private const val SCREEN_PADDING = 16
private const val ITEM_SPACING = 8

/**
 * Weight, rounded indexes, and the last broadcast and GATT hex for one measurement.
 */
@Composable
fun ResultScreen(
    measurementId: Int,
    heightCm: Double,
    broadcastHex: String,
    gattHex: String,
    modifier: Modifier = Modifier,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(measurementId, heightCm, broadcastHex, gattHex) {
        viewModel.load(measurementId, heightCm, broadcastHex, gattHex)
    }
    ResultContent(
        measurementId = measurementId,
        uiState = uiState,
        modifier = modifier,
    )
}

@Composable
private fun ResultContent(
    measurementId: Int,
    uiState: ResultUIState,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isLoading -> LoadingIndicator(modifier = modifier.fillMaxSize())
        uiState.error != null -> {
            Text(
                text = uiState.error.message,
                modifier = modifier.padding(SCREEN_PADDING.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        else -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(SCREEN_PADDING.dp)
                    .testTag("result-$measurementId"),
                verticalArrangement = Arrangement.spacedBy(ITEM_SPACING.dp),
            ) {
                Text(
                    text = "Hasil pengukuran",
                    style = MaterialTheme.typography.headlineSmall,
                )
                val weight = uiState.weightText
                if (weight != null) {
                    Text(
                        text = "Berat $weight kg",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                uiState.rows.forEach { row ->
                    ResultLine(row = row)
                }
                HexLine(label = "Siaran", hex = uiState.broadcastHex)
                HexLine(label = "GATT", hex = uiState.gattHex)
            }
        }
    }
}

@Composable
private fun ResultLine(row: ResultRow) {
    Text(text = "${row.label}: ${row.value}")
}

@Composable
private fun HexLine(label: String, hex: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        SelectionContainer {
            Text(
                text = hex,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ResultContentPreview() {
    HealthTrackTheme {
        ResultContent(
            measurementId = 1,
            uiState = ResultUIState(
                isLoading = false,
                weightText = "68.7",
                rows = listOf(
                    ResultRow("Lemak", "23,1"),
                    ResultRow("Tulang (kg)", "2,77"),
                    ResultRow("Metabolisme", "1502,0"),
                ),
                broadcastHex = "c007",
                gattHex = "ca11",
            ),
        )
    }
}
