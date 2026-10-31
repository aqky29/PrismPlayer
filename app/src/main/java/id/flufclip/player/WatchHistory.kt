package id.flufclip.player

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class WatchedVideo(
    val url: String, val title: String, val uploader: String, val thumbnail: String, val position: Long,
    val watchedAt: Long = 0, val duration: Long = 0
)

internal object WatchHistory {
    fun read(prefs: SharedPreferences): List<WatchedVideo> = runCatching {
        val array = JSONArray(prefs.getString("videos", "[]"))
        (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val url = item.optString("url")
            if (url.isBlank()) null else WatchedVideo(url, item.optString("title"), item.optString("uploader"), item.optString("thumbnail"), item.optLong("position"), item.optLong("watchedAt"), item.optLong("duration"))
        }
    }.getOrDefault(emptyList())

    private fun write(prefs: SharedPreferences, videos: List<WatchedVideo>) {
        val array = JSONArray()
        videos.forEach { video -> array.put(JSONObject().put("url", video.url).put("title", video.title).put("uploader", video.uploader).put("thumbnail", video.thumbnail).put("position", video.position).put("watchedAt", video.watchedAt).put("duration", video.duration)) }
        prefs.edit().putString("videos", array.toString()).apply()
    }

    fun save(prefs: SharedPreferences, url: String, title: String, uploader: String, thumbnail: String, position: Long, duration: Long) {
        val saved = if (duration > 0 && position >= duration - 5000) 0 else position
        val videos = (listOf(WatchedVideo(url, title, uploader, thumbnail, saved, System.currentTimeMillis(), duration.coerceAtLeast(0))) + read(prefs).filterNot { it.url == url }).take(100)
        write(prefs, videos)
    }
    fun remove(prefs: SharedPreferences, url: String) = write(prefs, read(prefs).filterNot { it.url == url })
    fun clear(prefs: SharedPreferences) { prefs.edit().remove("videos").apply() }
}

private fun historyDate(timestamp: Long): LocalDate? = if (timestamp > 0) runCatching {
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
}.getOrNull() else null

private fun historyDateLabel(date: LocalDate?, today: LocalDate): String = when (date) {
    null -> "Sebelumnya"
    today -> "Hari ini"
    today.minusDays(1) -> "Kemarin"
    else -> date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("id", "ID")))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WatchHistoryScreen(videos: List<WatchedVideo>, onBack: () -> Unit, onOpen: (WatchedVideo) -> Unit, onClear: () -> Unit, onRemove: (WatchedVideo) -> Unit, onResetRecommendations: (() -> Unit)? = null) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val filtered = videos.filter { query.isBlank() || it.title.contains(query, true) || it.uploader.contains(query, true) }
    val sections = filtered.groupBy { historyDate(it.watchedAt) }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Hapus riwayat tontonan?") },
        text = { Text("Semua riwayat tontonan yang tersimpan di perangkat ini akan dihapus.") },
        confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("Hapus") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } }
    )
    Column(Modifier.fillMaxSize().background(Background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (searching) { searching = false; query = "" } else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            if (searching) {
                TextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Telusuri riwayat tontonan") },
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Hapus pencarian") }
            } else {
                Text("Riwayat", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "Telusuri riwayat") }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Opsi riwayat") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Hapus semua riwayat tontonan") }, enabled = videos.isNotEmpty(), onClick = { menuOpen = false; confirmClear = true })
                        if (onResetRecommendations != null) DropdownMenuItem(text = { Text("Reset video dan channel yang disembunyikan") }, onClick = { menuOpen = false; onResetRecommendations() })
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (videos.isEmpty()) "Belum ada riwayat tontonan" else "Tidak ada hasil", color = Color.LightGray)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                sections.forEach { (date, entries) ->
                    item(key = "date_${date ?: "unknown"}") {
                        Text(historyDateLabel(date, today), Modifier.padding(start = 16.dp, top = 20.dp, bottom = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(entries, key = { it.url }) { video ->
                        HistoryVideoRow(video, onOpen = { onOpen(video) }, onRemove = { onRemove(video) })
                    }
                }
            }
        }
    }
}

@Composable private fun HistoryVideoRow(video: WatchedVideo, onOpen: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember(video.url) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(152.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Surface)) {
            AsyncImage(video.thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (video.duration > 0) {
                val seconds = video.duration / 1000
                val label = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
                Text(label, Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color(0xCC000000), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                if (video.position > 0) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth((video.position.toFloat() / video.duration).coerceIn(0f, 1f)).height(3.dp).background(Color.Red))
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, top = 1.dp)) {
            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(video.uploader, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "Opsi video") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Hapus dari riwayat tontonan") }, onClick = { menuOpen = false; onRemove() })
            }
        }
    }
}
