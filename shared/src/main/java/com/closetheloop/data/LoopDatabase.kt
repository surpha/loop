package com.closetheloop.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Loop::class], version = 1, exportSchema = false)
abstract class LoopDatabase : RoomDatabase() {
    abstract fun loopDao(): LoopDao

    companion object {
        fun create(context: Context): LoopDatabase =
            Room.databaseBuilder(context, LoopDatabase::class.java, "loops.db").build()
    }
}
