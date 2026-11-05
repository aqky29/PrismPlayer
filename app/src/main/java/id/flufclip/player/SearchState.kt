package id.flufclip.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
}
