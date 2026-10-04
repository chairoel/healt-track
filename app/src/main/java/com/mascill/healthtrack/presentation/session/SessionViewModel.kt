package com.mascill.healthtrack.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mascill.healthtrack.domain.repository.BodyRepository
import com.mascill.healthtrack.presentation.session.model.SessionUIState
import com.mascill.healthtrack.util.AppDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Opens the measurement flow only when a body profile is stored.
 * Bluetooth is not started here.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val bodyRepository: BodyRepository,
    private val dispatcher: AppDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionUIState())
    val uiState: StateFlow<SessionUIState> = _uiState.asStateFlow()

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
     * Opens the session flow when a profile exists.
     * Without a profile, [SessionUIState.isMeasuring] stays false.
     */
    fun onStartClick() {
        viewModelScope.launch(dispatcher.io) {
            val hasProfile = bodyRepository.latest() != null
            if (!hasProfile) {
                _uiState.update {
                    it.copy(
                        hasProfile = false,
                        isMeasuring = false,
                        isFlowOpen = false,
                        message = PROFILE_REQUIRED,
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    hasProfile = true,
                    isMeasuring = false,
                    isFlowOpen = true,
                    message = null,
                )
            }
        }
    }

    private companion object {
        const val PROFILE_REQUIRED = "Simpan profil tubuh sebelum mengukur."
    }
}
