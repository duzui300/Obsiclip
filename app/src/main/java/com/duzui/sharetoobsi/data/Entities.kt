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
/**
 * Where a capture goes, and how it is shaped on arrival. Deliberately knows nothing about
 * *what* is being read: one target — `30-Reading/Book/{title}.md` under
 * `## Quotes worth keeping` — serves every book.
 *
 * [path] is a template, filled from the book chosen alongside it, which is what makes the
 * pairing work without a target per book.
 */
@Entity(tableName = "targets")
data class TargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** A label for the chip. Defaults to the file's own name when left blank. */
    val name: String,
    val path: String,
    /** Blank means "append to the end of the file", the only safe option without a heading. */
    val heading: String = "",
    /** A named output format; null uses the default one. */
    val formatId: Long? = null,
    /** Position in the chip row, which is also the order in settings. */
    val sortOrder: Int = 0,
)

/**
 * What is being read. Pure metadata: it fills `{title}`, `{author}` and `{year}` and
 * decides nothing about where the words end up.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val year: String = "",
    val sortOrder: Int = 0,
)

/** A named output template, so a target can pick a shape rather than edit one. */
@Entity(tableName = "formats")
data class FormatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val template: String,
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
