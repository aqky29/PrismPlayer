package id.flufclip.player

import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

internal data class SearchHistoryEntry(
    val term: String,
    val thumbnailUrl: String? = null,
    val avatarUrl: String? = null,
    val subtitle: String? = null,
    val hasNewVideo: Boolean = false,
)

/** Kata pencarian terakhir beserta pratinjau visual (terbaru dulu, maks. 20) di prefs "search_history". */
@Stable
internal class SearchHistoryStore(private val preferences: SharedPreferences) {
    var entries by mutableStateOf(readEntries()); private set
    val terms: List<String> get() = entries.map { it.term }

    fun add(
        term: String,
        thumbnailUrl: String? = null,
        avatarUrl: String? = null,
        subtitle: String? = null,
        hasNewVideo: Boolean = false,
    ) {
        val clean = term.trim()
        if (clean.isBlank()) return
        val existing = entries.firstOrNull { it.term.equals(clean, ignoreCase = true) }
        val merged = SearchHistoryEntry(
            term = clean,
            thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() } ?: existing?.thumbnailUrl,
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() } ?: existing?.avatarUrl,
            subtitle = subtitle?.takeIf { it.isNotBlank() } ?: existing?.subtitle,
            hasNewVideo = hasNewVideo || (existing?.hasNewVideo == true),
        )
        entries = (listOf(merged) + entries.filterNot { it.term.equals(clean, ignoreCase = true) }).take(20)
        save()
    }

    fun updatePreview(
        term: String,
        thumbnailUrl: String? = null,
        avatarUrl: String? = null,
        subtitle: String? = null,
        hasNewVideo: Boolean = false,
    ) {
        val clean = term.trim()
        if (clean.isBlank()) return
        val index = entries.indexOfFirst { it.term.equals(clean, ignoreCase = true) }
        if (index < 0) return
        val current = entries[index]
        val updated = current.copy(
            thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() } ?: current.thumbnailUrl,
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() } ?: current.avatarUrl,
            subtitle = subtitle?.takeIf { it.isNotBlank() } ?: current.subtitle,
            hasNewVideo = hasNewVideo,
        )
        if (updated != current) {
            entries = entries.toMutableList().also { it[index] = updated }
            save()
        }
    }

    fun remove(term: String) {
        entries = entries.filterNot { it.term.equals(term, ignoreCase = true) }
        save()
    }

    fun entryFor(term: String): SearchHistoryEntry? =
        entries.firstOrNull { it.term.equals(term.trim(), ignoreCase = true) }

    private fun readEntries(): List<SearchHistoryEntry> = runCatching {
        val richRaw = preferences.getString("recent_entries_v2", null)
        if (!richRaw.isNullOrBlank()) {
            val arr = JSONArray(richRaw)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val term = obj.optString("term").trim()
                if (term.isBlank()) null
                else SearchHistoryEntry(
                    term = term,
                    thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                    avatarUrl = obj.optString("avatarUrl").takeIf { it.isNotBlank() },
                    subtitle = obj.optString("subtitle").takeIf { it.isNotBlank() },
                    hasNewVideo = obj.optBoolean("hasNewVideo", false),
                )
            }
        } else {
            val json = JSONArray(preferences.getString("recent", "[]"))
            (0 until json.length()).mapNotNull { idx ->
                val term = json.optString(idx).trim()
                if (term.isBlank()) null else SearchHistoryEntry(term = term)
            }
        }
    }.getOrDefault(emptyList())

    private fun save() {
        val richArray = JSONArray()
        val legacyArray = JSONArray()
        entries.forEach { entry ->
            legacyArray.put(entry.term)
            richArray.put(
                JSONObject()
                    .put("term", entry.term)
                    .put("thumbnailUrl", entry.thumbnailUrl.orEmpty())
                    .put("avatarUrl", entry.avatarUrl.orEmpty())
                    .put("subtitle", entry.subtitle.orEmpty())
                    .put("hasNewVideo", entry.hasNewVideo)
            )
        }
        preferences.edit()
            .putString("recent", legacyArray.toString())
            .putString("recent_entries_v2", richArray.toString())
            .apply()
    }
}
