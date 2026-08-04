/*
 * Copyright (C) 2023 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.feature.scenario.config.ui.scenario.config

import android.content.Context
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import com.buzbuz.smartautoclicker.core.ui.bindings.dropdown.DropdownItem
import com.buzbuz.smartautoclicker.core.ui.bindings.dropdown.setItems
import com.buzbuz.smartautoclicker.core.ui.bindings.setLabel
import com.buzbuz.smartautoclicker.core.ui.bindings.setOnTextChangedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.dropdown.setSelectedItem
import com.buzbuz.smartautoclicker.core.ui.bindings.setText
import com.buzbuz.smartautoclicker.core.ui.overlays.dialog.NavBarDialogContent
import com.buzbuz.smartautoclicker.core.domain.model.endcondition.EndCondition
import com.buzbuz.smartautoclicker.core.ui.overlays.manager.OverlayManager
import com.buzbuz.smartautoclicker.core.ui.overlays.dialog.viewModels
import com.buzbuz.smartautoclicker.feature.scenario.config.R
import com.buzbuz.smartautoclicker.feature.scenario.config.databinding.ContentScenarioConfigBinding
import com.buzbuz.smartautoclicker.feature.scenario.config.ui.endcondition.EndConditionConfigDialog
import com.buzbuz.smartautoclicker.feature.scenario.config.utils.setError

import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ScenarioConfigContent(appContext: Context) : NavBarDialogContent(appContext) {

    /** View model for this content. */
    private val viewModel: ScenarioConfigViewModel by viewModels()

    private lateinit var viewBinding: ContentScenarioConfigBinding
    private lateinit var endConditionAdapter: EndConditionAdapter

    override fun onCreateView(container: ViewGroup): ViewGroup {
        viewBinding = ContentScenarioConfigBinding.inflate(LayoutInflater.from(context), container, false).apply {
            scenarioNameField.apply {
                setLabel(R.string.input_field_label_scenario_name)
                setOnTextChangedListener { viewModel.setScenarioName(it.toString()) }
                textField.filters = arrayOf<InputFilter>(
                    LengthFilter(context.resources.getInteger(R.integer.name_max_length))
                )
            }
            dialogController.hideSoftInputOnFocusLoss(scenarioNameField.textField)

            textSpeed.setOnClickListener { viewModel.decreaseDetectionQuality() }
            textPrecision.setOnClickListener { viewModel.increaseDetectionQuality() }
            seekbarQuality.addOnChangeListener { _, value, fromUser ->
                if (fromUser) viewModel.setDetectionQuality(value.roundToInt())
            }

            seekbarDetectionInterval.addOnChangeListener { _, value, fromUser ->
                if (fromUser) viewModel.setDetectionInterval(value.roundToInt())
            }

            endConditionsOperatorField.setItems(
                items = viewModel.endConditionOperatorsItems,
                onItemSelected = viewModel::setConditionOperator,
            )

            endConditionAdapter = EndConditionAdapter(
                addEndConditionClickedListener = ::onAddEndConditionClicked,
                endConditionClickedListener = ::showEndConditionDialog,
            )
            endConditionsList.adapter = endConditionAdapter
        }

        return viewBinding.root
    }

    override fun onViewCreated() {
        updateRandomizationDropdown(viewModel.randomizationDropdownState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.scenarioName.collect(::updateScenarioName) }
                launch { viewModel.scenarioNameError.collect(viewBinding.scenarioNameField::setError) }
                launch { viewModel.randomization.collect(::updateRandomization) }
                launch { viewModel.detectionInterval.collect(::updateDetectionInterval) }
                launch { viewModel.detectionQuality.collect(::updateQuality) }
                launch { viewModel.endConditionOperator.collect(::updateEndConditionOperator) }
                launch { viewModel.endConditions.collect(::updateEndConditions) }
            }
        }
    }

    private fun updateScenarioName(name: String?) {
        viewBinding.scenarioNameField.setText(name)
    }

    private fun updateRandomizationDropdown(dropdownState: RandomizationDropdownUiState) {
        viewBinding.scenarioActionRandomization.setItems(
            label = context.resources.getString(R.string.input_field_label_anti_detection),
            items = dropdownState.items,
            onItemSelected = viewModel::setRandomization,
        )
    }

    private fun updateRandomization(randomizationItem: DropdownItem) {
        viewBinding.scenarioActionRandomization.setSelectedItem(randomizationItem)
    }

    private fun updateDetectionInterval(interval: Int?) {
        if (interval == null) return

        viewBinding.apply {
            textDetectionIntervalValue.text = interval.toString()
            if (seekbarDetectionInterval.value != interval.toFloat()) {
                seekbarDetectionInterval.value = interval.toFloat()
            }
        }
    }

    private fun updateQuality(quality: Int?) {
        if (quality == null) return

        viewBinding.apply {
            textQualityValue.text = quality.toString()

            val isNotInitialized = seekbarQuality.value == 0f
            seekbarQuality.value = quality.toFloat()

            if (isNotInitialized) {
                seekbarQuality.valueFrom = SLIDER_QUALITY_MIN
                seekbarQuality.valueTo = SLIDER_QUALITY_MAX
            }
        }
    }

    private fun updateEndConditionOperator(operatorItem: DropdownItem) {
        viewBinding.endConditionsOperatorField.setSelectedItem(operatorItem)
    }

    private fun updateEndConditions(endConditions: List<EndConditionListItem>) {
        viewBinding.apply {
            if (endConditions.isEmpty()) {
                endConditionsList.visibility = View.GONE
                endConditionsNoEvents.visibility = View.VISIBLE
            } else {
                endConditionsList.visibility = View.VISIBLE
                endConditionsNoEvents.visibility = View.GONE
            }
        }

        endConditionAdapter.submitList(endConditions)
    }

    private fun onAddEndConditionClicked() {
        showEndConditionDialog(viewModel.createNewEndCondition())
    }

    private fun showEndConditionDialog(endCondition: EndCondition) {
        viewModel.startEndConditionEdition(endCondition)
        OverlayManager.getInstance(context).navigateTo(
            context = context,
            newOverlay = EndConditionConfigDialog(
                onConfirmClicked = viewModel::upsertEndCondition,
                onDeleteClicked = viewModel::deleteEndCondition,
                onDismissClicked = viewModel::discardEndCondition,
            )
        )
    }
}
