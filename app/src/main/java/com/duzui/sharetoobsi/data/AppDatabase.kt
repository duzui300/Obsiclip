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
        BookEntity::class,
        HistoryEntity::class,
        OutboxEntity::class,
        ProfileEntity::class,
        AppProfileEntity::class,
        FormatEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun targets(): TargetDao
    abstract fun books(): BookDao
    abstract fun history(): HistoryDao
    abstract fun outbox(): OutboxDao
    abstract fun profiles(): ProfileDao
    abstract fun appProfiles(): AppProfileDao
    abstract fun formats(): FormatDao

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

        /**
         * `formatId` is nullable, so no DEFAULT; `formats` is a fresh table, so it has to
         * match Room's generated CREATE column for column.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE targets ADD COLUMN formatId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `formats` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`template` TEXT NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)"
                )
            }
        }

        /**
         * Drops `seeded`. The skeleton is only ever offered when a target is created, so
         * afterwards nothing read the column — and SQLite cannot drop one on the oldest
         * supported devices, hence the rebuild.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `targets_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`path` TEXT NOT NULL, " +
                        "`heading` TEXT NOT NULL, " +
                        "`author` TEXT NOT NULL, " +
                        "`year` TEXT NOT NULL, " +
                        "`formatId` INTEGER, " +
                        "`sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `targets_new` (id, name, path, heading, author, year, formatId, sortOrder) " +
                        "SELECT id, name, path, heading, author, year, formatId, sortOrder FROM `targets`"
                )
                db.execSQL("DROP TABLE `targets`")
                db.execSQL("ALTER TABLE `targets_new` RENAME TO `targets`")
            }
        }

        /**
         * Splits books out of targets.
         *
         * A target whose path spelled out its own name was really a book wearing a
         * destination: its metadata is lifted into a book row and the name in the path is
         * replaced with `{title}`, so the one target then serves every book. Targets whose
         * path does not contain their name — the inbox — are left exactly as they are.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `books` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`author` TEXT NOT NULL, " +
                        "`year` TEXT NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `books` (title, author, year, sortOrder) " +
                        "SELECT name, author, year, sortOrder FROM `targets` " +
                        "WHERE INSTR(path, name) > 0"
                )
                db.execSQL(
                    "UPDATE `targets` SET path = REPLACE(path, name, '{title}') " +
                        "WHERE INSTR(path, name) > 0"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `targets_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`path` TEXT NOT NULL, " +
                        "`heading` TEXT NOT NULL, " +
                        "`formatId` INTEGER, " +
                        "`sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `targets_new` (id, name, path, heading, formatId, sortOrder) " +
                        "SELECT id, name, path, heading, formatId, sortOrder FROM `targets`"
                )
                db.execSQL("DROP TABLE `targets`")
                db.execSQL("ALTER TABLE `targets_new` RENAME TO `targets`")
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
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                ).build().also { instance = it }
            }
    }
}
