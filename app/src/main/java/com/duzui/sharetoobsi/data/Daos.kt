package com.duzui.sharetoobsi.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY lastUsedAt DESC LIMIT 1")
    fun observeMostRecent(): Flow<BookEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Query("SELECT * FROM books WHERE title = :title")
    suspend fun find(title: String): BookEntity?

    @Query("DELETE FROM books WHERE title = :title")
    suspend fun delete(title: String)
}

@Dao
interface TargetDao {
    @Query("SELECT * FROM targets ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<TargetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(target: TargetEntity)

    @Query("DELETE FROM targets WHERE id = :id")
    suspend fun delete(id: Long)
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
