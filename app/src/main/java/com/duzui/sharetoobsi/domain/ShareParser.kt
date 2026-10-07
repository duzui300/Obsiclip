package com.duzui.sharetoobsi.domain

/** Book identity inferred from the text a reading app wrapped around a highlight. */
data class SourceHint(val title: String?, val author: String?)

/**
 * Reading apps wrap a highlight in a preamble that names the book — which is both noise
 * to strip and the only place the title appears. Built against a real Kindle share:
 *
 *     我在 平野啓一郎 所著的《本心 (Japanese Edition)》中讀到以下這段引述時，就想到您：
 *     「僕にはまだ、お母さんが必要なんだよ。」
 *     開始免費閱讀這本書：https://read.amazon.co.jp/kp/kshare?asin=B092J53NPG
 *
 * Deliberately conservative. A wrong title files the highlight in a brand new note
 * instead of the real one, which is worse than leaving the field for the user to fill.
 */
object ShareParser {

    /** `我在 {author} 所著的《{title}》…` — Kindle's Chinese share. */
    private val CHINESE_PREAMBLE = Regex("""我在\s*(.{1,40}?)\s*所著的\s*《(.{1,80}?)》""")

    /** `Read this quote from {title} by {author}` — Kindle's English share. */
    private val ENGLISH_PREAMBLE =
        Regex("""[Rr]ead\w*\s+(?:this\s+)?(?:quote\s+)?from\s+《?(.{1,80}?)》?\s+by\s+(.{1,40})""")

    /** A bracketed title is only trusted on a line that otherwise looks like a preamble. */
    private val QUOTED_TITLE = Regex("""《(.{1,80}?)》""")

    private val PREAMBLE_MARKER =
        Regex("""(所著|引述|分享|推薦|推荐|quote|reading|read this)""", RegexOption.IGNORE_CASE)

    private val EDITION_SUFFIX = Regex(
        """\s*[（(][^（()）]{0,30}?(?:Edition|版|Kindle|eBook)[^（()）]{0,20}?[)）]\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun extract(text: String): SourceHint {
        CHINESE_PREAMBLE.find(text)?.let { match ->
            return SourceHint(
                title = cleanTitle(match.groupValues[2]),
                author = match.groupValues[1].trim().ifBlank { null },
            )
        }
        ENGLISH_PREAMBLE.find(text)?.let { match ->
            return SourceHint(
                title = cleanTitle(match.groupValues[1]),
                author = match.groupValues[2].trim().ifBlank { null },
            )
        }
        // Fall back to a bracketed title, but only from the opening line of a preamble —
        // a book title mentioned *inside* a highlight must not rename the target.
        val opening = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return SourceHint(null, null)
        if (!PREAMBLE_MARKER.containsMatchIn(opening)) return SourceHint(null, null)
        return SourceHint(
            title = QUOTED_TITLE.find(opening)?.let { cleanTitle(it.groupValues[1]) },
            author = null,
        )
    }

    /**
     * Kindle appends an edition marker to the title — `本心 (Japanese Edition)` — while the
     * vault note is plain `本心.md`, so leaving it on would create a second note.
     */
    fun cleanTitle(raw: String): String =
        EDITION_SUFFIX.replace(raw.trim(), "").trim().ifBlank { raw.trim() }
}
