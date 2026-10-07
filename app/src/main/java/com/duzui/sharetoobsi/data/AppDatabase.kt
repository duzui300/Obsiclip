package com.duzui.sharetoobsi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, TargetEntity::class, HistoryEntity::class, OutboxEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun books(): BookDao
    abstract fun targets(): TargetDao
    abstract fun history(): HistoryDao
    abstract fun outbox(): OutboxDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sharetoobsi.db",
                ).build().also { instance = it }
            }
    }
}
