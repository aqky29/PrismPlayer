package id.flufclip.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory

internal const val SEARCH_SUGGESTION_DEBOUNCE_MS = 130L
internal const val SEARCH_SUGGESTION_LIMIT = 20

internal val SEARCH_FILTER_CHIPS: List<Pair<String, String>> = listOf(
    YoutubeSearchQueryHandlerFactory.ALL to "All",
    YoutubeSearchQueryHandlerFactory.VIDEOS to "Videos",
    YoutubeSearchQueryHandlerFactory.CHANNELS to "Channels",
    YoutubeSearchQueryHandlerFactory.PLAYLISTS to "Playlists",
    YoutubeSearchQueryHandlerFactory.MUSIC_SONGS to "Songs",
    YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS to "Music videos",
    YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS to "Albums",
)

internal fun isInlineSearchFilter(filter: String): Boolean =
    filter == YoutubeSearchQueryHandlerFactory.ALL ||
        filter == YoutubeSearchQueryHandlerFactory.VIDEOS ||
        filter == YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS

internal fun buildSuggestionBuilderField(term: String): TextFieldValue {
    val filled = "${term.trimEnd()} "
    return TextFieldValue(text = filled, selection = TextRange(filled.length))
}

/**
 * State untuk pencarian: query aktif, filter kategori pencarian, riwayat kata terakhir,
 * saran pencarian, koreksi ejaan, dan paging hasil pencarian.
 */
@Stable
internal class SearchState {
    var active by mutableStateOf(false)
    var lastSearch by mutableStateOf("")
    var filter by mutableStateOf(YoutubeSearchQueryHandlerFactory.ALL)
    var queryField by mutableStateOf(TextFieldValue(""))
    val query: String get() = queryField.text
    var results by mutableStateOf<List<InfoItem>>(emptyList())
    var suggestions by mutableStateOf<List<String>>(emptyList())
    var searchSuggestion by mutableStateOf<String?>(null)
    var isCorrectedSearch by mutableStateOf(false)
    var page by mutableStateOf<SearchPage?>(null)
    var moreLoading by mutableStateOf(false)
    var moreError by mutableStateOf<String?>(null)
    var generation by mutableIntStateOf(0)
    var mode by mutableStateOf(false)

    fun setQueryText(text: String, cursorAtEnd: Boolean = true) {
        val selection = if (cursorAtEnd) TextRange(text.length) else TextRange.Zero
        queryField = TextFieldValue(text = text, selection = selection)
    }

    fun fillQueryFromSuggestion(term: String) {
        queryField = buildSuggestionBuilderField(term)
    }

    fun moreSearchResults(scope: CoroutineScope, loading: Boolean) {
        val page = page ?: return
        if (page.next == null || moreLoading || loading) return
        moreLoading = true; moreError = null
        val generation = generation
        scope.launch {
            try {
                val next = VideoRepository.moreSearch(page)
                if (generation != this@SearchState.generation) return@launch
                val updated = (results + next.items).distinctBy { it.url }
                this@SearchState.page = if (updated.size == results.size) next.copy(next = null) else next
                results = updated
            } catch (e: Exception) { if (generation == this@SearchState.generation) moreError = "Failed to load more results: ${e.message ?: "try again"}" }
            finally { if (generation == this@SearchState.generation) moreLoading = false }
        }
    }
}

