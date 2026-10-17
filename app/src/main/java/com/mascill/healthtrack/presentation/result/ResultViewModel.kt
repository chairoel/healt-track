package com.mascill.healthtrack.presentation.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mascill.healthtrack.domain.model.MeasurementDisplay
import com.mascill.healthtrack.domain.model.MeasurementResult
import com.mascill.healthtrack.domain.repository.BodyRepository
import com.mascill.healthtrack.domain.repository.MeasurementRepository
import com.mascill.healthtrack.domain.usecase.DisplayMeasurementUseCase
import com.mascill.healthtrack.presentation.common.model.AppError
import com.mascill.healthtrack.presentation.result.model.ResultRow
import com.mascill.healthtrack.presentation.result.model.ResultUIState
import com.mascill.healthtrack.util.AppDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shows one stored measurement. Numbers come from [DisplayMeasurementUseCase].
 */
@HiltViewModel
class ResultViewModel @Inject constructor(
    private val measurementRepository: MeasurementRepository,
    private val bodyRepository: BodyRepository,
    private val displayMeasurement: DisplayMeasurementUseCase,
    private val dispatcher: AppDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ResultUIState())
    val uiState: StateFlow<ResultUIState> = _uiState.asStateFlow()

    /**
     * Loads the stored row [measurementId] and rounds it with [DisplayMeasurementUseCase].
     * [heightCm] is the profile from the session that just finished. A stored height on the row
     * is used first. History passes 0, and a row saved before height was stored uses the newest
     * body profile. Blank hex is shown as `tidak ada`.
     */
    fun load(
        measurementId: Int,
        heightCm: Double,
        broadcastHex: String,
        gattHex: String,
    ) {
        viewModelScope.launch(dispatcher.io) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val stored = measurementRepository.list().firstOrNull { it.id == measurementId }
            if (stored == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = AppError(NOT_FOUND))
                }
                return@launch
            }
            val storedHeight = stored.result.heightCm?.takeIf { it > 0.0 }
            val height = storedHeight ?: if (heightCm > 0.0) {
                heightCm
            } else {
                bodyRepository.latest()?.heightCm
            }
            if (height == null || height <= 0.0) {
                _uiState.update {
                    it.copy(isLoading = false, error = AppError(HEIGHT_MISSING))
                }
                return@launch
            }
            val result = stored.result
            val display = displayMeasurement(
                report = result.report,
                weightKg = result.weightKg,
                heightCm = height,
            )
            val weightText = result.scaleWeight.ifBlank { formatDecimal(result.weightKg, ONE_DECIMAL) }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    weightText = weightText,
                    rows = profileRows(stored.result) + rows(display),
                    broadcastHex = broadcastHex.ifBlank { HEX_MISSING },
                    gattHex = gattHex.ifBlank { HEX_MISSING },
                    error = null,
                )
            }
        }
    }

    private fun profileRows(result: MeasurementResult): List<ResultRow> {
        val rows = mutableListOf<ResultRow>()
        result.heightCm?.let { height ->
            rows += ResultRow("Tinggi (cm)", formatDecimal(height, ONE_DECIMAL))
        }
        result.age?.let { age ->
            rows += ResultRow("Umur", age.toString())
        }
        return rows
    }

    private fun rows(display: MeasurementDisplay): List<ResultRow> = listOf(
        ResultRow("BMI", formatDecimal(display.bmi, ONE_DECIMAL)),
        ResultRow("Lemak", formatDecimal(display.bodyFatPercent, ONE_DECIMAL)),
        ResultRow("Massa lemak (kg)", formatDecimal(display.fatMassKg, ONE_DECIMAL)),
        ResultRow("Rasio otot rangka", formatDecimal(display.skeletalMuscleRatio, ONE_DECIMAL)),
        ResultRow("Otot rangka (kg)", formatDecimal(display.skeletalMuscleKg, ONE_DECIMAL)),
        ResultRow("Rasio otot", formatDecimal(display.muscleRatio, ONE_DECIMAL)),
        ResultRow("Massa otot (kg)", formatDecimal(display.muscleMassKg, ONE_DECIMAL)),
        ResultRow("Air", formatDecimal(display.waterPercent, ONE_DECIMAL)),
        ResultRow("Massa air (kg)", formatDecimal(display.waterMassKg, ONE_DECIMAL)),
        ResultRow("Viseral", formatDecimal(display.visceralFat, ONE_DECIMAL)),
        ResultRow("Tulang (kg)", formatDecimal(display.boneKg, TWO_DECIMALS)),
        ResultRow("Metabolisme", formatDecimal(display.metabolism, ONE_DECIMAL)),
        ResultRow("Protein", formatDecimal(display.proteinPercent, ONE_DECIMAL)),
        ResultRow("Tingkat kegemukan", formatDecimal(display.obesityPercent, ONE_DECIMAL)),
        ResultRow("Umur tubuh", formatDecimal(display.bodyAge, ONE_DECIMAL)),
        ResultRow("Berat tanpa lemak (kg)", formatDecimal(display.leanWeightKg, TWO_DECIMALS)),
        ResultRow("Skor", display.score.toString()),
    )

    private companion object {
        const val ONE_DECIMAL = 1
        const val TWO_DECIMALS = 2
        const val NOT_FOUND = "Hasil tidak ditemukan."
        const val HEIGHT_MISSING = "Tinggi badan belum tersimpan."
        const val HEX_MISSING = "tidak ada"
    }
}

private fun formatDecimal(value: Double, places: Int): String {
    val symbols = DecimalFormatSymbols(Locale.US).apply {
        decimalSeparator = ','
    }
    val pattern = "0." + "0".repeat(places)
    return DecimalFormat(pattern, symbols).apply {
        roundingMode = RoundingMode.HALF_UP
        isGroupingUsed = false
    }.format(value)
}
