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
package com.buzbuz.smartautoclicker.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database v13 to v15: add the take captures and sound alarm event flags.
 * Done manually instead of AutoMigrations 13 to 14 to 15 because the v14 schema file is only generated
 * by the CI and is not part of the repository, so Room could not verify an intermediate auto migration.
 */
object Migration13to15 : Migration(13, 15) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(addTakeCapturesColumn)
        db.execSQL(addSoundAlarmColumn)
    }

    private val addTakeCapturesColumn = """
        ALTER TABLE `event_table`
        ADD COLUMN `take_captures` INTEGER NOT NULL DEFAULT 0
    """.trimIndent()

    private val addSoundAlarmColumn = """
        ALTER TABLE `event_table`
        ADD COLUMN `sound_alarm` INTEGER NOT NULL DEFAULT 0
    """.trimIndent()
}
