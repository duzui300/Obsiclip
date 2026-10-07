package com.duzui.sharetoobsi.domain

/** Starting values shaped around the `random` vault's conventions. All editable in settings. */
object Defaults {

    const val VAULT = "random"

    /** The section book notes and reading notes keep highlights in. */
    const val HEADING = "Quotes worth keeping"

    /** Vault-relative path of the note for the book currently being read. */
    const val BOOK_PATH_TEMPLATE = "30-Reading/Book/{title}.md"

    /**
     * Where captures go when no book has been named. A rolling note rather than
     * `00-Inbox/_index.md`, which is a landing page with live query blocks in it.
     */
    const val INBOX_NOTE = "00-Inbox/摘录.md"

    /** The seeded inbox target's name. It is an ordinary target, not a special case. */
    const val TAGS = "#reading"

    /**
     * [text] is already markdown-wrapped by [Cleanup] when wrapQuote is on, so the
     * attribution becomes its own quote block rather than being re-prefixed.
     */
    const val TEMPLATE = "{text}\n\n> — {author}《{title}》{year} {tags}"

    /**
     * Starter output formats, offered once on first run.
     *
     * Templates only: the names that go with them are UI text, so they come from resources
     * when these are seeded. Ordinary rows afterwards — editable and deletable — so treat
     * this as a starting point rather than a fixture.
     */
    val PRESET_FORMATS: List<String> = listOf(
        "{text}\n\n> — {author}《{title}》{year} {tags}",
        "{text}",
        "{text}\n\n— {author}《{title}》{year} {tags}",
        "> =={text}==\n>\n> — {author}《{title}》{tags}",
        "{text}\n\n> — {author}《{title}》{year} · {date} {tags}",
        "### {title}\n\n{text}\n\n> — {author} {year} {tags}",
    )

    /** The book-note skeleton, mirroring `Templates/Book.md` in the vault. */
    fun bookSkeleton(title: String, author: String?, year: String?, date: String): String {
        val authorLine = listOfNotNull(author?.takeIf { it.isNotBlank() }, year?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
        return buildString {
            appendLine("---")
            appendLine("type: book")
            appendLine("shelf: reading")
            appendLine("author: ${author.orEmpty()}")
            appendLine("year: ${year.orEmpty()}")
            appendLine("rating: ")
            appendLine("link: ")
            appendLine("cover: ")
            appendLine("added: $date")
            appendLine("tags: [reading, reading/book]")
            appendLine("---")
            appendLine()
            appendLine("# $title")
            appendLine()
            appendLine("> [!info] Book")
            appendLine("> $authorLine")
            appendLine()
            appendLine("## Why I want to read it")
            appendLine("- ")
            appendLine()
            appendLine("## Notes")
            appendLine("- ")
            appendLine()
            appendLine("## Quotes worth keeping")
        }
    }
}
