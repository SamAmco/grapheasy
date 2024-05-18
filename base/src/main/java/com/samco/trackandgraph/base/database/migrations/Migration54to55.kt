package com.samco.trackandgraph.base.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_54_55 = object : Migration(54, 55) {
    override fun migrate(database: SupportSQLiteDatabase) {
        //Nothing actually required here, but we need a migration or Room will fall back to
        // destructive migration. Although the schema didn't change in this version, I bumped the
        // database version because it is not backwards compatible with the previous version.
        // This version added "now" as a possible value for a graph end time.
    }
}
