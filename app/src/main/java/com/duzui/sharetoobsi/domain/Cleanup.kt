package com.duzui.sharetoobsi.domain

/** Which pipeline stages to run. Every stage is independently switchable from settings. */
data class CleanupOptions(
    val normalize: Boolean = true,
    val stripBoilerplate: Boolean = true,
    val stripLoneUrlLines: Boolean = true,
    val unwrapLines: Boolean = true,
    val collapseBlankLines: Boolean = true,
    val wrapHighlight: Boolean = false,
    val wrapQuote: Boolean = true,
)

/**
 * Turns whatever an app handed us into clean quote text.
 *
 * The stages are ordered so that each one sees the output of the last:
 * line endings are normalised before anything line-based, boilerplate is gone before
 * lines get joined (otherwise a dangling attribution line gets welded onto the quote),
 * and markdown wrapping is applied last.
 *
 * The risky stage is [unwrapLines]: a hard-wrapped paragraph and a deliberate line
 * break look identical from here, so it stays behind a switch and bails out of
 * anything that resembles a list or a heading.
 */
object Cleanup {

    private val CJK = Regex(
        "[\\u3000-\\u303F\\u3040-\\u30FF\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF\\uFF00-\\uFFEF]"
    )

    /** Ends a sentence, so the next line is a new sentence rather than a wrapped continuation. */
    private val SENTENCE_END = Regex("[。！？；：…!?;:.。」』）》】〕”’\"']$")

    private val LIST_OR_HEADING_START = Regex("^\\s*(-|\\*|\\+|>|#{1,6}\\s|\\d+[.)]|•)\\s*")

    private val LONE_URL_LINE = Regex("^<?https?://\\S+>?$")

    private val ZERO_WIDTH = Regex("[\\u200B-\\u200D\\uFEFF\\u2060]")

    private val TRAILING_WHITESPACE = Regex("[ \\t]+$")

    private val BLANK_RUN = Regex("\n{3,}")

    fun clean(
        raw: String,
        profile: SourceProfile,
        options: CleanupOptions = CleanupOptions(),
        extraLineRules: List<Regex> = emptyList(),
    ): String {
        var text = raw
        if (options.normalize) text = normalize(text)
        if (options.stripBoilerplate || options.stripLoneUrlLines) {
            text = stripBoilerplate(text, profile, extraLineRules, options.stripLoneUrlLines)
        }
        if (options.unwrapLines) text = unwrapLines(text)
        if (options.collapseBlankLines) text = collapseBlankLines(text)
        text = trimLines(text)
        // Trim before markdown wrapping: dropping boilerplate can leave a trailing blank
        // line, which wrapQuote would otherwise turn into a dangling `>`.
        text = text.trim()
        if (options.wrapHighlight) text = wrapHighlight(text)
        if (options.wrapQuote) text = wrapQuote(text)
        return text.trim()
    }

    private fun normalize(text: String): String = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace('\u00A0', ' ')
        .replace(ZERO_WIDTH, "")

    /**
     * Drops whole lines: bare links, plus whatever the source profile and the user's own
     * rules reject. Inline patterns edit within a line instead, so `quote https://t.co/x`
     * keeps the quote.
     */
    private fun stripBoilerplate(
        text: String,
        profile: SourceProfile,
        extraLineRules: List<Regex>,
        dropLoneUrls: Boolean,
    ): String {
        var result = text
        for (pattern in profile.inlinePatterns) {
            result = pattern.replace(result, "")
        }

        val lineRules = buildList {
            if (dropLoneUrls) add(LONE_URL_LINE)
            addAll(profile.linePatterns)
            addAll(extraLineRules)
        }
        if (lineRules.isEmpty()) return result

        return result.lines()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() && lineRules.any { it.matches(trimmed) }
            }
            .joinToString("\n")
    }

    private fun unwrapLines(text: String): String =
        text.split(Regex("\n[ \\t]*\n")).joinToString("\n\n") { unwrapParagraph(it) }

    private fun unwrapParagraph(paragraph: String): String {
        val lines = paragraph.lines()
        if (lines.size < 2) return paragraph
        if (lines.any { LIST_OR_HEADING_START.containsMatchIn(it) }) return paragraph

        val out = StringBuilder()
        var accumulated = lines.first().trimEnd()
        for (i in 1 until lines.size) {
            val next = lines[i]
            if (shouldJoin(lines[i - 1], next)) {
                accumulated = joinPair(accumulated, next)
            } else {
                out.append(accumulated).append('\n')
                accumulated = next.trimEnd()
            }
        }
        return out.append(accumulated).toString()
    }

    private fun shouldJoin(previous: String, next: String): Boolean {
        val end = previous.trimEnd()
        val start = next.trimStart()
        if (end.isEmpty() || start.isEmpty()) return false
        if (LIST_OR_HEADING_START.containsMatchIn(start)) return false
        return !SENTENCE_END.containsMatchIn(end)
    }

    private fun joinPair(accumulated: String, next: String): String {
        val joined = next.trim()
        // English line break inside a word: "architec-" + "ture" -> "architecture".
        if (accumulated.endsWith("-") && !accumulated.endsWith("--") &&
            joined.isNotEmpty() && joined.first().isLowerCase()
        ) {
            return accumulated.dropLast(1) + joined
        }
        val cjk = CJK.containsMatchIn(accumulated.takeLast(1)) || CJK.containsMatchIn(joined.take(1))
        return if (cjk) accumulated + joined else "$accumulated $joined"
    }

    private fun collapseBlankLines(text: String): String = BLANK_RUN.replace(text, "\n\n")

    private fun trimLines(text: String): String =
        text.lines().joinToString("\n") { TRAILING_WHITESPACE.replace(it, "") }

    private fun wrapQuote(text: String): String =
        text.lines().joinToString("\n") { if (it.isBlank()) ">" else "> $it" }

    private fun wrapHighlight(text: String): String =
        text.split(Regex("\n[ \\t]*\n")).joinToString("\n\n") { block ->
            val trimmed = block.trim()
            if (trimmed.isEmpty()) block else "==$trimmed=="
        }
}
