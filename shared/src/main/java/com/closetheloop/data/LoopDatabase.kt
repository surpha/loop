package com.closetheloop.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Loop::class], version = 2, exportSchema = false)
abstract class LoopDatabase : RoomDatabase() {
    abstract fun loopDao(): LoopDao

    companion object {
        /** v2: sync columns. Existing loops get a random syncId and are marked dirty so they upload on first sync. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE loops ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE loops SET syncId = lower(hex(randomblob(16)))")
                db.execSQL("ALTER TABLE loops ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE loops SET updatedAt = COALESCE(closedAt, lastSnoozedAt, createdAt)")
                db.execSQL("ALTER TABLE loops ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE loops ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_loops_syncId ON loops (syncId)")
            }
        }

        fun create(context: Context, name: String = "loops.db"): LoopDatabase =
            Room.databaseBuilder(context, LoopDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
