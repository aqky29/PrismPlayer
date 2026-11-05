package id.flufclip.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * State riwayat tonton akun YouTube. App() membuatnya ulang setiap sesi akun berganti
 * (`remember(sessionRevision, isLoggedIn)`), jadi isinya kosong lagi untuk akun baru.
 */
@Stable
internal class AccountHistoryState(loggedIn: Boolean) {
    var entries by mutableStateOf<List<AccountHistoryEntry>>(emptyList())
    var continuation by mutableStateOf<String?>(null)
    var loading by mutableStateOf(loggedIn)
    var error by mutableStateOf<String?>(null)
    /** Naikkan untuk memuat ulang halaman pertama. */
    var request by mutableIntStateOf(0)
    /** Naikkan untuk memuat halaman berikutnya. */
    var nextRequest by mutableIntStateOf(0)
    /** Pengguna yang login sedang melihat riwayat perangkat, bukan riwayat akun. */
    var deviceHistory by mutableStateOf(false)
}
