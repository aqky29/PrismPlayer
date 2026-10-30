package id.flufclip.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlin.math.roundToInt

internal val LinkBlue = Color(0xFF3EA6FF)
internal val Muted = Color(0xFFAAAAAA)
private val PanelLine = Color(0xFF303030)

/** Comment thread of the open video; shared by the teaser card under the video and the comments panel. */
internal class CommentsState(val url: String, private val scope: CoroutineScope) {
    var info by mutableStateOf<CommentsInfo?>(null)
    var items by mutableStateOf<List<CommentsInfoItem>>(emptyList())
    var next by mutableStateOf<Page?>(null)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    suspend fun load() {
        loading = true; error = null
        try {
            val result = VideoRepository.comments(url)
            info = result; items = result.relatedItems; next = result.nextPage
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = "Komentar gagal dimuat: ${e.message ?: "coba lagi"}" }
        finally { loading = false }
    }
    fun retry() { scope.launch { load() } }
    fun loadMore() {
        val source = info ?: return
        val page = next ?: return
        if (loading) return
        loading = true; error = null
        scope.launch {
            try {
                val result = VideoRepository.moreComments(source, page)
                val updated = (items + result.items).distinctBy { it.commentId ?: "${it.uploaderName}:${it.commentText?.content}" }
                next = if (updated.size == items.size) null else result.next
                items = updated
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = "Komentar berikutnya gagal dimuat: ${e.message ?: "coba lagi"}" }
            finally { loading = false }
        }
    }
}

@Composable internal fun rememberComments(url: String): CommentsState {
    val scope = rememberCoroutineScope()
    val state = remember(url) { CommentsState(url, scope) }
    LaunchedEffect(state) { state.load() }
    return state
}

// ---------- formatting ----------

private val UrlOrTimestamp = Regex("(https?://[^\\s<]+)|(?<![\\d:])(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})(?![\\d:])")

/** HTML with links for a description or comment; plain text gets its URLs and timestamps linked like on YouTube. */
internal fun richHtml(description: Description?): String {
    val content = description?.content.orEmpty()
    if (description?.type == Description.HTML) return content
    val escaped = android.text.TextUtils.htmlEncode(content)
    return UrlOrTimestamp.replace(escaped) { match ->
        val url = match.groups[1]?.value
        if (url != null) "<a href=\"$url\">$url</a>"
        else {
            val h = match.groups[2]?.value?.toInt() ?: 0
            val seconds = h * 3600 + match.groups[3]!!.value.toInt() * 60 + match.groups[4]!!.value.toInt()
            "<a href=\"flufseek:$seconds\">${match.value}</a>"
        }
    }.replace("\n", "<br>")
}

internal fun plainText(description: Description?): String {
    val content = description?.content.orEmpty()
    return if (description?.type == Description.HTML) android.text.Html.fromHtml(content, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim() else content.trim()
}

internal fun relativeTime(date: DateWrapper?, fallback: String?): String? {
    val time = date?.offsetDateTime() ?: return fallback?.takeIf { it.isNotBlank() }
    val seconds = java.time.Duration.between(time, java.time.OffsetDateTime.now()).seconds.coerceAtLeast(0)
    val (value, unit) = when {
        seconds < 60 -> return "Baru saja"
        seconds < 3_600 -> seconds / 60 to "menit"
        seconds < 86_400 -> seconds / 3_600 to "jam"
        seconds < 604_800 -> seconds / 86_400 to "hari"
        seconds < 2_592_000 -> seconds / 604_800 to "minggu"
        seconds < 31_536_000 -> seconds / 2_592_000 to "bulan"
        else -> seconds / 31_536_000 to "tahun"
    }
    return "$value $unit yang lalu"
}

internal fun fullCount(count: Long): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale("id", "ID")).format(count)

@Composable internal fun RichText(html: String, onLink: (String) -> Unit, modifier: Modifier = Modifier, collapsedLines: Int = Int.MAX_VALUE, fontSize: Int = 14) {
    val latestOnLink by rememberUpdatedState(onLink)
    val text = remember(html) {
        AnnotatedString.fromHtml(html, linkStyles = TextLinkStyles(SpanStyle(color = LinkBlue)),
            linkInteractionListener = LinkInteractionListener { link -> (link as? LinkAnnotation.Url)?.url?.let { latestOnLink(it) } })
    }
    var expanded by remember(html) { mutableStateOf(false) }
    var overflow by remember(html) { mutableStateOf(false) }
    Column(modifier) {
        Text(text, color = Color.White, fontSize = fontSize.sp, lineHeight = (fontSize + 6).sp, maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis, onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow })
        if (overflow || expanded) Text(if (expanded) "Lebih sedikit" else "Baca selengkapnya", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp).clickable { expanded = !expanded })
    }
}

// ---------- shared panel frame ----------

/** YouTube engagement panel: drag handle, title row with close button, divider; dragging the header down dismisses it. */
@Composable internal fun EngagementPanel(title: String, subtitle: String?, draggable: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val dismissDistance = with(LocalDensity.current) { 120.dp.toPx() }
    val drag = if (!draggable) Modifier else Modifier.draggable(orientation = Orientation.Vertical,
        state = rememberDraggableState { delta -> scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) } },
        onDragStopped = { velocity -> if (offset.value > dismissDistance || velocity > 1500f) onDismiss() else scope.launch { offset.animateTo(0f) } })
    Column(modifier.offset { IntOffset(0, offset.value.roundToInt()) }.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(Background)) {
        Column(drag) {
            if (draggable) Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(40.dp, 4.dp).clip(RoundedCornerShape(50)).background(Color(0xFF606060))) }
            Row(Modifier.fillMaxWidth().padding(start = if (navigation == null) 16.dp else 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                navigation?.invoke()
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                subtitle?.let { Text(it, color = Muted, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Tutup") }
            }
        }
        HorizontalDivider(color = PanelLine)
        content()
    }
}

// ---------- comments ----------

@Composable internal fun CommentsTeaser(state: CommentsState, onClick: () -> Unit) {
    val count = state.info?.commentsCount?.takeIf { it >= 0 }
    val first = state.items.firstOrNull()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).clickable(onClick = onClick).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Komentar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            count?.let { Text(formatCount(it.toLong()), color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp)) }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                state.info?.isCommentsDisabled == true -> Text("Komentar dinonaktifkan.", color = Muted, fontSize = 13.sp)
                first != null -> {
                    Avatar(first.uploaderAvatars.lastOrNull()?.url, first.uploaderName, 24)
                    Text(plainText(first.commentText), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                }
                state.loading -> Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF3A3A3A)))
                state.error != null -> Text("Komentar gagal dimuat. Ketuk untuk mencoba lagi.", color = Muted, fontSize = 13.sp)
                else -> Text("Belum ada komentar.", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable internal fun CommentsPanel(state: CommentsState, info: StreamInfo, draggable: Boolean, onDismiss: () -> Unit, onLink: (String) -> Unit, onChannel: (String) -> Unit, modifier: Modifier = Modifier) {
    var thread by remember(state) { mutableStateOf<CommentsInfoItem?>(null) }
    BackHandler(thread != null) { thread = null }
    val creatorAvatar = info.uploaderAvatars.lastOrNull()?.url
    val parent = thread
    if (parent != null) {
        EngagementPanel("Balasan", null, draggable, onDismiss, modifier, navigation = { IconButton(onClick = { thread = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali ke komentar") } }) {
            RepliesList(state, parent, info, creatorAvatar, onLink, onChannel)
        }
        return
    }
    val count = state.info?.commentsCount?.takeIf { it >= 0 }
    EngagementPanel("Komentar", count?.let { formatCount(it.toLong()) }, draggable, onDismiss, modifier) {
        val listState = rememberLazyListState()
        val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 3 } == true } }
        LaunchedEffect(nearEnd, state.next) { if (nearEnd && state.next != null && state.error == null) state.loadMore() }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
            if (state.info?.isCommentsDisabled == true) item { PanelMessage("Komentar dinonaktifkan.") }
            items(state.items, key = { it.commentId ?: it.hashCode().toString() }) { comment ->
                CommentRow(comment, info, creatorAvatar, avatarSize = 32, onLink = onLink, onChannel = onChannel, onReplies = { thread = comment })
            }
            if (!state.loading && state.info?.isCommentsDisabled == false && state.items.isEmpty() && state.error == null) item { PanelMessage("Belum ada komentar.") }
            if (state.loading) item { PanelSpinner() }
            state.error?.let { message -> item { PanelError(message) { if (state.info == null) state.retry() else state.loadMore() } } }
        }
    }
}

@Composable private fun RepliesList(state: CommentsState, parent: CommentsInfoItem, info: StreamInfo, creatorAvatar: String?, onLink: (String) -> Unit, onChannel: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var replies by remember(parent) { mutableStateOf<List<CommentsInfoItem>>(emptyList()) }
    var next by remember(parent) { mutableStateOf(parent.replies) }
    var loading by remember(parent) { mutableStateOf(false) }
    var error by remember(parent) { mutableStateOf<String?>(null) }
    fun load() {
        val source = state.info ?: return
        val page = next ?: return
        if (loading) return
        loading = true; error = null
        scope.launch {
            try {
                val result = VideoRepository.replies(source, page)
                val updated = (replies + result.items).distinctBy { it.commentId ?: "${it.uploaderName}:${it.commentText?.content}" }
                next = if (updated.size == replies.size) null else result.next
                replies = updated
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = "Balasan gagal dimuat: ${e.message ?: "coba lagi"}" }
            finally { loading = false }
        }
    }
    LaunchedEffect(parent) { load() }
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 3 } == true } }
    LaunchedEffect(nearEnd, next) { if (nearEnd && next != null && error == null && replies.isNotEmpty()) load() }
    LazyColumn(Modifier.fillMaxWidth(), state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Column(Modifier.background(Color(0xFF1A1A1A))) { CommentRow(parent, info, creatorAvatar, avatarSize = 32, onLink = onLink, onChannel = onChannel, onReplies = null, expandedByDefault = true) }
            HorizontalDivider(color = PanelLine)
        }
        items(replies, key = { it.commentId ?: it.hashCode().toString() }) { reply ->
            CommentRow(reply, info, creatorAvatar, avatarSize = 24, onLink = onLink, onChannel = onChannel, onReplies = null, modifier = Modifier.padding(start = 40.dp))
        }
        if (loading) item { PanelSpinner() }
        error?.let { message -> item { PanelError(message) { load() } } }
        if (!loading && error == null && replies.isEmpty() && next == null) item { PanelMessage("Balasan tidak tersedia.") }
    }
}

@Composable private fun CommentRow(comment: CommentsInfoItem, info: StreamInfo, creatorAvatar: String?, avatarSize: Int, onLink: (String) -> Unit, onChannel: (String) -> Unit,
    onReplies: (() -> Unit)?, modifier: Modifier = Modifier, expandedByDefault: Boolean = false) {
    val channelUrl = comment.uploaderUrl?.takeIf { it.isNotBlank() }
    val avatarUrl = rememberAvatarUrl(comment.uploaderAvatars.lastOrNull()?.url, channelUrl)
    Row(modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
        Box(Modifier.clickable(enabled = channelUrl != null) { channelUrl?.let(onChannel) }) { Avatar(avatarUrl, comment.uploaderName, avatarSize) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            if (comment.isPinned) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                PinGlyph(Modifier.size(12.dp))
                Text("Disematkan oleh ${info.uploaderName ?: "kreator"}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val name = comment.uploaderName.orEmpty()
                if (comment.isChannelOwner) Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF3F3F3F)).padding(horizontal = 6.dp, vertical = 1.dp))
                else Text(name, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (comment.isUploaderVerified) Text(" ✓", fontSize = 11.sp, color = Muted)
                relativeTime(comment.uploadDate, comment.textualUploadDate)?.let { Text(" · $it", fontSize = 12.sp, color = Muted, maxLines = 1) }
            }
            RichText(richHtml(comment.commentText), onLink, Modifier.padding(top = 2.dp), collapsedLines = if (expandedByDefault) Int.MAX_VALUE else 5)
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}, enabled = false, modifier = Modifier.size(32.dp).offset(x = (-8).dp)) { Icon(Icons.Outlined.ThumbUp, "Suka", Modifier.size(16.dp), tint = Color.White) }
                val likes = comment.likeCount.takeIf { it > 0 }?.let { formatCount(it.toLong()) } ?: comment.textualLikeCount?.takeIf { it.isNotBlank() && it != "0" }
                likes?.let { Text(it, fontSize = 12.sp, color = Muted, modifier = Modifier.offset(x = (-8).dp)) }
                IconButton(onClick = {}, enabled = false, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.ThumbUp, "Tidak suka", Modifier.size(16.dp).graphicsLayer { scaleY = -1f }, tint = Color.White) }
                IconButton(onClick = { onReplies?.invoke() }, enabled = onReplies != null, modifier = Modifier.size(32.dp)) { ReplyGlyph(Modifier.size(16.dp)) }
                if (comment.isHeartedByUploader) Box(Modifier.padding(start = 6.dp).size(20.dp)) {
                    Avatar(creatorAvatar, info.uploaderName, 16)
                    Icon(Icons.Default.Favorite, "Disukai kreator", tint = Color(0xFFFF4E45), modifier = Modifier.size(11.dp).align(Alignment.BottomEnd))
                }
            }
            val replyCount = comment.replyCount
            if (onReplies != null && comment.replies != null && replyCount != 0) {
                Row(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onReplies).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (comment.hasCreatorReply()) Avatar(creatorAvatar, info.uploaderName, 20, Modifier.padding(end = 6.dp))
                    else Icon(Icons.Default.KeyboardArrowDown, null, tint = LinkBlue, modifier = Modifier.size(18.dp))
                    Text(if (replyCount > 0) "$replyCount balasan" else "Lihat balasan", color = LinkBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

// ---------- description ----------

@Composable internal fun DescriptionTeaser(info: StreamInfo, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(info.name, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val tags = info.tags.take(2).map { "#" + it.replace(" ", "") }
        val meta = listOfNotNull(info.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} x ditonton" }, relativeTime(info.uploadDate, info.textualUploadDate)) + tags
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(meta.joinToString("  "), color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text("  ...selengkapnya", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable internal fun DescriptionPanel(info: StreamInfo, draggable: Boolean, onDismiss: () -> Unit, onLink: (String) -> Unit, onSeek: (Int) -> Unit, onChannel: () -> Unit, modifier: Modifier = Modifier) {
    EngagementPanel("Deskripsi", null, draggable, onDismiss, modifier) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(info.name, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                val date = info.uploadDate?.offsetDateTime()
                val locale = java.util.Locale("id", "ID")
                DescriptionStat(if (info.likeCount >= 0) fullCount(info.likeCount) else "–", "Suka")
                DescriptionStat(if (info.viewCount >= 0) fullCount(info.viewCount) else "–", "Penayangan")
                if (date != null) DescriptionStat(date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", locale)), date.year.toString())
                else DescriptionStat(info.textualUploadDate?.takeIf { it.isNotBlank() } ?: "–", "Diunggah")
            }
            val html = richHtml(info.description)
            if (info.description?.content.isNullOrBlank()) Text("Tidak ada deskripsi.", color = Muted, fontSize = 14.sp)
            else RichText(html, onLink)
            val chapters = info.streamSegments
            if (chapters.isNotEmpty()) {
                Text("Bab", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(chapters) { chapter ->
                        Column(Modifier.width(168.dp).clip(RoundedCornerShape(8.dp)).clickable { onSeek(chapter.startTimeSeconds) }) {
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Surface)) {
                                (chapter.previewUrl ?: info.thumbnails.lastOrNull()?.url)?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                            }
                            Text(chapter.title.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            Text(formatSeconds(chapter.startTimeSeconds), color = LinkBlue, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
            info.metaInfo.filter { !it.title.isNullOrBlank() || !it.content?.content.isNullOrBlank() }.forEach { meta ->
                Column(Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).padding(12.dp)) {
                    meta.title?.takeIf { it.isNotBlank() }?.let { Text(it, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    meta.content?.takeIf { !it.content.isNullOrBlank() }?.let { RichText(richHtml(it), onLink, Modifier.padding(top = 4.dp), fontSize = 13) }
                    meta.urls.zip(meta.urlTexts).forEach { (url, label) -> Text(label, color = LinkBlue, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp).clickable { onLink(url.toString()) }) }
                }
            }
            HorizontalDivider(color = PanelLine, modifier = Modifier.padding(vertical = 20.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = !info.uploaderUrl.isNullOrBlank(), onClick = onChannel), verticalAlignment = Alignment.CenterVertically) {
                Avatar(info.uploaderAvatars.lastOrNull()?.url, info.uploaderName, 56)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(info.uploaderName ?: "Channel", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    info.uploaderSubscriberCount.takeIf { it >= 0 }?.let { Text("${formatCount(it)} subscriber", color = Muted, fontSize = 13.sp) }
                }
            }
            Row(Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelPill("Video", onChannel)
                PanelPill("Tentang", onChannel)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable private fun RowScope.DescriptionStat(value: String, label: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
        Text(label, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

// ---------- small pieces ----------

@Composable private fun PanelPill(label: String, onClick: () -> Unit) {
    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Color(0xFF3F3F3F), RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable internal fun Avatar(url: String?, name: String?, size: Int, modifier: Modifier = Modifier) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier.size(size.dp).clip(RoundedCornerShape(50)).background(Color(0xFF5F5F5F)), contentAlignment = Alignment.Center) {
        if (url == null || failed) Text(name?.trimStart('@')?.firstOrNull()?.uppercase() ?: "", fontSize = (size * .45f).sp, color = Color.White)
        url?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), onError = { failed = true }) }
    }
}

/** Foto profil komentar; kalau kosong, pakai foto channel penulisnya (hasilnya di-cache di VideoRepository). */
@Composable private fun rememberAvatarUrl(suppliedUrl: String?, channelUrl: String?): String? {
    var avatar by remember(channelUrl, suppliedUrl) { mutableStateOf(suppliedUrl?.takeIf { it.isNotBlank() }) }
    LaunchedEffect(channelUrl, suppliedUrl) {
        if (suppliedUrl.isNullOrBlank() && !channelUrl.isNullOrBlank()) avatar = VideoRepository.channelAvatar(channelUrl)
    }
    return avatar
}

@Composable private fun PanelMessage(text: String) { Text(text, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp)) }
@Composable private fun PanelSpinner() { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }
@Composable private fun PanelError(message: String, onRetry: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Text(message, color = Muted, fontSize = 13.sp); TextButton(onClick = onRetry) { Text("Coba lagi", color = LinkBlue) } }
}

@Composable private fun ReplyGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val stroke = 1.5.dp.toPx()
        drawRoundRect(Color.White, topLeft = Offset(w * .08f, h * .12f), size = androidx.compose.ui.geometry.Size(w * .84f, h * .62f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .12f), style = Stroke(stroke))
        drawLine(Color.White, Offset(w * .30f, h * .74f), Offset(w * .22f, h * .94f), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(w * .22f, h * .94f), Offset(w * .48f, h * .74f), stroke, StrokeCap.Round)
    }
}

@Composable private fun PinGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val stroke = 1.3.dp.toPx()
        drawLine(Muted, Offset(w * .30f, h * .10f), Offset(w * .70f, h * .10f), stroke, StrokeCap.Round)
        drawLine(Muted, Offset(w * .36f, h * .10f), Offset(w * .32f, h * .55f), stroke, StrokeCap.Round)
        drawLine(Muted, Offset(w * .64f, h * .10f), Offset(w * .68f, h * .55f), stroke, StrokeCap.Round)
        drawLine(Muted, Offset(w * .15f, h * .55f), Offset(w * .85f, h * .55f), stroke, StrokeCap.Round)
        drawLine(Muted, Offset(w * .50f, h * .55f), Offset(w * .50f, h * .95f), stroke, StrokeCap.Round)
    }
}

internal fun formatSeconds(seconds: Int): String = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)

/** Seconds to seek to when [url] is a timestamp link for video [videoId], else null. */
internal fun timestampSeconds(url: String, videoId: String): Int? {
    if (url.startsWith("flufseek:")) return url.removePrefix("flufseek:").toIntOrNull()
    val uri = runCatching { android.net.Uri.parse(if (url.startsWith("/")) "https://www.youtube.com$url" else url) }.getOrNull() ?: return null
    val host = uri.host.orEmpty()
    val id = when {
        host.endsWith("youtu.be") -> uri.lastPathSegment
        host.contains("youtube.com") -> uri.getQueryParameter("v")
        else -> null
    }
    if (id != videoId) return null
    val t = uri.getQueryParameter("t") ?: return null
    t.toIntOrNull()?.let { return it }
    val parts = Regex("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?").matchEntire(t) ?: return null
    val (h, m, s) = parts.destructured
    return (h.toIntOrNull() ?: 0) * 3600 + (m.toIntOrNull() ?: 0) * 60 + (s.toIntOrNull() ?: 0)
}
