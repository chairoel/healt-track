package com.mascill.healthtrack.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.domain.model.ScaleSessionStatus
import com.mascill.healthtrack.domain.repository.BodyRepository
import com.mascill.healthtrack.domain.usecase.MeasureScaleUseCase
import com.mascill.healthtrack.presentation.common.model.AppError
import com.mascill.healthtrack.presentation.session.model.FinishedSession
import com.mascill.healthtrack.presentation.session.model.SessionUIState
import com.mascill.healthtrack.util.AppDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Starts a scale session only when a body profile is stored and Bluetooth permission is granted.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val bodyRepository: BodyRepository,
    private val measure: MeasureScaleUseCase,
    private val dispatcher: AppDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionUIState())
    val uiState: StateFlow<SessionUIState> = _uiState.asStateFlow()

    private val results = Channel<FinishedSession>(capacity = Channel.BUFFERED)
    val openResult: Flow<FinishedSession> = results.receiveAsFlow()

    private var pendingBody: BodyData? = null
    private var sessionJob: Job? = null
    private var openedId: Int? = null

    init {
        refreshProfile()
    }

    /**
     * Reads whether a profile row exists.
     */
    fun refreshProfile() {
        viewModelScope.launch(dispatcher.io) {
            val hasProfile = bodyRepository.latest() != null
            _uiState.update { it.copy(hasProfile = hasProfile) }
        }
    }

    /**
     * Asks for Bluetooth permission when a profile exists.
     * Without a profile, measuring stays off.
     */
    fun onStartClick() {
        if (_uiState.value.isMeasuring) {
            return
        }
        viewModelScope.launch(dispatcher.io) {
            val body = bodyRepository.latest()
            if (body == null) {
                pendingBody = null
                _uiState.update {
                    it.copy(
                        hasProfile = false,
                        isMeasuring = false,
                        isFlowOpen = false,
                        error = AppError(PROFILE_REQUIRED),
                    )
                }
                return@launch
            }
            pendingBody = body
            _uiState.update {
                it.copy(
                    hasProfile = true,
                    isMeasuring = false,
                    isFlowOpen = true,
                    error = null,
                    permissionRequestId = it.permissionRequestId + 1,
                )
            }
        }
    }

    /**
     * Starts the scale session after the scan permissions are granted.
     */
    fun onPermissionsGranted() {
        val body = pendingBody ?: return
        val previous = sessionJob
        sessionJob = viewModelScope.launch(dispatcher.main) {
            previous?.cancelAndJoin()
            measure(body).collect { status ->
                val finishedId = status.measurementId
                _uiState.update {
                    it.copy(
                        isMeasuring = status.running,
                        isFlowOpen = true,
                        stateLabel = status.stateLabel.ifBlank { null },
                        liveWeightText = status.liveWeightText,
                        error = status.notice(),
                    )
                }
                if (finishedId != null && openedId != finishedId) {
                    openedId = finishedId
                    results.send(
                        FinishedSession(
                            measurementId = finishedId,
                            heightCm = status.heightCm,
                            broadcastHex = status.broadcastHex,
                            gattHex = status.gattHex,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Keeps the session off when the user denies Bluetooth permission.
     */
    fun onPermissionsDenied() {
        _uiState.update {
            it.copy(isMeasuring = false, error = AppError(PERMISSION_REQUIRED))
        }
    }

    /**
     * Stops scanning when the user leaves the visible activity during a measurement.
     */
    fun onHostStop() {
        if (!_uiState.value.isMeasuring) {
            return
        }
        stopMeasuring()
    }

    /**
     * Stops the current measurement.
     */
    fun onStopClick() {
        stopMeasuring()
    }

    override fun onCleared() {
        sessionJob?.cancel()
        results.close()
        super.onCleared()
    }

    private fun stopMeasuring() {
        sessionJob?.cancel()
        _uiState.update { it.copy(isMeasuring = false) }
    }

    private fun ScaleSessionStatus.notice(): AppError? = when {
        bluetoothOff -> AppError(BLUETOOTH_OFF)
        connectionTimedOut -> AppError(CONNECTION_TIMEOUT)
        scanFailed -> AppError(SCAN_FAILED)
        encrypted -> AppError(ENCRYPTED)
        else -> null
    }

    private companion object {
        const val PROFILE_REQUIRED = "Simpan profil tubuh sebelum mengukur."
        const val PERMISSION_REQUIRED = "Izin Bluetooth diperlukan untuk mengukur."
        const val BLUETOOTH_OFF = "Nyalakan Bluetooth untuk mengukur."
        const val CONNECTION_TIMEOUT =
            "Waktu sambungan habis. Tutup aplikasi OKOK di ponsel bila timbangan sedang tersambung di sana."
        const val SCAN_FAILED = "Pemindaian Bluetooth gagal."
        const val ENCRYPTED = "Siaran terenkripsi. Pengukuran ini tidak masuk rumus."
    }
}
