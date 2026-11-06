package id.flufclip.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.InfoItem

/**
 * State untuk pencarian: query aktif, filter kategori pencarian, riwayat kata terakhir,
 * saran pencarian, dan paging hasil pencarian.
 */
@Stable
internal class SearchState {
    var active by mutableStateOf(false)
    var lastSearch by mutableStateOf("")
    var filter by mutableStateOf("all")
    var query by mutableStateOf("")
    var results by mutableStateOf<List<InfoItem>>(emptyList())
    var suggestions by mutableStateOf<List<String>>(emptyList())
    var page by mutableStateOf<SearchPage?>(null)
    var moreLoading by mutableStateOf(false)
    var moreError by mutableStateOf<String?>(null)
    var generation by mutableIntStateOf(0)
    var mode by mutableStateOf(false)

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

