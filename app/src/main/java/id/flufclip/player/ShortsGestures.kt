package id.flufclip.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Gestur satu jari di halaman Shorts, mengikuti pengamatan pada penangan sentuh YouTube:
 * - ketuk: bila ketuk dua kali mungkin, ditunda selama jendela ketuk kedua (waktu sistem), kalau tidak langsung;
 * - ketuk dua kali: diteruskan beserta titik ketuknya;
 * - tahan di zona tepi: 2x (lihat ShortsScreen); tahan di zona tengah: membuka deskripsi. Mulai setelah batas tahan sistem; selama menahan sentuhan dikonsumsi sehingga pager tidak menggulir;
 *   menarik jari lebih dari [PULL_DP] ke bawah dari titik awal menandai "tarik" (untuk mengunci/keluar dari 2x),
 *   dan melepas jari melaporkan apakah sedang dalam keadaan tarik.
 * Geser vertikal lebih dulu dikonsumsi pager (mengambil alih), sehingga ketuk/tahan otomatis batal.
 */
@Composable
internal fun Modifier.shortsGestures(
    enabled: Boolean,
    doubleTapEnabled: Boolean,
    onTap: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onHoldStart: () -> Unit,
    onHoldPull: (Boolean) -> Unit,
    onHoldEnd: (pulled: Boolean) -> Unit,
    onHoldCenter: () -> Unit,
): Modifier {
    val tap by rememberUpdatedState(onTap)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val holdStart by rememberUpdatedState(onHoldStart)
    val holdPull by rememberUpdatedState(onHoldPull)
    val holdEnd by rememberUpdatedState(onHoldEnd)
    val holdCenter by rememberUpdatedState(onHoldCenter)
    val canDouble by rememberUpdatedState(doubleTapEnabled)
    if (!enabled) return this
    return pointerInput(Unit) {
        val pullPx = PULL_DP.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var cancelled = false
            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation().also { if (it == null) cancelled = true }
            }
            if (up == null) {
                if (cancelled) return@awaitEachGesture
                // Zona tepi (20% lebar di kiri dan kanan) = 2x. Zona tengah = perintah tahan dari server, yang di YouTube
                // membuka panel deskripsi (`atez.ci(2)` menjalankan `blgz.x`); sentuhan tetap dikonsumsi agar pager tidak menggulir.
                val edge = down.position.x < size.width * EDGE_ZONE || down.position.x > size.width * (1f - EDGE_ZONE)
                if (!edge) {
                    holdCenter()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                    }
                    return@awaitEachGesture
                }
                holdStart()
                var pulled = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    val nowPulled = change.position.y - down.position.y > pullPx
                    if (nowPulled != pulled) { pulled = nowPulled; holdPull(pulled) }
                }
                holdEnd(pulled)
                return@awaitEachGesture
            }
            up.consume()
            if (!canDouble) { tap(); return@awaitEachGesture }
            val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown(requireUnconsumed = false) }
            if (second == null) { tap(); return@awaitEachGesture }
            second.consume()
            val secondUp = waitForUpOrCancellation()
            if (secondUp != null) { secondUp.consume(); doubleTap(second.position) }
        }
    }
}

/** Lebar zona tepi per sisi (fraksi lebar layar); bawaan YouTube 0,2 (konfigurasi 45751590). */
private const val EDGE_ZONE = 0.2f

/** Jarak tarik ke bawah (dp) yang mengubah tahan 2x menjadi kunci/keluar. YouTube memakai 50 px mentah (sekitar 17–20 dp). */
private const val PULL_DP = 20
