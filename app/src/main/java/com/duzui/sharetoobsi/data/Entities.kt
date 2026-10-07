package com.duzui.sharetoobsi.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A book being read. Named by title because that is also how the vault names the note,
 * so the title doubles as the key and as `{title}` in the path template.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val title: String,
    val author: String = "",
    val year: String = "",
    /** Set once a Book skeleton has been written, so it is never written twice. */
    val seeded: Boolean = false,
    val lastUsedAt: Long = 0,
)

/** A saved destination, e.g. the inbox note or a long-running index note. */
@Entity(tableName = "targets")
data class TargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val path: String,
    /** Blank means "append to the end of the file", which is the only safe option without a heading. */
    val heading: String = "",
    val sortOrder: Int = 0,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = 0,
    val targetPath: String = "",
    val payload: String = "",
    /** [Outcome] name. "Dispatched" only means the intent left the app — see [Outcome]. */
    val outcome: String = "",
    val viaClipboard: Boolean = false,
    val detail: String = "",
)

/** A send that did not get away. Retried in the foreground only. */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = 0,
    val vault: String = "",
    val filePath: String = "",
    val heading: String? = null,
    val payload: String = "",
    val mode: String = "",
    val attempts: Int = 0,
    val lastError: String = "",
)
