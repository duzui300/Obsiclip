package com.duzui.sharetoobsi.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity): Long

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TargetDao {
    @Query("SELECT * FROM targets ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<TargetEntity>>

    @Query("SELECT * FROM targets ORDER BY sortOrder, id")
    suspend fun all(): List<TargetEntity>

    @Query("SELECT * FROM targets WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TargetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(target: TargetEntity): Long

    @Query("DELETE FROM targets WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface FormatDao {
    @Query("SELECT * FROM formats ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<FormatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(format: FormatEntity): Long

    @Query("DELETE FROM formats WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity): Long

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AppProfileDao {
    @Query("SELECT * FROM app_profiles")
    fun observeAll(): Flow<List<AppProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: AppProfileEntity)

    @Query("DELETE FROM app_profiles WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox ORDER BY createdAt")
    fun observeAll(): Flow<List<OutboxEntity>>

    @Insert
    suspend fun insert(entry: OutboxEntity)

    @Delete
    suspend fun delete(entry: OutboxEntity)

    @Query("UPDATE outbox SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun recordFailure(id: Long, error: String)
}
