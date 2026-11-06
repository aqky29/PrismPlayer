package id.flufclip.player

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val PlaylistBackground = Color(0xFF0F0F0F)
private val PlaylistChip = Color(0xFF272727)

@Composable
internal fun AccountPlaylistScreen(
    playlist: AccountPlaylist,
    onBack: () -> Unit,
    onOpen: (WatchedVideo, List<WatchedVideo>) -> Unit,
    onPlay: (List<WatchedVideo>) -> Unit,
    onNavigate: (String) -> Unit,
    onSave: (WatchedVideo) -> Unit,
    onShare: (WatchedVideo) -> Unit,
) {
    val revision = YouTubeAuth.sessionRevision
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    var entries by remember(playlist.id, revision) { mutableStateOf<List<WatchedVideo>>(emptyList()) }
    var next by remember(playlist.id, revision) { mutableStateOf<String?>(null) }
    var header by remember(playlist.id, revision) { mutableStateOf<AccountPlaylistHeader?>(null) }
    var filters by remember(playlist.id, revision) { mutableStateOf<List<AccountPlaylistFilter>>(emptyList()) }
    var activeFilter by remember(playlist.id, revision) { mutableStateOf<AccountPlaylistFilter?>(null) }
    var loading by remember(playlist.id, revision) { mutableStateOf(false) }
    var error by remember(playlist.id, revision) { mutableStateOf<String?>(null) }
    var request by remember(playlist.id, revision) { mutableIntStateOf(0) }
    var more by remember(playlist.id, revision) { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val requiresAccount = playlist.id.removePrefix("VL") in setOf("LL", "WL", "YS")
    LaunchedEffect(playlist.id, revision, request, activeFilter?.title, activeFilter?.params) {
        if (requiresAccount && !YouTubeAuth.isLoggedIn) return@LaunchedEffect
        loading = true; error = null; entries = emptyList(); next = null
        try {
            val page = AccountLibrary.videos(playlist, params = activeFilter?.params,
                shortsOnly = activeFilter?.title.equals("Shorts", ignoreCase = true))
            entries = page.videos; next = page.continuation
            if (page.header != null) header = page.header
            if (page.filters.isNotEmpty()) filters = page.filters
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Couldn't load this playlist. Check your connection and try again." }
        finally { loading = false }
    }
    LaunchedEffect(playlist.id, revision, more) {
        val token = next
        if (more == 0 || token == null || loading || (requiresAccount && !YouTubeAuth.isLoggedIn)) return@LaunchedEffect
        loading = true; error = null
        try {
            val page = AccountLibrary.videos(playlist, continuation = token,
                shortsOnly = activeFilter?.title.equals("Shorts", ignoreCase = true))
            entries = entries + page.videos
            next = page.continuation?.takeUnless { it == token }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Couldn't load more videos." }
        finally { loading = false }
    }

    val effectivePlaylist = remember(playlist, header, entries) {
        val resolvedTitle = if (playlist.title.isBlank() || playlist.title.equals("Playlist", ignoreCase = true)) {
            header?.title?.ifBlank { playlist.title } ?: playlist.title
        } else playlist.title
        val resolvedThumb = playlist.thumbnail.ifBlank {
            header?.thumbnail?.ifBlank { entries.firstOrNull()?.thumbnail.orEmpty() }.orEmpty()
        }
        playlist.copy(title = resolvedTitle.ifBlank { "Playlist" }, thumbnail = resolvedThumb)
    }

    val visible = remember(entries, query) {
        if (query.isBlank()) entries else entries.filter { it.title.contains(query, true) || it.uploader.contains(query, true) }
    }
    val remove: (WatchedVideo) -> Unit = { video ->
        scope.launch {
            try {
                val choice = AccountLibrary.choices(videoKey(video.url)).firstOrNull { it.id == playlist.id && it.selected }
                    ?: error("YouTube did not provide a removal action")
                AccountLibrary.setChoice(videoKey(video.url), choice, revision)
                if (revision == YouTubeAuth.sessionRevision) entries = entries.filterNot { it === video }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Toast.makeText(context, e.message ?: "Couldn't remove video", Toast.LENGTH_SHORT).show() }
        }
    }

    val bottomNavAutoHide = rememberBottomNavAutoHideState(playlist.id, activeFilter, searching)
    FrostedGlassBackdropHost(Modifier.fillMaxSize().background(PlaylistBackground).statusBarsPadding().navigationBarsPadding().bottomNavAutoHide(bottomNavAutoHide)) {
        val glassBackdrop = LocalFrostedGlassBackdrop.current
        BoxWithConstraints(Modifier.fillMaxSize().frostedGlassSource(glassBackdrop)) {
            val screenWidth = maxWidth
            if (playlist.id == "YS") {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp), state = gridState, modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column { Spacer(Modifier.height(56.dp)); Text("Saved Shorts", Modifier.padding(start = 16.dp, top = 8.dp, bottom = 16.dp), fontSize = 32.sp, fontWeight = FontWeight.Bold) }
                    }
                    if (loading && entries.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    if (error != null) item(span = { GridItemSpan(maxLineSpan) }) { PlaylistError(error.orEmpty()) { if (entries.isEmpty()) request++ else more++ } }
                    items(visible) { video ->
                        SavedShortCard(video, onClick = { onOpen(video, entries) }, onSave = { onSave(video) }, onShare = { onShare(video) }, onRemove = { remove(video) })
                    }
                    if (!loading && error == null && visible.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(if (query.isNotBlank()) "No results" else "No saved Shorts", Modifier.padding(16.dp), color = Color.LightGray)
                    }
                    if (next != null) item(span = { GridItemSpan(maxLineSpan) }) { TextButton(onClick = { more++ }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "Loading…" else "Load more") } }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
                    item {
                        PlaylistHeader(effectivePlaylist, header, filters, activeFilter, screenWidth, entries.isNotEmpty(),
                            onFilter = { activeFilter = it }, onPlay = { onPlay(entries) })
                    }
                    if (loading && entries.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    if (error != null) item { PlaylistError(error.orEmpty()) { if (entries.isEmpty()) request++ else more++ } }
                    if (!loading && error == null && visible.isEmpty()) item { Text(if (query.isNotBlank()) "No results" else "No videos in this playlist", Modifier.padding(16.dp), color = Color.LightGray) }
                    items(visible) { video ->
                        PlaylistVideoRow(video, screenWidth, onClick = { onOpen(video, entries) }, onSave = { onSave(video) },
                            onShare = { onShare(video) }, onRemove = { remove(video) })
                    }
                    if (next != null) item { TextButton(onClick = { more++ }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "Loading…" else "Load more") } }
                }
            }
            val collapsed = if (playlist.id == "YS") gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 80
                else listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 150
            PlaylistTopBar(title = effectivePlaylist.title, collapsed = collapsed, searching = searching, query = query,
                onBack = onBack, onQuery = { query = it }, onSearch = { searching = true },
                onCloseSearch = { searching = false; query = "" }, onShare = { onSharePlaylist(context, effectivePlaylist) })
        }
        Box(Modifier.align(Alignment.BottomCenter)) {
            HomeNavigation("you", onNavigate, visible = bottomNavAutoHide.visible)
        }
    }
}

@Composable
private fun PlaylistHeader(
    playlist: AccountPlaylist, header: AccountPlaylistHeader?, filters: List<AccountPlaylistFilter>,
    active: AccountPlaylistFilter?, screenWidth: androidx.compose.ui.unit.Dp, hasVideos: Boolean,
    onFilter: (AccountPlaylistFilter) -> Unit, onPlay: () -> Unit,
) {
    val context = LocalContext.current
    val base = header?.backgroundArgb?.let { Color(it.toInt()) } ?: Color(0xFF343434)
    val stops = header?.gradient?.takeIf { it.size >= 2 }?.map { it.first to Color(it.second.toInt()) }
        ?: listOf(0f to base, .5f to base.copy(alpha = .32f), 1f to PlaylistBackground)
    val isLiked = playlist.id == "LL"
    val imageWidth = if (isLiked) (screenWidth * .32f).coerceIn(120.dp, 240.dp) else (screenWidth * .38f).coerceIn(140.dp, 284.dp)
    Box(Modifier.fillMaxWidth()) {
        val backdrop = header?.backgroundImage
        if (!backdrop.isNullOrBlank()) {
            // Decode kecil lalu di-upscale: efek buram murah tanpa RenderEffect/offscreen per frame.
            val request = remember(backdrop) { ImageRequest.Builder(context).data(backdrop).size(96).build() }
            AsyncImage(request, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(colorStops = *stops.toTypedArray())))
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(56.dp))
            Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(imageWidth).aspectRatio(if (isLiked) 2f / 3f else 16f / 9f).clip(RoundedCornerShape(8.dp)).background(PlaylistChip)) {
                    AsyncImage(playlist.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(playlist.title, color = Color.White, fontSize = if (screenWidth >= 700.dp) 36.sp else 24.sp,
                        lineHeight = if (screenWidth >= 700.dp) 44.sp else 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val owner = header?.owner.orEmpty()
                    if (owner.isNotBlank()) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val avatar = YouTubeAuth.profile.avatarUrl
                        if (!avatar.isNullOrBlank() && owner == YouTubeAuth.profile.name) AsyncImage(avatar, null, Modifier.size(28.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.AccountCircle, null, Modifier.size(28.dp), tint = Color.LightGray)
                        Text("by $owner", Modifier.padding(start = 8.dp), color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val privacy = header?.privacy.orEmpty().lowercase().replaceFirstChar { it.titlecase() }.ifBlank { playlist.subtitle }
                    val count = header?.count.orEmpty().let { if (it.matches(Regex("[\\d,.]+"))) "$it videos" else it }
                    val details = listOf("Playlist", privacy, count, header?.views.orEmpty()).filter { it.isNotBlank() }.joinToString("  •  ")
                    Text(details, Modifier.padding(top = 8.dp), color = Color(0xFFE5E5E5), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = onPlay, enabled = hasVideos, modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            contentPadding = PaddingValues(horizontal = 16.dp)) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp)); Text("Play all", Modifier.padding(start = 4.dp), fontSize = 14.sp)
                        }
                        if (playlist.id == "WL") PlaylistHeaderAction(Icons.Default.Add, "Add videos") {
                            Toast.makeText(context, "Add videos from a video’s Save menu", Toast.LENGTH_SHORT).show()
                        }
                        PlaylistDownloadAction {
                            Toast.makeText(context, "Playlist downloads aren't available yet", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            if (filters.isNotEmpty()) Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(start = 16.dp, top = 16.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { filter ->
                    val selected = (active?.title ?: filters.firstOrNull { it.selected }?.title) == filter.title
                    Box(Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).background(if (selected) Color(0xFFF1F1F1) else PlaylistChip)
                        .clickable { onFilter(filter) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(filter.title, color = if (selected) PlaylistBackground else Color.White, fontSize = 14.sp)
                    }
                }
            } else if (playlist.id == "WL") {
                Box(Modifier.widthIn(max = 720.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(start = 16.dp, top = 16.dp, bottom = 18.dp)) {
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(PlaylistChip).clickable {
                        Toast.makeText(context, "Playlist sorting isn't available yet", Toast.LENGTH_SHORT).show()
                    }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Manual", fontSize = 14.sp); Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(20.dp))
                    }
                }
            } else Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PlaylistHeaderAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(22.dp), tint = Color.White)
    }
}

@Composable
private fun PlaylistDownloadAction(onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.yt_download), "Download playlist", Modifier.size(22.dp), tint = Color.White)
    }
}

@Composable
private fun PlaylistTopBar(title: String, collapsed: Boolean, searching: Boolean, query: String,
    onBack: () -> Unit, onQuery: (String) -> Unit, onSearch: () -> Unit, onCloseSearch: () -> Unit, onShare: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(56.dp).background(if (collapsed || searching) PlaylistBackground else Color.Transparent), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        if (searching) {
            TextField(query, onQuery, Modifier.weight(1f), singleLine = true, placeholder = { Text("Search loaded videos") },
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
            IconButton(onClick = onCloseSearch) { Icon(Icons.Default.Close, "Close search") }
        } else {
            if (collapsed) Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1) else Spacer(Modifier.weight(1f))
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Playlist options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Share playlist") }, onClick = { menu = false; onShare() })
                }
            }
        }
    }
}

@Composable
private fun PlaylistVideoRow(video: WatchedVideo, screenWidth: androidx.compose.ui.unit.Dp, onClick: () -> Unit,
    onSave: () -> Unit, onShare: () -> Unit, onRemove: () -> Unit) {
    val thumbWidth = (screenWidth * .30f).coerceIn(152.dp, 288.dp)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(thumbWidth).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(PlaylistChip)) {
            AsyncImage(video.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (video.duration > 0) Text(playlistDuration(video.duration), Modifier.align(Alignment.BottomEnd).padding(4.dp)
                .background(Color.Black.copy(alpha = .8f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp), color = Color.White, fontSize = 12.sp)
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, lineHeight = 20.sp)
            if (video.uploader.isNotBlank()) Text(video.uploader, color = Color(0xFFAAAAAA), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            val meta = listOf(video.viewsText, video.publishedText).filter { it.isNotBlank() }.joinToString("  •  ")
            if (meta.isNotBlank()) Text(meta, color = Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 1)
        }
        CollectionVideoMenu(onSave, onShare, onRemove)
    }
}

@Composable
private fun SavedShortCard(video: WatchedVideo, onClick: () -> Unit, onSave: () -> Unit, onShare: () -> Unit, onRemove: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(.60f).clip(RoundedCornerShape(8.dp)).background(PlaylistChip).clickable(onClick = onClick)) {
        AsyncImage(video.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Text(video.title, Modifier.align(Alignment.BottomStart).padding(8.dp), color = Color.White, fontSize = 15.sp,
            lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Box(Modifier.align(Alignment.TopEnd)) { CollectionVideoMenu(onSave, onShare, onRemove) }
    }
}

@Composable
private fun CollectionVideoMenu(onSave: () -> Unit, onShare: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "Video options", tint = Color.White) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Save to playlist") }, onClick = { open = false; onSave() })
            DropdownMenuItem(text = { Text("Share") }, onClick = { open = false; onShare() })
            DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { open = false; onRemove() })
        }
    }
}

@Composable
private fun PlaylistError(message: String, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, Modifier.weight(1f), color = Color.LightGray)
        TextButton(onClick = onRetry) { Text("Try again") }
    }
}

private fun playlistDuration(ms: Long): String {
    val sec = ms / 1000
    return if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)
}

private fun onSharePlaylist(context: android.content.Context, playlist: AccountPlaylist) {
    val url = "https://www.youtube.com/playlist?list=${android.net.Uri.encode(playlist.id)}"
    runCatching { context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, url), "Share playlist")) }
}
