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
 */
data class SourceProfile(
    val id: String,
    val label: String,
    val packages: List<String> = emptyList(),
    val linePatterns: List<Regex> = emptyList(),
    val inlinePatterns: List<Regex> = emptyList(),
)

object SourceProfiles {

    /** Fallback for any app we have no profile for. Deliberately conservative. */
    val GENERIC = SourceProfile(
        id = "generic",
        label = "通用",
    )

    val KINDLE = SourceProfile(
        id = "kindle",
        label = "Kindle",
        packages = listOf("com.amazon.kindle", "com.amazon.kindlefc"),
        inlinePatterns = listOf(
            // Kindle appends a store/short link to the highlight.
            Regex("""\s*https?://\S+"""),
        ),
        // Every rule here demands a leading attribution dash. Dropping any line that merely
        // mentions "amazon" would silently eat a highlight *about* Amazon.
        linePatterns = listOf(
            Regex("""^\s*[—–\-]{1,3}\s*(from|摘自|来自)\b.*$""", RegexOption.IGNORE_CASE),
            Regex("""^\s*[—–\-]{1,3}\s*.*\b(kindle|amazon|amzn\.to|a\.co)\b.*$""", RegexOption.IGNORE_CASE),
            Regex("""^\s*在?\s*Kindle\s*(阅读器|App)?\s*(中|里)?\s*(阅读|查看|购买|分享).*$"""),
        ),
    )

    val WEREAD = SourceProfile(
        id = "weread",
        label = "微信读书",
        packages = listOf("com.tencent.weread"),
        inlinePatterns = listOf(
            Regex("""\s*https?://\S+"""),
        ),
        linePatterns = listOf(
            // 尾部出处行, e.g. "—— 《书名》作者"
            Regex("""^\s*[—–\-]{1,3}\s*《.*$"""),
            Regex("""^\s*[—–\-]{1,3}\s*.*(weread\.qq\.com|微信读书).*$"""),
        ),
    )

    val READEST = SourceProfile(
        id = "readest",
        label = "Readest",
        packages = listOf("com.bilingify.readest"),
        inlinePatterns = listOf(
            Regex("""\s*https?://\S+"""),
        ),
    )

    val ALL = listOf(KINDLE, WEREAD, READEST, GENERIC)

    /** Package names that report a package the user actually shared from. */
    private val byPackage: Map<String, SourceProfile> =
        ALL.flatMap { profile -> profile.packages.map { it to profile } }.toMap()

    fun forPackage(packageName: String?): SourceProfile =
        packageName?.let { byPackage[it] } ?: GENERIC

    fun byId(id: String?): SourceProfile =
        ALL.firstOrNull { it.id == id } ?: GENERIC
}
