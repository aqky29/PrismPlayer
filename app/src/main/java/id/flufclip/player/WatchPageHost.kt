package id.flufclip.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

@Composable
internal fun WatchPageHost(
    info: StreamInfo,
    player: ExoPlayer,
    playerLive: Boolean,
    activeChatState: LiveChatState?,
    chaptersOpen: Boolean,
    onChaptersOpenChange: (Boolean) -> Unit,
    watchFade: Float,
    fullscreen: Boolean,
    verticalProgress: Float,
    exitDragging: Boolean,
    wideWatch: Boolean,
    error: String?,
    queue: List<StreamInfoItem>,
    activePlaylistTitle: String?,
    activePlaylistId: String?,
    onOpenFullPlaylist: (() -> Unit)?,
    showQuality: Boolean,
    onDismissQuality: () -> Unit,
    onOpenQuality: () -> Unit,
    qualityInitialPage: String,
    showDownloads: Boolean,
    onDismissDownloads: () -> Unit,
    onOpenDownloads: () -> Unit,
    quality: VideoStream?,
    selectedAudio: AudioStream?,
    selectedSubtitle: SubtitlesStream?,
    autoplay: Boolean,
    onAutoplayChange: (Boolean) -> Unit,
    maxVideoHeight: Int,
    qualityMode: String,
    onQualityModeChange: (String) -> Unit,
    onLockPlayer: () -> Unit,
    usesAdaptiveAudio: Boolean,
    onAudioPreference: (AudioPreference) -> Unit,
    onSelectManifestAudio: (Tracks.Group, List<Int>) -> Unit,
    onSelectEmbeddedAudio: (AudioTrackMetadata) -> Unit,
    onSelectStreamQuality: (VideoStream?) -> Unit,
    onSelectAudioTrack: (AudioStream?) -> Unit,
    onSetMaxVideoHeight: (Int) -> Unit,
    onChangeSubtitle: (SubtitlesStream?) -> Unit,
    onOpenLink: (url: String) -> Unit,
    onShareVideo: () -> Unit,
    onRemixVideo: () -> Unit,
    onClipVideo: () -> Unit,
    onCopyVideoLink: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onOpenChannel: (url: String) -> Unit,
    onOpenVideo: (item: StreamInfoItem) -> Unit,
    onSelectQueueItem: (item: StreamInfoItem) -> Unit,
    onRateWatch: (delta: Int) -> Unit,
    onSaveWatch: () -> Unit,
    onSubscribeWatch: (subscribe: Boolean) -> Unit,
    onNotificationPreference: (channelId: String, pref: NotificationPreference) -> Unit,
    onStartDownload: (height: Int, audio: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        if (fullscreen) modifier.fillMaxSize()
        else modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
    ) {
        val isLive = info.isOngoingLive() || playerLive
        val isPostLive = info.streamType == StreamType.POST_LIVE_STREAM ||
            info.streamType == StreamType.POST_LIVE_AUDIO_STREAM
        val chatState = activeChatState ?: rememberLiveChat(info.url, isLive, isPostLive, player)
        var showComments by remember(info.url) { mutableStateOf(false) }
        var showChat by remember(info.url, isLive) { mutableStateOf(isLive) }
        var showDescription by remember(info.url) { mutableStateOf(false) }
        var showAsk by remember(info.url) { mutableStateOf(false) }
        // Ask hanya ada bila server mengirim panelnya untuk akun dan video ini (lihat AskYouTube.panel); tanpa itu tombolnya tidak muncul.
        val askPanel by produceState<AskPanel?>(null, info.url, YouTubeAuth.isLoggedIn) { value = null; value = AskYouTube.panel(info.url) }
        // Status akun dibaca ulang saat video dibuka. Generasi menolak respons basi yang dimulai sebelum
        // tindakan pengguna, tetapi tetap mengizinkan pembacaan baru memperbaiki nilai uncertain/known.
        LaunchedEffect(info.url, YouTubeAuth.sessionRevision) {
            val revision = YouTubeAuth.sessionRevision
            if (!YouTubeAuth.isLoggedIn) return@LaunchedEffect
            val videoId = info.id.ifBlank { videoKey(info.url) }
            val channelId = Regex("/channel/(UC[\\w-]{22})").find(info.uploaderUrl.orEmpty())?.groupValues?.get(1)
            val generation = Engagement.generation(videoId, channelId)
            val snapshot = try { EngagementSource.fetch(videoId, revision) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
            if (snapshot != null) Engagement.refresh(videoId, snapshot, revision, generation, channelId)
            // Status "tersimpan" juga berasal dari server; nilai baru menggantikan status lama bila belum ada klik.
            val saveGeneration = Engagement.generation(videoId, channelId).save
            val choices = try { AccountLibrary.choices(videoId) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
            if (choices != null && revision == YouTubeAuth.sessionRevision) {
                Engagement.refresh(videoId, EngagementSnapshot(saved = choices.any { it.selected }), revision,
                    EngagementGeneration(save = saveGeneration), channelId)
            }
        }
        val askScope = rememberCoroutineScope()
        val askSession = remember(info.url, askPanel != null) { askPanel?.let { AskSession(info.url, it, askScope) { player.currentPosition } } }
        val comments = rememberComments(info.url)
        val watchRecommendations = rememberWatchRecommendations(info, YouTubeAuth.sessionRevision)
        val watchAspect = if (wideWatch) (16f / 9f) else run {
            val a = videoAspectOf(player.videoSize)
            if (a > 0f) a.coerceIn(4f / 3f, 21f / 9f) else 16f / 9f
        }
        val channelId = Regex("/channel/(UC[\\w-]{22})").find(info.uploaderUrl.orEmpty())?.groupValues?.get(1)
        val engagement = run {
            val key = videoKey(info.url)
            val ratingState = Engagement.rating(key)
            val subscriptionState = channelId?.let { Engagement.subscription(it) }
            WatchEngagement(
                rating = ratingState.value ?: 0,
                saved = Engagement.saved(key).saved == true,
                subscribed = subscriptionState?.subscribed == true,
                onLike = { onRateWatch(1) },
                onDislike = { onRateWatch(-1) },
                onSave = { onSaveWatch() },
                onSubscribe = { onSubscribeWatch(true) },
                onUnsubscribe = { onSubscribeWatch(false) },
                likeDelta = ratingState.likeDelta,
                ratingUncertain = ratingState.uncertain,
                subscriptionUncertain = subscriptionState?.uncertain == true,
                notificationPreference = subscriptionState?.notificationPreference ?: NotificationPreference.PERSONALIZED,
                onNotificationPreference = { pref -> channelId?.let { onNotificationPreference(it, pref) } }
            )
        }

        WatchScreen(
            showDescription = showDescription,
            showComments = showComments,
            showChat = showChat,
            showAsk = showAsk && askSession != null,
            ask = askSession,
            onAsk = {
                onChaptersOpenChange(false)
                showComments = false
                showDescription = false
                showChat = false
                showAsk = true
            },
            showChapters = chaptersOpen,
            onClosePanel = {
                showDescription = false
                showComments = false
                showChat = false
                showAsk = false
                onChaptersOpenChange(false)
            },
            watchAlpha = 1f - watchFade,
            watchOffset = if (fullscreen) 0f else verticalProgress,
            info = info,
            isLive = isLive,
            comments = comments,
            chatState = chatState,
            onComments = {
                onChaptersOpenChange(false)
                showDescription = false
                showChat = false
                showAsk = false
                showComments = true
            },
            onDescription = {
                onChaptersOpenChange(false)
                showComments = false
                showChat = false
                showAsk = false
                showDescription = true
            },
            onChat = {
                onChaptersOpenChange(false)
                showComments = false
                showDescription = false
                showAsk = false
                showChat = true
            },
            onChapters = {
                showComments = false
                showDescription = false
                showChat = false
                showAsk = false
                onChaptersOpenChange(true)
            },
            onLink = onOpenLink,
            onSeek = { player.seekTo(it * 1000L); player.play() },
            onShare = onShareVideo,
            onRemix = onRemixVideo,
            onClip = onClipVideo,
            onCopy = onCopyVideoLink,
            player = player,
            hidden = fullscreen && !exitDragging,
            onFullscreen = onToggleFullscreen,
            recommendations = watchRecommendations,
            error = error,
            onBack = onBack,
            onSettings = onOpenQuality,
            onDownload = onOpenDownloads,
            onChannel = { onOpenChannel(info.uploaderUrl.orEmpty()) },
            onOpenChannel = onOpenChannel,
            onVideo = onOpenVideo,
            engagement = engagement,
            queue = queue,
            playlistTitle = activePlaylistTitle,
            playlistId = activePlaylistId,
            watchAspect = watchAspect,
            onSelectQueueItem = onSelectQueueItem,
            onOpenFullPlaylist = onOpenFullPlaylist,
        )

        if (showQuality) {
            QualityDialog(
                info = info,
                selected = quality,
                selectedAudio = selectedAudio,
                selectedSubtitle = selectedSubtitle,
                autoplay = autoplay,
                maxHeight = maxVideoHeight,
                player = player,
                initialPage = qualityInitialPage,
                fullscreen = fullscreen,
                qualityMode = qualityMode,
                useManifestAudio = usesAdaptiveAudio,
                onAudioPreference = onAudioPreference,
                onSelectManifestAudio = onSelectManifestAudio,
                onSelectEmbedded = onSelectEmbeddedAudio,
                onQualityMode = onQualityModeChange,
                onLock = onLockPlayer,
                onMaxHeight = onSetMaxVideoHeight,
                onAutoplay = onAutoplayChange,
                onSubtitle = onChangeSubtitle,
                onDismiss = onDismissQuality,
                onSelect = onSelectStreamQuality,
                onSelectAudio = onSelectAudioTrack,
            )
        }

        if (showDownloads) {
            DownloadDialog(
                info = info,
                onDismiss = onDismissDownloads,
            ) { height, audio ->
                onDismissDownloads()
                onStartDownload(height, audio)
            }
        }
    }
}
