package com.mascill.healthtrack.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.domain.repository.BodyRepository
import com.mascill.healthtrack.presentation.common.model.AppError
import com.mascill.healthtrack.presentation.profile.model.ProfileUIState
import com.mascill.healthtrack.util.AppDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads the newest body profile and inserts a new row on save.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val bodyRepository: BodyRepository,
    private val dispatcher: AppDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUIState())
    val uiState: StateFlow<ProfileUIState> = _uiState.asStateFlow()

    init {
        loadLatest()
    }

    /**
     * Fills the form from the newest stored profile.
     */
    fun loadLatest() {
        viewModelScope.launch(dispatcher.io) {
            val latest = bodyRepository.latest() ?: return@launch
            _uiState.update {
                it.copy(
                    heightCm = formatNumber(latest.heightCm),
                    age = latest.age.toString(),
                    sex = latest.sex,
                    saved = latest,
                )
            }
        }
    }

    /**
     * Updates the height field.
     */
    fun onHeightChange(value: String) {
        _uiState.update { it.copy(heightCm = value, error = null) }
    }

    /**
     * Updates the age field.
     */
    fun onAgeChange(value: String) {
        _uiState.update { it.copy(age = value, error = null) }
    }

    /**
     * Selects female (0) or male (1). Other values are ignored.
     */
    fun onSexSelected(sex: Int) {
        if (sex != 0 && sex != 1) {
            return
        }
        _uiState.update { it.copy(sex = sex, error = null) }
    }

    /**
     * Inserts a new profile when height, age, and sex are valid.
     */
    fun onSaveClick() {
        val height = _uiState.value.heightCm.replace(',', '.').toDoubleOrNull()
        val age = _uiState.value.age.toIntOrNull()
        val sex = _uiState.value.sex
        val error = when {
            height == null || height <= 0.0 -> AppError("Isi tinggi dalam sentimeter.")
            age == null || age <= 0 -> AppError("Isi umur.")
            sex == null -> AppError("Pilih jenis kelamin.")
            else -> null
        }
        if (error != null || height == null || age == null || sex == null) {
            _uiState.update { it.copy(error = error) }
            return
        }
        viewModelScope.launch(dispatcher.io) {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val body = BodyData(heightCm = height, age = age, sex = sex)
                bodyRepository.save(body)
                _uiState.update { it.copy(isSaving = false, saved = body) }
            } catch (_: IllegalArgumentException) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = AppError("Pilih jenis kelamin."),
                    )
                }
            }
        }
    }

    private fun formatNumber(value: Double): String {
        val whole = value.toLong()
        val text = if (value == whole.toDouble()) whole.toString() else value.toString()
        return text.replace('.', ',')
    }
}
