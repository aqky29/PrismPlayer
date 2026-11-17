package id.flufclip.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamSegment
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
        catch (e: Exception) { error = "Failed to load comments: ${e.message ?: "try again"}" }
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
            catch (e: Exception) { error = "Failed to load more comments: ${e.message ?: "try again"}" }
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
        seconds < 60 -> return "Just now"
        seconds < 3_600 -> seconds / 60 to "minute"
        seconds < 86_400 -> seconds / 3_600 to "hour"
        seconds < 604_800 -> seconds / 86_400 to "day"
        seconds < 2_592_000 -> seconds / 604_800 to "week"
        seconds < 31_536_000 -> seconds / 2_592_000 to "month"
        else -> seconds / 31_536_000 to "year"
    }
    return "$value $unit${if (value == 1L) "" else "s"} ago"
}

/**
 * Teks tanggal mulai siaran live seperti `dateText` dari server YouTube ("Started streaming on Jun 15, 2026" untuk siaran lama,
 * "Started streaming 2 hours ago" untuk yang baru). Ambang tujuh hari antara bentuk relatif dan tanggal penuh adalah pilihan
 * sendiri (server tidak menyebutnya). Mengembalikan null bila tanggal mulai tidak diketahui; teks mentah dari extractor tidak dipakai.
 */
internal fun startedStreamingText(date: DateWrapper?): String? {
    val time = date?.offsetDateTime() ?: return null
    val seconds = java.time.Duration.between(time, java.time.OffsetDateTime.now()).seconds
    if (seconds < 604_800) return relativeTime(date, null)?.let { "Started streaming ${it.replaceFirstChar(Char::lowercaseChar)}" }
    return "Started streaming on " + time.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.US))
}

internal fun fullCount(count: Long): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(count)

private fun applyCommentEmojis(
    base: AnnotatedString,
    emojis: Map<String, String>,
): Pair<AnnotatedString, Map<String, String>> {
    if (emojis.isEmpty() || base.isEmpty()) return base to emptyMap()
    val links = base.getLinkAnnotations(0, base.length)
    val matches = CommentEmojiParser.findMatches(base.text, emojis, links.map { it.start until it.end })
    if (matches.isEmpty()) return base to emptyMap()

    val offsetMap = IntArray(base.length + 1)
    val usedEmojis = LinkedHashMap<String, String>()
    val annotated = buildAnnotatedString {
        var cursor = 0
        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > cursor) {
                for (i in cursor until start) offsetMap[i] = length + (i - cursor)
                append(base.text.substring(cursor, start))
            }
            val beforeLen = length
            offsetMap[start] = beforeLen
            appendInlineContent(match.token, match.token)
            val afterLen = length
            for (i in (start + 1)..end) offsetMap[i] = afterLen
            usedEmojis[match.token] = match.url
            cursor = end
        }
        if (cursor < base.length) {
            for (i in cursor until base.length) offsetMap[i] = length + (i - cursor)
            append(base.text.substring(cursor))
        }
        offsetMap[base.length] = length

        base.spanStyles.forEach { range ->
            val s = offsetMap[range.start]
            val e = offsetMap[range.end]
            if (s < e) addStyle(range.item, s, e)
        }
        base.paragraphStyles.forEach { range ->
            val s = offsetMap[range.start]
            val e = offsetMap[range.end]
            if (s < e) addStyle(range.item, s, e)
        }
        links.forEach { range ->
            val s = offsetMap[range.start]
            val e = offsetMap[range.end]
            if (s < e) {
                when (val link = range.item) {
                    is LinkAnnotation.Url -> addLink(link, s, e)
                    is LinkAnnotation.Clickable -> addLink(link, s, e)
                }
            }
        }
    }
    return annotated to usedEmojis
}

@Composable
private fun rememberInlineEmojiContent(usedEmojis: Map<String, String>, sizeSp: Int): Map<String, InlineTextContent> =
    remember(usedEmojis, sizeSp) {
        if (usedEmojis.isEmpty()) emptyMap()
        else usedEmojis.mapValues { (token, url) ->
            InlineTextContent(Placeholder(sizeSp.sp, sizeSp.sp, PlaceholderVerticalAlign.TextCenter)) {
                AsyncImage(
                    model = url,
                    contentDescription = token,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

@Composable internal fun RichText(html: String, onLink: (String) -> Unit, modifier: Modifier = Modifier, collapsedLines: Int = Int.MAX_VALUE, fontSize: Int = 14, emojis: Map<String, String> = emptyMap()) {
    val latestOnLink by rememberUpdatedState(onLink)
    val (text, usedEmojis) = remember(html, emojis) {
        val base = AnnotatedString.fromHtml(html, linkStyles = TextLinkStyles(SpanStyle(color = LinkBlue)),
            linkInteractionListener = LinkInteractionListener { link -> (link as? LinkAnnotation.Url)?.url?.let { latestOnLink(it) } })
        applyCommentEmojis(base, emojis)
    }
    val inlineContent = rememberInlineEmojiContent(usedEmojis, fontSize + 4)
    var expanded by remember(html) { mutableStateOf(false) }
    var overflow by remember(html) { mutableStateOf(false) }
    Column(modifier) {
        Text(text, inlineContent = inlineContent, color = Color.White, fontSize = fontSize.sp, lineHeight = (fontSize + 6).sp, maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis, onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow })
        if (overflow || expanded) {
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    if (expanded) "Show less" else "Read more",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ---------- shared panel frame ----------

/** YouTube engagement panel: drag handle, title row with close button, divider; dragging the header down dismisses it. */
@Composable internal fun EngagementPanel(title: String, subtitle: String?, draggable: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null, showHandle: Boolean = true, titleWeight: FontWeight = FontWeight.Bold, content: @Composable ColumnScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var panelHeight by remember { mutableIntStateOf(0) }
    val dismissDistance = with(LocalDensity.current) { 120.dp.toPx() }
    val drag = if (!draggable) Modifier else Modifier.draggable(orientation = Orientation.Vertical,
        state = rememberDraggableState { delta -> scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) } },
        onDragStopped = { velocity ->
            scope.launch {
                if (offset.value > dismissDistance || velocity > 1500f) {
                    offset.animateTo(panelHeight.toFloat().coerceAtLeast(offset.value), tween(200))
                    onDismiss()
                } else offset.animateTo(0f)
            }
        })
    Column(modifier.onSizeChanged { panelHeight = it.height }.offset { IntOffset(0, offset.value.roundToInt()) }.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(Background)) {
        Column(drag) {
            if (showHandle) Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(40.dp, 4.dp).clip(RoundedCornerShape(50)).background(DragHandleInk)) }
            Row(Modifier.fillMaxWidth().padding(start = if (navigation == null) 16.dp else 4.dp, end = 4.dp, top = if (showHandle) 4.dp else 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                navigation?.invoke()
                Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = titleWeight)
                subtitle?.let { Text(it, color = Muted, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = TextPrimary) }
            }
        }
        HorizontalDivider(color = PanelLine)
        content()
    }
}

// ---------- comments ----------

/**
 * Kartu komentar di bawah deskripsi. Seperti YouTube: label "Comments" + jumlah dan cuplikan komentar.
 * Dapat ditampilkan dalam carousel horizontal bersama komentar lain, live chat replay, atau daftar bab.
 */
@Composable
private fun CommentTeaserRow(comment: CommentsInfoItem) {
    val preview = plainText(comment.commentText)
    val emojis = remember(comment.commentId) { VideoRepository.commentEmojis(comment.commentId) }
    val (annotated, usedEmojis) = remember(preview, emojis) {
        applyCommentEmojis(AnnotatedString(preview), emojis)
    }
    val inlineContent = rememberInlineEmojiContent(usedEmojis, sizeSp = 17)
    val channelUrl = comment.uploaderUrl?.takeIf { it.isNotBlank() }
    val rawAvatar = comment.uploaderAvatars.lastOrNull()?.url ?: VideoRepository.commentAvatar(comment.commentId)
    val avatarUrl = rememberAvatarUrl(rawAvatar, channelUrl)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(avatarUrl, comment.uploaderName, 24)
        Text(annotated, inlineContent = inlineContent, color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable internal fun CommentsTeaser(
    state: CommentsState,
    onClick: () -> Unit,
    pageIndicator: (@Composable () -> Unit)? = null,
    commentIndex: Int = 0
) {
    val count = state.info?.commentsCount?.takeIf { it >= 0 }
    val comment = state.items.getOrNull(commentIndex) ?: state.items.firstOrNull()
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).clickable(onClick = onClick)) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Comments", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                count?.let { Text(formatCount(it.toLong()), color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp)) }
                Spacer(Modifier.weight(1f))
                pageIndicator?.invoke()
            }
            Box(Modifier.padding(top = 12.dp)) {
                when {
                    state.info?.isCommentsDisabled == true -> Text("Comments are turned off.", color = Muted, fontSize = 13.sp)
                    comment != null -> CommentTeaserRow(comment)
                    state.loading -> Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF3A3A3A)))
                    state.error != null -> Text("Failed to load comments. Tap to try again.", color = Muted, fontSize = 13.sp)
                    else -> Text("No comments yet.", color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Kartu cuplikan bab (Chapters teaser) di layar tonton, ditampilkan dalam carousel kartu bersama komentar.
 * Menampilkan judul bab pertama atau cuplikan waktu, dan membuka panel bab / deskripsi saat diketuk.
 */
@Composable internal fun ChaptersTeaser(
    chapters: List<StreamSegment>,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pageIndicator: (@Composable () -> Unit)? = null
) {
    val firstChapter = chapters.firstOrNull()
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).clickable(onClick = onClick)) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Chapters", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text("${chapters.size}", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
                Spacer(Modifier.weight(1f))
                pageIndicator?.invoke()
            }
            Box(Modifier.padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(width = 44.dp, height = 26.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF272727))
                    ) {
                        (firstChapter?.previewUrl ?: thumbnailUrl)?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(
                            firstChapter?.title?.takeIf { it.isNotBlank() } ?: "Key moments",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        firstChapter?.startTimeSeconds?.let {
                            Text(formatSeconds(it), color = LinkBlue, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kartu carousel metadata di layar tonton seperti YouTube (metapanel_overlay):
 * Bingkai kartu (Surface RoundedCornerShape 12dp) dan header (Judul + jumlah + indikator titik) tetap statis di posisinya,
 * sementara isi cuplikan (komentar, bab, replay chat) bergeser secara halus di dalamnya dengan animasi titik yang dinamis.
 */
@Composable internal fun WatchMetadataCarousel(
    comments: CommentsState,
    chapters: List<StreamSegment>,
    chatState: LiveChatState,
    showReplayChat: Boolean,
    thumbnailUrl: String?,
    onComments: () -> Unit,
    onChapters: () -> Unit,
    onChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasChapters = chapters.isNotEmpty()

    val pages = remember(showReplayChat, hasChapters) {
        buildList {
            add(0) // 1. Comments (Top comment preview)
            if (hasChapters) add(-2) // 2. Chapters (Key moments)
            if (showReplayChat) add(-1) // 3. Replay chat (Live chat replay)
        }
    }

    val pageCount = pages.size
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { pageCount }
    val currentPage = pages.getOrElse(pagerState.currentPage.coerceIn(0, pageCount - 1)) { 0 }

    val onCurrentClick = when (currentPage) {
        -1 -> onChat
        -2 -> onChapters
        else -> onComments
    }

    val reduceMotion = rememberReduceMotion()
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable(onClick = onCurrentClick)
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 14.dp)) {
            // Header Row: Judul dinamis (Comments / Chapters / Replay chat) + Count + Indikator Titik
            Row(
                Modifier.fillMaxWidth().heightIn(min = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (currentPage) {
                    -1 -> {
                        Text("Live chat replay", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                    -2 -> {
                        Text("Chapters", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("${chapters.size}", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                    else -> {
                        Text("Comments", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        val count = comments.info?.commentsCount?.takeIf { it >= 0 }
                        count?.let { Text(formatCount(it.toLong()), color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp)) }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (pageCount > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        repeat(pageCount) { i ->
                            val fraction = if (reduceMotion) {
                                if (pagerState.currentPage == i) 1f else 0f
                            } else {
                                val distance = kotlin.math.abs(pagerState.currentPage + pagerState.currentPageOffsetFraction - i)
                                (1f - distance).coerceIn(0f, 1f)
                            }
                            val width = androidx.compose.ui.unit.lerp(4.dp, 10.dp, fraction)
                            val color = androidx.compose.ui.graphics.lerp(Color(0xFF717171), Color.White, fraction)
                            Box(
                                Modifier
                                    .size(width = width, height = 4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(color)
                            )
                        }
                    }
                } else {
                    Icon(
                        painter = painterResource(R.drawable.yt_chevron_down),
                        contentDescription = "Open panel",
                        tint = Color.LightGray,
                        modifier = Modifier.padding(end = 4.dp).size(16.dp)
                    )
                }
            }

            // Body carousel dikunci tinggi seragam 2 baris agar kartu tidak membesar ke bawah saat digeser ke Live chat replay.
            val density = LocalDensity.current
            val teaserBodyHeight = with(density) { 40.sp.toDp() }.coerceAtLeast(40.dp)
            androidx.compose.foundation.pager.HorizontalPager(
                pagerState,
                Modifier.padding(top = 8.dp).fillMaxWidth().height(teaserBodyHeight),
                userScrollEnabled = pageCount > 1,
                pageSpacing = 16.dp,
                verticalAlignment = Alignment.CenterVertically
            ) { pageIdx ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    when (val item = pages.getOrElse(pageIdx) { 0 }) {
                        -1 -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.yt_live_chat_replay),
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "See what others said about this video while it was live.",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                        -2 -> {
                            val firstChapter = chapters.firstOrNull()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(width = 44.dp, height = 26.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF272727))
                                ) {
                                    (firstChapter?.previewUrl ?: thumbnailUrl)?.let {
                                        AsyncImage(
                                            model = it,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                    Text(
                                        firstChapter?.title?.takeIf { it.isNotBlank() } ?: "Key moments",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    firstChapter?.startTimeSeconds?.let {
                                        Text(formatSeconds(it), color = LinkBlue, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        else -> {
                            val comment = comments.items.getOrNull(item) ?: comments.items.firstOrNull()
                            when {
                                comments.info?.isCommentsDisabled == true -> Text("Comments are turned off.", color = Muted, fontSize = 13.sp)
                                comment != null -> CommentTeaserRow(comment)
                                comments.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF3A3A3A)))
                                    Box(Modifier.padding(start = 10.dp).fillMaxWidth(0.72f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF3A3A3A)))
                                }
                                comments.error != null -> Text("Failed to load comments. Tap to try again.", color = Muted, fontSize = 13.sp)
                                else -> Text("No comments yet.", color = Muted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun CommentsPanel(state: CommentsState, info: StreamInfo, draggable: Boolean, onDismiss: () -> Unit, onLink: (String) -> Unit, onChannel: (String) -> Unit, modifier: Modifier = Modifier, showCount: Boolean = true,
    /** Balasan yang sedang dibuka; bisa diberikan dari luar agar ketuk di luar panel dapat kembali ke daftar komentar. */
    threadState: MutableState<CommentsInfoItem?> = remember(state) { mutableStateOf<CommentsInfoItem?>(null) },
    /** Panel samping di layar lebar tidak memakai handle geser ala bottom sheet. */
    showHandle: Boolean = true) {
    var thread by threadState
    BackHandler(thread != null) { thread = null }
    val creatorAvatar = info.uploaderAvatars.lastOrNull()?.url
    val parent = thread
    if (parent != null) {
        EngagementPanel("Replies", null, draggable, onDismiss, modifier, navigation = { IconButton(onClick = { thread = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to comments") } }, showHandle = showHandle) {
            RepliesList(state, parent, info, creatorAvatar, onLink, onChannel)
        }
        return
    }
    val count = state.info?.commentsCount?.takeIf { it >= 0 }
    EngagementPanel("Comments", if (showCount) count?.let { formatCount(it.toLong()) } else null, draggable, onDismiss, modifier, showHandle = showHandle) {
        val listState = rememberLazyListState()
        val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 3 } == true } }
        LaunchedEffect(nearEnd, state.next) { if (nearEnd && state.next != null && state.error == null) state.loadMore() }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
            if (state.info?.isCommentsDisabled == true) item { PanelMessage("Comments are turned off.") }
            items(state.items, key = { it.commentId ?: it.hashCode().toString() }) { comment ->
                CommentRow(comment, info, creatorAvatar, avatarSize = 32, onLink = onLink, onChannel = onChannel, onReplies = { thread = comment })
            }
            if (!state.loading && state.info?.isCommentsDisabled == false && state.items.isEmpty() && state.error == null) item { PanelMessage("No comments yet.") }
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
            catch (e: Exception) { error = "Failed to load replies: ${e.message ?: "try again"}" }
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
        if (!loading && error == null && replies.isEmpty() && next == null) item { PanelMessage("Replies are unavailable.") }
    }
}

@Composable private fun CommentRow(comment: CommentsInfoItem, info: StreamInfo, creatorAvatar: String?, avatarSize: Int, onLink: (String) -> Unit, onChannel: (String) -> Unit,
    onReplies: (() -> Unit)?, modifier: Modifier = Modifier, expandedByDefault: Boolean = false) {
    val channelUrl = comment.uploaderUrl?.takeIf { it.isNotBlank() }
    val rawAvatar = comment.uploaderAvatars.lastOrNull()?.url ?: VideoRepository.commentAvatar(comment.commentId)
    val avatarUrl = rememberAvatarUrl(rawAvatar, channelUrl)
    Row(modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp)) {
        Box(
            Modifier
                .clip(CircleShape)
                .clickable(enabled = channelUrl != null) { channelUrl?.let(onChannel) }
        ) { Avatar(avatarUrl, comment.uploaderName, avatarSize) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            if (comment.isPinned) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                PinGlyph(Modifier.size(12.dp))
                Text("Pinned by ${info.uploaderName ?: "creator"}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val name = comment.uploaderName.orEmpty()
                if (comment.isChannelOwner) {
                    Text(
                        name,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF3F3F3F))
                            .clickable(enabled = channelUrl != null) { channelUrl?.let(onChannel) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                } else {
                    Text(
                        name,
                        fontSize = 12.sp,
                        color = Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = channelUrl != null) { channelUrl?.let(onChannel) }
                            .padding(vertical = 2.dp)
                    )
                }
                if (comment.isUploaderVerified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = Muted,
                        modifier = Modifier.size(12.dp)
                    )
                }
                relativeTime(comment.uploadDate, comment.textualUploadDate)?.let { Text(" · $it", fontSize = 12.sp, color = Muted, maxLines = 1) }
            }
            val commentEmojis = remember(comment.commentId) { VideoRepository.commentEmojis(comment.commentId) }
            RichText(richHtml(comment.commentText), onLink, Modifier.padding(top = 2.dp), collapsedLines = if (expandedByDefault) Int.MAX_VALUE else 5, emojis = commentEmojis)
            val likes = comment.likeCount.takeIf { it > 0 }?.let { formatCount(it.toLong()) } ?: comment.textualLikeCount?.takeIf { it.isNotBlank() && it != "0" }
            if (likes != null || onReplies != null || comment.isHeartedByUploader) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    likes?.let { count ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Outlined.ThumbUp, contentDescription = "Likes", modifier = Modifier.size(14.dp), tint = Muted)
                            Text(count, fontSize = 12.sp, color = Muted)
                        }
                    }
                    if (onReplies != null) {
                        IconButton(onClick = onReplies, modifier = Modifier.size(44.dp)) {
                            ReplyGlyph(Modifier.size(16.dp))
                        }
                    }
                    if (comment.isHeartedByUploader) Box(Modifier.size(22.dp)) {
                        Avatar(creatorAvatar, info.uploaderName, 18)
                        Icon(Icons.Default.Favorite, "Liked by creator", tint = Color(0xFFFF4E45), modifier = Modifier.size(11.dp).align(Alignment.BottomEnd))
                    }
                }
            }
            val replyCount = comment.replyCount
            if (onReplies != null && comment.replies != null && replyCount != 0) {
                Row(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onReplies)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (comment.hasCreatorReply()) Avatar(creatorAvatar, info.uploaderName, 20, Modifier.padding(end = 6.dp))
                    Text(if (replyCount > 0) "$replyCount replies" else "View replies", color = CallToActionBlue, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = CallToActionBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ---------- description ----------

@Composable internal fun DescriptionTeaser(info: StreamInfo, onClick: () -> Unit) {
    val isLive = info.isOngoingLive()
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Text(info.name, color = TextPrimary, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)

        val handle = info.uploaderName?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("@")) it else "@$it" }
        val likes = info.likeCount.takeIf { it > 0 }?.let { "${formatCount(it)} likes" }
        val views = if (isLive) {
            info.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} watching" } ?: "Live"
        } else {
            info.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} views" }
        }
        val time = if (isLive) startedStreamingText(info.uploadDate) else relativeTime(info.uploadDate, info.textualUploadDate)
        val meta = listOfNotNull(handle, likes, views, time)

        Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                meta.joinToString("  "),
                color = Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text("  more", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable internal fun DescriptionPanel(info: StreamInfo, draggable: Boolean, onDismiss: () -> Unit, onLink: (String) -> Unit, onSeek: (Int) -> Unit, onChannel: () -> Unit, modifier: Modifier = Modifier, showHandle: Boolean = true, onChapters: (() -> Unit)? = null) {
    val isLive = info.isOngoingLive()
    EngagementPanel("Description", null, draggable, onDismiss, modifier, showHandle = showHandle) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(info.name, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val date = info.uploadDate?.offsetDateTime()
                val locale = java.util.Locale.US
                DescriptionStat(if (info.likeCount >= 0) fullCount(info.likeCount) else "–", "Likes")
                Box(Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.15f)))
                DescriptionStat(if (info.viewCount >= 0) fullCount(info.viewCount) else "–", if (isLive) "Watching" else "Views")
                Box(Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.15f)))
                // Siaran live tetap punya kolom tanggal (tanggal mulai); teks mentah extractor bisa berupa stempel ISO, jadi tidak dipakai.
                if (date != null) {
                    DescriptionStat(date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", locale)), date.year.toString())
                } else {
                    DescriptionStat(info.textualUploadDate?.takeIf { it.isNotBlank() && !isLive } ?: "–", if (isLive) "Started" else "Uploaded")
                }
            }
            val html = richHtml(info.description)
            if (info.description?.content.isNullOrBlank()) Text("No description.", color = Muted, fontSize = 14.sp)
            else RichText(html, onLink)
            val chapters = info.streamSegments
            if (chapters.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Chapters", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (onChapters != null) PanelPill("View all", onChapters)
                }
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
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = !info.uploaderUrl.isNullOrBlank(), onClick = onChannel), verticalAlignment = Alignment.CenterVertically) {
                Avatar(info.uploaderAvatars.lastOrNull()?.url, info.uploaderName, 56)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(info.uploaderName ?: "Channel", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    info.uploaderSubscriberCount.takeIf { it >= 0 }?.let { Text("${formatCount(it)} subscribers", color = Muted, fontSize = 13.sp) }
                }
            }
            Row(Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelPill("Videos", onChannel)
                PanelPill("About", onChannel)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------- chapters ----------

/** Index of the chapter playing at [positionMs], or -1 before the first one. */
internal fun chapterIndexAt(segments: List<StreamSegment>, positionMs: Long): Int = segments.indexOfLast { it.startTimeSeconds * 1000L <= positionMs }

@Composable internal fun ChaptersPanel(info: StreamInfo, positionMs: () -> Long, draggable: Boolean, onDismiss: () -> Unit, onSeek: (Int) -> Unit, modifier: Modifier = Modifier, showHandle: Boolean = true) {
    val chapters = info.streamSegments
    var current by remember { mutableIntStateOf(chapterIndexAt(chapters, positionMs())) }
    LaunchedEffect(chapters) { while (true) { current = chapterIndexAt(chapters, positionMs()); delay(500) } }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - 1).coerceAtLeast(0))
    EngagementPanel("Chapters", null, draggable, onDismiss, modifier, showHandle = showHandle) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list, contentPadding = PaddingValues(vertical = 8.dp)) {
            itemsIndexed(chapters) { index, chapter ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .background(if (index == current) Surface else Color.Transparent)
                        .clickable { onSeek(chapter.startTimeSeconds) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Surface)) {
                        (chapter.previewUrl ?: info.thumbnails.lastOrNull()?.url)?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(chapter.title?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}", fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(formatSeconds(chapter.startTimeSeconds), color = LinkBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(4.dp)).background(LinkBlue.copy(alpha = .15f)).padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
            }
        }
    }
}

@Composable private fun RowScope.DescriptionStat(value: String, label: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
        Text(label, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
    }
}

// ---------- small pieces ----------

@Composable private fun PanelPill(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, HairlineBorder, RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable internal fun Avatar(url: String?, name: String?, size: Int, modifier: Modifier = Modifier) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier.size(size.dp).clip(RoundedCornerShape(50)).background(Color(0xFF5F5F5F)), contentAlignment = Alignment.Center) {
        if (url == null || failed) Text(name?.trimStart('@')?.firstOrNull()?.uppercase() ?: "", fontSize = (size * .45f).sp, color = Color.White)
        url?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), onError = { failed = true }) }
    }
}

/** Foto profil dari URL yang diberikan; kalau kosong, pakai foto channel-nya (hasilnya di-cache di VideoRepository). */
@Composable internal fun rememberAvatarUrl(suppliedUrl: String?, channelUrl: String?): String? {
    val initial = suppliedUrl?.takeIf { it.isNotBlank() }
        ?: channelUrl?.let { VideoRepository.cachedAvatar(it) }
    var avatar by remember(channelUrl, suppliedUrl) { mutableStateOf(initial) }
    LaunchedEffect(channelUrl, suppliedUrl) {
        if (avatar.isNullOrBlank() && !channelUrl.isNullOrBlank()) {
            val resolved = VideoRepository.channelAvatar(channelUrl)
            if (!resolved.isNullOrBlank()) avatar = resolved
        }
    }
    return avatar
}

@Composable private fun PanelMessage(text: String) { Text(text, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp)) }
@Composable private fun PanelSpinner() { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }
@Composable private fun PanelError(message: String, onRetry: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(message, color = ErrorText, fontSize = 13.sp)
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 44.dp)) {
            Text("Try again", color = LinkBlue)
        }
    }
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
