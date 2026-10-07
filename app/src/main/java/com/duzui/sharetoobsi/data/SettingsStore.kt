package com.duzui.sharetoobsi.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duzui.sharetoobsi.domain.CleanupOptions
import com.duzui.sharetoobsi.domain.Defaults
import com.duzui.sharetoobsi.domain.WriteMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Everything the pipeline needs that is not a list. */
data class AppSettings(
    val vault: String = Defaults.VAULT,
    val heading: String = Defaults.HEADING,
    val template: String = Defaults.TEMPLATE,
    val pathTemplate: String = Defaults.BOOK_PATH_TEMPLATE,
    val tags: String = Defaults.TAGS,
    val mode: WriteMode = WriteMode.ADVANCED,
    /**
     * On opening without a share — from the launcher or the quick settings tile — adopt
     * whatever is on the clipboard. This is what makes "Copy in the reader, tap the tile"
     * work without a second tap.
     */
    val autoReadClipboard: Boolean = true,
    /**
     * Hand the user back to the app they shared from instead of leaving them in Obsidian.
     * Off by default: jumping away from a note the user may want to look at is rude
     * unless they asked for it.
     */
    val returnToSource: Boolean = false,
    val defaultProfileId: String = "generic",
    /**
     * Whether the inbox has been offered as a target yet. Seeded once, not re-added when
     * missing: the user is free to delete or rename it like any other target.
     */
    val defaultTargetSeeded: Boolean = false,
    /**
     * Which target a capture lands in when nothing else is chosen. Null means the first
     * one in the chip order, which is what most people want anyway.
     */
    val defaultTargetId: Long? = null,
    /**
     * Write a collected batch straight away instead of showing it for review.
     * Off by default: a collapsed highlight reads exactly like a short one, so an
     * unreviewed batch can quietly contain fragments.
     */
    val autoWriteImports: Boolean = false,
    /** The starter output formats are offered once, then belong to the user. */
    val presetFormatsSeeded: Boolean = false,
    val cleanup: CleanupOptions = CleanupOptions(),
)

private val Context.settingsDataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val vault = stringPreferencesKey("vault")
        val heading = stringPreferencesKey("heading")
        val template = stringPreferencesKey("template")
        val pathTemplate = stringPreferencesKey("pathTemplate")
        val tags = stringPreferencesKey("tags")
        val mode = stringPreferencesKey("mode")
        val autoReadClipboard = booleanPreferencesKey("autoReadClipboard")
        val returnToSource = booleanPreferencesKey("returnToSource")
        val defaultProfileId = stringPreferencesKey("defaultProfileId")
        val defaultTargetSeeded = booleanPreferencesKey("defaultTargetSeeded")
        val defaultTargetId = longPreferencesKey("defaultTargetId")
        val presetFormatsSeeded = booleanPreferencesKey("presetFormatsSeeded")
        val autoWriteImports = booleanPreferencesKey("autoWriteImports")

        val normalize = booleanPreferencesKey("cleanup.normalize")
        val stripBoilerplate = booleanPreferencesKey("cleanup.stripBoilerplate")
        val stripLoneUrlLines = booleanPreferencesKey("cleanup.stripLoneUrlLines")
        val dropLinkFooterLines = booleanPreferencesKey("cleanup.dropLinkFooterLines")
        val unwrapLines = booleanPreferencesKey("cleanup.unwrapLines")
        val collapseBlankLines = booleanPreferencesKey("cleanup.collapseBlankLines")
        val wrapHighlight = booleanPreferencesKey("cleanup.wrapHighlight")
        val wrapQuote = booleanPreferencesKey("cleanup.wrapQuote")
    }

    private val defaults = AppSettings()

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val base = defaults.cleanup
        AppSettings(
            vault = prefs[Keys.vault] ?: defaults.vault,
            heading = prefs[Keys.heading] ?: defaults.heading,
            template = prefs[Keys.template] ?: defaults.template,
            pathTemplate = prefs[Keys.pathTemplate] ?: defaults.pathTemplate,
            tags = prefs[Keys.tags] ?: defaults.tags,
            mode = prefs[Keys.mode]?.let { runCatching { WriteMode.valueOf(it) }.getOrNull() }
                ?: defaults.mode,
            autoReadClipboard = prefs[Keys.autoReadClipboard] ?: defaults.autoReadClipboard,
            returnToSource = prefs[Keys.returnToSource] ?: defaults.returnToSource,
            defaultProfileId = prefs[Keys.defaultProfileId] ?: defaults.defaultProfileId,
            defaultTargetSeeded = prefs[Keys.defaultTargetSeeded] ?: defaults.defaultTargetSeeded,
            defaultTargetId = prefs[Keys.defaultTargetId],
            presetFormatsSeeded = prefs[Keys.presetFormatsSeeded] ?: defaults.presetFormatsSeeded,
            autoWriteImports = prefs[Keys.autoWriteImports] ?: defaults.autoWriteImports,
            cleanup = CleanupOptions(
                normalize = prefs[Keys.normalize] ?: base.normalize,
                stripBoilerplate = prefs[Keys.stripBoilerplate] ?: base.stripBoilerplate,
                stripLoneUrlLines = prefs[Keys.stripLoneUrlLines] ?: base.stripLoneUrlLines,
                dropLinkFooterLines = prefs[Keys.dropLinkFooterLines] ?: base.dropLinkFooterLines,
                unwrapLines = prefs[Keys.unwrapLines] ?: base.unwrapLines,
                collapseBlankLines = prefs[Keys.collapseBlankLines] ?: base.collapseBlankLines,
                wrapHighlight = prefs[Keys.wrapHighlight] ?: base.wrapHighlight,
                wrapQuote = prefs[Keys.wrapQuote] ?: base.wrapQuote,
            ),
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(settings.first())
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.vault] = next.vault
            prefs[Keys.heading] = next.heading
            prefs[Keys.template] = next.template
            prefs[Keys.pathTemplate] = next.pathTemplate
            prefs[Keys.tags] = next.tags
            prefs[Keys.mode] = next.mode.name
            prefs[Keys.autoReadClipboard] = next.autoReadClipboard
            prefs[Keys.returnToSource] = next.returnToSource
            prefs[Keys.defaultProfileId] = next.defaultProfileId
            prefs[Keys.defaultTargetSeeded] = next.defaultTargetSeeded
            if (next.defaultTargetId == null) {
                prefs.remove(Keys.defaultTargetId)
            } else {
                prefs[Keys.defaultTargetId] = next.defaultTargetId
            }
            prefs[Keys.presetFormatsSeeded] = next.presetFormatsSeeded
            prefs[Keys.autoWriteImports] = next.autoWriteImports
            prefs[Keys.normalize] = next.cleanup.normalize
            prefs[Keys.stripBoilerplate] = next.cleanup.stripBoilerplate
            prefs[Keys.stripLoneUrlLines] = next.cleanup.stripLoneUrlLines
            prefs[Keys.dropLinkFooterLines] = next.cleanup.dropLinkFooterLines
            prefs[Keys.unwrapLines] = next.cleanup.unwrapLines
            prefs[Keys.collapseBlankLines] = next.cleanup.collapseBlankLines
            prefs[Keys.wrapHighlight] = next.cleanup.wrapHighlight
            prefs[Keys.wrapQuote] = next.cleanup.wrapQuote
        }
    }
}
