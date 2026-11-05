package id.flufclip.player

import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Kata pencarian terakhir (terbaru dulu, maks. 20) di prefs "search_history", kunci "recent". */
@Stable
internal class SearchHistoryStore(private val preferences: SharedPreferences) {
    var terms by mutableStateOf(read()); private set

    fun add(term: String) {
        terms = (listOf(term) + terms.filterNot { it.equals(term, ignoreCase = true) }).take(20)
        save()
    }

    fun remove(term: String) {
        terms = terms.filterNot { it == term }
        save()
    }

    private fun read(): List<String> = runCatching {
        val json = org.json.JSONArray(preferences.getString("recent", "[]"))
        (0 until json.length()).map { json.getString(it) }
    }.getOrDefault(emptyList())

    private fun save() {
        preferences.edit().putString("recent", org.json.JSONArray(terms).toString()).apply()
    }
}
