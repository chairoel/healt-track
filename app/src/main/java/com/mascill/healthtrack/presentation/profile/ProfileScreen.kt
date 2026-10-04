package com.mascill.healthtrack.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.presentation.profile.model.ProfileUIState
import com.mascill.healthtrack.presentation.theme.HealthTrackTheme

private const val SCREEN_PADDING = 16
private const val ITEM_SPACING = 12

/**
 * Height, age, and sex. Saving inserts a new body row and shows the newest one.
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileContent(
        uiState = uiState,
        onHeightChange = viewModel::onHeightChange,
        onAgeChange = viewModel::onAgeChange,
        onSexSelected = viewModel::onSexSelected,
        onSaveClick = viewModel::onSaveClick,
    )
}

@Composable
private fun ProfileContent(
    uiState: ProfileUIState,
    onHeightChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onSexSelected: (Int) -> Unit,
    onSaveClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SCREEN_PADDING.dp),
        verticalArrangement = Arrangement.spacedBy(ITEM_SPACING.dp),
    ) {
        Text(
            text = "Profil tubuh",
            style = MaterialTheme.typography.headlineSmall,
        )
        OutlinedTextField(
            value = uiState.heightCm,
            onValueChange = onHeightChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tinggi (cm)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = uiState.age,
            onValueChange = onAgeChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Umur") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Text(text = "Jenis kelamin")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.sex == 0,
                onClick = { onSexSelected(0) },
                label = { Text("Perempuan") },
            )
            FilterChip(
                selected = uiState.sex == 1,
                onClick = { onSexSelected(1) },
                label = { Text("Laki-laki") },
            )
        }
        Button(
            onClick = onSaveClick,
            enabled = !uiState.isSaving,
        ) {
            Text("Simpan")
        }
        val error = uiState.error
        if (error != null) {
            Text(
                text = error.message,
                color = MaterialTheme.colorScheme.error,
            )
        }
        val saved = uiState.saved
        if (saved != null) {
            Text(
                text = savedSummary(saved),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun savedSummary(body: BodyData): String {
    val sex = if (body.sex == 1) "Laki-laki" else "Perempuan"
    val height = if (body.heightCm == body.heightCm.toLong().toDouble()) {
        body.heightCm.toLong().toString()
    } else {
        body.heightCm.toString().replace('.', ',')
    }
    return "Tersimpan: tinggi $height cm, umur ${body.age}, $sex"
}

@Preview(showBackground = true)
@Composable
private fun ProfileContentPreview() {
    HealthTrackTheme {
        ProfileContent(
            uiState = ProfileUIState(
                heightCm = "166",
                age = "29",
                sex = 1,
                saved = BodyData(166.0, 29, 1),
            ),
            onHeightChange = {},
            onAgeChange = {},
            onSexSelected = {},
            onSaveClick = {},
        )
    }
}
