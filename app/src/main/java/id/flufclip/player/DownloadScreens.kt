package id.flufclip.player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import org.schabi.newpipe.extractor.stream.StreamInfoItem

internal fun openMainFromDownloads(context: Context, url: String) {
    val path = android.net.Uri.parse(url).path.orEmpty()
    val handoff = (url.startsWith("flufplay-tab:") || path.startsWith("/channel/") || path.startsWith("/@")) &&
        (context as? DownloadsActivity)?.handoffPlayback() == true
    context.startActivity(Intent(context, MainActivity::class.java).putExtra("open_video", url)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    if (handoff) (context as? DownloadsActivity)?.finish()
}

@Composable
internal fun DownloadsHome(downloads: List<SavedDownload>, state: DownloadState, revision: Long, onBack: () -> Unit, onPlay: (SavedDownload) -> Unit) {
    val context = LocalContext.current
    var search by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var settings by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<SavedDownload?>(null) }
    var recommended by remember { mutableStateOf<List<StreamInfoItem>>(emptyList()) }
    val recommendationSource = downloads.firstOrNull { it.video.sourceUrl.isNotBlank() }?.video?.sourceUrl
    LaunchedEffect(recommendationSource) {
        recommended = emptyList()
        if (recommendationSource != null && downloadNetworkAvailable(context)) {
            recommended = withTimeoutOrNull(10000) {
                try { VideoRepository.resolve(recommendationSource, context).relatedItems.filterIsInstance<StreamInfoItem>().distinctBy { it.url }.take(12) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { emptyList() }
            }.orEmpty()
        }
    }
    val filtered = downloads.filter { query.isBlank() || it.title.contains(query, true) || it.video.uploader.contains(query, true) }
    val bottomNavAutoHide = rememberBottomNavAutoHideState(search)
    FrostedGlassBackdropHost(Modifier.fillMaxSize().bottomNavAutoHide(bottomNavAutoHide)) {
        val glassBackdrop = LocalFrostedGlassBackdrop.current
        Column(Modifier.fillMaxSize().frostedGlassSource(glassBackdrop)) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                if (search) TextField(query, { query = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Search downloads") })
                else Text("Downloads", Modifier.weight(1f), fontSize = 22.sp)
                IconButton(onClick = { search = !search; if (!search) query = "" }) { Icon(Icons.Default.Search, "Search downloads") }
                IconButton(onClick = { settings = true }) { Icon(painterResource(R.drawable.yt_settings), "Download settings", Modifier.size(24.dp)) }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp)) {
                item { Text("Your downloads", Modifier.padding(horizontal = 16.dp, vertical = 16.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                if (state.video != null && state.phase != DownloadPhase.COMPLETED) item {
                    DownloadingRow(state, onCancel = { context.startService(Intent(context, DownloadService::class.java).setAction("cancel")) })
                }
                if (filtered.isEmpty() && state.video == null) item {
                    Text(if (query.isNotBlank()) "No matching downloads." else "No downloads yet. Saved videos will appear here.", Modifier.padding(24.dp), color = Muted)
                }
                items(filtered, key = { it.uri }) { saved ->
                    Box {
                        DownloadedRow(saved, revision, onClick = { onPlay(saved) }, onMenu = { remove = saved })
                    }
                }
                if (query.isBlank() && recommended.isNotEmpty()) {
                    item { Text("Recommended downloads", Modifier.fillMaxWidth().background(Color(0xFF181818)).padding(16.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    val localKeys = downloads.map { it.video.sourceUrl }.filter { it.isNotBlank() }.map { videoKey(it) }.toSet()
                    items(recommended.filterNot { videoKey(it.url) in localKeys }, key = { it.url }) { item ->
                        Row(Modifier.fillMaxWidth().background(Color(0xFF181818)).clickable { openMainFromDownloads(context, item.url) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(item.thumbnails.lastOrNull()?.url, null, Modifier.width(downloadRowWidth() * 1.55f).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Surface), contentScale = ContentScale.Crop)
                            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                Text(item.name, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(item.uploaderName.orEmpty(), Modifier.padding(top = 5.dp), fontSize = 12.sp, color = Muted)
                            }
                            IconButton(onClick = { openMainFromDownloads(context, "flufplay-download:${item.url}") }) { Icon(painterResource(R.drawable.yt_w_download), "Download video", Modifier.size(24.dp)) }
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) {
            HomeNavigation("you", onSelect = { openMainFromDownloads(context, "flufplay-tab:$it") }, visible = bottomNavAutoHide.visible)
        }
    }
    if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text("Downloads") }, text = {
        Text("Storage location\nDownload/video-yt-downloader\n\nChoose quality before downloading. Saved videos can be played offline.")
    }, confirmButton = { TextButton(onClick = { settings = false }) { Text("Close") } })
    remove?.let { saved -> AlertDialog(onDismissRequest = { remove = null }, title = { Text(saved.title) }, text = { Text("Remove from Downloads? The file will remain on your device.") },
        confirmButton = { TextButton(onClick = { SavedDownloads.forget(context, saved); remove = null }) { Text("Remove from list") } },
        dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } }) }
}

internal data class DownloadRowDisplay(
    val statusText: String,
    val progressPercent: Int?,
    val isError: Boolean = false,
    val isCancelled: Boolean = false,
)

/** Maps internal download phases into a simpler user-facing status and progress state. */
internal fun downloadRowDisplay(state: DownloadState): DownloadRowDisplay = when (state.phase) {
    DownloadPhase.PREPARING -> DownloadRowDisplay(
        statusText = "Preparing…",
        progressPercent = null,
    )
    DownloadPhase.TRANSFERRING -> {
        val base = state.message.ifBlank { "Downloading…" }
        val text = if (state.running && state.etaSeconds != null) "$base · ${state.etaSeconds}s left" else base
        DownloadRowDisplay(
            statusText = text,
            progressPercent = state.progress,
        )
    }
    DownloadPhase.PROCESSING, DownloadPhase.COPYING, DownloadPhase.VERIFYING -> DownloadRowDisplay(
        statusText = "Finalizing…",
        progressPercent = null,
    )
    DownloadPhase.FAILED -> DownloadRowDisplay(
        statusText = state.message.ifBlank { "Download failed." },
        progressPercent = null,
        isError = true,
    )
    DownloadPhase.CANCELLED -> DownloadRowDisplay(
        statusText = state.message.ifBlank { "Download canceled." },
        progressPercent = null,
        isCancelled = true,
    )
    DownloadPhase.COMPLETED -> DownloadRowDisplay(
        statusText = state.message.ifBlank { "Downloaded" },
        progressPercent = 100,
    )
}

@Composable
private fun DownloadingRow(state: DownloadState, onCancel: () -> Unit) {
    val video = state.video ?: return
    val display = downloadRowDisplay(state)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.width(downloadRowWidth()).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Surface),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(video.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = if (state.running) .28f else .55f)))
            if (!state.running) {
                Icon(
                    imageVector = if (display.isError) Icons.Default.ErrorOutline else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (display.isError) ErrorText else Color.White,
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(video.title.ifBlank { "Video" }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = TextPrimary)
            if (video.uploader.isNotBlank()) {
                Text(video.uploader, Modifier.padding(top = 4.dp), color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (display.isError) {
                    Icon(Icons.Default.ErrorOutline, null, Modifier.padding(end = 4.dp).size(14.dp), tint = ErrorText)
                }
                Text(
                    text = display.statusText,
                    color = when {
                        display.isError -> ErrorText
                        state.running -> LinkBlue
                        else -> Muted
                    },
                    fontSize = 12.sp,
                    maxLines = if (state.running) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.running) {
                val barModifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                val progress = display.progressPercent
                if (progress == null) {
                    LinearProgressIndicator(modifier = barModifier, color = LinkBlue, trackColor = HairlineBorder)
                } else {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = barModifier, color = LinkBlue, trackColor = HairlineBorder)
                }
            }
        }
        if (state.running) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel download") }
        } else {
            IconButton(onClick = { DownloadStatus.state.value = DownloadState() }) { Icon(Icons.Default.Close, "Dismiss status") }
        }
    }
}

@Composable
internal fun downloadRowWidth(): androidx.compose.ui.unit.Dp {
    val c = LocalConfiguration.current
    val landscape = c.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    return when {
        c.smallestScreenWidthDp >= 720 -> if (landscape) 262.dp else 292.dp
        c.smallestScreenWidthDp >= 600 -> if (landscape) 192.dp else 262.dp
        landscape -> 172.dp
        else -> 128.dp
    }
}

@Composable
internal fun DownloadedRow(saved: SavedDownload, revision: Long, onClick: () -> Unit, onMenu: (() -> Unit)? = null) {
    val context = LocalContext.current
    val positions = remember { context.getSharedPreferences("download_positions", 0) }
    val position = remember(saved.uri, revision) { positions.getLong(saved.uri, 0) }
    val duration = remember(saved.uri, revision) { positions.getLong("duration:${saved.uri}", saved.video.durationMs) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(downloadRowWidth()).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Surface)) {
            DownloadThumbnail(saved)
            if (duration > 0) Text(playerClock(duration), Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = .8f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp), fontSize = 11.sp)
            if (duration > 0 && position > 0) LinearProgressIndicator(progress = { (position.toFloat() / duration).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter), color = Color.Red, trackColor = Color.White.copy(alpha = .3f))
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(saved.title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.yt_w_downloaded), "Downloaded", Modifier.size(13.dp), tint = Muted)
                Text(saved.video.uploader.ifBlank { if (saved.mime.startsWith("audio/")) "Audio" else "Video" }, Modifier.padding(start = 4.dp), fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (saved.video.views >= 0) "${formatCount(saved.video.views)} penayangan" else Formatter.formatFileSize(context, saved.bytes), Modifier.padding(top = 3.dp), fontSize = 12.sp, color = Muted)
        }
        if (onMenu != null) IconButton(onClick = onMenu, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.MoreVert, "Download options", Modifier.size(20.dp)) }
    }
}

private val thumbnailLock = Mutex()
private val thumbnailCache = android.util.LruCache<String, Bitmap>(12)
@Composable
internal fun DownloadThumbnail(saved: SavedDownload) {
    var artworkFailed by remember(saved.uri, saved.video.thumbnail) { mutableStateOf(false) }
    if (isLocalDownloadUri(saved.video.thumbnail) && !artworkFailed) {
        AsyncImage(saved.video.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            onError = { artworkFailed = true })
        return
    }
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(thumbnailCache.get(saved.uri), saved.uri) {
        if (!saved.mime.startsWith("video/") || value != null || !isLocalDownloadUri(saved.uri)) return@produceState
        value = withContext(Dispatchers.IO) { thumbnailLock.withLock {
            thumbnailCache.get(saved.uri) ?: runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(saved.uri))
                    val frame = if (android.os.Build.VERSION.SDK_INT >= 27) {
                        retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 384, 216)
                    } else null
                    frame ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally { retriever.release() }
            }.getOrNull()?.also { thumbnailCache.put(saved.uri, it) }
        } }
    }
    val frame = bitmap
    if (frame != null) Image(frame.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.PlayArrow, "Play download", Modifier.size(32.dp))
    }
}
