package com.duzui.sharetoobsi.domain

/**
 * Rules that strip the junk a particular app wraps around a highlight.
 *
 * These are starting points, not gospel: every reading app changes its share payload
 * between versions, so treat a mismatch as a reason to add a rule rather than to
 * distrust the pipeline. Capture the raw text from the share screen to tune them.
 *
 * @param packages     app package names this profile auto-selects for.
 * @param linePatterns whole lines whose trimmed content matches are dropped.
 * @param inlinePatterns matches inside a line are deleted, leaving the rest of the line.
 * @param template     output template for this profile; blank uses the global one.
 */
data class SourceProfile(
    val id: String,
    val label: String,
    val packages: List<String> = emptyList(),
    val linePatterns: List<Regex> = emptyList(),
    val inlinePatterns: List<Regex> = emptyList(),
    val template: String = "",
)

object SourceProfiles {

    /**
     * Lines no genuine quote would ever be: the preamble a reading app wraps around a
     * highlight, and its calls to action. Kept here rather than in one app's profile
     * because the sharing package is not always reported, and a Kindle highlight that
     * arrives as "generic" should still come out clean.
     */
    private val SHARE_CHROME_LINES = listOf(
        // Kindle's share preamble; the book identity is read out of it separately.
        Regex("""^我在\s*.{0,40}?所著的\s*《.*》.*$"""),
        Regex("""^「?.*」?\s*を読んでいます.*$"""),
        // Calls to action, whether or not the link survived.
        Regex("""^.*(開始免費閱讀|开始免费阅读|免費閱讀這本書|免费阅读这本书|在此处购买|查看详情|前往阅读)"""),
        Regex("""^.*(無料で読む|続きを読む|今すぐ読む|本を読む)"""),
        Regex(
            """^(read more|start reading|continue reading|buy now|buy the book|read this quote|keep reading)\b.*$""",
            RegexOption.IGNORE_CASE,
        ),
    )

    val KINDLE = SourceProfile(
        id = "kindle",
        label = "Kindle",
        packages = listOf("com.amazon.kindle", "com.amazon.kindlefc"),
        inlinePatterns = listOf(
            // Kindle appends a store link to the highlight.
            Regex("""\s*https?://\S+"""),
        ),
        linePatterns = SHARE_CHROME_LINES + listOf(
            // Attribution offers, e.g. "—— from 《Meditations》 by Marcus Aurelius". The
            // leading dash is required: dropping any line that merely mentions "amazon"
            // would silently eat a highlight *about* Amazon.
            Regex("""^\s*[—–\-]{1,3}\s*(from|摘自|来自)\b.*$""", RegexOption.IGNORE_CASE),
            Regex(
                """^\s*[—–\-]{1,3}\s*.*\b(kindle|amazon|amzn\.to|a\.co)\b.*$""",
                RegexOption.IGNORE_CASE,
            ),
        ),
    )

    /**
     * Everything else. Readest, 小米笔记 and most editors share the selection verbatim, so
     * beyond the universal share-chrome rules there is nothing app-specific to strip.
     */
    val GENERIC = SourceProfile(
        id = "generic",
        label = "Generic",
        linePatterns = SHARE_CHROME_LINES,
    )

    val ALL = listOf(KINDLE, GENERIC)

    private val byPackage: Map<String, SourceProfile> =
        ALL.flatMap { profile -> profile.packages.map { it to profile } }.toMap()

    fun forPackage(packageName: String?): SourceProfile =
        packageName?.let { byPackage[it] } ?: GENERIC

    fun byId(id: String?): SourceProfile =
        ALL.firstOrNull { it.id == id } ?: GENERIC
}
