package com.duzui.sharetoobsi.domain

/** A line of rules, split into the ones that compile and the ones the user has to fix. */
data class CompiledRules(
    val patterns: List<Regex> = emptyList(),
    val invalid: List<String> = emptyList(),
)

/**
 * One regex per line, blank lines ignored.
 *
 * Invalid lines come back rather than being dropped: a rule that silently does nothing is
 * the hardest kind of bug to notice, and the editor can point at the line instead.
 */
fun compileRules(text: String): CompiledRules {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val results = lines.map { line -> line to runCatching { Regex(line) } }
    return CompiledRules(
        patterns = results.mapNotNull { it.second.getOrNull() },
        invalid = results.filter { it.second.isFailure }.map { it.first },
    )
}

/** Profiles the user wrote share a namespace with the built-ins, so they need a prefix. */
const val USER_PROFILE_PREFIX = "user:"

fun userProfileId(rowId: Long): String = "$USER_PROFILE_PREFIX$rowId"

/** Rebuilds an editable [SourceProfile] from a stored row. */
fun userProfile(
    rowId: Long,
    name: String,
    lineRules: String,
    inlineRules: String,
    template: String,
): SourceProfile = SourceProfile(
    id = userProfileId(rowId),
    label = name.ifBlank { "未命名" },
    linePatterns = compileRules(lineRules).patterns,
    inlinePatterns = compileRules(inlineRules).patterns,
    template = template.trim(),
)
