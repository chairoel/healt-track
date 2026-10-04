package com.mascill.healthtrack.presentation.result

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Placeholder for a stored measurement.
 * Weight and indexes are shown when the result screen is filled in.
 */
@Composable
fun ResultScreen(
    measurementId: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("result-$measurementId"),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Hasil pengukuran")
    }
}
