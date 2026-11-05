package id.flufclip.player

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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.BehindLiveWindowException
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
import org.schabi.newpipe.extractor.stream.DeliveryMethod

@Composable internal fun VideoDetails(info: StreamInfo, comments: CommentsState, chatState: LiveChatState, error: String?, onDownload: () -> Unit, onChannel: () -> Unit, onComments: () -> Unit, onDescription: () -> Unit, onShare: () -> Unit, onRemix: () -> Unit, onClip: () -> Unit, onCopy: () -> Unit, onChat: () -> Unit, isLive: Boolean, engagement: WatchEngagement, downloaded: Boolean = false, askAvailable: Boolean = false, onAsk: () -> Unit = {}, onChapters: (() -> Unit)? = null, wide: Boolean = false, chatOpen: Boolean = false) {
    val isPostLive = info.streamType == org.schabi.newpipe.extractor.stream.StreamType.POST_LIVE_STREAM ||
        info.streamType == org.schabi.newpipe.extractor.stream.StreamType.POST_LIVE_AUDIO_STREAM
    val showReplayChat = !isLive && (chatState.hasChat || (isPostLive && chatState.error == null))
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        DescriptionTeaser(info, onDescription)

        // YouTube Modern: Baris tunggal terpadu (Avatar + Subscribe di kiri, Action Icons di kanan)
        val channelAvailable = !info.uploaderUrl.isNullOrBlank()
        val channelAvatar = rememberAvatarUrl(info.uploaderAvatars.lastOrNull()?.url, info.uploaderUrl)
        val downloadContext = LocalContext.current
        val localRevision by SavedDownloads.revision.collectAsState()
        val localCopy = remember(info.url, localRevision) { SavedDownloads.list(downloadContext).firstOrNull { it.video.sourceUrl.isNotBlank() && videoKey(it.video.sourceUrl) == videoKey(info.url) } }
        val isDownloaded = downloaded || localCopy != null
        val activeDownload by DownloadStatus.state.collectAsState()
        val isDownloading = activeDownload.running && activeDownload.video?.sourceUrl?.takeIf { it.isNotBlank() }?.let { videoKey(it) == videoKey(info.url) } == true

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sisi Kiri: Avatar + Subscribe (Tanpa weight agar teks Subscribe tidak pernah terpotong)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.clip(CircleShape).clickable(enabled = channelAvailable, onClick = onChannel)) {
                    Avatar(channelAvatar, info.uploaderName, 32)
                }
                Spacer(Modifier.width(8.dp))
                SubscribeButton(
                    subscribed = engagement.subscribed,
                    onSubscribe = engagement.onSubscribe,
                    onUnsubscribe = engagement.onUnsubscribe,
                    uncertain = engagement.subscriptionUncertain,
                    channelName = info.uploaderName,
                    notificationPreference = engagement.notificationPreference,
                    onNotificationPreference = engagement.onNotificationPreference
                )
            }

            Spacer(Modifier.weight(1f))

            // Sisi Kanan: Action Icons (Like, Dislike, Share, Ask, More)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 1. Like with Particle Burst Animation
                AnimatedLikeButton(
                    liked = engagement.rating == 1,
                    onLike = engagement.onLike
                )

                // 2. Dislike
                IconButton(onClick = engagement.onDislike, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(if (engagement.rating == -1) R.drawable.yt_w_dislike_on else R.drawable.yt_w_dislike),
                        contentDescription = "Dislike",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 3. Share
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.yt_w_share),
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 4. Ask (bila tersedia)
                if (askAvailable) {
                    IconButton(onClick = onAsk, modifier = Modifier.size(36.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.yt_w_spark),
                            contentDescription = "Ask",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 5. More actions (••• horizontal) -> Membuka Bottom Sheet persis aplikasi YouTube
                var showMoreSheet by remember { mutableStateOf(false) }
                IconButton(onClick = { showMoreSheet = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.yt_reel_more),
                        contentDescription = "More actions",
                        tint = Color.White,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = 90f }
                    )
                }
                if (showMoreSheet) {
                    WatchMoreBottomSheet(
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading,
                        saved = engagement.saved,
                        duration = info.duration,
                        isLive = isLive,
                        onDownload = {
                            if (isDownloading) SavedDownloads.open(downloadContext)
                            else if (!downloaded && localCopy != null) SavedDownloads.open(downloadContext, localCopy)
                            else onDownload()
                        },
                        onSave = engagement.onSave,
                        onCopy = onCopy,
                        onClip = onClip,
                        onDismiss = { showMoreSheet = false }
                    )
                }
            }
        }
        if (isLive) {
            Box(Modifier.padding(top = 16.dp)) {
                LiveChatTeaser(chatState, isLive = true, onClick = onChat)
            }
        } else {
            val openComments = { if (comments.error != null && comments.info == null) comments.retry(); onComments() }
            WatchMetadataCarousel(
                comments = comments,
                chapters = info.streamSegments,
                chatState = chatState,
                showReplayChat = showReplayChat,
                thumbnailUrl = info.thumbnails.lastOrNull()?.url,
                onComments = openComments,
                onChapters = { onChapters?.invoke() ?: onDescription() },
                onChat = onChat,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WatchMoreBottomSheet(
    isDownloaded: Boolean,
    isDownloading: Boolean,
    saved: Boolean,
    duration: Long,
    isLive: Boolean,
    onDownload: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onClip: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF272727),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF717171))
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            WatchSheetOption(
                icon = painterResource(if (saved) R.drawable.yt_w_save_on else R.drawable.yt_w_save),
                label = if (saved) "Saved to playlist" else "Save",
                onClick = { onDismiss(); onSave() }
            )
            WatchSheetOption(
                icon = painterResource(R.drawable.yt_w_download),
                label = if (isDownloading) "Downloading..." else if (isDownloaded) "Downloaded" else "Download",
                onClick = { onDismiss(); onDownload() }
            )
            WatchSheetOption(
                icon = painterResource(R.drawable.yt_w_copy),
                label = "Copy link",
                onClick = { onDismiss(); onCopy() }
            )
            WatchSheetOption(
                icon = painterResource(R.drawable.yt_reel_more),
                label = "Report",
                onClick = { onDismiss() }
            )
        }
    }
}

@Composable internal fun WatchSheetOption(
    icon: androidx.compose.ui.graphics.painter.Painter,
    label: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Status dan aksi akun di layar tonton: suka/tidak suka, simpan ke "Watch later", berlangganan. Semua perlu login. */
internal class WatchEngagement(
    val rating: Int, val saved: Boolean, val subscribed: Boolean,
    val onLike: () -> Unit, val onDislike: () -> Unit, val onSave: () -> Unit, val onSubscribe: () -> Unit, val onUnsubscribe: () -> Unit,
    val likeDelta: Int = 0, val ratingUncertain: Boolean = false, val subscriptionUncertain: Boolean = false,
    val notificationPreference: NotificationPreference = NotificationPreference.PERSONALIZED,
    val onNotificationPreference: (NotificationPreference) -> Unit = {},
)

@Composable internal fun WatchAction(icon: String, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .height(32.dp)
            .raisedWatchAction(interaction = interaction)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionGlyph(icon, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable internal fun WatchChip(icon: Int, label: String, onClick: () -> Unit, selected: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .height(34.dp)
            .raisedWatchAction(selected = selected, interaction = interaction)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(icon), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(Color.White))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/**
 * Tombol Like dengan animasi spring bounce dan particle burst seperti di YouTube (LKE_20250617 / LikeAnimation Lottie):
 * 1. Saat diklik (Like aktif): Jempol sedikit menyusut (0.85x), lalu membal membesar elastis (1.3x) dan kembali ke 1.0x.
 * 2. Meledakkan 5 partikel bintang/sparkle radial keluar di sekitar ikon.
 * 3. Mengubah ikon dari outline menjadi solid putih.
 */
@Composable internal fun AnimatedLikeButton(
    liked: Boolean,
    onLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    val rotation = remember { androidx.compose.animation.core.Animatable(0f) }
    val burstProgress = remember { androidx.compose.animation.core.Animatable(0f) }

    fun triggerLikeAnimation() {
        coroutineScope.launch {
            launch {
                rotation.animateTo(-8f, androidx.compose.animation.core.tween(60))
                rotation.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 600f))
            }
            launch {
                scale.animateTo(0.85f, androidx.compose.animation.core.tween(60))
                scale.animateTo(1.3f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 800f))
                scale.animateTo(1.0f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f))
            }
            launch {
                burstProgress.snapTo(0f)
                burstProgress.animateTo(1f, androidx.compose.animation.core.tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            }
        }
    }

    Box(
        modifier = modifier.size(36.dp),
        contentAlignment = Alignment.Center
    ) {
        val progress = burstProgress.value
        if (progress > 0f && progress < 1f) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.width * 0.35f
                val travelDistance = size.width * 0.35f * progress
                val currentRadius = baseRadius + travelDistance
                val particleAlpha = (1f - progress).coerceIn(0f, 1f)
                val particleSize = 3.dp.toPx() * (1f - progress * 0.5f)

                for (i in 0 until 5) {
                    val angleDeg = i * 72f - 90f
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val x = center.x + (currentRadius * kotlin.math.cos(angleRad)).toFloat()
                    val y = center.y + (currentRadius * kotlin.math.sin(angleRad)).toFloat()

                    drawCircle(
                        color = Color.White.copy(alpha = particleAlpha),
                        radius = particleSize / 2f,
                        center = Offset(x, y)
                    )
                }
            }
        }

        IconButton(
            onClick = {
                if (!liked) triggerLikeAnimation()
                onLike()
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(if (liked) R.drawable.yt_w_like_on else R.drawable.yt_w_like),
                contentDescription = "Like",
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        rotationZ = rotation.value
                    }
            )
        }
    }
}

/** Pil gabungan suka | tidak suka dengan pemisah tipis, seperti di YouTube (tinggi 34 dp, mikro-animasi bounce). */
@Composable internal fun LikeDislikePill(likeLabel: String, rating: Int, onLike: () -> Unit, onDislike: () -> Unit, enabled: Boolean = true) {
    val likeScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (rating == 1) 1.25f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "likeBounce"
    )
    val dislikeScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (rating == -1) 1.25f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "dislikeBounce"
    )

    val ratingInteraction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .height(34.dp)
            .raisedWatchAction(selected = rating != 0, interaction = ratingInteraction),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .clickable(enabled = enabled, interactionSource = ratingInteraction, indication = LocalIndication.current, onClick = onLike)
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painterResource(if (rating == 1) R.drawable.yt_w_like_on else R.drawable.yt_w_like),
                "Like",
                Modifier
                    .size(18.dp)
                    .graphicsLayer(scaleX = likeScale, scaleY = likeScale)
            )
            Text(
                likeLabel,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = if (rating == 1) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        Box(
            Modifier
                .width(1.dp)
                .height(18.dp)
                .background(Color.White.copy(alpha = .20f))
        )
        Box(
            Modifier
                .fillMaxHeight()
                .clickable(enabled = enabled, interactionSource = ratingInteraction, indication = LocalIndication.current, onClick = onDislike)
                .padding(start = 10.dp, end = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painterResource(if (rating == -1) R.drawable.yt_w_dislike_on else R.drawable.yt_w_dislike),
                "Dislike",
                Modifier
                    .size(18.dp)
                    .graphicsLayer(scaleX = dislikeScale, scaleY = dislikeScale)
            )
        }
    }
}

@Composable internal fun ActionGlyph(icon: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val stroke = 1.8.dp.toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(Color.White, Offset(w*x1,h*y1), Offset(w*x2,h*y2), strokeWidth = stroke, cap = StrokeCap.Round)
        when (icon) {
            "chat" -> {
                drawRoundRect(Color.White, topLeft = Offset(w*.12f, h*.16f), size = androidx.compose.ui.geometry.Size(w*.76f, h*.54f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w*.14f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                line(.28f, .70f, .20f, .88f); line(.20f, .88f, .44f, .70f)
                line(.28f, .36f, .72f, .36f); line(.28f, .50f, .58f, .50f)
            }
            "share" -> { line(.27f,.50f,.72f,.25f); line(.27f,.50f,.72f,.75f); listOf(Offset(.23f,.50f), Offset(.76f,.23f), Offset(.76f,.77f)).forEach { drawCircle(Color.White, radius = w*.10f, center = Offset(it.x*w,it.y*h)) } }
            "download" -> { line(.5f,.12f,.5f,.68f); line(.27f,.47f,.5f,.70f); line(.73f,.47f,.5f,.70f); line(.18f,.84f,.82f,.84f) }
            else -> { drawRoundRect(Color.White, topLeft = Offset(w*.31f,h*.28f), size = androidx.compose.ui.geometry.Size(w*.52f,h*.58f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w*.06f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)); line(.19f,.66f,.19f,.14f); line(.19f,.14f,.69f,.14f) }
        }
    }
}

