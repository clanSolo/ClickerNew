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
package com.buzbuz.smartautoclicker.feature.scenario.config.ui.event.actions

import android.app.Application
import android.content.Context
import android.view.View

import androidx.lifecycle.AndroidViewModel

import com.buzbuz.smartautoclicker.core.ui.overlays.dialog.DialogChoice
import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.core.ui.monitoring.MonitoredViewsManager
import com.buzbuz.smartautoclicker.core.ui.monitoring.MonitoredViewType
import com.buzbuz.smartautoclicker.feature.scenario.config.R
import com.buzbuz.smartautoclicker.feature.scenario.config.domain.EditionRepository
import com.buzbuz.smartautoclicker.feature.scenario.config.ui.bindings.ActionDetails
import com.buzbuz.smartautoclicker.feature.scenario.config.ui.bindings.toActionDetails

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ActionsViewModel(application: Application) : AndroidViewModel(application) {

    /** Maintains the currently configured scenario state. */
    private val editionRepository = EditionRepository.getInstance(application)
    /** Monitors views. */
    private val monitoredViewsManager: MonitoredViewsManager = MonitoredViewsManager.getInstance()

    /** Currently configured actions. */
    private val configuredActions = editionRepository.editionState.editedEventActionsState

    /** Tells if there is at least one action to copy. */
    val canCopyAction: Flow<Boolean> = combine(
        editionRepository.editionState.editedEventState,
        editionRepository.editionState.editedScenarioOtherActionsForCopy,
        editionRepository.editionState.allOtherScenarioActionsForCopy,
    ) { eventState, scenarioActions, allActions ->
        val event = eventState.value ?: return@combine false
        event.actions.isNotEmpty() || scenarioActions.isNotEmpty() || allActions.isNotEmpty()
    }

    /** List of action details. */
    val actionDetails: Flow<List<Pair<Action, ActionDetails>>> = configuredActions
        .map { actions ->
            actions.value?.mapIndexed { index, action ->
                action to action.toActionDetails(application, !actions.itemValidity[index])
            } ?: emptyList()
        }
    /** Type of actions to be displayed in the new action creation dialog. */
    val actionCreationItems: List<ActionTypeChoice> = listOf(
        ActionTypeChoice.Click,
        ActionTypeChoice.Swipe,
        ActionTypeChoice.Pause,
        ActionTypeChoice.Intent,
        ActionTypeChoice.ToggleEvent,
    )

    /**
     * Create a new action with the default values from configuration.
     *
     * @param context the Android Context.
     * @param actionType the type of action to create.
     */
    fun createAction(context: Context, actionType: ActionTypeChoice): Action = when (actionType) {
        is ActionTypeChoice.Click -> editionRepository.editedItemsBuilder.createNewClick(context)
        is ActionTypeChoice.Swipe -> editionRepository.editedItemsBuilder.createNewSwipe(context)
        is ActionTypeChoice.Pause -> editionRepository.editedItemsBuilder.createNewPause(context)
        is ActionTypeChoice.Intent -> editionRepository.editedItemsBuilder.createNewIntent(context)
        is ActionTypeChoice.ToggleEvent -> editionRepository.editedItemsBuilder.createNewToggleEvent(context)
    }

    /**
     * Get a new action based on the provided one.
     * @param action the item containing the action to copy.
     */
    fun createNewActionFrom(action: Action): Action =
        editionRepository.editedItemsBuilder.createNewActionFrom(action)

    fun startActionEdition(action: Action) = editionRepository.startActionEdition(action)

    fun upsertEditedAction(): Unit = editionRepository.upsertEditedAction()

    fun removeEditedAction(): Unit = editionRepository.deleteEditedAction()

    fun dismissEditedAction() = editionRepository.stopActionEdition()

    /**
     * Update the priority of the actions.
     * @param actions the new actions order.
     */
    fun updateActionOrder(actions: List<Pair<Action, ActionDetails>>) =
        editionRepository.updateActionsOrder(actions.map { it.first })

    fun monitorCreateActionView(view: View) {
        monitoredViewsManager.attach(MonitoredViewType.EVENT_DIALOG_BUTTON_CREATE_ACTION, view)
    }

    fun monitorFirstActionView(view: View) {
        monitoredViewsManager.attach(MonitoredViewType.EVENT_DIALOG_ITEM_FIRST_ACTION, view)
    }

    fun stopFirstActionViewMonitoring() {
        monitoredViewsManager.detach(MonitoredViewType.EVENT_DIALOG_ITEM_FIRST_ACTION)
    }

    fun stopAllViewMonitoring() {
        monitoredViewsManager.detach(MonitoredViewType.EVENT_DIALOG_BUTTON_CREATE_ACTION)
        monitoredViewsManager.detach(MonitoredViewType.EVENT_DIALOG_ITEM_FIRST_ACTION)
    }
}

/** Choices for the action type selection dialog. */
sealed class ActionTypeChoice(
    title: Int,
    description: Int,
    iconId: Int?,
): DialogChoice(
    title = title,
    description = description,
    iconId = iconId,
) {
    /** Click Action choice. */
    object Click : ActionTypeChoice(
        R.string.item_title_click,
        R.string.item_desc_click,
        R.drawable.ic_click,
    )
    /** Swipe Action choice. */
    object Swipe : ActionTypeChoice(
        R.string.item_title_swipe,
        R.string.item_desc_swipe,
        R.drawable.ic_swipe,
    )
    /** Pause Action choice. */
    object Pause : ActionTypeChoice(
        R.string.item_title_pause,
        R.string.item_desc_pause,
        R.drawable.ic_wait,
    )
    /** Intent Action choice. */
    object Intent : ActionTypeChoice(
        R.string.item_title_intent,
        R.string.item_desc_intent,
        R.drawable.ic_intent,
    )
    /** Toggle Event Action choice. */
    object ToggleEvent : ActionTypeChoice(
        R.string.item_title_toggle_event,
        R.string.item_desc_toggle_event,
        R.drawable.ic_toggle_event,
    )
}
