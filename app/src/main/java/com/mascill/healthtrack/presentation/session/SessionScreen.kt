package com.mascill.healthtrack.presentation.session

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mascill.healthtrack.presentation.common.model.AppError
import com.mascill.healthtrack.presentation.session.model.FinishedSession
import com.mascill.healthtrack.presentation.session.model.SessionUIState
import com.mascill.healthtrack.presentation.theme.HealthTrackTheme

private const val SCREEN_PADDING = 16
private const val ITEM_SPACING = 12

/**
 * Measurement session. Permissions are requested here, before the scan starts.
 */
@Composable
fun SessionScreen(
    onOpenResult: (FinishedSession) -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.onPermissionsGranted()
        } else {
            viewModel.onPermissionsDenied()
        }
    }
    LaunchedEffect(uiState.permissionRequestId) {
        if (uiState.permissionRequestId == 0) {
            return@LaunchedEffect
        }
        if (context.hasBluetoothPermissions()) {
            viewModel.onPermissionsGranted()
        } else {
            permissionLauncher.launch(bluetoothPermissions())
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.openResult.collect { finished ->
            onOpenResult(finished)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onHostStop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    KeepScreenOn(enabled = uiState.isMeasuring)
    SessionContent(
        uiState = uiState,
        onStartClick = viewModel::onStartClick,
        onStopClick = viewModel::onStopClick,
    )
}

@Composable
private fun SessionContent(
    uiState: SessionUIState,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
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
        if (uiState.isMeasuring) {
            Button(onClick = onStopClick) {
                Text("Berhenti")
            }
        } else {
            Button(onClick = onStartClick) {
                Text("Mulai")
            }
        }
        val stateLabel = uiState.stateLabel
        if (stateLabel != null) {
            Text(text = stateLabel)
        }
        val weight = uiState.liveWeightText
        if (weight != null) {
            Text(text = "Berat $weight kg")
        }
        val message = uiState.error?.message
        if (message != null) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = view.context.findActivity()?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return null
}

@Preview(showBackground = true)
@Composable
private fun SessionContentPreview() {
    HealthTrackTheme {
        SessionContent(
            uiState = SessionUIState(
                hasProfile = true,
                isMeasuring = true,
                stateLabel = "menunggu impedansi",
                liveWeightText = "68.7",
                error = AppError(
                    "Waktu sambungan habis. Tutup aplikasi OKOK di ponsel bila timbangan sedang tersambung di sana.",
                ),
            ),
            onStartClick = {},
            onStopClick = {},
        )
    }
}
