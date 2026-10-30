package id.flufclip.player

import android.app.DownloadManager
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType

internal val Background = Color(0xFF0F0F0F)
internal val Surface = Color(0xFF272727)
private val Accent = Color(0xFFFF3B30)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Background, surface = Surface)) { App() } } }
}

@Composable private fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val preferences = remember { context.getSharedPreferences("search_history", Context.MODE_PRIVATE) }
    var history by remember { mutableStateOf(runCatching {
        val json = org.json.JSONArray(preferences.getString("recent", "[]"))
        (0 until json.length()).map { json.getString(it) }
    }.getOrDefault(emptyList())) }
    var searchActive by remember { mutableStateOf(false) }
    var lastSearch by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<InfoItem>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchPage by remember { mutableStateOf<SearchPage?>(null) }
    var searchMoreLoading by remember { mutableStateOf(false) }
    var searchMoreError by remember { mutableStateOf<String?>(null) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    var home by remember { mutableStateOf<List<StreamInfoItem>>(emptyList()) }
    var searchMode by remember { mutableStateOf(false) }
    LaunchedEffect(searchActive, query) {
        suggestions = emptyList()
        val term = query.trim()
        if (searchActive && term.isNotBlank()) {
            delay(350)
            try { suggestions = VideoRepository.suggestions(term).distinct().take(10) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { suggestions = emptyList() }
        }
    }
    var channel by remember { mutableStateOf<ChannelPage?>(null) }
    var channelTab by remember { mutableStateOf(ChannelTabs.VIDEOS) }
    var channelItems by remember { mutableStateOf<List<InfoItem>>(emptyList()) }
    var playlist by remember { mutableStateOf<PlaylistInfo?>(null) }
    var selected by remember { mutableStateOf<StreamInfo?>(null) }
    var minimized by remember { mutableStateOf(false) }
    var miniPlayerWidth by remember { mutableStateOf(220.dp) }
    var fullscreen by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    var category by remember { mutableStateOf("Semua") }
    var categoryItems by remember { mutableStateOf<List<InfoItem>>(emptyList()) }
    var homePage by remember { mutableStateOf<SearchPage?>(null) }
    var categoryPage by remember { mutableStateOf<SearchPage?>(null) }
    var feedLoading by remember { mutableStateOf(false) }
    var feedError by remember { mutableStateOf<String?>(null) }
    var feedEnd by remember { mutableStateOf(false) }
    var topicIndex by remember { mutableIntStateOf(0) }
    val topics = remember { listOf("video terbaru", "musik terbaru", "game terbaru", "sains teknologi terbaru", "film terbaru", "olahraga terbaru") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDownloads by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<DownloadChoice?>(null) }
    val downloadState by DownloadStatus.state.collectAsState()
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val choice = pendingDownload
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null && choice != null) {
            try {
                val flags = result.data?.flags ?: 0
                if (flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val intent = Intent(context, DownloadService::class.java).putExtra("url", choice.url).putExtra("destination", uri.toString()).putExtra("height", choice.height).putExtra("audio", choice.audio)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) { DownloadStatus.state.value = DownloadState(message = "Tidak bisa memulai unduhan: ${e.message}") }
        }
        pendingDownload = null
    }
    var showQuality by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf<VideoStream?>(null) }
    var selectedAudio by remember { mutableStateOf<AudioStream?>(null) }
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    fun setFullscreen(value: Boolean) {
        fullscreen = value
        (context as? Activity)?.let { activity ->
            val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            if (value) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
    fun shareVideo(info: StreamInfo) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, info.url)
        context.startActivity(Intent.createChooser(intent, "Bagikan video"))
    }
    fun copyVideoLink(info: StreamInfo) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Link video", info.url))
        android.widget.Toast.makeText(context, "Link disalin", android.widget.Toast.LENGTH_SHORT).show()
    }
    fun closePlayer() { player.stop(); player.clearMediaItems(); selected = null; minimized = false; quality = null; selectedAudio = null }
    fun back() {
        when {
            fullscreen -> setFullscreen(false)
            searchActive -> { searchActive = false; keyboard?.hide() }
            selected != null && !minimized -> minimized = true
            playlist != null -> playlist = null
            channel != null -> channel = null
            searchMode -> { searchMode = false; query = "" }
        }
        error = null
    }
    fun playQuality(info: StreamInfo, stream: VideoStream?, audio: AudioStream?, position: Long, play: Boolean) {
        val factory = DefaultMediaSourceFactory(context)
        when {
            stream != null && stream.isVideoOnly && audio != null -> player.setMediaSource(MergingMediaSource(factory.createMediaSource(MediaItem.fromUri(stream.content)), factory.createMediaSource(MediaItem.fromUri(audio.content))))
            stream != null && !stream.isVideoOnly -> player.setMediaItem(MediaItem.fromUri(stream.content))
            stream == null && !info.hlsUrl.isNullOrBlank() -> player.setMediaItem(MediaItem.fromUri(info.hlsUrl))
            else -> error("Format video yang bisa diputar tidak tersedia.")
        }
        quality = stream
        player.prepare()
        if (position > 0) player.seekTo(position)
        player.playWhenReady = play
    }
    BackHandler(fullscreen || searchActive || (selected != null && !minimized) || playlist != null || channel != null || searchMode) { back() }
    fun loadHome(refresh: Boolean = false) {
        if (loading || feedLoading) return
        loading = true; error = null; feedError = null; feedEnd = false
        scope.launch {
            try {
                if (refresh) {
                    val page = VideoRepository.searchPage(topics[topicIndex % topics.size])
                    topicIndex++
                    homePage = page
                    home = page.items.filterIsInstance<StreamInfoItem>().distinctBy { it.url }
                } else {
                    home = VideoRepository.home().distinctBy { it.url }
                    homePage = null
                }
                if (home.isEmpty()) error = "Feed belum tersedia."
            } catch (e: Exception) { error = "Feed gagal dimuat: ${e.message ?: "coba lagi"}" }
            finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { loadHome() }
    fun selectCategory(value: String, force: Boolean = false) {
        if (loading || feedLoading || (value == category && !force)) return
        category = value; error = null; feedError = null; feedEnd = false
        if (value == "Semua") return
        categoryItems = emptyList(); categoryPage = null; loading = true
        scope.launch {
            try {
                val page = VideoRepository.searchPage(value)
                categoryPage = page
                categoryItems = page.items.filterIsInstance<StreamInfoItem>().distinctBy { it.url }
                feedEnd = page.next == null
            } catch (e: Exception) { error = "Kategori gagal dimuat: ${e.message ?: "coba lagi"}" }
            finally { loading = false }
        }
    }
    fun loadMoreFeed() {
        if (loading || feedLoading || feedEnd || searchMode || channel != null || playlist != null || searchActive || (selected != null && !minimized)) return
        feedLoading = true; feedError = null
        val currentCategory = category
        scope.launch {
            try {
                if (currentCategory == "Semua") {
                    val current = homePage
                    val page = if (current?.next != null) VideoRepository.moreSearch(current) else {
                        val next = VideoRepository.searchPage(topics[topicIndex % topics.size]); topicIndex++; next
                    }
                    homePage = page
                    val updated = (home + page.items.filterIsInstance<StreamInfoItem>()).distinctBy { it.url }
                    feedEnd = updated.size == home.size
                    home = updated
                } else {
                    val current = categoryPage
                    if (current?.next == null) { feedEnd = true } else {
                        val page = VideoRepository.moreSearch(current)
                        categoryPage = page
                        val updated = (categoryItems + page.items.filterIsInstance<StreamInfoItem>()).distinctBy { it.url }
                        feedEnd = page.next == null || updated.size == categoryItems.size
                        categoryItems = updated
                    }
                }
            } catch (e: Exception) { feedError = "Tidak bisa memuat video berikutnya: ${e.message ?: "coba lagi"}" }
            finally { feedLoading = false }
        }
    }
    fun openChannel(url: String) {
        if (loading || url.isBlank()) return
        loading = true; error = null
        scope.launch {
            try {
                val page = VideoRepository.channel(url)
                player.stop(); selected = null; quality = null; selectedAudio = null; channel = page; channelTab = ChannelTabs.VIDEOS; channelItems = page.videos; playlist = null
            } catch (e: Exception) { error = "Channel gagal dibuka: ${e.message ?: "coba lagi"}" }
            finally { loading = false }
        }
    }
    fun search(text: String = query, filter: String = "all") {
        val term = text.trim()
        if (term.isBlank() || loading) return
        query = term; lastSearch = term; searchFilter = filter; searchActive = false; keyboard?.hide()
        history = (listOf(term) + history.filterNot { it.equals(term, ignoreCase = true) }).take(20)
        preferences.edit().putString("recent", org.json.JSONArray(history).toString()).apply()
        searchGeneration++; searchMoreLoading = false
        loading = true; error = null; searchMode = true; channel = null; playlist = null; results = emptyList(); searchPage = null; searchMoreError = null
        scope.launch {
            try { val page = VideoRepository.searchPage(term, filter); searchPage = page; results = page.items; if (results.isEmpty()) error = "Tidak ada hasil ditemukan." }
            catch (e: Exception) { error = "Pencarian gagal: ${e.message ?: "coba lagi"}" }
            finally { loading = false }
        }
    }
    fun moreSearchResults() {
        val page = searchPage ?: return
        if (page.next == null || searchMoreLoading || loading) return
        searchMoreLoading = true; searchMoreError = null
        val generation = searchGeneration
        scope.launch {
            try {
                val next = VideoRepository.moreSearch(page)
                if (generation != searchGeneration) return@launch
                val updated = (results + next.items).distinctBy { it.url }
                searchPage = if (updated.size == results.size) next.copy(next = null) else next
                results = updated
            } catch (e: Exception) { if (generation == searchGeneration) searchMoreError = "Hasil berikutnya gagal dimuat: ${e.message ?: "coba lagi"}" }
            finally { if (generation == searchGeneration) searchMoreLoading = false }
        }
    }
    fun loadChannelTab(filter: String) {
        val page = channel ?: return
        if (loading) return
        channelTab = filter; channelItems = emptyList(); error = null; loading = true
        scope.launch {
            try { channelItems = VideoRepository.channelTab(page, filter) }
            catch (e: Exception) { error = "Tab gagal dimuat: ${e.message ?: "coba lagi"}" }
            finally { loading = false }
        }
    }
    fun openPlaylist(url: String) {
        if (loading) return
        loading = true; error = null
        scope.launch {
            try { playlist = VideoRepository.playlist(url) }
            catch (e: Exception) { error = "Playlist gagal dibuka: ${e.message ?: "coba lagi"}" }
            finally { loading = false }
        }
    }
    fun openUrl(url: String) {
        if (loading) return
        loading = true; error = null
        scope.launch {
            try {
                val info = VideoRepository.resolve(url)
                val options = qualityOptions(info)
                val initial = options.firstOrNull()
                if (initial == null && info.hlsUrl.isNullOrBlank()) error("Format video yang bisa diputar tidak tersedia.")
                val audio = audioTracks(info).firstOrNull { it.audioTrackType == AudioTrackType.ORIGINAL } ?: audioTracks(info).firstOrNull()
                playQuality(info, initial, audio, 0L, true)
                selectedAudio = audio
                selected = info
                minimized = false
            } catch (e: Exception) { error = "Video gagal dibuka: ${e.message ?: "coba lagi"}" }
            finally { loading = false }
        }
    }
    fun open(item: StreamInfoItem) = openUrl(item.url)
    fun openLink(info: StreamInfo, url: String) {
        timestampSeconds(url, info.id)?.let { seconds -> player.seekTo(seconds * 1000L); player.play(); return }
        val uri = runCatching { Uri.parse(if (url.startsWith("/")) "https://www.youtube.com$url" else url) }.getOrNull() ?: return
        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty()
        when {
            host.endsWith("youtu.be") || (host.contains("youtube.com") && (path == "/watch" || path.startsWith("/shorts/"))) -> openUrl(uri.toString())
            host.contains("youtube.com") && (path.startsWith("/@") || path.startsWith("/channel/") || path.startsWith("/c/") || path.startsWith("/user/")) -> openChannel(uri.toString())
            else -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val wideWatch = maxWidth >= 840.dp
        Column(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (selected == null || minimized) {
                if (searchActive) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus(); keyboard?.show() }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { searchActive = false; keyboard?.hide() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
                        TextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f).focusRequester(focusRequester), placeholder = { Text("Telusuri YouTube") }, singleLine = true,
                            colors = TextFieldDefaults.colors(focusedContainerColor = Surface, unfocusedContainerColor = Surface, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Hapus") } },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }), shape = RoundedCornerShape(50))
                        IconButton(onClick = { search() }) { Icon(Icons.Default.Search, "Cari") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (playlist != null || channel != null || searchMode) IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
                        if (searchMode && playlist == null && channel == null) {
                            Text(lastSearch, modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(Surface).clickable { query = lastSearch; searchActive = true }.padding(horizontal = 16.dp, vertical = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                        Box(Modifier.size(width = 34.dp, height = 24.dp).background(Accent, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Text("FlufPlay", modifier = Modifier.padding(start = 7.dp).weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { searchActive = true }) { Icon(Icons.Default.Search, "Telusuri") }
                        IconButton(onClick = {
                            val currentPlaylist = playlist
                            val currentChannel = channel
                            when {
                                currentPlaylist != null -> openPlaylist(currentPlaylist.url)
                                currentChannel != null -> loadChannelTab(channelTab)
                                searchMode -> search(lastSearch, searchFilter)
                                else -> if (category == "Semua") loadHome(refresh = true) else selectCategory(category, true)
                            }
                        }, enabled = !loading) { Icon(Icons.Default.Refresh, "Muat ulang halaman") }
                        IconButton(onClick = { context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }) { Text("↓", style = MaterialTheme.typography.headlineSmall) }
                    }
                }
            }
            if (downloadState.message.isNotBlank() && (selected == null || minimized)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(downloadState.message, style = MaterialTheme.typography.bodySmall)
                        if (downloadState.running) {
                            val value = downloadState.progress
                            if (value == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            else LinearProgressIndicator(progress = { value / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                    if (downloadState.running) TextButton(onClick = { context.startService(Intent(context, DownloadService::class.java).setAction("cancel")) }) { Text("Batal") }
                    else TextButton(onClick = { DownloadStatus.state.value = DownloadState() }) { Text("Tutup") }
                }
            }
            if (loading && (selected == null || minimized)) LinearProgressIndicator(Modifier.fillMaxWidth())
            val info = selected?.takeUnless { minimized }
            if (info == null && searchActive) {
                val recent = history.filter { query.isBlank() || it.contains(query, ignoreCase = true) }
                val online = suggestions.filterNot { suggestion -> recent.any { it.equals(suggestion, ignoreCase = true) } }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                    items(recent) { term ->
                        Row(Modifier.fillMaxWidth().clickable { search(term) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("◷", style = MaterialTheme.typography.titleLarge, color = Color.LightGray)
                            Text(term, Modifier.weight(1f).padding(start = 20.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { history = history.filterNot { it == term }; preferences.edit().putString("recent", org.json.JSONArray(history).toString()).apply() }) { Icon(Icons.Default.Close, "Hapus dari riwayat", tint = Color.LightGray) }
                        }
                    }
                    items(online) { term ->
                        Row(Modifier.fillMaxWidth().clickable { search(term) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = Color.LightGray)
                            Text(term, Modifier.weight(1f).padding(start = 20.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { query = term }) { Text("↖", color = Color.LightGray) }
                        }
                    }
                }
            } else if (info == null) {
                error?.let { message ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        if (!searchMode && channel == null) TextButton(onClick = { if (category == "Semua") loadHome() else selectCategory(category, true) }) { Text("Coba lagi") }
                    }
                }
                val page = channel
                val currentPlaylist = playlist
                val visibleItems: List<InfoItem> = if (currentPlaylist != null) currentPlaylist.relatedItems else if (page != null) channelItems else if (searchMode) results else if (category == "Semua") home else categoryItems
                val gridState = rememberLazyGridState()
                val isFeed = currentPlaylist == null && page == null && !searchMode
                LaunchedEffect(gridState, isFeed, category, visibleItems.size, feedLoading, feedError, feedEnd, loading) {
                    if (isFeed && !feedLoading && feedError == null && !feedEnd && !loading && visibleItems.isNotEmpty()) {
                        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                            .distinctUntilChanged().collect { last ->
                                if (last >= visibleItems.size - 3) loadMoreFeed()
                            }
                    }
                }
                val isSearchResults = searchMode && page == null && currentPlaylist == null
                LaunchedEffect(lastSearch, searchFilter, isSearchResults) { if (isSearchResults) gridState.scrollToItem(0) }
                LaunchedEffect(gridState, isSearchResults, results.size, searchMoreLoading, searchMoreError, searchPage, loading) {
                    if (isSearchResults && !loading && !searchMoreLoading && searchMoreError == null && searchPage?.next != null && results.isNotEmpty()) {
                        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged().collect { last ->
                            if (last >= results.size - 3) moreSearchResults()
                        }
                    }
                }
                LazyVerticalGrid(state = gridState, modifier = Modifier.weight(1f), columns = if (isSearchResults) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 300.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        if (currentPlaylist != null) {
                            Column { Text(currentPlaylist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Playlist · ${currentPlaylist.relatedItems.size} video dimuat", color = Color.LightGray, modifier = Modifier.padding(top = 8.dp)) }
                        } else if (page != null) ChannelHeader(page, channelTab) { loadChannelTab(it) }
                        else if (searchMode) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("all" to "Semua", "videos" to "Video", "channels" to "Channel", "playlists" to "Playlist").forEach { (value, label) ->
                                val active = searchFilter == value
                                Text(label, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (active) Color.White else Surface).clickable(enabled = !loading && !searchMoreLoading) { if (value != searchFilter) search(lastSearch, value) }.padding(horizontal = 13.dp, vertical = 8.dp), color = if (active) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Semua", "Musik", "Game", "Berita", "Film", "Olahraga", "Live").forEach { label ->
                                val active = category == label
                                Text(label, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (active) Color.White else Surface).clickable { selectCategory(label) }.padding(horizontal = 13.dp, vertical = 8.dp), color = if (active) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    items(visibleItems.size) { index ->
                        val item = visibleItems[index]
                        if (isSearchResults) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                BoxWithConstraints(Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                                    val wideResult = maxWidth >= 600.dp
                                    when (item) {
                                        is StreamInfoItem -> if (wideResult) SearchWideVideoCard(item) { open(item) } else VideoCard(item) { open(item) }
                                        is ChannelInfoItem -> SearchChannelCard(item) { openChannel(item.url) }
                                        is PlaylistInfoItem -> if (wideResult) SearchWidePlaylistCard(item) { openPlaylist(item.url) } else PlaylistCard(item) { openPlaylist(item.url) }
                                    }
                                }
                            }
                        } else when (item) {
                            is StreamInfoItem -> VideoCard(item) { open(item) }
                            is ChannelInfoItem -> ChannelCard(item) { openChannel(item.url) }
                            is PlaylistInfoItem -> PlaylistCard(item) { openPlaylist(item.url) }
                        }
                    }
                    if (isSearchResults && searchMoreLoading) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                    if (isSearchResults && searchMoreError != null) item(span = { GridItemSpan(maxLineSpan) }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(searchMoreError ?: "", Modifier.weight(1f)); TextButton(onClick = { moreSearchResults() }) { Text("Coba lagi") } } }
                    if (isFeed && feedLoading) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                    if (isFeed && feedError != null) item(span = { GridItemSpan(maxLineSpan) }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(feedError ?: "", Modifier.weight(1f), color = Color.LightGray); TextButton(onClick = { loadMoreFeed() }) { Text("Coba lagi") } } }
                    if (!loading && error == null && visibleItems.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text(if (page != null) "Belum ada video yang tersedia." else "Tidak ada hasil.", color = Color.LightGray) }
                }
            } else {
                var showComments by remember(info.url) { mutableStateOf(false) }
                var showDescription by remember(info.url) { mutableStateOf(false) }
                val comments = rememberComments(info.url)
                WatchScreen(showDescription = showDescription, showComments = showComments, onClosePanel = { showDescription = false; showComments = false }, watchAlpha = (1f - (dragProgress ?: 0f) * .3f).coerceIn(0f, 1f), info = info, comments = comments, onComments = { showDescription = false; showComments = true }, onDescription = { showComments = false; showDescription = true },
                    onLink = { openLink(info, it) }, onSeek = { player.seekTo(it * 1000L); player.play() }, onShare = { shareVideo(info) }, onCopy = { copyVideoLink(info) }, player = player, fullscreen = fullscreen, onFullscreen = { setFullscreen(!fullscreen) }, recommendations = info.relatedItems.filterIsInstance<StreamInfoItem>().filter { it.url != info.url }.distinctBy { it.url }, error = error,
                    onBack = { back() }, onSettings = { showQuality = true }, onDownload = { showDownloads = true },
                    onChannel = { openChannel(info.uploaderUrl ?: "") }, onOpenChannel = { openChannel(it) }, onVideo = { open(it) })
                if (showQuality) QualityDialog(info, quality, selectedAudio, player, onDismiss = { showQuality = false }, onSelect = { stream ->
                    if (stream != quality) {
                        val position = player.currentPosition.coerceAtLeast(0L)
                        val playing = player.playWhenReady
                        try { playQuality(info, stream, selectedAudio, position, playing) }
                        catch (e: Exception) { android.widget.Toast.makeText(context, "Gagal mengganti kualitas: ${e.message}", android.widget.Toast.LENGTH_LONG).show() }
                    }
                    showQuality = false
                }, onSelectAudio = { audio ->
                    if (audio != selectedAudio || quality?.isVideoOnly != true) {
                        val separate = VideoRepository.videos(info)
                        val currentHeight = quality?.height ?: 0
                        val video = if (quality?.isVideoOnly == true) quality else separate.minByOrNull { kotlin.math.abs(it.height - currentHeight) }
                        if (video == null) {
                            android.widget.Toast.makeText(context, "Pilihan audio tidak tersedia untuk format video ini", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            try {
                                playQuality(info, video, audio, player.currentPosition.coerceAtLeast(0L), player.playWhenReady)
                                selectedAudio = audio
                            } catch (e: Exception) { android.widget.Toast.makeText(context, "Gagal mengganti audio: ${e.message}", android.widget.Toast.LENGTH_LONG).show() }
                        }
                    }
                    showQuality = false
                })
                if (showDownloads) DownloadDialog(info, onDismiss = { showDownloads = false }) { height, audio ->
                    showDownloads = false
                    if (downloadState.running) {
                        android.widget.Toast.makeText(context, "Tunggu unduhan yang sedang berjalan", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        val safeTitle = info.name.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(90).ifBlank { "video" }
                        pendingDownload = DownloadChoice(info.url, height, audio)
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType(if (audio) "audio/mpeg" else "video/mp4")
                            .putExtra(Intent.EXTRA_TITLE, "$safeTitle.${if (audio) "mp3" else "mp4"}")
                        saveFile.launch(intent)
                    }
                }
            }
            if (info == null && !searchActive) {
                HomeNavigation(onHome = { searchMode = false; searchActive = false; channel = null; playlist = null; category = "Semua" }, onDownloads = { context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) })
            }
        }
        selected?.let { playing ->
            val insets = if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            BoxWithConstraints(insets) {
                val wide = maxWidth >= 840.dp
                val fullWidth = if (fullscreen) maxWidth else if (wide) (maxWidth - 72.dp) / 1.48f else maxWidth
                val fullHeight = if (fullscreen) maxHeight else fullWidth * 9f / 16f
                val startX = if (wide && !fullscreen) 24.dp else 0.dp
                val maxMiniWidth = minOf(360.dp, (maxWidth - 16.dp).coerceAtLeast(80.dp), (maxHeight - 96.dp).coerceAtLeast(80.dp) * 16f / 9f)
                val minMiniWidth = minOf(160.dp, maxMiniWidth)
                val miniWidth = miniPlayerWidth.coerceIn(minMiniWidth, maxMiniWidth)
                val miniHeight = miniWidth * 9f / 16f
                val target = if (fullscreen) 0f else dragProgress ?: if (minimized) 1f else 0f
                val progress by animateFloatAsState(targetValue = target, animationSpec = tween(if (dragProgress == null) 300 else 0, easing = FastOutSlowInEasing), label = "player transition")
                val width = fullWidth + (miniWidth - fullWidth) * progress
                val height = fullHeight + (miniHeight - fullHeight) * progress
                val endX = (maxWidth - miniWidth - 8.dp).coerceAtLeast(0.dp)
                val bottomGap = if (searchActive) 8.dp else 64.dp
                val endY = (maxHeight - miniHeight - bottomGap).coerceAtLeast(0.dp)
                val x = startX + (endX - startX) * progress
                val y = endY * progress
                FloatingPlayer(player = player, title = playing.name, previewFrames = playing.previewFrames, fullscreen = fullscreen, minimized = minimized, progress = progress, travelDistance = endY.coerceAtLeast(180.dp),
                    modifier = Modifier.offset(x = x, y = y).size(width, height),
                    onNext = playing.relatedItems.filterIsInstance<StreamInfoItem>().firstOrNull { it.url != playing.url }?.let { next -> { open(next) } },
                    onBack = { back() }, onSettings = { showQuality = true }, onShare = { shareVideo(playing) }, onFullscreen = { setFullscreen(!fullscreen) },
                    onExpand = { minimized = false }, onClose = { closePlayer() },
                    onResize = { scale -> miniPlayerWidth = (miniWidth * scale).coerceIn(minMiniWidth, maxMiniWidth) },
                    onToggleSize = { miniPlayerWidth = if (miniWidth < (minMiniWidth + maxMiniWidth) / 2f) maxMiniWidth else minMiniWidth },
                    onDrag = { dragProgress = it }, onCancelDrag = { dragProgress = null }, onRelease = { value, velocity ->
                        minimized = when { velocity > 800f -> true; velocity < -800f -> false; minimized -> value >= .65f; else -> value >= .35f }
                        dragProgress = null
                    })
            }
        }
      }
    }
}

@Composable private fun WatchScreen(showDescription: Boolean, showComments: Boolean, onClosePanel: () -> Unit, watchAlpha: Float, info: StreamInfo, comments: CommentsState, onComments: () -> Unit, onDescription: () -> Unit, onLink: (String) -> Unit, onSeek: (Int) -> Unit, onShare: () -> Unit, onCopy: () -> Unit, player: ExoPlayer, fullscreen: Boolean, onFullscreen: () -> Unit, recommendations: List<StreamInfoItem>, error: String?, onBack: () -> Unit, onSettings: () -> Unit, onDownload: () -> Unit, onChannel: () -> Unit, onOpenChannel: (String) -> Unit, onVideo: (StreamInfoItem) -> Unit) {
    if (fullscreen) {
        return
    }
    val panel = when { showDescription -> "description"; showComments -> "comments"; else -> null }
    BackHandler(panel != null) { onClosePanel() }
    @Composable fun Panel(kind: String, draggable: Boolean, modifier: Modifier) {
        if (kind == "description") DescriptionPanel(info, draggable, onClosePanel, onLink, onSeek, onChannel, modifier)
        else CommentsPanel(comments, info, draggable, onClosePanel, onLink, onOpenChannel, modifier)
    }
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = watchAlpha }) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    VideoDetails(info, comments, error, onDownload, onChannel, onComments, onDescription, onShare, onCopy)
                }
                if (panel != null) {
                    Box(Modifier.widthIn(min = 300.dp, max = 400.dp).weight(.5f).fillMaxHeight().padding(vertical = 8.dp).border(1.dp, Color(0xFF303030), RoundedCornerShape(12.dp))) {
                        Panel(panel, false, Modifier.fillMaxSize())
                    }
                } else {
                    LazyColumn(Modifier.widthIn(min = 260.dp, max = 400.dp).weight(.5f), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        item { Text("Video lainnya", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(recommendations) { item -> CompactVideoCard(item) { onVideo(item) } }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item { Spacer(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) }
                item { VideoDetails(info, comments, error, onDownload, onChannel, onComments, onDescription, onShare, onCopy) }
                if (recommendations.isEmpty()) item { Text("Video terkait tidak tersedia.", color = Color.LightGray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                items(recommendations) { item -> VideoCard(item, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { onVideo(item) } }
            }
            // Like YouTube, the panel slides up from the bottom and covers everything below the player, which keeps playing.
            androidx.compose.animation.AnimatedContent(targetState = panel, modifier = Modifier.fillMaxSize(), label = "watch panel", transitionSpec = {
                val enter = if (targetState != null) androidx.compose.animation.slideInVertically(tween(250, easing = FastOutSlowInEasing)) { it } else androidx.compose.animation.EnterTransition.None
                val exit = if (targetState == null) androidx.compose.animation.slideOutVertically(tween(200)) { it } else androidx.compose.animation.fadeOut(tween(120))
                enter togetherWith exit
            }) { kind ->
                if (kind != null) Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    Panel(kind, true, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

@Composable private fun FloatingPlayer(
    player: ExoPlayer, title: String, previewFrames: List<org.schabi.newpipe.extractor.stream.Frameset>, fullscreen: Boolean, minimized: Boolean, progress: Float, travelDistance: androidx.compose.ui.unit.Dp,
    modifier: Modifier, onNext: (() -> Unit)?, onBack: () -> Unit, onSettings: () -> Unit, onShare: () -> Unit, onFullscreen: () -> Unit,
    onExpand: () -> Unit, onClose: () -> Unit, onResize: (Float) -> Unit, onToggleSize: () -> Unit, onDrag: (Float) -> Unit, onCancelDrag: () -> Unit, onRelease: (Float, Float) -> Unit
) {
    val currentDrag by rememberUpdatedState(onDrag)
    val currentRelease by rememberUpdatedState(onRelease)
    val currentCancel by rememberUpdatedState(onCancelDrag)
    val currentResize by rememberUpdatedState(onResize)
    val currentTravelDistance by rememberUpdatedState(travelDistance)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val swipeModifier = if (fullscreen) Modifier else Modifier.pointerInput(minimized) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val startPosition = down.position + origin
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, startPosition) }
            val startProgress = if (minimized) 1f else 0f
            var dragging = false
            var rejected = false
            var completed = false
            var resizing = false
            var fraction = startProgress
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (minimized && event.changes.count { it.pressed } >= 2) {
                        if (dragging) { currentCancel(); dragging = false }
                        resizing = true; rejected = true
                        val scale = event.calculateZoom()
                        if (scale.isFinite() && scale > 0f && scale != 1f) currentResize(scale)
                        event.changes.forEach { it.consume() }
                    }
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val currentPosition = change.position + origin
                    tracker.addPosition(change.uptimeMillis, currentPosition)
                    val dx = currentPosition.x - startPosition.x
                    val dy = currentPosition.y - startPosition.y
                    if (!resizing && !dragging && !rejected && (kotlin.math.abs(dx) > viewConfiguration.touchSlop || kotlin.math.abs(dy) > viewConfiguration.touchSlop)) {
                        dragging = kotlin.math.abs(dy) > kotlin.math.abs(dx) && (if (minimized) dy < 0 else dy > 0)
                        rejected = !dragging
                    }
                    if (dragging) {
                        change.consume()
                        fraction = (startProgress + dy / currentTravelDistance.toPx()).coerceIn(0f, 1f)
                        if (change.pressed) currentDrag(fraction) else {
                            completed = true
                            currentRelease(fraction, tracker.calculateVelocity().y / density)
                        }
                    }
                } while (change.pressed)
            } finally {
                if (dragging && !completed) currentCancel()
            }
        }
    }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(player.playbackState == androidx.media3.common.Player.STATE_BUFFERING) }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(playbackState: Int) { buffering = playbackState == androidx.media3.common.Player.STATE_BUFFERING }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val corner = 8.dp * progress
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot() }.then(swipeModifier).shadow(2.dp * progress, RoundedCornerShape(corner)).clip(RoundedCornerShape(corner)).background(Color.Black)) {
        AndroidView(factory = { context ->
            android.view.LayoutInflater.from(context).inflate(R.layout.floating_player, null, false) as PlayerView
        }, update = { view ->
            view.player = player
            view.useController = false
        }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize())
        if (minimized && progress > .9f) {
            Box(Modifier.fillMaxSize().clickable(onClick = onExpand).background(Color.Black.copy(alpha = .16f))) {
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).size(40.dp)) { Icon(Icons.Default.Close, "Tutup video", tint = Color.White) }
                IconButton(onClick = onExpand, modifier = Modifier.align(Alignment.TopStart).size(40.dp)) { Text("⤢", color = Color.White, style = MaterialTheme.typography.titleLarge) }
                IconButton(onClick = onToggleSize, modifier = Modifier.align(Alignment.BottomEnd).size(40.dp).semantics { contentDescription = "Ubah ukuran mini player" }) {
                    Text("⤧", color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = { if (playing) player.pause() else player.play() }, modifier = Modifier.align(Alignment.Center).size(48.dp).background(Color.Black.copy(alpha = .4f), RoundedCornerShape(50))) {
                    PlayerGlyph(if (playing) "pause" else "play", Modifier.size(22.dp))
                }
            }
        } else if (progress < .1f) {
            PlayerControlsOverlay(player, playing, buffering, fullscreen, title, previewFrames, onBack, onSettings, onShare, onFullscreen, onNext)
        }
    }
}

@Composable private fun HomeNavigation(onHome: () -> Unit, onDownloads: () -> Unit) {
    HorizontalDivider(color = Color(0xFF303030))
    Row(Modifier.fillMaxWidth().height(56.dp).background(Background), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.clickable(onClick = onHome).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Home, null); Text("Beranda", style = MaterialTheme.typography.labelSmall) }
        Column(Modifier.clickable(onClick = onDownloads).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("↓", style = MaterialTheme.typography.titleLarge); Text("Unduhan", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable private fun VideoDetails(info: StreamInfo, comments: CommentsState, error: String?, onDownload: () -> Unit, onChannel: () -> Unit, onComments: () -> Unit, onDescription: () -> Unit, onShare: () -> Unit, onCopy: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        DescriptionTeaser(info, onDescription)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = !info.uploaderUrl.isNullOrBlank(), onClick = onChannel).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(info.uploaderAvatars.lastOrNull()?.url, info.uploaderName, 36)
            Text(info.uploaderName ?: "Channel", modifier = Modifier.padding(start = 12.dp).weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            info.uploaderSubscriberCount.takeIf { it >= 0 }?.let { Text(formatCount(it), color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp)) }
            Spacer(Modifier.weight(1f))
            Text("Lihat channel", color = Color.Black, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (info.likeCount >= 0) Row(Modifier.height(36.dp).clip(RoundedCornerShape(50)).background(Surface).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("♡", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(7.dp)); Text(formatCount(info.likeCount), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            WatchAction("share", "Bagikan", onShare)
            WatchAction("download", "Unduh", onDownload)
            WatchAction("copy", "Salin link", onCopy)
        }
        Box(Modifier.padding(top = 16.dp)) { CommentsTeaser(comments, onClick = { if (comments.error != null && comments.info == null) comments.retry(); onComments() }) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable private fun WatchAction(icon: String, label: String, onClick: () -> Unit) {
    Row(Modifier.height(36.dp).clip(RoundedCornerShape(50)).background(Surface).clickable(onClick = onClick).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionGlyph(icon, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable private fun ActionGlyph(icon: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val stroke = 1.8.dp.toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(Color.White, Offset(w*x1,h*y1), Offset(w*x2,h*y2), strokeWidth = stroke, cap = StrokeCap.Round)
        when (icon) {
            "share" -> { line(.27f,.50f,.72f,.25f); line(.27f,.50f,.72f,.75f); listOf(Offset(.23f,.50f), Offset(.76f,.23f), Offset(.76f,.77f)).forEach { drawCircle(Color.White, radius = w*.10f, center = Offset(it.x*w,it.y*h)) } }
            "download" -> { line(.5f,.12f,.5f,.68f); line(.27f,.47f,.5f,.70f); line(.73f,.47f,.5f,.70f); line(.18f,.84f,.82f,.84f) }
            else -> { drawRoundRect(Color.White, topLeft = Offset(w*.31f,h*.28f), size = androidx.compose.ui.geometry.Size(w*.52f,h*.58f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w*.06f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)); line(.19f,.66f,.19f,.14f); line(.19f,.14f,.69f,.14f) }
        }
    }
}

internal fun formatCount(count: Long): String = when {
    count >= 1_000_000_000 -> String.format(java.util.Locale("id", "ID"), "%.1f M", count / 1_000_000_000.0).replace(",0 ", " ")
    count >= 1_000_000 -> String.format(java.util.Locale("id", "ID"), "%.1f jt", count / 1_000_000.0).replace(",0 ", " ")
    count >= 10_000 -> "${count / 1_000} rb"
    count >= 1_000 -> String.format(java.util.Locale("id", "ID"), "%.1f rb", count / 1_000.0).replace(",0 ", " ")
    else -> count.toString()
}

@Composable private fun SearchWideVideoCard(item: StreamInfoItem, onClick: () -> Unit) {
    val suppliedAvatar = item.uploaderAvatars.lastOrNull()?.url
    var avatar by remember(item.url, suppliedAvatar) { mutableStateOf(suppliedAvatar) }
    LaunchedEffect(item.uploaderUrl, suppliedAvatar) {
        if (suppliedAvatar.isNullOrBlank() && !item.uploaderUrl.isNullOrBlank()) avatar = VideoRepository.channelAvatar(item.uploaderUrl)
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.weight(.43f).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Surface)) {
            AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (item.duration > 0) DurationBadge(item.duration, Modifier.align(Alignment.BottomEnd))
        }
        Column(Modifier.weight(.57f).padding(top = 2.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val metadata = listOfNotNull(item.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} ditonton" }, item.textualUploadDate?.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (metadata.isNotBlank()) Text(metadata, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).clip(RoundedCornerShape(50)).background(Surface), contentAlignment = Alignment.Center) {
                    Text(item.uploaderName?.firstOrNull()?.uppercase() ?: "•", style = MaterialTheme.typography.labelSmall)
                    avatar?.let { AsyncImage(model = it, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                Text(item.uploaderName.orEmpty(), color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
            }
            item.shortDescription?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp)) }
        }
    }
}

@Composable private fun SearchWidePlaylistCard(item: PlaylistInfoItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.weight(.43f).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Surface)) {
            AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Text(if (item.streamCount >= 0) "${item.streamCount} video · Playlist" else "Playlist", modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = .85f), RoundedCornerShape(4.dp)).padding(5.dp), style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.weight(.57f).padding(top = 2.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Playlist", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            Text("Lihat playlist", color = Color.LightGray, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable private fun CompactVideoCard(item: StreamInfoItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Surface)) {
            AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (item.duration > 0) DurationBadge(item.duration, Modifier.align(Alignment.BottomEnd))
        }
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.uploaderName ?: "", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable private fun VideoCard(item: StreamInfoItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val suppliedAvatar = item.uploaderAvatars.lastOrNull()?.url
    var avatar by remember(item.url, suppliedAvatar) { mutableStateOf(suppliedAvatar) }
    LaunchedEffect(item.uploaderUrl, suppliedAvatar) {
        if (suppliedAvatar.isNullOrBlank() && !item.uploaderUrl.isNullOrBlank()) avatar = VideoRepository.channelAvatar(item.uploaderUrl)
    }
    Column(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Surface)) {
            AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (item.duration > 0) DurationBadge(item.duration, Modifier.align(Alignment.BottomEnd))
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(Surface), contentAlignment = Alignment.Center) {
                Text(item.uploaderName?.firstOrNull()?.uppercase() ?: "•", style = MaterialTheme.typography.titleSmall)
                avatar?.let { imageUrl ->
                    AsyncImage(model = imageUrl, contentDescription = "Foto channel ${item.uploaderName ?: ""}", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val metadata = listOfNotNull(item.uploaderName?.takeIf { it.isNotBlank() }, item.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} ditonton" }, item.textualUploadDate?.takeIf { it.isNotBlank() }).joinToString(" · ")
                Text(metadata, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable private fun DurationBadge(duration: Long, modifier: Modifier = Modifier) {
    val label = if (duration >= 3600) "%d:%02d:%02d".format(duration / 3600, duration / 60 % 60, duration % 60) else "%d:%02d".format(duration / 60, duration % 60)
    Text(label, modifier = modifier.padding(6.dp).background(Color.Black.copy(alpha = .85f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
}
private fun Modifier.clipCard(): Modifier = this.clip(RoundedCornerShape(16.dp)).background(Surface)

private data class DownloadChoice(val url: String, val height: Int, val audio: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DownloadDialog(info: StreamInfo, onDismiss: () -> Unit, download: (Int, Boolean) -> Unit) {
    var choice by remember(info.url) { mutableIntStateOf(0) }
    val choices = listOf(0 to "Kualitas terbaik", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p", -1 to "Audio saja")
    val listHeight = (LocalConfiguration.current.screenHeightDp - 310).coerceIn(72, 300).dp
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF272727), contentColor = Color.White, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Unduh video", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Tutup") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = info.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Background))
                Text(info.name, Modifier.weight(1f).padding(start = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Text("Kualitas unduhan", Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = Color.LightGray)
            Column(Modifier.heightIn(max = listHeight).verticalScroll(rememberScrollState())) {
                choices.forEach { (height, label) ->
                    val description = if (height == -1) "MP3" else if (height == 0) "MP4 · terbaik yang tersedia" else "MP4 · maksimum $label"
                    PlayerChoiceRow(label, description, selected = choice == height, onClick = { choice = height })
                }
            }
            Text("Kualitas akhir mengikuti format yang tersedia.", Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { download(choice.coerceAtLeast(0), choice == -1) }, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 20.dp).height(48.dp), shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                Text("Pilih lokasi dan unduh", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable private fun PlayerChoiceRow(label: String, detail: String? = null, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(start = 20.dp, end = 20.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
        }
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Color.LightGray), modifier = Modifier.size(40.dp))
    }
}

private fun audioTracks(info: StreamInfo): List<AudioStream> = VideoRepository.audios(info)
    .distinctBy { audio -> audio.audioTrackId?.takeIf { it.isNotBlank() }
        ?: listOf(audio.audioLocale?.toLanguageTag(), audio.audioTrackType?.name, audio.audioTrackName).joinToString("|") }

private fun audioLabel(audio: AudioStream): String {
    val name = audio.audioTrackName?.takeIf { it.isNotBlank() }
        ?: audio.audioLocale?.getDisplayName(java.util.Locale.forLanguageTag("id"))?.takeIf { it.isNotBlank() }
        ?: "Audio default"
    val type = when (audio.audioTrackType) {
        AudioTrackType.ORIGINAL -> "Asli"
        AudioTrackType.DUBBED -> "Dub"
        AudioTrackType.DESCRIPTIVE -> "Deskripsi audio"
        AudioTrackType.SECONDARY -> "Audio tambahan"
        else -> null
    }
    return if (type != null && !name.contains(type, ignoreCase = true)) "$name · $type" else name
}

private fun qualityOptions(info: StreamInfo): List<VideoStream> {
    val separate = if (VideoRepository.audios(info).isNotEmpty()) VideoRepository.videos(info) else emptyList()
    return (separate + VideoRepository.progressive(info))
        .sortedWith(compareByDescending<VideoStream> { it.height.takeIf { height -> height > 0 } ?: (it.resolution.filter(Char::isDigit).toIntOrNull() ?: 0) }.thenByDescending { it.fps })
        .distinctBy { "${it.resolution}-${it.fps}-${it.format?.name}" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun QualityDialog(info: StreamInfo, selected: VideoStream?, selectedAudio: AudioStream?, player: ExoPlayer, onDismiss: () -> Unit, onSelect: (VideoStream) -> Unit, onSelectAudio: (AudioStream) -> Unit) {
    val options = remember(info) { qualityOptions(info) }
    val tracks = remember(info) { audioTracks(info) }
    val canSelectAudio = remember(info) { VideoRepository.videos(info).isNotEmpty() }
    var page by remember(info.url) { mutableStateOf("menu") }
    var speed by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    val listHeight = (LocalConfiguration.current.screenHeightDp - 140).coerceIn(120, 380).dp
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF272727), contentColor = Color.White, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = if (page == "menu") 20.dp else 4.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page != "menu") IconButton(onClick = { page = "menu" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
                Text(when (page) { "quality" -> "Kualitas"; "speed" -> "Kecepatan pemutaran"; "audio" -> "Trek audio"; else -> "Pengaturan" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Tutup") }
            }
            HorizontalDivider(color = Color.White.copy(alpha = .08f))
            Column(Modifier.heightIn(max = listHeight).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                when (page) {
                    "menu" -> {
                        SettingsRow("▣", "Kualitas", selected?.resolution ?: "Otomatis") { page = "quality" }
                        SettingsRow("◷", "Kecepatan pemutaran", if (speed == 1f) "Normal" else "${speed}x") { page = "speed" }
                        if (tracks.size > 1 && canSelectAudio) SettingsRow("♫", "Trek audio", if (selected?.isVideoOnly == true) selectedAudio?.let { audioLabel(it) } ?: "Default" else "Audio bawaan") { page = "audio" }
                    }
                    "quality" -> {
                        if (options.isEmpty()) Text("Kualitas diatur otomatis oleh stream.", modifier = Modifier.padding(20.dp), color = Color.LightGray)
                        options.forEach { stream ->
                            val detail = listOfNotNull(if (stream.fps > 30) "${stream.fps} fps" else null, stream.format?.name).joinToString(" · ")
                            PlayerChoiceRow(stream.resolution, detail, selected = stream == selected) { onSelect(stream) }
                        }
                    }
                    "audio" -> {
                        if (selected?.isVideoOnly != true) Text("Memilih trek akan memakai format video dengan audio terpisah.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        tracks.forEach { audio -> PlayerChoiceRow(audioLabel(audio), selected = selected?.isVideoOnly == true && audio == selectedAudio) { onSelectAudio(audio) } }
                    }
                    "speed" -> listOf(.25f, .5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { value ->
                        PlayerChoiceRow(if (value == 1f) "Normal" else "${value}x", selected = value == speed) { player.setPlaybackSpeed(value); speed = value; onDismiss() }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable private fun SettingsRow(icon: String, label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(34.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
        Text("  ›", color = Color.LightGray, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable private fun ChannelHeader(page: ChannelPage, selectedTab: String, onTab: (String) -> Unit) {
    val tabNames = mapOf(ChannelTabs.VIDEOS to "Video", ChannelTabs.SHORTS to "Shorts", ChannelTabs.LIVESTREAMS to "Live", ChannelTabs.PLAYLISTS to "Playlist", ChannelTabs.CHANNELS to "Channel", ChannelTabs.TRACKS to "Lagu", ChannelTabs.ALBUMS to "Album", ChannelTabs.LIKES to "Disukai")
    val tabs = page.info.tabs.mapNotNull { it.contentFilters.firstOrNull { filter -> filter in tabNames } }.distinct()
    Column(Modifier.fillMaxWidth()) {
        val banner = page.info.banners.lastOrNull()?.url
        if (banner != null) AsyncImage(model = banner, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 180.dp).aspectRatio(5f).clip(RoundedCornerShape(12.dp)).background(Surface))
        Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = page.info.avatars.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(50)).background(Surface))
            Spacer(Modifier.width(14.dp))
            Column { Text(page.info.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); if (page.info.subscriberCount >= 0) Text("${formatCount(page.info.subscriberCount)} subscriber", color = Color.LightGray, style = MaterialTheme.typography.bodySmall) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEach { tab ->
                Column(Modifier.clickable { onTab(tab) }.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tabNames[tab] ?: tab, color = if (tab == selectedTab) Color.White else Color.LightGray, fontWeight = if (tab == selectedTab) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(vertical = 12.dp))
                    Box(Modifier.width(32.dp).height(2.dp).background(if (tab == selectedTab) Color.White else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = Surface)
    }
}

@Composable private fun PlaylistCard(item: PlaylistInfoItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Surface)) {
            AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Text("${if (item.streamCount >= 0) item.streamCount else ""} video · Playlist", modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = .85f), RoundedCornerShape(4.dp)).padding(5.dp), style = MaterialTheme.typography.labelSmall)
        }
        Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        Text("Lihat playlist", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable private fun SearchChannelCard(item: ChannelInfoItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(76.dp).clip(RoundedCornerShape(50)).background(Surface))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (item.subscriberCount >= 0) "${formatCount(item.subscriberCount)} subscriber" else "Channel", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        Text("Lihat", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundedCornerShape(50)).background(Surface).padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable private fun ChannelCard(item: ChannelInfoItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clipCard().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(50)).background(Color.DarkGray))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(if (item.subscriberCount >= 0) "Channel · ${item.subscriberCount} subscriber" else "Channel", color = Color.LightGray, style = MaterialTheme.typography.bodySmall) }
        Text("›", style = MaterialTheme.typography.headlineMedium, color = Accent)
    }
}
