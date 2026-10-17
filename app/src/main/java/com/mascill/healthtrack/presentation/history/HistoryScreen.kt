package com.mascill.healthtrack.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import com.mascill.healthtrack.domain.model.MeasurementResult
import com.mascill.healthtrack.domain.model.StoredMeasurement
import com.mascill.healthtrack.domain.model.V235Report
import com.mascill.healthtrack.presentation.common.component.LoadingIndicator
import com.mascill.healthtrack.presentation.history.model.HistoryUIState
import com.mascill.healthtrack.presentation.theme.HealthTrackTheme

private const val SCREEN_PADDING = 16
private const val ITEM_SPACING = 8

/**
 * Stored measurements, newest first. A row opens that result and does not start Bluetooth.
 */
@Composable
fun HistoryScreen(
    onOpenMeasurement: (Int) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContent(
        uiState = uiState,
        onOpenMeasurement = onOpenMeasurement,
    )
}

@Composable
private fun HistoryContent(
    uiState: HistoryUIState,
    onOpenMeasurement: (Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }
            uiState.items.isEmpty() -> {
                Text(
                    text = "Belum ada pengukuran.",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(SCREEN_PADDING.dp),
                    verticalArrangement = Arrangement.spacedBy(ITEM_SPACING.dp),
                ) {
                    items(
                        items = uiState.items,
                        key = { it.id },
                    ) { item ->
                        HistoryRow(
                            item = item,
                            onClick = { onOpenMeasurement(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: StoredMeasurement,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("history-${item.id}"),
    ) {
        Column(modifier = Modifier.padding(SCREEN_PADDING.dp)) {
            Text(
                text = weightLabel(item),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = item.createdAt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            bodyLabel(item)?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun weightLabel(item: StoredMeasurement): String {
    val shown = item.result.scaleWeight.ifBlank {
        item.result.weightKg.toString().replace('.', ',')
    }
    return "$shown kg"
}

private fun bodyLabel(item: StoredMeasurement): String? {
    val height = item.result.heightCm
    val age = item.result.age
    if (height == null && age == null) {
        return null
    }
    val parts = mutableListOf<String>()
    if (height != null) {
        parts += "${heightText(height)} cm"
    }
    if (age != null) {
        parts += "umur $age"
    }
    return parts.joinToString(separator = ", ")
}

private fun heightText(heightCm: Double): String {
    val symbols = DecimalFormatSymbols(Locale.US).apply {
        decimalSeparator = ','
    }
    return DecimalFormat("0.#", symbols).apply {
        isGroupingUsed = false
    }.format(heightCm)
}

@Preview(showBackground = true)
@Composable
private fun HistoryContentPreview() {
    HealthTrackTheme {
        HistoryContent(
            onOpenMeasurement = {},
            uiState = HistoryUIState(
                isLoading = false,
                items = listOf(
                    StoredMeasurement(
                        id = 1,
                        createdAt = "2026-10-04T00:00:00Z",
                        result = MeasurementResult(
                            mac = "AA:BB:CC:DD:EE:FF",
                            productId = 1,
                            weightKg = 68.7,
                            scaleWeight = "68,7",
                            impedanceOhm = listOf(600.0),
                            formula = "v235",
                            code = 0,
                            heightCm = 166.0,
                            age = 29,
                            report = V235Report(
                                bfp = 1.0,
                                slm = 1.0,
                                slp = 1.0,
                                bwp = 1.0,
                                bmc = 1.0,
                                vfl = 1.0,
                                pp = 1.0,
                                smm = 1.0,
                                swt = 1.0,
                                mc = 1.0,
                                wc = 1.0,
                                fc = 1.0,
                                bmr = 1502,
                                ma = 36,
                                sbc = 61,
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}
