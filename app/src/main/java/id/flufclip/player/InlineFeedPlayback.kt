package id.flufclip.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaptionDisabled
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.schabi.newpipe.extractor.stream.Frameset
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import java.util.Locale
import kotlin.math.abs

private val SUPPORTED_SUBTITLE_SUFFIXES = setOf("vtt", "ttml", "srt")

internal fun firstSupportedSubtitle(info: StreamInfo?): SubtitlesStream? =
    info?.subtitles?.firstOrNull {
        it.isUrl && it.format?.suffix?.lowercase() in SUPPORTED_SUBTITLE_SUFFIXES
    }

/** Home feed autoplays regular videos and livestreams inline; Shorts and playlists stay cards. */
internal fun isInlineFeedCandidate(item: StreamInfoItem): Boolean {
    if (item.isShortFormContent) return false
    val meta = HomeFeed.metaOf(item.url)
    if (meta?.isPlaylist == true) return false
    return true
}

internal data class InlineTimelineState(
    val isLive: Boolean,
    val isDvr: Boolean,
    val canSeek: Boolean,
    val showScrubber: Boolean,
    val behindLive: Boolean,
    val barPositionMs: Long,
    val barBufferedMs: Long,
    val barDurationMs: Long,
)

/**
 * Mirrors YouTube's VOD / LIVE / LIVE_DVR inline timebar modes:
 * non-DVR livestreams hide the scrubber completely, while DVR livestreams expose the seekable live window.
 */
internal fun computeInlineTimelineState(
    isLive: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    windowDurationMs: Long,
    windowDefaultPositionMs: Long,
    isSeekable: Boolean,
    liveOffsetMs: Long,
    targetOffsetMs: Long,
): InlineTimelineState {
    val safePos = positionMs.coerceAtLeast(0L)
    val safeDur = durationMs.coerceAtLeast(0L)
    val safeBuffered = bufferedMs.coerceAtLeast(0L)
    if (!isLive) {
        val showBar = safeDur > 0L
        return InlineTimelineState(
            isLive = false,
            isDvr = false,
            canSeek = isSeekable && showBar,
            showScrubber = showBar,
            behindLive = false,
            barPositionMs = safePos.coerceIn(0L, safeDur),
            barBufferedMs = safeBuffered.coerceIn(0L, safeDur),
            barDurationMs = safeDur,
        )
    }
    val liveBase = windowDurationMs.takeIf { it > 0L } ?: safeDur
    val seekEndMs = if (liveBase > 0L) {
        windowDefaultPositionMs.takeIf { it > 0L }?.coerceAtMost(liveBase) ?: liveBase
    } else 0L
    val isDvr = isSeekable && seekEndMs >= 1_000L
    val safeTarget = targetOffsetMs.takeIf { it > 0L } ?: 10_000L
    val behindLive = liveOffsetMs >= 0L && liveOffsetMs > safeTarget + 5_000L
    if (!isDvr) {
        return InlineTimelineState(
            isLive = true,
            isDvr = false,
            canSeek = false,
            showScrubber = false,
            behindLive = behindLive,
            barPositionMs = 0L,
            barBufferedMs = 0L,
            barDurationMs = 0L,
        )
    }
    val clampedPos = if (!behindLive && liveOffsetMs >= 0L) seekEndMs else safePos.coerceIn(0L, seekEndMs)
    return InlineTimelineState(
        isLive = true,
        isDvr = true,
        canSeek = true,
        showScrubber = true,
        behindLive = behindLive,
        barPositionMs = clampedPos,
        barBufferedMs = safeBuffered.coerceIn(0L, seekEndMs),
        barDurationMs = seekEndMs,
    )
}

internal fun formatInlineTime(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

internal fun formatInlineScrubLabel(targetMs: Long, timeline: InlineTimelineState): String {
    if (!timeline.isLive) {
        return "${formatInlineTime(targetMs)} / ${formatInlineTime(timeline.barDurationMs)}"
    }
    val offsetBehindEdgeMs = (timeline.barDurationMs - targetMs).coerceAtLeast(0L)
    return if (offsetBehindEdgeMs < 2_000L) "LIVE" else "-${formatInlineTime(offsetBehindEdgeMs)}"
}

@Composable
internal fun BoxScope.InlinePlayerOverlay(
    player: ExoPlayer,
    isReady: Boolean,
    isLive: Boolean,
    muted: Boolean,
    hasCaptions: Boolean,
    captionsEnabled: Boolean,
    previewFrames: List<Frameset> = emptyList(),
    onToggleMute: () -> Unit,
    onToggleCaptions: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekToLiveEdge: () -> Unit = { player.seekToDefaultPosition() },
) {
    val liveWindow = remember { Timeline.Window() }
    var timeline by remember(player, isLive) {
        mutableStateOf(
            InlineTimelineState(
                isLive = isLive,
                isDvr = false,
                canSeek = false,
                showScrubber = false,
                behindLive = false,
                barPositionMs = 0L,
                barBufferedMs = 0L,
                barDurationMs = 0L,
            )
        )
    }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(player, isReady, isLive) {
        var previousDuration = 0L
        var lastMediaId: String? = null
        while (true) {
            if (!scrubbing) {
                val mediaId = player.currentMediaItem?.mediaId
                if (mediaId != lastMediaId) {
                    lastMediaId = mediaId
                    previousDuration = 0L
                }
                val measuredDur = player.duration.takeIf { it > 0L && it != C.TIME_UNSET }
                if (measuredDur != null) previousDuration = measuredDur
                val effectiveDur = measuredDur
                    ?: if (!isLive && mediaId != null && player.playbackState == Player.STATE_BUFFERING) previousDuration else 0L

                val currentTimeline = player.currentTimeline
                val index = player.currentMediaItemIndex
                val hasWindow = !currentTimeline.isEmpty && index in 0 until currentTimeline.windowCount
                val winDuration = if (hasWindow) {
                    currentTimeline.getWindow(index, liveWindow).durationMs.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
                } else 0L
                val winDefaultPos = if (hasWindow) {
                    liveWindow.defaultPositionMs.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
                } else 0L
                val targetOffset = if (hasWindow) {
                    liveWindow.liveConfiguration?.targetOffsetMs?.takeIf { it > 0L && it != C.TIME_UNSET } ?: C.TIME_UNSET
                } else C.TIME_UNSET
                val effectiveLive = isLive || player.isCurrentMediaItemLive

                timeline = computeInlineTimelineState(
                    isLive = effectiveLive,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = effectiveDur,
                    bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
                    windowDurationMs = winDuration,
                    windowDefaultPositionMs = winDefaultPos,
                    isSeekable = player.isCurrentMediaItemSeekable,
                    liveOffsetMs = player.currentLiveOffset.let { if (it == C.TIME_UNSET) -1L else it },
                    targetOffsetMs = targetOffset,
                )
            }
            delay(250L)
        }
    }

    if (isReady) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    keepScreenOn = true
                }
            },
            update = { view ->
                if (view.player !== player) view.player = player
            },
            onRelease = { view -> view.player = null },
            modifier = Modifier.fillMaxSize()
        )
    }

    // Top-right inline action pills (Mute/Unmute + CC)
    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasCaptions) {
            InlineControlPill(
                label = if (captionsEnabled) "Disable captions" else "Enable captions",
                active = captionsEnabled,
                onClick = onToggleCaptions
            ) {
                Icon(
                    imageVector = if (captionsEnabled) Icons.Default.ClosedCaption else Icons.Outlined.ClosedCaptionDisabled,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        InlineControlPill(
            label = if (muted) "Unmute inline video" else "Mute inline video",
            active = !muted,
            onClick = onToggleMute
        ) {
            Icon(
                imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    // LIVE status pill while playing a livestream inline; tap returns to live edge if behind live DVR window.
    if (timeline.isLive && !scrubbing) {
        val jumpEnabled = timeline.behindLive && timeline.canSeek
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = if (timeline.showScrubber) 14.dp else 8.dp)
                .then(
                    if (jumpEnabled) {
                        Modifier
                            .size(width = 64.dp, height = 44.dp)
                            .clickable(role = Role.Button, onClick = onSeekToLiveEdge)
                    } else Modifier
                ),
            contentAlignment = Alignment.BottomEnd
        ) {
            Row(
                modifier = Modifier
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (jumpEnabled) "Behind live edge, tap to jump to live" else "Live"
                    }
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (timeline.behindLive) Color.White.copy(alpha = 0.7f) else Color(0xFFFF0033))
                )
                Text(
                    text = "LIVE",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    )
                )
            }
        }
    }

    // Storyboard frame (when available for VOD) + timestamp pill anchored above the finger while scrubbing.
    val displayPosMs = if (scrubbing && timeline.barDurationMs > 0L) {
        (scrubFraction * timeline.barDurationMs).toLong().coerceIn(0L, timeline.barDurationMs)
    } else {
        timeline.barPositionMs
    }
    if (scrubbing && timeline.barDurationMs > 0L) {
        val frameset = remember(previewFrames, timeline.isLive) {
            if (timeline.isLive) null else selectScrubFrameset(previewFrames)
        }
        val frameUrls = remember(frameset, displayPosMs) {
            frameset?.let { set ->
                runCatching { set.getFrameBoundsAt(displayPosMs) }.getOrNull()?.let { bounds ->
                    listOfNotNull(set.urls.getOrNull(bounds[0]))
                }
            }.orEmpty()
        }
        val sheets = if (frameUrls.isNotEmpty()) rememberStoryboardSheets(frameUrls) else emptyMap()
        val labelText = formatInlineScrubLabel(displayPosMs, timeline)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val aspect = if (frameset != null && frameset.frameWidth > 0) {
                frameset.frameHeight.toFloat() / frameset.frameWidth
            } else 9f / 16f
            val previewW = minOf(128.dp, maxWidth * 0.36f)
            val previewH = previewW * aspect
            val showThumb = frameset != null && (previewH + 44.dp) < maxHeight
            val itemW = if (showThumb) previewW else 96.dp
            val xOffset = (maxWidth * scrubFraction - itemW / 2)
                .coerceIn(8.dp, (maxWidth - itemW - 8.dp).coerceAtLeast(8.dp))
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = xOffset)
                    .padding(bottom = 18.dp)
                    .width(itemW),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showThumb) {
                    StoryboardFrame(
                        frameset = frameset,
                        position = displayPosMs,
                        sheets = sheets,
                        modifier = Modifier
                            .size(previewW, previewH)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.5.dp, Color.White, RoundedCornerShape(6.dp))
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.80f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = labelText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
    }

    // Bottom interactive scrubber bar (visible for VOD and LIVE_DVR; excluded from Android edge-back gesture).
    if (timeline.showScrubber && timeline.barDurationMs > 0L) {
        val progress = if (scrubbing) scrubFraction else {
            (timeline.barPositionMs.toFloat() / timeline.barDurationMs.toFloat()).coerceIn(0f, 1f)
        }
        val bufferedProgress = (timeline.barBufferedMs.toFloat() / timeline.barDurationMs.toFloat()).coerceIn(0f, 1f)
        fun commitFraction(fraction: Float) {
            scrubbing = false
            val target = (fraction * timeline.barDurationMs).toLong().coerceIn(0L, timeline.barDurationMs)
            if (timeline.isLive && target >= (timeline.barDurationMs - 750L).coerceAtLeast(0L)) {
                onSeekToLiveEdge()
            } else {
                onSeekTo(target)
            }
        }
        InlineScrubberBar(
            progress = progress,
            bufferedProgress = bufferedProgress,
            enabled = timeline.canSeek,
            scrubbing = scrubbing,
            onScrubStart = { fraction ->
                scrubbing = true
                scrubFraction = fraction
            },
            onScrubMove = { fraction ->
                scrubFraction = fraction
            },
            onScrubEnd = ::commitFraction,
            onScrubCancel = { scrubbing = false },
            onTapSeek = ::commitFraction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun InlineControlPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
                stateDescription = if (active) "Active" else "Inactive"
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (active) Color.Black.copy(alpha = 0.78f) else Color.Black.copy(alpha = 0.56f)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
    }
}

@Composable
private fun InlineScrubberBar(
    progress: Float,
    bufferedProgress: Float,
    enabled: Boolean,
    scrubbing: Boolean,
    onScrubStart: (Float) -> Unit,
    onScrubMove: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    onScrubCancel: () -> Unit,
    onTapSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var currentFraction by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    if (!scrubbing) currentFraction = progress.coerceIn(0f, 1f)

    val latestScrubStart by rememberUpdatedState(onScrubStart)
    val latestScrubMove by rememberUpdatedState(onScrubMove)
    val latestScrubEnd by rememberUpdatedState(onScrubEnd)
    val latestScrubCancel by rememberUpdatedState(onScrubCancel)
    val latestTapSeek by rememberUpdatedState(onTapSeek)
    val latestFraction by rememberUpdatedState(currentFraction)

    val barHeightDp by animateDpAsState(
        targetValue = if (scrubbing) 4.5.dp else 2.5.dp,
        animationSpec = tween(durationMillis = 120),
        label = "inlineBarHeight"
    )
    val thumbRadiusDp by animateDpAsState(
        targetValue = if (scrubbing) 7.dp else 0.dp,
        animationSpec = tween(durationMillis = 120),
        label = "inlineThumbRadius"
    )

    Box(
        modifier = modifier
            .height(48.dp)
            .systemGestureExclusion()
            .then(
                if (enabled) {
                    Modifier.semantics {
                        contentDescription = "Video progress"
                        progressBarRangeInfo = ProgressBarRangeInfo(currentFraction.coerceIn(0f, 1f), 0f..1f)
                        setProgress { targetFraction ->
                            val clamped = targetFraction.coerceIn(0f, 1f)
                            currentFraction = clamped
                            latestTapSeek(clamped)
                            true
                        }
                    }
                } else Modifier
            )
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = widthPx
                    val thumbX = w * latestFraction
                    val nearThumb = abs(down.position.x - thumbX) <= 22.dp.toPx()
                    val baseSlop = viewConfiguration.touchSlop
                    val horizontalSlop = if (nearThumb) baseSlop * 0.45f else baseSlop
                    val fineThresholdPx = 40.dp.toPx()

                    var activeScrub = false
                    var moved = false
                    var released = false
                    var fraction = (down.position.x / w).coerceIn(0f, 1f)
                    var lastX = down.position.x

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                released = true
                                change.consume()
                                break
                            }
                            if (change.isConsumed) break
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (!activeScrub) {
                                if (abs(dx) > baseSlop || abs(dy) > baseSlop) moved = true
                                if (abs(dx) > horizontalSlop && abs(dx) >= abs(dy)) {
                                    activeScrub = true
                                    moved = true
                                    fraction = (change.position.x / w).coerceIn(0f, 1f)
                                    lastX = change.position.x
                                    currentFraction = fraction
                                    change.consume()
                                    latestScrubStart(fraction)
                                } else if (moved && abs(dy) > abs(dx)) {
                                    // Vertical gesture wins so LazyVerticalGrid scrolls normally.
                                    break
                                }
                            } else {
                                change.consume()
                                val fine = down.position.y - change.position.y > fineThresholdPx
                                fraction = if (fine) {
                                    (fraction + ((change.position.x - lastX) / w) * 0.2f).coerceIn(0f, 1f)
                                } else {
                                    (change.position.x / w).coerceIn(0f, 1f)
                                }
                                lastX = change.position.x
                                currentFraction = fraction
                                latestScrubMove(fraction)
                            }
                        }
                    } finally {
                        if (activeScrub && !released) {
                            latestScrubCancel()
                        }
                    }
                    if (activeScrub && released) {
                        latestScrubEnd(fraction)
                    } else if (released && !moved) {
                        val tapFraction = (down.position.x / w).coerceIn(0f, 1f)
                        currentFraction = tapFraction
                        latestTapSeek(tapFraction)
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
        ) {
            val barHeight = barHeightDp.toPx()
            val topY = size.height - barHeight
            val corner = CornerRadius(barHeight / 2f, barHeight / 2f)
            val filledWidth = size.width * currentFraction.coerceIn(0f, 1f)
            val bufferedWidth = size.width * bufferedProgress.coerceIn(0f, 1f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.25f),
                topLeft = Offset(0f, topY),
                size = Size(size.width, barHeight),
                cornerRadius = corner
            )
            if (bufferedWidth > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.55f),
                    topLeft = Offset(0f, topY),
                    size = Size(bufferedWidth, barHeight),
                    cornerRadius = corner
                )
            }
            if (filledWidth > 0f) {
                drawRoundRect(
                    color = Accent,
                    topLeft = Offset(0f, topY),
                    size = Size(filledWidth, barHeight),
                    cornerRadius = corner
                )
            }
            val thumbRadius = thumbRadiusDp.toPx()
            if (thumbRadius > 0.5f) {
                val centerX = filledWidth.coerceIn(thumbRadius, (size.width - thumbRadius).coerceAtLeast(thumbRadius))
                drawCircle(
                    color = Accent,
                    radius = thumbRadius,
                    center = Offset(centerX, topY + barHeight / 2f)
                )
            }
        }
    }
}
