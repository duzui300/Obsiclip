package com.duzui.sharetoobsi.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved destination for a write.
 *
 * A book is just a target whose path came from the path template, which is why the author
 * and year live here rather than in a separate table: they exist to fill the `{author}`
 * and `{year}` placeholders of that book's quotes, and nothing else.
 */
@Entity(tableName = "targets")
data class TargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Doubles as `{title}` in the path template and in the attribution line. */
    val name: String,
    val path: String,
    /** Blank means "append to the end of the file", the only safe option without a heading. */
    val heading: String = "",
    val author: String = "",
    val year: String = "",
    /** Set once a Book skeleton has been written, so it is never written twice. */
    val seeded: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = 0,
    val targetPath: String = "",
    /** Null means the file's end, which is also what an unset heading resolves to. */
    val heading: String? = null,
    val payload: String = "",
    /** "Dispatched" only means the intent left the app — see SendOutcome. */
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

/**
 * A rule set the user wrote. Pattern strings rather than compiled regexes, because the
 * editor has to show them back and point at the ones that do not compile.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** One regex per line; a matching line is dropped. */
    val lineRules: String = "",
    /** One regex per line; each match is deleted, leaving the rest of the line. */
    val inlineRules: String = "",
    /** Blank falls back to the global template. */
    val template: String = "",
)

/** Pins one source app to one profile, built-in or user-written. */
@Entity(tableName = "app_profiles")
data class AppProfileEntity(
    @PrimaryKey val packageName: String,
    val profileId: String,
)
