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
package com.buzbuz.smartautoclicker

import android.app.Application

import com.buzbuz.smartautoclicker.core.domain.Repository

import com.google.android.material.color.DynamicColors

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmartAutoClickerApplication : Application() {

    /** Scope for the application wide maintenance tasks, cancelled with the process death. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)

        // Convert the legacy whole screen conditions into in area ones targeting the full display. They can no
        // longer be created nor selected since the whole screen detection type has been removed.
        applicationScope.launch {
            val displayMetrics = resources.displayMetrics
            Repository.getRepository(this@SmartAutoClickerApplication)
                .convertLegacyWholeScreenConditions(maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels))
        }
    }
}