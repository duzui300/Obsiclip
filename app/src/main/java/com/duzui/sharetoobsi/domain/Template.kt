package com.duzui.sharetoobsi.domain

/** The values a template can interpolate. Anything null renders as empty and is then pruned. */
data class TemplateValues(
    val text: String,
    val title: String? = null,
    val author: String? = null,
    val year: String? = null,
    val url: String? = null,
    val source: String? = null,
    val tags: String? = null,
    val page: String? = null,
    val date: String? = null,
)

/**
 * Renders `{placeholder}` templates. `{{title}}` is accepted too, because the vault's own
 * note templates use that form and muscle memory should not punish the user.
 *
 * Missing values must not leave debris: `> — {author}《{title}》{year}` with no author
 * should collapse to `> — 《反建築論》1976`, and if the whole line has nothing left but
 * punctuation it disappears rather than leaving a stray `> —`.
 */
object Template {

    private val PLACEHOLDER = Regex("""\{\{?\s*([A-Za-z_]+)\s*\}?\}""")

    private val DECORATION_ONLY = Regex("""[>\-–—《》「」\[\]（）()【】:：,，.。、#*_~|\s]+""")

    private val TRAILING_WHITESPACE = Regex("[ \\t]+$")

    private val BLANK_RUN = Regex("\n{3,}")

    fun render(template: String, values: TemplateValues): String {
        val resolved = mapOf(
            "text" to values.text,
            "title" to values.title.orEmpty(),
            "author" to values.author.orEmpty(),
            "year" to values.year.orEmpty(),
            "url" to values.url.orEmpty(),
            "source" to values.source.orEmpty(),
            "tags" to values.tags.orEmpty(),
            "page" to values.page.orEmpty(),
            "date" to values.date.orEmpty(),
        )

        val substituted = PLACEHOLDER.replace(template) { match ->
            resolved[match.groupValues[1].lowercase()] ?: match.value
        }

        return BLANK_RUN.replace(pruneDecorationLines(substituted), "\n\n").trim()
    }

    /**
     * Drops lines that are only markdown/punctuation noise once the values are gone, and
     * strips trailing spaces so a substituted value can never leave a markdown hard break.
     */
    private fun pruneDecorationLines(text: String): String =
        text.lines()
            .filterNot { line ->
                line.isNotBlank() && DECORATION_ONLY.replace(line, "").isEmpty()
            }
            .joinToString("\n") { line -> TRAILING_WHITESPACE.replace(line, "") }

    /** Every placeholder name, for the settings screen's cheat sheet. */
    val placeholders: List<String> =
        listOf("text", "title", "author", "year", "url", "source", "tags", "page", "date")
}
