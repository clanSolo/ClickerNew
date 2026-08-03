/*
 * Copyright (C) 2026 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.core.processing.data

import android.util.Log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * BISECT STUB: same structure as the real player, but the ToneGenerator usage is stubbed out to
 * isolate the CI compilation failure.
 */
internal class EventAlarmPlayer {

    /** Scope for the beep loop and the auto stop watchdog. */
    private val playerScope = CoroutineScope(Dispatchers.IO)

    /** True when the alarm resources are available. */
    private var isStarted: Boolean = false
    /** Job looping the beeps, null if the alarm is not ringing. */
    private var beepLoopJob: Job? = null
    /** Job stopping the alarm after the stop delay without any trigger. */
    private var watchdogJob: Job? = null

    /** Guards all accesses to the watchdog and beep loop jobs, as they span multiple coroutines. */
    private val stateLock = Any()

    fun start() {
        isStarted = true
    }

    fun onEventTriggered() {
        if (!isStarted) return

        synchronized(stateLock) {
            watchdogJob?.cancel()
            watchdogJob = playerScope.launch {
                delay(ALARM_STOP_DELAY_MS)
                stopBeepLoop()
            }

            if (beepLoopJob?.isActive == true) return

            beepLoopJob = playerScope.launch {
                Log.d(TAG, "Alarm beep loop started")
                while (isActive) {
                    Log.d(TAG, "beep")
                    delay(BEEP_INTERVAL_MS)
                }
            }
        }
    }

    fun stop() {
        isStarted = false
        synchronized(stateLock) {
            watchdogJob?.cancel()
            watchdogJob = null
        }
        stopBeepLoop()
    }

    /** Stop the beep loop, if any. */
    private fun stopBeepLoop() {
        synchronized(stateLock) {
            if (beepLoopJob?.isActive == true) {
                Log.d(TAG, "Alarm beep loop stopped")
            }
            beepLoopJob?.cancel()
            beepLoopJob = null
        }
    }
}

/** Tag for logs. */
private const val TAG = "EventAlarmPlayer"
/** Delay between the start of two beeps. */
private const val BEEP_INTERVAL_MS = 500L
/** Delay without any trigger after which the alarm is silenced. */
private const val ALARM_STOP_DELAY_MS = 1500L
