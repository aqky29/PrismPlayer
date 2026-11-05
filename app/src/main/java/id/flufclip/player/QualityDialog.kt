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

internal fun audioTracks(info: StreamInfo): List<AudioStream> = VideoRepository.audios(info)
    .distinctBy(::audioTrackIdentity)

/** BCP 47 tag of [audio]'s track (e.g. "id" for an Indonesian original), as HLS `LANGUAGE` uses it. */
internal fun audioLanguage(audio: AudioStream?): String? = audioLanguageTag(audio)

internal fun audioLabel(audio: AudioStream): String {
    val name = audio.audioTrackName?.takeIf { it.isNotBlank() }
        ?: audio.audioLocale?.getDisplayName(java.util.Locale.ENGLISH)?.takeIf { it.isNotBlank() }
        ?: "Audio default"
    val type = when (audio.audioTrackType) {
        AudioTrackType.ORIGINAL -> "Asli"
        AudioTrackType.DUBBED -> "Dub"
        AudioTrackType.DESCRIPTIVE -> "Audio description"
        AudioTrackType.SECONDARY -> "Additional audio"
        else -> null
    }
    return if (type != null && !name.contains(type, ignoreCase = true)) "$name · $type" else name
}

internal fun embeddedAudioLabel(metadata: AudioTrackMetadata): String {
    val name = metadata.name?.takeIf { it.isNotBlank() }
        ?: metadata.locale?.getDisplayName(java.util.Locale.ENGLISH)?.takeIf { it.isNotBlank() }
        ?: "Audio default"
    val type = when (metadata.type) {
        AudioTrackType.ORIGINAL -> "Asli"
        AudioTrackType.DUBBED -> "Dub"
        AudioTrackType.DESCRIPTIVE -> "Audio description"
        AudioTrackType.SECONDARY -> "Additional audio"
        else -> null
    }
    return if (type != null && !name.contains(type, ignoreCase = true)) "$name · $type" else name
}

internal fun qualityOptions(info: StreamInfo, embeddedKey: String? = null): List<VideoStream> {
    val separate = if (VideoRepository.audios(info).isNotEmpty()) VideoRepository.videos(info) else emptyList()
    val allProgressive = VideoRepository.progressive(info)
    val embedded = VideoRepository.embeddedAudio(info)
    val key = embeddedKey ?: selectEmbeddedAudio(embedded, PlayerSettings.audioPreference, java.util.Locale.getDefault())?.metadata?.id
    val preferredProgressive = if (key == null) allProgressive else allProgressive.filter { VideoRepository.embeddedAudio(info, it)?.id == key }
    return (separate + preferredProgressive.ifEmpty { allProgressive })
        .sortedWith(compareByDescending<VideoStream> { it.height.takeIf { height -> height > 0 } ?: (it.resolution.filter(Char::isDigit).toIntOrNull() ?: 0) }.thenByDescending { it.fps })
        .distinctBy { "${it.resolution}-${it.fps}-${it.format?.name}" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun QualityDialog(info: StreamInfo, selected: VideoStream?, selectedAudio: AudioStream?, selectedSubtitle: SubtitlesStream?, autoplay: Boolean, maxHeight: Int, player: ExoPlayer, initialPage: String, fullscreen: Boolean, qualityMode: String,
    useManifestAudio: Boolean, onAudioPreference: (AudioPreference) -> Unit, onSelectManifestAudio: (Tracks.Group, List<Int>) -> Unit, onSelectEmbedded: (AudioTrackMetadata) -> Unit,
    onQualityMode: (String) -> Unit, onLock: () -> Unit, onMaxHeight: (Int) -> Unit, onAutoplay: (Boolean) -> Unit, onSubtitle: (SubtitlesStream?) -> Unit, onDismiss: () -> Unit, onSelect: (VideoStream?) -> Unit, onSelectAudio: (AudioStream) -> Unit) {
    val currentEmbedded = VideoRepository.embeddedAudio(info, selected)
    val embeddedChoices = remember(info) { VideoRepository.embeddedAudio(info).distinctBy { it.metadata.id } }
    val options = remember(info, currentEmbedded?.id, PlayerSettings.audioPreference) { qualityOptions(info, currentEmbedded?.id) }
    var playerTracks by remember(player) { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { playerTracks = tracks }
        }
        player.addListener(listener)
        playerTracks = player.currentTracks
        onDispose { player.removeListener(listener) }
    }
    val hlsTracks = if (selected == null && !info.hlsUrl.isNullOrBlank()) {
        playerTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) && group.getTrackFormat(it).height > 0 }
                .map { index -> group to index }
        }.sortedByDescending { (group, index) -> group.getTrackFormat(index).height }
    } else emptyList()
    val qualityLabel = selected?.resolution ?: if (qualityMode == "advanced") {
        hlsTracks.firstOrNull { (group, index) -> group.isTrackSelected(index) }
            ?.let { (group, index) -> "${group.getTrackFormat(index).height}p" } ?: "Selected"
    } else "Auto"
    val tracks = remember(info) { audioTracks(info) }
    val canSelectAudio = remember(info) { VideoRepository.videos(info).isNotEmpty() }
    val manifestAudio = remember(playerTracks, useManifestAudio) { if (useManifestAudio) manifestAudioChoices(manifestAudioFormats(playerTracks)) else emptyList() }
    val hasAudioChoices = manifestAudio.size > 1 || embeddedChoices.size > 1 || tracks.size > 1 && canSelectAudio
    val activeAudioLabel = currentEmbedded?.let(::embeddedAudioLabel) ?: manifestAudio.firstOrNull { it.selected }?.label ?: selectedAudio?.let(::audioLabel) ?: "Default"
    var page by remember(info.url, initialPage) { mutableStateOf(initialPage) }
    var speed by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    val configuration = LocalConfiguration.current
    val listHeight = (configuration.screenHeightDp - 140).coerceIn(120, 380).dp
    val wide = configuration.screenWidthDp >= 840 || (configuration.screenWidthDp >= 600 && configuration.screenWidthDp > configuration.screenHeightDp)
    if (wide && page == "menu") {
        val density = LocalDensity.current
        val screenWidth = configuration.screenWidthDp.dp
        val playerWidth = if (fullscreen) screenWidth else wideWatchPlayerWidth(screenWidth)
        val menuWidth = minOf(380.dp, playerWidth - 24.dp)
        val offset = with(density) { IntOffset((playerWidth - menuWidth).roundToPx(), 64.dp.roundToPx()) }
        Popup(alignment = Alignment.TopStart, offset = offset, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
            Column(Modifier.width(menuWidth).clip(RoundedCornerShape(14.dp)).background(Color(0xFF252525)).padding(vertical = 8.dp)) {
                SettingsRow(R.drawable.yt_menu_quality, "Quality", qualityLabel) { page = "quality" }
                SettingsRow(R.drawable.yt_menu_speed, "Playback speed", if (speed == 1f) "1x" else "${speed}x") { page = "speed" }
                if (hasAudioChoices) SettingsRow(R.drawable.yt_menu_audio, "Audio track", activeAudioLabel) { page = "audio" }
                SettingsRow(R.drawable.yt_menu_audio, "Preferred audio", PlayerSettings.audioPreference.label) { page = "audio_preference" }
                if (info.subtitles.any { it.isUrl && it.format?.suffix?.lowercase() in listOf("vtt", "ttml", "srt") }) SettingsRow(R.drawable.yt_cc_active, "Subtitle", selectedSubtitle?.displayLanguageName ?: "Off") { page = "subtitle" }
                SettingsRow(R.drawable.yt_menu_lock, "Lock screen", "", onClick = onLock)
                SettingsRow(R.drawable.yt_settings, "More", "") { page = "more" }
            }
        }
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF272727), contentColor = Color.White, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = if (page == "quality" || page == "advanced" || page == "menu") 20.dp else 4.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page != "menu" && page != "quality" && page != "advanced") IconButton(onClick = { page = "menu" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(when (page) { "quality", "advanced" -> "Quality for this video  •  ${selected?.resolution ?: player.videoFormat?.height?.takeIf { it > 0 }?.let { "${it}p" } ?: "Auto"}"; "speed" -> "Playback speed"; "audio" -> "Audio track"; "audio_preference" -> "Preferred audio"; "doubletap" -> "Double-tap to seek"; "subtitle" -> "Subtitle"; "data" -> "Auto quality limit"; "more" -> "More"; else -> "Settings" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (page != "quality" && page != "advanced") IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
            }
            if (page != "quality" && page != "advanced") HorizontalDivider(color = Color.White.copy(alpha = .08f))
            Column(Modifier.heightIn(max = listHeight).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                when (page) {
                    "menu" -> {
                        SettingsRow(R.drawable.yt_menu_quality, "Quality", qualityLabel) { page = "quality" }
                        SettingsRow(R.drawable.yt_menu_speed, "Playback speed", if (speed == 1f) "Normal" else "${speed}x") { page = "speed" }
                        if (hasAudioChoices) SettingsRow(R.drawable.yt_menu_audio, "Audio track", activeAudioLabel) { page = "audio" }
                        SettingsRow(R.drawable.yt_menu_audio, "Preferred audio", PlayerSettings.audioPreference.label) { page = "audio_preference" }
                        if (info.subtitles.any { it.isUrl && it.format?.suffix?.lowercase() in listOf("vtt", "ttml", "srt") }) SettingsRow(R.drawable.yt_cc_active, "Subtitle", selectedSubtitle?.displayLanguageName ?: "Off") { page = "subtitle" }
                        SettingsRow(R.drawable.yt_menu_lock, "Lock screen", "", onClick = onLock)
                        SettingsRow(R.drawable.yt_menu_quality, "Auto quality limit", "${maxHeight}p") { page = "data" }
                        SettingsRow(R.drawable.yt_menu_autoplay, "Autoplay", if (autoplay) "On" else "Off") { onAutoplay(!autoplay) }
                        SettingsRow(R.drawable.yt_menu_speed, "Double-tap to seek", "${PlayerSettings.doubleTapSeconds} seconds") { page = "doubletap" }
                    }
                    "more" -> {
                        SettingsRow(R.drawable.yt_menu_quality, "Auto quality limit", "${maxHeight}p") { page = "data" }
                        SettingsRow(R.drawable.yt_menu_autoplay, "Autoplay", if (autoplay) "On" else "Off") { onAutoplay(!autoplay) }
                        SettingsRow(R.drawable.yt_menu_speed, "Double-tap to seek", "${PlayerSettings.doubleTapSeconds} seconds") { page = "doubletap" }
                    }
                    "data" -> listOf(144, 240, 360, 480, 720, 1080, 2160).forEach { height ->
                        PlayerChoiceRow("${height}p", selected = height == maxHeight) { onMaxHeight(height); page = "menu" }
                    }
                    "subtitle" -> {
                        PlayerChoiceRow("Off", selected = selectedSubtitle == null) { onSubtitle(null); page = "menu" }
                        info.subtitles.filter { it.isUrl && it.format?.suffix?.lowercase() in listOf("vtt", "ttml", "srt") }.forEach { subtitle ->
                            PlayerChoiceRow(subtitle.displayLanguageName, if (subtitle.isAutoGenerated) "Auto" else null, selectedSubtitle == subtitle) { onSubtitle(subtitle); page = "menu" }
                        }
                    }
                    "doubletap" -> PlayerSettings.doubleTapChoices.forEach { seconds ->
                        PlayerChoiceRow("$seconds seconds", selected = seconds == PlayerSettings.doubleTapSeconds) { PlayerSettings.updateDoubleTap(seconds); page = "menu" }
                    }
                    "audio_preference" -> {
                        AudioPreference.entries.forEach { preference ->
                            PlayerChoiceRow(preference.label, selected = preference == PlayerSettings.audioPreference) { onAudioPreference(preference); page = "menu" }
                        }
                        Text("Applies to playback. If the language is unavailable, the original audio is used when known.",
                            Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
                    }
                    "quality" -> {
                        fun choosePreset(mode: String, height: Int) {
                            onQualityMode(mode)
                            if (!info.hlsUrl.isNullOrBlank()) {
                                onSelect(null)
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).setMaxVideoSize(Int.MAX_VALUE, height).build()
                            } else {
                                val target = options.firstOrNull { it.height in 1..height } ?: options.lastOrNull()
                                if (target != null) onSelect(target) else onDismiss()
                            }
                        }
                        QualityOption("Auto (recommended)", if (!info.hlsUrl.isNullOrBlank()) "Adjusts to your conditions" else "Selects an available quality", qualityMode == "auto") { choosePreset("auto", maxHeight) }
                        QualityOption("Higher picture quality", "Uses more data", qualityMode == "high") { choosePreset("high", Int.MAX_VALUE) }
                        QualityOption("Data saver", "Lower picture quality", qualityMode == "data") { choosePreset("data", 480) }
                        QualityOption("Advanced", "Choose a specific resolution", false) { page = "advanced" }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = Color.White.copy(alpha = .15f))
                        Text("This setting only applies to the current video.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
                    }
                    "advanced" -> {
                        if (options.isEmpty() && hlsTracks.isEmpty()) Text("Custom resolutions are unavailable for this stream.", Modifier.padding(20.dp), color = Color.LightGray)
                        hlsTracks.forEach { (group, index) ->
                            val format = group.getTrackFormat(index)
                            val detail = listOfNotNull(format.frameRate.takeIf { it > 30f }?.let { "${Math.round(it)} fps" }, format.sampleMimeType?.substringAfter('/')).joinToString(" · ")
                            QualityOption("${format.height}p", detail, qualityMode == "advanced" && group.isTrackSelected(index)) {
                                onQualityMode("advanced")
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index))).build()
                                onDismiss()
                            }
                        }
                        options.forEach { stream ->
                            val detail = listOfNotNull(if (stream.fps > 30) "${stream.fps} fps" else null, stream.format?.name).joinToString(" · ")
                            QualityOption(stream.resolution, detail, qualityMode == "advanced" && stream == selected) { onQualityMode("advanced"); onSelect(stream) }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = Color.White.copy(alpha = .15f))
                        Text("This setting only applies to the current video.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
                    }
                    "audio" -> {
                        if (manifestAudio.size > 1) {
                            manifestAudio.forEach { choice -> PlayerChoiceRow(choice.label, selected = choice.selected) { onSelectManifestAudio(choice.group, choice.indices) } }
                        } else if (embeddedChoices.size > 1) {
                            Text("This video carries its audio inside the video format. Selecting a language reloads it at the current quality.",
                                style = MaterialTheme.typography.bodySmall, color = Color.LightGray, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                            embeddedChoices.forEach { track ->
                                PlayerChoiceRow(embeddedAudioLabel(track.metadata), selected = track.metadata.id == currentEmbedded?.id) { onSelectEmbedded(track.metadata) }
                            }
                        } else {
                            if (selected?.isVideoOnly != true) Text("Selecting a track uses a video format with separate audio.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                            tracks.forEach { audio -> PlayerChoiceRow(audioLabel(audio), selected = audio == selectedAudio) { onSelectAudio(audio) } }
                        }
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

@Composable internal fun QualityOption(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 62.dp).padding(start = 20.dp, end = 20.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterStart) {
            if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            if (!detail.isNullOrBlank()) Text(detail, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable internal fun SettingsRow(icon: Int, label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) { Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White)) }
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
        Image(painterResource(R.drawable.yt_chevron_right), contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(24.dp), colorFilter = ColorFilter.tint(Color.LightGray))
    }
}

