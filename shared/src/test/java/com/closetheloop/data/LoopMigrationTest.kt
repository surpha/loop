package com.closetheloop.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoopMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun v1LoopsSurviveMigrationAndQueueForUpload() = runTest {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val v1 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `loops` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `deadline` INTEGER, " +
                                "`originalDurationMs` INTEGER, `status` TEXT NOT NULL, `snoozeCount` INTEGER NOT NULL, " +
                                "`lastSnoozedAt` INTEGER, `closedAt` INTEGER)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        v1.writableDatabase.apply {
            execSQL("INSERT INTO loops (title, createdAt, deadline, status, snoozeCount) VALUES ('Old A', 100, 5000, 'ACTIVE', 0)")
            execSQL("INSERT INTO loops (title, createdAt, status, snoozeCount, closedAt) VALUES ('Old B', 200, 'CLOSED', 0, 900)")
        }
        v1.close()

        // Room validates the migrated schema against the entity on open and throws on mismatch.
        val db = Room.databaseBuilder(context, LoopDatabase::class.java, name)
            .addMigrations(LoopDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        val repo = LoopRepository(db.loopDao())

        assertEquals(listOf("Old A"), repo.observeOpen().first().map { it.title })
        val pending = repo.pendingUploads().sortedBy { it.title }
        assertEquals(listOf("Old A", "Old B"), pending.map { it.title })
        assertEquals(2, pending.map { it.syncId }.toSet().size)
        assertTrue(pending.all { it.syncId.isNotBlank() })
        assertEquals(listOf(100L, 900L), pending.map { it.updatedAt })
        db.close()
    }
}
