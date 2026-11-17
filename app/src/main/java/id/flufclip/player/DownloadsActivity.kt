package id.flufclip.player

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.*
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update

internal var onlinePlayerForDownloads = java.lang.ref.WeakReference<Player>(null)

class DownloadsActivity : ComponentActivity() {
    internal var localPlayer: ExoPlayer? = null
    internal var localRecord: SavedDownload? = null
    private var requestedUri by mutableStateOf<String?>(null)
    private var requestGeneration by mutableIntStateOf(0)
    internal var startMinimized = false
    internal var initialAutoplay = true
    internal var borrowedPlayer: ExoPlayer? = null
    internal var handingOff = false
    internal var autoAdvance = true
    internal fun handoffPlayback(): Boolean {
        if (handingOff) return true
        val saved = localRecord ?: return false
        val player = localPlayer ?: return false
        savePosition()
        handingOff = true
        OfflinePlaybackBridge.offer(applicationContext, saved, player, autoAdvance)
        return true
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        YouTubeAuth.init(applicationContext)
        PlayerSettings.load(applicationContext)
        val incoming = intent.getStringExtra("saved_uri")
        initialAutoplay = OfflinePlaybackBridge.session?.autoplay ?: true
        startMinimized = incoming == null && OfflinePlaybackBridge.session != null
        requestedUri = incoming ?: OfflinePlaybackBridge.session?.saved?.uri
        borrowedPlayer = OfflinePlaybackBridge.take(requestedUri)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = androidx.compose.ui.graphics.Color.White, background = Background, surface = Background)) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize()) { DownloadsScreen(this, requestedUri, requestGeneration) }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedUri = intent.getStringExtra("saved_uri")
        requestGeneration++
    }
    override fun onStart() {
        super.onStart()
        PlaybackSessionHost.setVideoTrackDisabled(localPlayer, false)
        onlinePlayerForDownloads.get()?.pause()
    }
    internal fun savePosition() {
        val record = localRecord ?: return
        val player = localPlayer ?: return
        val duration = player.duration.takeIf { it > 0 } ?: record.video.durationMs
        getSharedPreferences("download_positions", MODE_PRIVATE).edit()
            .putLong(record.uri, downloadResumePosition(player.currentPosition, duration))
            .putLong("duration:${record.uri}", duration).apply()
        SavedDownloads.playbackRevision.update { it + 1 }
    }
    override fun onStop() {
        if (!handingOff) {
            savePosition()
            val player = localPlayer
            val keep = PlayerSettings.backgroundPlayback && localRecord != null && player != null &&
                player.playWhenReady && (player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY)
            if (keep) PlaybackSessionHost.setVideoTrackDisabled(player, true)
            else player?.pause()
        }
        super.onStop()
    }
}

@Composable
private fun DownloadsScreen(activity: DownloadsActivity, requestedUri: String?, requestGeneration: Int) {
    val state by DownloadStatus.state.collectAsState()
    val revision by SavedDownloads.revision.collectAsState()
    val playbackRevision by SavedDownloads.playbackRevision.collectAsState()
    val downloads = remember(revision, state.completed) { SavedDownloads.list(activity) }
    var selectedUri by rememberSaveable { mutableStateOf(requestedUri) }
    var minimized by rememberSaveable { mutableStateOf(activity.startMinimized) }
    LaunchedEffect(requestedUri, requestGeneration) {
        if (requestedUri != null) {
            selectedUri = requestedUri
            if (requestGeneration > 0 || !activity.startMinimized) minimized = false
        }
    }
    val selected = downloads.firstOrNull { it.uri == selectedUri }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var autoplay by rememberSaveable { mutableStateOf(activity.initialAutoplay) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var hasSubtitles by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val player = remember {
        (activity.borrowedPlayer ?: ExoPlayer.Builder(activity.applicationContext)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply { setAudioAttributes(AudioAttributes.DEFAULT, true) }).apply {
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_NETWORK)
        }
    }
    val currentDownloads by rememberUpdatedState(downloads)
    val currentSelected by rememberUpdatedState(selectedUri)
    val auto by rememberUpdatedState(autoplay)
    SideEffect { activity.autoAdvance = autoplay }
    val onEnded by rememberUpdatedState(newValue = {
        val index = currentDownloads.indexOfFirst { it.uri == currentSelected }
        if (!activity.handingOff && auto && index >= 0 && index + 1 < currentDownloads.size) selectedUri = currentDownloads[index + 1].uri
    })
    DisposableEffect(player) {
        activity.localPlayer = player
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing = player.isPlaying
                buffering = player.playbackState == Player.STATE_BUFFERING
                hasSubtitles = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
                val flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                if (playing) activity.window.addFlags(flag) else activity.window.clearFlags(flag)
                if (events.containsAny(Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                    PlaybackSessionHost.syncService(activity)
                }
            }
            override fun onPlayerError(failure: PlaybackException) { error = "Cannot read the file. It may have been moved, deleted, or access may be unavailable." }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) onEnded() }
        }
        player.addListener(listener)
        onDispose {
            if (!activity.handingOff) {
                activity.savePosition()
                if (PlaybackSessionHost.activeTarget?.tag == "offline") {
                    PlaybackSessionHost.setTarget(null)
                    PlaybackSessionHost.syncService(activity)
                }
            }
            activity.localPlayer = null
            activity.localRecord = null
            player.removeListener(listener)
            if (!activity.handingOff) player.release()
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(selected?.uri, downloads) {
        if (selected == null) {
            if (PlaybackSessionHost.activeTarget?.tag == "offline") {
                PlaybackSessionHost.setTarget(null)
                PlaybackSessionHost.syncService(activity)
            }
        } else {
            val index = downloads.indexOfFirst { it.uri == selected.uri }
            PlaybackSessionHost.setTarget(
                PlaybackSessionHost.Target(
                    tag = "offline",
                    player = player,
                    hasNext = { index >= 0 && index + 1 < currentDownloads.size },
                    hasPrevious = { index > 0 },
                    onNext = {
                        val next = currentDownloads.getOrNull(index + 1)
                        if (next != null) { activity.savePosition(); selectedUri = next.uri }
                    },
                    onPrevious = {
                        val prev = currentDownloads.getOrNull(index - 1)
                        if (prev != null) { activity.savePosition(); selectedUri = prev.uri }
                    },
                    onClose = { player.pause() },
                    contentIntent = { ctx -> SavedDownloads.openIntent(ctx, selected) },
                )
            )
            PlaybackSessionHost.syncService(activity)
        }
    }
    LaunchedEffect(selected?.uri) {
        val sameSource = selected != null && player.currentMediaItem?.mediaId == selected.uri && player.mediaItemCount > 0
        if (!sameSource) {
            activity.savePosition()
            player.stop()
            player.clearMediaItems()
            error = null
        }
        activity.localRecord = selected
        if (selected != null && !sameSource) {
            if (!isLocalDownloadUri(selected.uri)) { error = "The download location is not a local file."; return@LaunchedEffect }
            player.setMediaSource(ProgressiveMediaSource.Factory(DefaultDataSource.Factory(activity)).createMediaSource(
                MediaItem.Builder().setUri(selected.uri).setMimeType(selected.mime).setMediaId(selected.uri)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(selected.title).setArtist(selected.video.uploader).build()).build()))
            player.seekTo(activity.getSharedPreferences("download_positions", 0).getLong(selected.uri, 0))
            player.prepare()
            player.playWhenReady = true
        } else if (selected == null) { fullscreen = false; minimized = false }
    }
    LaunchedEffect(selected?.uri) {
        while (selected != null) { delay(5000); activity.savePosition() }
    }
    LaunchedEffect(fullscreen) {
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (fullscreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(Unit) { onDispose { WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars()) } }
    BackHandler(selectedUri != null) { if (fullscreen) fullscreen = false else if (!minimized) minimized = true else selectedUri = null }
    val root = if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxSize().systemBarsPadding()
    Box(root) {
        if (selected == null || minimized) DownloadsHome(downloads, state, playbackRevision, onBack = { activity.finish() }, onPlay = { selectedUri = it.uri; minimized = false })
        else DownloadedWatch(
            selected, downloads, player, playing, buffering, hasSubtitles, fullscreen, autoplay, error,
            onBack = { if (fullscreen) fullscreen = false else minimized = true },
            onFullscreen = { fullscreen = !fullscreen }, onAutoplay = { autoplay = !autoplay },
            onPick = { activity.savePosition(); selectedUri = it.uri; minimized = false }, onList = { minimized = true },
            onRetry = { if (isLocalDownloadUri(selected.uri)) { error = null; player.prepare(); player.play() } })
        if (selected != null && minimized) OfflineFloatingMini(selected, player,
            onExpand = { minimized = false }, onClose = { selectedUri = null })
    }
}
