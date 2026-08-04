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

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

import com.buzbuz.smartautoclicker.core.domain.model.event.Event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays a beep alarm while the events with alarm enabled keep being detected.
 *
 * The alarm starts in a beep loop at the first trigger and is silenced automatically when no event with alarm
 * enabled has been detected during the last [ALARM_STOP_DELAY_MS] milliseconds. All the playback is handled on the
 * IO dispatcher and never blocks the detection thread.
 */
internal class EventAlarmPlayer {

    /** Scope for the beep loop and the auto stop watchdog. */
    private val playerScope = CoroutineScope(Dispatchers.IO)

    /** Generates the beeps, or null if the player is stopped. */
    private var toneGenerator: ToneGenerator? = null
    /** Job looping the beeps, null if the alarm is not ringing. */
    private var beepLoopJob: Job? = null
    /** Job stopping the alarm after the stop delay without any trigger. */
    private var watchdogJob: Job? = null

    /** Guards all accesses to the watchdog and beep loop jobs, as they span multiple coroutines. */
    private val stateLock = Any()

    /** Create the tone generator. */
    fun start() {
        if (toneGenerator != null) return

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
        } catch (rtEx: RuntimeException) {
            Log.e(TAG, "Cannot create the tone generator, alarm is disabled", rtEx)
            toneGenerator = null
        }
    }

    /**
     * Called when an event with alarm enabled is detected.
     * Starts the beep loop if it is not ringing yet, and re-arms the stop watchdog.
     *
     * @param event the event that has been detected.
     */
    fun onEventTriggered(event: Event) {
        val generator = toneGenerator ?: return

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
                    generator.startTone(ToneGenerator.TONE_CDMA_PIP, BEEP_DURATION_MS)
                    delay(BEEP_INTERVAL_MS)
                }
            }
        }
    }

    /** Stop the player and release its resources. */
    fun stop() {
        synchronized(stateLock) {
            watchdogJob?.cancel()
            watchdogJob = null
        }
        stopBeepLoop()

        toneGenerator?.release()
        toneGenerator = null
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
/** Duration of a single beep. */
private const val BEEP_DURATION_MS = 150
/** Delay between the start of two beeps. */
private const val BEEP_INTERVAL_MS = 500L
/** Delay without any trigger after which the alarm is silenced. */
private const val ALARM_STOP_DELAY_MS = 1500L
