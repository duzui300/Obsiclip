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
 * Missing values must not leave debris. `> — {author}《{title}》{year}` with no author
 * collapses to `> — 《反建築論》1976`; with nothing at all the line disappears rather than
 * leaving a stray `> —`; and with no title the brackets go too, rather than leaving `《》`.
 */
object Template {

    private const val BRACES = """\{\{?\s*([A-Za-z_]+)\s*\}?\}"""

    private val PLACEHOLDER = Regex(BRACES)

    /** `《{title}》`, `「{source}」`, `({year})`: the brackets belong to the value. */
    private val WRAPPED_PLACEHOLDER = Regex("([《「（(\\[【])" + BRACES + "([》」）)\\]】])")

    private val DECORATION_ONLY = Regex("""[>\-–—《》「」\[\]（）()【】:：,，.。、#*_~|\s]+""")

    private val TRAILING_WHITESPACE = Regex("[ \\t]+$")

    /**
     * `> — #reading` — an attribution dash with nothing left to introduce. Only matches a
     * line that is nothing but the dash and tags, so a quote line beginning `- #tag` is safe.
     * MULTILINE matters: without it `^`/`$` anchor to the whole text and only a one-line
     * payload would ever match.
     */
    private val DANGLING_ATTRIBUTION = Regex(
        """^([> \t]*)[—–\-]+[ \t]+(#[^\s#]+(?:[ \t]+#[^\s#]+)*)[ \t]*$""",
        RegexOption.MULTILINE,
    )

    /** Left behind when a value between two literals vanishes. Markdown collapses these anyway. */
    private val RUN_OF_SPACES = Regex("[ \\t]{2,}")

    private val BLANK_RUN = Regex("\n{3,}")

    /** Substitution only, no cleanup: for value-shaped strings such as vault paths. */
    fun substitute(template: String, values: TemplateValues): String {
        val resolved = resolve(values)
        val prepared = WRAPPED_PLACEHOLDER.replace(template) { match ->
            val name = match.groupValues[2].lowercase()
            if (resolved[name].isNullOrEmpty()) "" else match.value
        }
        return PLACEHOLDER.replace(prepared) { match ->
            val name = match.groupValues[1].lowercase()
            resolved[name] ?: match.value
        }
    }

    fun render(template: String, values: TemplateValues): String {
        val substituted = substitute(template, values)
        val tidied = DANGLING_ATTRIBUTION.replace(pruneDecorationLines(substituted)) { match ->
            match.groupValues[1] + match.groupValues[2]
        }
        return BLANK_RUN.replace(tidied, "\n\n").trim()
    }

    /** Drops lines that are only markdown/punctuation noise once the values are gone. */
    private fun pruneDecorationLines(text: String): String =
        text.lines()
            .filterNot { line ->
                line.isNotBlank() && DECORATION_ONLY.replace(line, "").isEmpty()
            }
            .joinToString("\n") { line ->
                RUN_OF_SPACES.replace(TRAILING_WHITESPACE.replace(line, ""), " ")
            }

    private fun resolve(values: TemplateValues): Map<String, String> = mapOf(
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

    /** Every placeholder name, for the settings screen's cheat sheet. */
    val placeholders: List<String> =
        listOf("text", "title", "author", "year", "url", "source", "tags", "page", "date")
}
