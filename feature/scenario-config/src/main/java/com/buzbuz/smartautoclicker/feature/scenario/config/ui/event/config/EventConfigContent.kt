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
package com.buzbuz.smartautoclicker.feature.scenario.config.ui.event.config

import android.content.Context
import android.text.InputFilter
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
import com.buzbuz.smartautoclicker.core.ui.overlays.dialog.viewModels
import com.buzbuz.smartautoclicker.feature.scenario.config.R
import com.buzbuz.smartautoclicker.feature.scenario.config.databinding.ContentEventConfigBinding
import com.buzbuz.smartautoclicker.feature.scenario.config.utils.setError

import kotlinx.coroutines.launch

class EventConfigContent(appContext: Context) : NavBarDialogContent(appContext) {

    /** View model for this content. */
    private val viewModel: EventConfigViewModel by viewModels()

    /** View binding for all views in this content. */
    private lateinit var viewBinding: ContentEventConfigBinding

    override fun onCreateView(container: ViewGroup): ViewGroup {
        viewBinding = ContentEventConfigBinding.inflate(LayoutInflater.from(context), container, false).apply {
            eventNameInputLayout.apply {
                setLabel(R.string.input_field_label_name)
                setOnTextChangedListener { viewModel.setEventName(it.toString()) }
                textField.filters = arrayOf<InputFilter>(
                    InputFilter.LengthFilter(context.resources.getInteger(R.integer.name_max_length))
                )
            }
            dialogController.hideSoftInputOnFocusLoss(eventNameInputLayout.textField)

            conditionsOperatorField.setItems(
                label = context.getString(R.string.dropdown_label_condition_operator),
                items = viewModel.conditionOperatorsItems,
                onItemSelected = viewModel::setConditionOperator,
                onItemBound = ::onConditionOperatorDropdownItemBound,
            )

            takeCapturesSwitch.setOnCheckedChangeListener { _, isChecked -> viewModel.setTakeCaptures(isChecked) }
            soundAlarmSwitch.setOnCheckedChangeListener { _, isChecked -> viewModel.setSoundAlarm(isChecked) }
        }

        return viewBinding.root
    }

    override fun onViewCreated() {
        updateEventStateDropdown(viewModel.eventStateDropdownState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.eventNameError.collect(viewBinding.eventNameInputLayout::setError) }
                launch { viewModel.eventName.collect(::updateEventName) }
                launch { viewModel.conditionOperator.collect(::updateConditionOperator) }
                launch { viewModel.eventStateItem.collect(::updateEventState) }
                launch { viewModel.takeCaptures.collect(::updateTakeCaptures) }
                launch { viewModel.soundAlarm.collect(::updateSoundAlarm) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.monitorConditionOperatorView(viewBinding.conditionsOperatorField.root)
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopViewMonitoring()
    }

    private fun onConditionOperatorDropdownItemBound(item: DropdownItem, view: View?) {
        if (item == viewModel.conditionAndItem) {
            if (view != null) viewModel.monitorDropdownItemAndView(view)
            else viewModel.stopDropdownItemConditionViewMonitoring()
        }
    }

    private fun updateEventName(name: String?) {
        viewBinding.eventNameInputLayout.setText(name)
    }

    private fun updateConditionOperator(operatorItem: DropdownItem) {
        viewBinding.conditionsOperatorField.setSelectedItem(operatorItem)
    }

    private fun updateEventStateDropdown(dropdownState: EventStateDropdownUiState) {
        viewBinding.enabledOnStartField.setItems(
            label = context.resources.getString(R.string.input_field_label_event_state),
            items = dropdownState.items,
            onItemSelected = viewModel::setEventState,
        )
    }

    private fun updateEventState(stateItem: DropdownItem) {
        viewBinding.enabledOnStartField.setSelectedItem(stateItem)
    }

    private fun updateTakeCaptures(enabled: Boolean) {
        // Guard against listener loops: only update the switch when its state actually differs
        if (viewBinding.takeCapturesSwitch.isChecked != enabled) {
            viewBinding.takeCapturesSwitch.isChecked = enabled
        }
    }

    private fun updateSoundAlarm(enabled: Boolean) {
        // Guard against listener loops: only update the switch when its state actually differs
        if (viewBinding.soundAlarmSwitch.isChecked != enabled) {
            viewBinding.soundAlarmSwitch.isChecked = enabled
        }
    }
}
