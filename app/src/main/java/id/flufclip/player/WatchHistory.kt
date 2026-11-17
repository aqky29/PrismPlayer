package id.flufclip.player

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
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

internal data class PlaylistRef(
    val playlistId: String,
    val playlistTitle: String,
    val playlistVideoCount: String = "",
    val uploader: String = "",
)

internal data class WatchedVideo(
    val url: String, val title: String, val uploader: String, val thumbnail: String, val position: Long,
    val watchedAt: Long = 0, val duration: Long = 0,
    /** Ditonton lewat layar Shorts; null untuk entri lama yang belum menyimpan tanda ini. */
    val short: Boolean? = null,
    /** URL channel pengunggah; null untuk entri lama. Dipakai untuk mengecek siaran live channel yang sering views. */
    val channelUrl: String? = null,
    val viewsText: String = "",
    val publishedText: String = "",
    val progressFraction: Float? = null,
    val playlistId: String? = null,
    val playlistTitle: String? = null,
    val playlistVideoCount: String? = null,
) {
    /** Entri lama tanpa tanda dianggap Short bila durasinya 60 seconds atau kurang. */
    val isShort: Boolean get() = short ?: (duration in 1..60_000L)
}

internal object WatchHistory {
    private val memoryRefs = java.util.concurrent.ConcurrentHashMap<String, PlaylistRef>()

    fun rememberPlaylistRef(videoId: String, ref: PlaylistRef) {
        if (videoId.length == 11 && ref.playlistId.isNotBlank()) {
            memoryRefs[videoId] = ref
        }
    }

    fun playlistRefForVideoId(videoId: String, prefs: SharedPreferences? = null): PlaylistRef? {
        if (videoId.length != 11) return null
        memoryRefs[videoId]?.let { return it }
        if (prefs != null) {
            readRefs(prefs)[videoId]?.let {
                memoryRefs[videoId] = it
                return it
            }
        }
        return null
    }

    private fun readRefs(prefs: SharedPreferences): MutableMap<String, PlaylistRef> = runCatching {
        val obj = JSONObject(prefs.getString("playlist_refs", "{}") ?: "{}")
        val out = LinkedHashMap<String, PlaylistRef>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val item = obj.optJSONObject(k) ?: continue
            val pid = item.optString("playlistId")
            val ptitle = item.optString("playlistTitle")
            if (pid.isNotBlank()) {
                val ref = PlaylistRef(pid, ptitle, item.optString("playlistVideoCount"), item.optString("uploader"))
                out[k] = ref
                memoryRefs[k] = ref
            }
        }
        out
    }.getOrDefault(LinkedHashMap())

    private fun writeRefs(prefs: SharedPreferences, refs: Map<String, PlaylistRef>) {
        val obj = JSONObject()
        refs.entries.toList().takeLast(400).forEach { (k, v) ->
            obj.put(
                k,
                JSONObject()
                    .put("playlistId", v.playlistId)
                    .put("playlistTitle", v.playlistTitle)
                    .put("playlistVideoCount", v.playlistVideoCount)
                    .put("uploader", v.uploader)
            )
        }
        prefs.edit().putString("playlist_refs", obj.toString()).apply()
    }

    fun rememberPlaylistVideos(prefs: SharedPreferences?, ref: PlaylistRef, videoIds: Collection<String>) {
        if (ref.playlistId.isBlank() || videoIds.isEmpty()) return
        val validIds = videoIds.map { if (it.length == 11) it else videoKey(it) }.filter { it.length == 11 }.toSet()
        if (validIds.isEmpty()) return
        validIds.forEach { memoryRefs[it] = ref }
        if (prefs != null) {
            val existing = readRefs(prefs)
            var changed = false
            validIds.forEach { id ->
                if (existing[id] != ref) {
                    existing[id] = ref
                    changed = true
                }
            }
            if (changed) writeRefs(prefs, existing)
            val currentVideos = readRaw(prefs)
            var videosUpdated = false
            val updated = currentVideos.map { video ->
                val vid = videoKey(video.url)
                if (vid in validIds && (video.playlistId != ref.playlistId || video.playlistTitle.isNullOrBlank())) {
                    videosUpdated = true
                    video.copy(
                        playlistId = ref.playlistId,
                        playlistTitle = ref.playlistTitle.ifBlank { video.playlistTitle.orEmpty() }.takeIf { it.isNotBlank() },
                        playlistVideoCount = ref.playlistVideoCount.ifBlank { video.playlistVideoCount.orEmpty() }.takeIf { it.isNotBlank() }
                    )
                } else video
            }
            if (videosUpdated) write(prefs, updated)
        }
    }

    fun enrichVideo(video: WatchedVideo, prefs: SharedPreferences? = null): WatchedVideo {
        if (!video.playlistId.isNullOrBlank() && !video.playlistTitle.isNullOrBlank()) return video
        val vid = videoKey(video.url)
        val meta = HomeFeed.metaOf(video.url)
        val ref = playlistRefForVideoId(vid, prefs)
        val pid = video.playlistId?.takeIf { it.isNotBlank() }
            ?: ref?.playlistId?.takeIf { it.isNotBlank() }
            ?: meta?.playlistId?.takeIf { it.isNotBlank() }
            ?: return video
        val ptitle = video.playlistTitle?.takeIf { it.isNotBlank() }
            ?: ref?.playlistTitle?.takeIf { it.isNotBlank() }
            ?: meta?.playlistTitle?.takeIf { it.isNotBlank() }
        val pcount = video.playlistVideoCount?.takeIf { it.isNotBlank() }
            ?: ref?.playlistVideoCount?.takeIf { it.isNotBlank() }
            ?: meta?.playlistVideoCountText?.takeIf { it.isNotBlank() }
        return video.copy(playlistId = pid, playlistTitle = ptitle, playlistVideoCount = pcount)
    }

    fun enrichAccountEntries(entries: List<AccountHistoryEntry>, prefs: SharedPreferences? = null): List<AccountHistoryEntry> =
        entries.map { entry ->
            val enriched = enrichVideo(entry.video, prefs)
            if (enriched === entry.video) entry else entry.copy(video = enriched)
        }

    private fun readRaw(prefs: SharedPreferences): List<WatchedVideo> = runCatching {
        val array = JSONArray(prefs.getString("videos", "[]"))
        (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val url = item.optString("url")
            if (url.isBlank()) null else WatchedVideo(
                url = url,
                title = item.optString("title"),
                uploader = item.optString("uploader"),
                thumbnail = item.optString("thumbnail"),
                position = item.optLong("position"),
                watchedAt = item.optLong("watchedAt"),
                duration = item.optLong("duration"),
                short = if (item.has("short")) item.optBoolean("short") else null,
                channelUrl = item.optString("channel").takeIf { it.isNotBlank() },
                playlistId = item.optString("playlistId").takeIf { it.isNotBlank() },
                playlistTitle = item.optString("playlistTitle").takeIf { it.isNotBlank() },
                playlistVideoCount = item.optString("playlistVideoCount").takeIf { it.isNotBlank() },
            )
        }
    }.getOrDefault(emptyList())

    fun read(prefs: SharedPreferences): List<WatchedVideo> =
        readRaw(prefs).map { enrichVideo(it, prefs) }

    private fun write(prefs: SharedPreferences, videos: List<WatchedVideo>) {
        val array = JSONArray()
        videos.forEach { video ->
            array.put(
                JSONObject()
                    .put("url", video.url)
                    .put("title", video.title)
                    .put("uploader", video.uploader)
                    .put("thumbnail", video.thumbnail)
                    .put("position", video.position)
                    .put("watchedAt", video.watchedAt)
                    .put("duration", video.duration)
                    .also { json ->
                        video.short?.let { json.put("short", it) }
                        video.channelUrl?.let { json.put("channel", it) }
                        video.playlistId?.takeIf { it.isNotBlank() }?.let { json.put("playlistId", it) }
                        video.playlistTitle?.takeIf { it.isNotBlank() }?.let { json.put("playlistTitle", it) }
                        video.playlistVideoCount?.takeIf { it.isNotBlank() }?.let { json.put("playlistVideoCount", it) }
                    }
            )
        }
        prefs.edit().putString("videos", array.toString()).apply()
    }

    fun save(
        prefs: SharedPreferences,
        url: String,
        title: String,
        uploader: String,
        thumbnail: String,
        position: Long,
        duration: Long,
        short: Boolean? = null,
        channelUrl: String? = null,
        playlistId: String? = null,
        playlistTitle: String? = null,
        playlistVideoCount: String? = null,
    ) {
        val saved = if (duration > 0 && position >= duration - 5000) 0 else position
        val existingList = readRaw(prefs)
        val previous = existingList.firstOrNull { it.url == url }
        val vid = videoKey(url)
        val remembered = playlistRefForVideoId(vid, prefs)
        val resolvedPlaylistId = playlistId?.takeIf { it.isNotBlank() }
            ?: previous?.playlistId?.takeIf { it.isNotBlank() }
            ?: remembered?.playlistId?.takeIf { it.isNotBlank() }
        val resolvedPlaylistTitle = playlistTitle?.takeIf { it.isNotBlank() }
            ?: previous?.playlistTitle?.takeIf { it.isNotBlank() }
            ?: remembered?.playlistTitle?.takeIf { it.isNotBlank() }
        val resolvedPlaylistCount = playlistVideoCount?.takeIf { it.isNotBlank() }
            ?: previous?.playlistVideoCount?.takeIf { it.isNotBlank() }
            ?: remembered?.playlistVideoCount?.takeIf { it.isNotBlank() }
        if (!resolvedPlaylistId.isNullOrBlank() && !resolvedPlaylistTitle.isNullOrBlank()) {
            rememberPlaylistVideos(
                prefs,
                PlaylistRef(resolvedPlaylistId, resolvedPlaylistTitle, resolvedPlaylistCount.orEmpty(), uploader),
                listOf(vid)
            )
        }
        val entry = WatchedVideo(
            url = url,
            title = title,
            uploader = uploader,
            thumbnail = thumbnail,
            position = saved,
            watchedAt = System.currentTimeMillis(),
            duration = duration.coerceAtLeast(0),
            short = short,
            channelUrl = channelUrl?.takeIf { it.isNotBlank() },
            playlistId = resolvedPlaylistId,
            playlistTitle = resolvedPlaylistTitle,
            playlistVideoCount = resolvedPlaylistCount,
        )
        val videos = (listOf(entry) + existingList.filterNot { it.url == url }).take(100)
        livePositions[vid] = saved
        prefs.edit().putLong("resume_$vid", saved).apply()
        write(prefs, videos)
    }
    fun remove(prefs: SharedPreferences, url: String) {
        val vid = videoKey(url)
        livePositions.remove(vid)
        prefs.edit().remove("resume_$vid").apply()
        write(prefs, readRaw(prefs).filterNot { it.url == url })
    }
    fun clear(prefs: SharedPreferences) {
        livePositions.clear()
        prefs.edit().remove("videos").apply()
    }

    private val livePositions = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Menyimpan posisi pemutaran secara instan dan ringan (hanya key-value Long)
     * tanpa membaca/menulis ulang seluruh array JSON (100 item) dan tanpa
     * memicu rekomposisi Compose di tengah pemutaran video.
     */
    fun recordPlaybackPosition(prefs: SharedPreferences, url: String, position: Long, duration: Long) {
        val saved = if (duration > 0 && position >= duration - 5000) 0L else position
        val vid = videoKey(url)
        livePositions[vid] = saved
        prefs.edit().putLong("resume_$vid", saved).apply()
    }

    /**
     * Mengambil posisi resume terakhir untuk video tertentu.
     * Mengutamakan posisi terbaru di memori/SharedPreferences, lalu fallback ke riwayat tontonan.
     */
    fun getResumePosition(prefs: SharedPreferences, url: String, fallbackList: List<WatchedVideo> = emptyList()): Long {
        val vid = videoKey(url)
        livePositions[vid]?.let { return it }
        val saved = prefs.getLong("resume_$vid", -1L)
        if (saved >= 0L) return saved
        return fallbackList.firstOrNull { videoKey(it.url) == vid }?.position ?: 0L
    }
}

private fun historyDate(timestamp: Long): LocalDate? = if (timestamp > 0) runCatching {
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
}.getOrNull() else null

private fun historyDateLabel(date: LocalDate?, today: LocalDate): String = when (date) {
    null -> "Earlier"
    today -> "Today"
    today.minusDays(1) -> "Kemarin"
    else -> date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.US))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WatchHistoryScreen(videos: List<WatchedVideo>, onBack: () -> Unit, onOpen: (WatchedVideo) -> Unit, onClear: () -> Unit, onRemove: (WatchedVideo) -> Unit, historyActions: HistoryActions, onResetRecommendations: (() -> Unit)? = null) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val filtered = videos.filter { query.isBlank() || it.title.contains(query, true) || it.uploader.contains(query, true) }
    val sections = filtered.groupBy { historyDate(it.watchedAt) }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear watch history?") },
        text = { Text("All watch history saved on this device will be deleted.") },
        confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
    )
    Column(Modifier.fillMaxSize().background(Background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (searching) { searching = false; query = "" } else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            if (searching) {
                TextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Search watch history") },
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") }
            } else {
                Text("History", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "Search history") }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreHoriz, "History options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Clear all watch history") }, enabled = videos.isNotEmpty(), onClick = { menuOpen = false; confirmClear = true })
                        if (onResetRecommendations != null) DropdownMenuItem(text = { Text("Reset hidden videos and channels") }, onClick = { menuOpen = false; onResetRecommendations() })
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (videos.isEmpty()) "No watch history yet" else "No results", color = Color.LightGray)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                sections.forEach { (date, entries) ->
                    item(key = "date_${date ?: "unknown"}") {
                        Text(historyDateLabel(date, today), Modifier.padding(start = 16.dp, top = 20.dp, bottom = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(entries, key = { it.url }) { video ->
                        HistoryVideoRow(video, onOpen = { onOpen(video) }, actions = historyActions.copy(remove = onRemove))
                    }
                }
            }
        }
    }
}

@Composable private fun HistoryVideoRow(video: WatchedVideo, onOpen: () -> Unit, actions: HistoryActions) {
    val playlistId = video.playlistId?.takeIf { it.isNotBlank() }
    val primaryTitle = video.playlistTitle?.takeIf { it.isNotBlank() } ?: video.title
    val progress = video.progressFraction
        ?: if (video.position > 0 && video.duration > 0) (video.position.toFloat() / video.duration).coerceIn(0f, 1f) else null
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
        if (playlistId != null) {
            PlaylistStackedThumbnail(
                playlist = AccountPlaylist(playlistId, primaryTitle, video.thumbnail),
                modifier = Modifier.width(152.dp),
                badgeText = video.playlistVideoCount?.takeIf { it.isNotBlank() } ?: "Playlist",
                progressFraction = progress
            )
        } else {
            Box(Modifier.width(152.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Surface)) {
                AsyncImage(video.thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (video.duration > 0) {
                    val seconds = video.duration / 1000
                    val label = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
                    Text(label, Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color(0xCC000000), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                if (progress != null && progress > 0f) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(3.dp).background(Color.Red))
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, top = 1.dp)) {
            Text(primaryTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            if (playlistId != null && !video.playlistTitle.isNullOrBlank() && !video.title.equals(video.playlistTitle, ignoreCase = true)) {
                Spacer(Modifier.height(2.dp))
                Text(video.title, color = Color(0xFFCCCCCC), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            val byline = if (playlistId != null) {
                listOf(video.uploader, "Playlist").filter { it.isNotBlank() }.joinToString(" · ")
            } else video.uploader
            Text(byline, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
        HistoryVideoMenu(video, actions)
    }
}

@Composable internal fun AccountHistoryScreen(
    entries: List<AccountHistoryEntry>, loading: Boolean, error: String?, hasMore: Boolean,
    onBack: () -> Unit, onOpen: (WatchedVideo) -> Unit, onRefresh: () -> Unit,
    onMore: () -> Unit, onDeviceHistory: () -> Unit, onManage: () -> Unit,
    onNavigate: (String) -> Unit, historyActions: HistoryActions,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var menu by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 100 } }
    val filtered = entries.filter {
        (query.isBlank() || it.video.title.contains(query, true) || it.video.playlistTitle?.contains(query, true) == true || it.video.uploader.contains(query, true)) &&
            when (filter) {
                "All" -> true
                "Shorts" -> it.video.isShort
                "Playlists" -> !it.video.playlistId.isNullOrBlank()
                else -> !it.video.isShort
            }
    }
    // Keep server order and section boundaries while collecting adjacent Shorts into a shelf,
    // and collapsing consecutive items from the same playlist in the same date section.
    val groups = remember(filtered) {
        val result = mutableListOf<List<AccountHistoryEntry>>()
        for (entry in filtered) {
            val previous = result.lastOrNull()
            val prevFirst = previous?.firstOrNull()
            if (entry.video.isShort && prevFirst?.video?.isShort == true && prevFirst.section == entry.section) {
                result[result.lastIndex] = previous + entry
            } else if (!entry.video.playlistId.isNullOrBlank() && prevFirst?.video?.playlistId == entry.video.playlistId && prevFirst?.section == entry.section) {
                // Keep the latest watched episode as the representative entry for this playlist in the section.
            } else {
                result.add(listOf(entry))
            }
        }
        result
    }
    val bottomNavAutoHide = rememberBottomNavAutoHideState(filter, searching)
    FrostedGlassBackdropHost(Modifier.fillMaxSize().background(Background).statusBarsPadding().navigationBarsPadding().bottomNavAutoHide(bottomNavAutoHide)) {
        val glassBackdrop = LocalFrostedGlassBackdrop.current
        Column(Modifier.fillMaxSize().frostedGlassSource(glassBackdrop)) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                if (searching) {
                    TextField(query, { query = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Search loaded history") })
                    IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Default.Close, "Close search") }
                } else {
                    if (collapsed) Text("History", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge) else Spacer(Modifier.weight(1f))
                    IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "Search history") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, "History options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Refresh") }, enabled = !loading, onClick = { menu = false; onRefresh() })
                            DropdownMenuItem(text = { Text("Device history") }, onClick = { menu = false; onDeviceHistory() })
                            DropdownMenuItem(text = { Text("Manage on YouTube") }, onClick = { menu = false; onManage() })
                        }
                    }
                }
            }
            LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
                item {
                    Text("History", Modifier.padding(start = 16.dp, top = 8.dp, bottom = 16.dp), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    TextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search loaded history") },
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF212121), unfocusedContainerColor = Color(0xFF212121), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("All", "Videos", "Shorts", "Playlists")) { choice ->
                            val selected = filter == choice
                            Box(
                                Modifier.height(32.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Color(0xFFF1F1F1) else Color(0xFF272727))
                                    .clickable { filter = choice }.padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(choice, color = if (selected) Color(0xFF0F0F0F) else Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
                if (loading && entries.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (error != null) item { Text(error, Modifier.padding(16.dp), color = Color.LightGray) }
                if (!loading && filtered.isEmpty() && entries.isNotEmpty()) item { Text("No matching loaded history", Modifier.padding(16.dp), color = Color.LightGray) }
                groups.forEachIndexed { index, group ->
                    val entry = group.first()
                    if (!entry.section.isNullOrBlank() && (index == 0 || groups[index - 1].first().section != entry.section)) {
                        item { Text(entry.section, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    }
                    if (entry.video.isShort) {
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(group) { short ->
                                    Box(Modifier.width(166.dp).height(250.dp).clip(RoundedCornerShape(8.dp)).background(Surface).clickable { onOpen(short.video) }) {
                                        AsyncImage(short.video.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        Box(Modifier.fillMaxWidth().height(100.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f)))))
                                        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                                            Text(short.video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                            if (short.viewsText.isNotBlank()) Text(short.viewsText, color = Color.White, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Box(Modifier.align(Alignment.TopEnd)) { HistoryVideoMenu(short.video, historyActions) }
                                    }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                    } else {
                        item {
                            val video = entry.video
                            val playlistId = video.playlistId?.takeIf { it.isNotBlank() }
                            val primaryTitle = video.playlistTitle?.takeIf { it.isNotBlank() } ?: video.title
                            val progress = video.progressFraction
                                ?: if (video.position > 0 && video.duration > 0) (video.position.toFloat() / video.duration).coerceIn(0f, 1f) else null
                            Row(Modifier.fillMaxWidth().clickable { onOpen(video) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                                if (playlistId != null) {
                                    PlaylistStackedThumbnail(
                                        playlist = AccountPlaylist(playlistId, primaryTitle, video.thumbnail),
                                        modifier = Modifier.width(172.dp),
                                        badgeText = video.playlistVideoCount?.takeIf { it.isNotBlank() } ?: "Playlist",
                                        progressFraction = progress
                                    )
                                } else {
                                    Box(Modifier.width(172.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Surface)) {
                                        AsyncImage(video.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        if (video.duration > 0) {
                                            val sec = video.duration / 1000
                                            val text = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)
                                            Text(text, Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = .8f), RoundedCornerShape(3.dp)).padding(horizontal = 3.dp), style = MaterialTheme.typography.labelSmall)
                                        }
                                        if (progress != null && progress > 0f) {
                                            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(3.dp).background(Color(0xFFFF0033)))
                                        }
                                    }
                                }
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text(primaryTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                    if (playlistId != null && !video.playlistTitle.isNullOrBlank() && !video.title.equals(video.playlistTitle, ignoreCase = true)) {
                                        Text(video.title, color = Color(0xFFCCCCCC), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                                    }
                                    val byline = if (playlistId != null) {
                                        listOf(video.uploader, "Playlist").filter { it.isNotBlank() }.joinToString(" · ")
                                    } else video.uploader
                                    if (byline.isNotBlank()) Text(byline, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                                }
                                HistoryVideoMenu(video, historyActions)
                            }
                        }
                    }
                }
                if (hasMore) item { TextButton(onClick = onMore, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "Loading…" else "Load more") } }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) {
            HomeNavigation("you", onNavigate, visible = bottomNavAutoHide.visible)
        }
    }
}
