package com.mascill.healthtrack.presentation.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mascill.healthtrack.presentation.session.model.SessionUIState
import com.mascill.healthtrack.presentation.theme.HealthTrackTheme

private const val SCREEN_PADDING = 16
private const val ITEM_SPACING = 12

/**
 * Start control for a measurement. The scale is not connected yet.
 */
@Composable
fun SessionScreen(
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SessionContent(
        uiState = uiState,
        onStartClick = viewModel::onStartClick,
    )
}

@Composable
private fun SessionContent(
    uiState: SessionUIState,
    onStartClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SCREEN_PADDING.dp),
        verticalArrangement = Arrangement.spacedBy(ITEM_SPACING.dp),
    ) {
        Text(
            text = "Sesi ukur",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = if (uiState.hasProfile) {
                "Profil tersimpan."
            } else {
                "Profil tubuh belum tersimpan."
            },
        )
        Button(onClick = onStartClick) {
            Text("Mulai")
        }
        val message = uiState.message
        if (message != null) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (uiState.isFlowOpen) {
            Text("Timbangan belum disambungkan.")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionContentPreview() {
    HealthTrackTheme {
        SessionContent(
            uiState = SessionUIState(hasProfile = false),
            onStartClick = {},
        )
    }
}
