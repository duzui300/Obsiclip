package com.duzui.sharetoobsi.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    val inboxPath: String = Defaults.INBOX_NOTE,
    val tags: String = Defaults.TAGS,
    val mode: WriteMode = WriteMode.ADVANCED,
    val silent: Boolean = true,
    /**
     * Hand the user back to the app they shared from instead of leaving them in Obsidian.
     * Off by default: jumping away from a note the user may want to look at is rude
     * unless they asked for it.
     */
    val returnToSource: Boolean = false,
    val defaultProfileId: String = "generic",
    val cleanup: CleanupOptions = CleanupOptions(),
)

private val Context.settingsDataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val vault = stringPreferencesKey("vault")
        val heading = stringPreferencesKey("heading")
        val template = stringPreferencesKey("template")
        val pathTemplate = stringPreferencesKey("pathTemplate")
        val inboxPath = stringPreferencesKey("inboxPath")
        val tags = stringPreferencesKey("tags")
        val mode = stringPreferencesKey("mode")
        val silent = booleanPreferencesKey("silent")
        val returnToSource = booleanPreferencesKey("returnToSource")
        val defaultProfileId = stringPreferencesKey("defaultProfileId")

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
            inboxPath = prefs[Keys.inboxPath] ?: defaults.inboxPath,
            tags = prefs[Keys.tags] ?: defaults.tags,
            mode = prefs[Keys.mode]?.let { runCatching { WriteMode.valueOf(it) }.getOrNull() }
                ?: defaults.mode,
            silent = prefs[Keys.silent] ?: defaults.silent,
            returnToSource = prefs[Keys.returnToSource] ?: defaults.returnToSource,
            defaultProfileId = prefs[Keys.defaultProfileId] ?: defaults.defaultProfileId,
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
            prefs[Keys.inboxPath] = next.inboxPath
            prefs[Keys.tags] = next.tags
            prefs[Keys.mode] = next.mode.name
            prefs[Keys.silent] = next.silent
            prefs[Keys.returnToSource] = next.returnToSource
            prefs[Keys.defaultProfileId] = next.defaultProfileId
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
