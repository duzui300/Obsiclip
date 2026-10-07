package com.duzui.sharetoobsi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TargetEntity::class,
        HistoryEntity::class,
        OutboxEntity::class,
        ProfileEntity::class,
        AppProfileEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun targets(): TargetDao
    abstract fun history(): HistoryDao
    abstract fun outbox(): OutboxDao
    abstract fun profiles(): ProfileDao
    abstract fun appProfiles(): AppProfileDao

    companion object {

        /**
         * Books used to be their own table; they are targets now. Existing targets are
         * preserved — only the new columns and the dropped table change.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE targets ADD COLUMN author TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE targets ADD COLUMN year TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE targets ADD COLUMN seeded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DROP TABLE IF EXISTS books")
            }
        }

        /**
         * Two new tables, nothing altered. Written out by hand because Room validates the
         * live schema against the entity definitions on open, so these have to match the
         * generated CREATE statements column for column.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `profiles` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`lineRules` TEXT NOT NULL, " +
                        "`inlineRules` TEXT NOT NULL, " +
                        "`template` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_profiles` (" +
                        "`packageName` TEXT NOT NULL, " +
                        "`profileId` TEXT NOT NULL, " +
                        "PRIMARY KEY(`packageName`))"
                )
            }
        }

        /** Nullable column, so no DEFAULT is needed and the generated schema matches. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN heading TEXT")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sharetoobsi.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
