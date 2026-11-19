package id.flufclip.player

import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import org.schabi.newpipe.extractor.stream.StreamInfoItem

private val Tab1Color = CallToActionBlue
private val Tab2Color = MembershipSelectedTint

@Composable
internal fun DualWatchScreen(
    controller: DualTabController,
    primaryPlayer: ExoPlayer,
    secondaryPlayer: ExoPlayer?,
    maxVideoHeight: Int,
    scope: CoroutineScope,
    onPickVideoForSlot: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val model = controller.model
    val slot1 = model.slot1
    val slot2 = model.slot2

    BackHandler { onClose() }

    LaunchedEffect(primaryPlayer, secondaryPlayer) {
        controller.syncAudio(primaryPlayer, secondaryPlayer)
    }

    val orderedIds = if (model.swappedDisplay) listOf(2, 1) else listOf(1, 2)

    fun playerFor(slotId: Int): ExoPlayer? = if (slotId == 2) secondaryPlayer else primaryPlayer

    fun loadRelated(slotId: Int, item: StreamInfoItem, forceRefresh: Boolean = false) {
        controller.focusSlot(slotId, primaryPlayer, PlaybackSessionHost.currentSecondaryPlayer())
        controller.loadIntoSlot(
            slotId = slotId,
            item = item,
            context = context,
            scope = scope,
            maxVideoHeight = maxVideoHeight,
            primary = primaryPlayer,
            secondaryProvider = { PlaybackSessionHost.obtainSecondaryPlayer(context) },
            forceRefresh = forceRefresh,
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val wide = isWideWatchLayout(maxWidth, maxHeight)
        val containerHeight = maxHeight

        Column(modifier = Modifier.fillMaxSize()) {
            DualWorkspaceBar(
                wide = wide,
                audioPolicy = model.audioPolicy,
                focusedSlot = model.focusedSlot,
                onBack = onClose,
                onSwap = { model.swapSlots() },
                onToggleAudioPolicy = {
                    val next = if (model.audioPolicy == "focus") "mix" else "focus"
                    controller.setAudioPolicy(next, primaryPlayer, PlaybackSessionHost.currentSecondaryPlayer())
                },
                onPickFocused = { onPickVideoForSlot(model.focusedSlot) },
            )

            if (wide) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    orderedIds.forEach { slotId ->
                        key(slotId) {
                            val slot = model.slotOf(slotId)
                            val slotPlayer = playerFor(slotId)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF141416))
                                    .border(
                                        width = if (model.focusedSlot == slotId) 1.5.dp else 1.dp,
                                        color = if (model.focusedSlot == slotId) slotAccent(slotId) else Color(0xFF272727),
                                        shape = RoundedCornerShape(14.dp),
                                    ),
                            ) {
                                DualSlotViewportCard(
                                    slot = slot,
                                    player = slotPlayer,
                                    focused = model.focusedSlot == slotId,
                                    viewportHeight = null,
                                    onFocus = { controller.focusSlot(slotId, primaryPlayer, PlaybackSessionHost.currentSecondaryPlayer()) },
                                    onToggleMute = { controller.toggleAudioForSlot(slotId, primaryPlayer, PlaybackSessionHost.currentSecondaryPlayer()) },
                                    onPickVideo = { onPickVideoForSlot(slotId) },
                                    onRetry = {
                                        slot.item?.let { loadRelated(slotId, it, forceRefresh = true) } ?: onPickVideoForSlot(slotId)
                                    },
                                )
                                DualSlotDetailColumn(
                                    slot = slot,
                                    modifier = Modifier.weight(1f),
                                    onPickVideo = { onPickVideoForSlot(slotId) },
                                    onSelectRelated = { related -> loadRelated(slotId, related) },
                                )
                            }
                        }
                    }
                }
            } else {
                val playerHeight = ((containerHeight - 250.dp) / 2f).coerceIn(144.dp, 200.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A0A0C))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    orderedIds.forEach { slotId ->
                        key(slotId) {
                            val slot = model.slotOf(slotId)
                            val slotPlayer = playerFor(slotId)
                            DualSlotViewportCard(
                                slot = slot,
                                player = slotPlayer,
                                focused = model.focusedSlot == slotId,
                                viewportHeight = playerHeight,
                                onFocus = { controller.focusSlot(slotId, primaryPlayer, PlaybackSessionHost.currentSecondaryPlayer()) },
                                onToggleMute = { controller.toggleAudioForSlot(slotId, primaryPlayer, PlaybackSessionHost.currentSecondaryPlayer()) },
                                onPickVideo = { onPickVideoForSlot(slotId) },
                                onRetry = {
                                    slot.item?.let { loadRelated(slotId, it, forceRefresh = true) } ?: onPickVideoForSlot(slotId)
                                },
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = Surface)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141416))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(1, 2).forEach { slotId ->
                        val selected = model.focusedSlot == slotId
                        val accent = slotAccent(slotId)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) accent.copy(alpha = 0.2f) else Surface)
                                .border(
                                    width = 1.dp,
                                    color = if (selected) accent else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    controller.focusSlot(slotId, primaryPlayer, PlaybackSessionHost.currentSecondaryPlayer())
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "Detail Tab $slotId",
                                color = if (selected) Color.White else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (model.audioPolicy == "mix") "Audio: Mix" else "Audio: Focus",
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                }
                val focused = model.slotOf(model.focusedSlot)
                DualSlotDetailColumn(
                    slot = focused,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    onPickVideo = { onPickVideoForSlot(focused.id) },
                    onSelectRelated = { related -> loadRelated(focused.id, related) },
                )
            }
        }
    }
}

@Composable
private fun DualWorkspaceBar(
    wide: Boolean,
    audioPolicy: String,
    focusedSlot: Int,
    onBack: () -> Unit,
    onSwap: () -> Unit,
    onToggleAudioPolicy: () -> Unit,
    onPickFocused: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF14161B))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
        }
        WorkspaceChip(
            icon = {
                Icon(
                    imageVector = if (wide) Icons.Default.SwapHoriz else Icons.Default.SwapVert,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp),
                )
            },
            label = "Swap",
            highlighted = false,
            onClick = onSwap,
        )
        WorkspaceChip(
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = if (audioPolicy == "mix") Tab1Color else TextPrimary,
                    modifier = Modifier.size(16.dp),
                )
            },
            label = if (audioPolicy == "mix") "Mix Audio" else "Focus Audio",
            highlighted = audioPolicy == "mix",
            onClick = onToggleAudioPolicy,
        )
        Spacer(Modifier.weight(1f))
        WorkspaceChip(
            icon = null,
            label = "Ganti Tab $focusedSlot",
            highlighted = false,
            onClick = onPickFocused,
        )
    }
}

@Composable
private fun WorkspaceChip(
    icon: (@Composable () -> Unit)?,
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) Tab1Color.copy(alpha = 0.18f) else Color(0xFF22252D))
            .border(
                width = 1.dp,
                color = if (highlighted) Tab1Color else Color(0xFF343844),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        icon?.invoke()
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun DualSlotViewportCard(
    slot: DualTabSlotState,
    player: ExoPlayer?,
    focused: Boolean,
    viewportHeight: Dp?,
    onFocus: () -> Unit,
    onToggleMute: () -> Unit,
    onPickVideo: () -> Unit,
    onRetry: () -> Unit,
) {
    val accent = slotAccent(slot.id)
    val cardModifier = if (viewportHeight != null) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF141416))
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) accent else Surface,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onFocus)
    } else {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onFocus)
    }

    Column(modifier = cardModifier) {
        // Mini slot header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1D22))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "TAB ${slot.id}",
                color = Color(0xFF06131C),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(accent)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Text(
                text = slot.info?.name ?: slot.item?.name ?: "Slot kosong — pilih video",
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Ganti",
                color = TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Surface)
                    .clickable(onClick = onPickVideo)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        val viewportModifier = if (viewportHeight != null) {
            Modifier.fillMaxWidth().height(viewportHeight).background(Color.Black)
        } else {
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
        }

        Box(modifier = viewportModifier) {
            val hasMedia = slot.info != null || slot.item != null
            if (hasMedia && player != null) {
                var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
                var playWhenReady by remember(player) { mutableStateOf(player.playWhenReady) }
                var playbackState by remember(player) { mutableIntStateOf(player.playbackState) }
                var positionMs by remember(player) { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
                var durationMs by remember(player) { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }

                DisposableEffect(player) {
                    val listener = object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                        }

                        override fun onPlayWhenReadyChanged(pwr: Boolean, reason: Int) {
                            playWhenReady = pwr
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            playbackState = state
                            durationMs = player.duration.coerceAtLeast(0L)
                        }
                    }
                    player.addListener(listener)
                    isPlaying = player.isPlaying
                    playWhenReady = player.playWhenReady
                    playbackState = player.playbackState
                    durationMs = player.duration.coerceAtLeast(0L)
                    onDispose { player.removeListener(listener) }
                }

                LaunchedEffect(player, isPlaying, playWhenReady) {
                    while (true) {
                        positionMs = player.currentPosition.coerceAtLeast(0L)
                        durationMs = player.duration.coerceAtLeast(0L)
                        delay(if (isPlaying || playWhenReady) 500L else 1000L)
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        LayoutInflater.from(ctx).inflate(R.layout.floating_player, null) as PlayerView
                    },
                    update = { view ->
                        view.useController = false
                        if (view.player !== player) view.player = player
                    },
                    onRelease = { view ->
                        view.player = null
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Overlay controls bar at bottom
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            ),
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val isLive = slot.info?.isOngoingLive() == true
                        val fraction = if (isLive || durationMs <= 0L) 1f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            color = if (isLive) Accent else accent,
                            trackColor = Color.White.copy(alpha = 0.25f),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = if (isLive) "LIVE" else "${formatSlotTime(positionMs)} / ${formatSlotTime(durationMs)}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val showPause = playWhenReady && playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE
                                SlotMiniOverlayButton(
                                    icon = if (showPause) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    label = if (showPause) "Pause" else "Play",
                                    mutedStyle = false,
                                    onClick = {
                                        onFocus()
                                        when {
                                            player.playbackState == Player.STATE_ENDED -> {
                                                player.seekTo(0L)
                                                player.play()
                                            }
                                            player.mediaItemCount == 0 && slot.item != null -> onRetry()
                                            player.playWhenReady -> player.pause()
                                            else -> player.play()
                                        }
                                    },
                                )
                                SlotMiniOverlayButton(
                                    icon = if (slot.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    label = if (slot.muted) "Muted" else "Audio",
                                    mutedStyle = slot.muted,
                                    onClick = onToggleMute,
                                )
                            }
                        }
                    }
                }
            } else if (!hasMedia) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onPickVideo)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Tab ${slot.id} masih kosong",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = onPickVideo,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color(0xFF06131C),
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text("Pilih Video", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (slot.loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = accent, modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                }
            }

            if (slot.error != null && !slot.loading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = slot.error,
                        color = ErrorText,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Coba lagi",
                        color = CallToActionBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onRetry),
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotMiniOverlayButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    mutedStyle: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (mutedStyle) Accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(13.dp))
        Text(text = label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DualSlotDetailColumn(
    slot: DualTabSlotState,
    modifier: Modifier = Modifier,
    onPickVideo: () -> Unit,
    onSelectRelated: (StreamInfoItem) -> Unit,
) {
    val info = slot.info
    val related = remember(info?.url) {
        info?.relatedItems
            ?.filterIsInstance<StreamInfoItem>()
            ?.filter { it.url != info.url && !it.isShortFormContent }
            ?.distinctBy { it.url }
            ?.take(10)
            .orEmpty()
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = info?.name ?: slot.item?.name ?: "Belum ada video di Tab ${slot.id}",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val uploader = info?.uploaderName ?: slot.item?.uploaderName
                if (!uploader.isNullOrBlank()) {
                    Text(
                        text = uploader,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ganti video Tab ${slot.id}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Surface)
                            .clickable(onClick = onPickVideo)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        if (related.isNotEmpty()) {
            item {
                Text(
                    text = "Up Next untuk Tab ${slot.id}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(related, key = { "${slot.id}:${it.url}" }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1B1B1E))
                        .clickable { onSelectRelated(item) }
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = item.thumbnails.maxByOrNull { it.width }?.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(96.dp)
                            .height(54.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(SurfaceElevated),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name.orEmpty(),
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!item.uploaderName.isNullOrBlank()) {
                            Text(
                                text = item.uploaderName,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun slotAccent(slotId: Int): Color = if (slotId == 2) Tab2Color else Tab1Color

private fun formatSlotTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
