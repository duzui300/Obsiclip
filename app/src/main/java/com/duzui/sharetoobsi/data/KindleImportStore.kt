package com.duzui.sharetoobsi.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One highlight lifted off Kindle's notebook screen. */
data class CollectedItem(
    val text: String,
    /** Kindle shows the page beside each entry; often absent, occasionally localised oddly. */
    val page: String? = null,
    /**
     * The list collapses long quotes, and a collapsed quote looks exactly like a short one —
     * so this is a guess, and anything carrying it is never written without being asked.
     */
    val suspect: Boolean = false,
)

/**
 * Where the accessibility service leaves what it found for the app to pick up.
 *
 * SharedPreferences rather than Room on purpose: the service is a separate process
 * component with no view model, and it only ever needs to publish a small finished list.
 */
class KindleImportStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("kindle_import", Context.MODE_PRIVATE)

    /**
     * Set by the service while it is connected. The app uses it to tell whether the
     * collector is really running — on some ROMs the system setting is not readable by
     * third-party apps, so believing it would mean never showing the feature as ready.
     */
    var connected: Boolean
        get() = prefs.getBoolean(KEY_CONNECTED, false)
        set(value) = prefs.edit().putBoolean(KEY_CONNECTED, value).apply()

    /** Set by the app, cleared once the collection finishes or is abandoned. */
    var armed: Boolean
        get() = prefs.getBoolean(KEY_ARMED, false)
        set(value) = prefs.edit().putBoolean(KEY_ARMED, value).apply()

    /** True once a run has finished, so the app knows there is something waiting. */
    var finished: Boolean
        get() = prefs.getBoolean(KEY_FINISHED, false)
        set(value) = prefs.edit().putBoolean(KEY_FINISHED, value).apply()

    /** True when the service could reach the bottom on its own rather than needing help. */
    var autoScrolled: Boolean
        get() = prefs.getBoolean(KEY_AUTO, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO, value).apply()

    fun arm() {
        prefs.edit()
            .putBoolean(KEY_ARMED, true)
            .putBoolean(KEY_FINISHED, false)
            .putBoolean(KEY_AUTO, false)
            .putString(KEY_ITEMS, null)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .putBoolean(KEY_ARMED, false)
            .putBoolean(KEY_FINISHED, false)
            .putBoolean(KEY_AUTO, false)
            .putString(KEY_ITEMS, null)
            .apply()
    }

    fun publish(items: List<CollectedItem>, autoScrolled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_ARMED, false)
            .putBoolean(KEY_FINISHED, true)
            .putBoolean(KEY_AUTO, autoScrolled)
            .putString(KEY_ITEMS, encode(items))
            .apply()
    }

    fun items(): List<CollectedItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                CollectedItem(
                    text = obj.getString("text"),
                    page = obj.optString("page").takeIf { it.isNotBlank() },
                    suspect = obj.optBoolean("suspect"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(items: List<CollectedItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("text", item.text)
                    item.page?.let { put("page", it) }
                    put("suspect", item.suspect)
                }
            )
        }
        return array.toString()
    }

    private companion object {
        const val KEY_CONNECTED = "connected"
        const val KEY_ARMED = "armed"
        const val KEY_FINISHED = "finished"
        const val KEY_AUTO = "autoScrolled"
        const val KEY_ITEMS = "items"
    }
}
