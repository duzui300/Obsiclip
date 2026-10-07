package com.duzui.sharetoobsi.domain

/** Starting values shaped around the `random` vault's conventions. All editable in settings. */
object Defaults {

    const val VAULT = "random"

    /** The section book notes and reading notes keep highlights in. */
    const val HEADING = "Quotes worth keeping"

    /** Vault-relative path of the note for the book currently being read. */
    const val BOOK_PATH_TEMPLATE = "30-Reading/Book/{title}.md"

    /** The vault's default folder for unsorted capture. */
    const val INBOX_PATH = "00-Inbox"

    const val TAGS = "#reading"

    /**
     * [text] is already markdown-wrapped by [Cleanup] when wrapQuote is on, so the
     * attribution becomes its own quote block rather than being re-prefixed.
     */
    const val TEMPLATE = "{text}\n\n> — {author}《{title}》{year} {tags}"

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
