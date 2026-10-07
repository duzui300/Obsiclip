package com.duzui.sharetoobsi.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The language this app draws itself in.
 *
 * Only the two languages that exist as resources. [System] means "no override", which is
 * what the app did before there was a setting — the device picks.
 */
enum class AppLanguage(val tag: String?) {
    System(null),
    English("en"),
    Chinese("zh"),
    ;

    val locale: Locale? get() = tag?.let(Locale::forLanguageTag)
}

/**
 * The app's own language choice, kept where it can be read synchronously.
 *
 * SharedPreferences rather than the settings DataStore for one reason: the override has to
 * be known in `attachBaseContext`, before the activity exists to read a flow from. The
 * alternative is blocking the main thread on a disk read during startup.
 */
object LanguageStore {

    private const val PREFS = "language"
    private const val KEY = "appLanguage"

    fun get(context: Context): AppLanguage {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return AppLanguage.entries.firstOrNull { it.name == stored } ?: AppLanguage.System
    }

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language.name)
            .apply()
    }
}

/**
 * [base] with the chosen language applied, so every resource lookup resolves in it — this
 * includes Compose's `stringResource`, which reads from the context it is given.
 *
 * Deliberately not `LocaleManager`: that exists only from API 33, and one path that behaves
 * the same on every supported version is worth more than system integration here. The cost
 * is that the system's per-app language screen will not show a language chosen in here.
 */
fun localizedContext(base: Context, language: AppLanguage): Context {
    val locale = language.locale ?: return base
    // Also the default, so dates and numbers follow the same choice rather than the device.
    Locale.setDefault(locale)
    val configuration = Configuration(base.resources.configuration)
    configuration.setLocale(locale)
    return base.createConfigurationContext(configuration)
}
