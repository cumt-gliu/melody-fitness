package com.giannisliu.melodyfitness.data.settings

import android.content.Context
import com.giannisliu.melodyfitness.data.repository.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val sharedPreferences =
        context.getSharedPreferences("melody_fitness_settings", Context.MODE_PRIVATE)
    private val weightUnitState = MutableStateFlow(loadWeightUnit())

    fun observeWeightUnit(): StateFlow<WeightUnit> = weightUnitState.asStateFlow()

    fun updateWeightUnit(weightUnit: WeightUnit) {
        sharedPreferences.edit()
            .putString(KEY_WEIGHT_UNIT, weightUnit.name)
            .apply()
        weightUnitState.value = weightUnit
    }

    private fun loadWeightUnit(): WeightUnit {
        val storedValue = sharedPreferences.getString(KEY_WEIGHT_UNIT, WeightUnit.KG.name)
        return WeightUnit.entries.firstOrNull { it.name == storedValue } ?: WeightUnit.KG
    }

    private companion object {
        const val KEY_WEIGHT_UNIT = "weight_unit"
    }
}
