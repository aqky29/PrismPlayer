package id.flufclip.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

internal object PlaybackSessionHost {
    internal data class Target(
        val tag: String,
        val player: ExoPlayer,
        val hasNext: () -> Boolean,
        val hasPrevious: () -> Boolean,
        val onNext: () -> Unit,
        val onPrevious: () -> Unit,
        val onClose: () -> Unit,
        val contentIntent: (Context) -> Intent,
    )

    private var onlinePlayer: ExoPlayer? = null
    private var secondaryPlayer: ExoPlayer? = null
    internal var activeTarget: Target? = null
        private set
    internal var keepForegroundDuringTransition: Boolean = false
    private var serviceInstance: PlaybackService? = null

    fun obtainOnlinePlayer(context: Context): ExoPlayer {
        onlinePlayer?.let { return it }
        val appContext = context.applicationContext
        return ExoPlayer.Builder(appContext)
            .setLoadControl(playbackLoadControl())
            .setLivePlaybackSpeedControl(playbackLiveSpeedControl())
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply { setAudioAttributes(AudioAttributes.DEFAULT, true) }
            .also { onlinePlayer = it }
    }

    fun obtainSecondaryPlayer(context: Context): ExoPlayer {
        secondaryPlayer?.let { return it }
        val appContext = context.applicationContext
        val renderers = DefaultRenderersFactory(appContext).setEnableDecoderFallback(true)
        return ExoPlayer.Builder(appContext, renderers)
            .setLoadControl(secondaryLoadControl())
            .setLivePlaybackSpeedControl(playbackLiveSpeedControl())
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply { setAudioAttributes(AudioAttributes.DEFAULT, false) }
            .also { secondaryPlayer = it }
    }

    fun currentOnlinePlayer(): ExoPlayer? = onlinePlayer

    fun currentSecondaryPlayer(): ExoPlayer? = secondaryPlayer

    fun pauseAllOnlinePlayers() {
        onlinePlayer?.pause()
        secondaryPlayer?.pause()
    }

    fun releaseSecondaryPlayer() {
        secondaryPlayer?.stop()
        secondaryPlayer?.clearMediaItems()
        secondaryPlayer?.release()
        secondaryPlayer = null
    }

    fun applyDualAudioRouting(
        primary: ExoPlayer,
        secondary: ExoPlayer?,
        focusedSlot: Int,
        audioPolicy: String,
        slot1Muted: Boolean,
        slot2Muted: Boolean,
    ) {
        val focusPrimary = focusedSlot != 2 || secondary == null
        // Release audio focus on the non-focused player first so switching focus never triggers AUDIOFOCUS_LOSS on it.
        if (focusPrimary) {
            secondary?.setAudioAttributes(AudioAttributes.DEFAULT, false)
            primary.setAudioAttributes(AudioAttributes.DEFAULT, true)
        } else {
            primary.setAudioAttributes(AudioAttributes.DEFAULT, false)
            secondary?.setAudioAttributes(AudioAttributes.DEFAULT, true)
        }
        val pVol = when {
            slot1Muted -> 0f
            audioPolicy == "mix" -> 1f
            focusPrimary -> 1f
            else -> 0f
        }
        val sVol = when {
            slot2Muted -> 0f
            audioPolicy == "mix" -> 1f
            !focusPrimary -> 1f
            else -> 0f
        }
        primary.volume = pVol
        secondary?.volume = sVol
    }

    fun releaseOnlinePlayerIfIdle() {
        if (activeTarget?.tag == "online") return
        releaseSecondaryPlayer()
        onlinePlayer?.release()
        onlinePlayer = null
    }

    fun releaseOnlinePlayerForce() {
        if (activeTarget?.tag == "online") {
            activeTarget = null
            serviceInstance?.stopFromHost()
        }
        releaseSecondaryPlayer()
        onlinePlayer?.release()
        onlinePlayer = null
    }

    fun setVideoTrackDisabled(player: Player?, disabled: Boolean) {
        if (player == null) return
        val params = player.trackSelectionParameters
        val alreadyDisabled = params.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO)
        if (alreadyDisabled == disabled) return
        player.trackSelectionParameters = params.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disabled)
            .build()
    }

    fun setTarget(target: Target?) {
        activeTarget = target
        serviceInstance?.onTargetUpdated(target)
    }

    fun syncService(context: Context) {
        val target = activeTarget
        if (!PlayerSettings.backgroundPlayback || target == null) {
            keepForegroundDuringTransition = false
            serviceInstance?.stopFromHost()
            return
        }
        val active = target.player.playWhenReady || keepForegroundDuringTransition
        if (active && serviceInstance == null) {
            runCatching {
                context.applicationContext.startService(
                    Intent(context.applicationContext, PlaybackService::class.java)
                )
            }
        }
    }

    internal fun attachService(service: PlaybackService?) {
        serviceInstance = service
    }
}

class PlaybackService : MediaSessionService() {
    companion object {
        private const val ACTION_CLOSE_PLAYBACK = "id.flufclip.player.CLOSE_PLAYBACK"
        private val CLOSE_COMMAND = SessionCommand(ACTION_CLOSE_PLAYBACK, Bundle.EMPTY)
    }

    private var mediaSession: MediaSession? = null
    private var attachedTarget: PlaybackSessionHost.Target? = null

    private class SessionBridgePlayer(
        private val target: PlaybackSessionHost.Target,
    ) : ForwardingPlayer(target.player) {
        override fun getAvailableCommands(): Player.Commands {
            val builder = super.getAvailableCommands().buildUpon()
            if (target.hasNext()) {
                builder.add(COMMAND_SEEK_TO_NEXT).add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            } else {
                builder.remove(COMMAND_SEEK_TO_NEXT).remove(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            }
            if (target.hasPrevious() || currentPosition > 0L) {
                builder.add(COMMAND_SEEK_TO_PREVIOUS).add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            } else {
                builder.remove(COMMAND_SEEK_TO_PREVIOUS).remove(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            }
            return builder.build()
        }

        override fun isCommandAvailable(command: Int): Boolean =
            availableCommands.contains(command)

        override fun seekToNext() {
            if (target.hasNext()) target.onNext()
        }

        override fun seekToNextMediaItem() {
            if (target.hasNext()) target.onNext()
        }

        override fun seekToPrevious() {
            if (currentPosition > 5_000L || !target.hasPrevious()) seekTo(0L)
            else target.onPrevious()
        }

        override fun seekToPreviousMediaItem() {
            if (currentPosition > 5_000L || !target.hasPrevious()) seekTo(0L)
            else target.onPrevious()
        }
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(CLOSE_COMMAND)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_CLOSE_PLAYBACK) {
                PlaybackSessionHost.keepForegroundDuringTransition = false
                PlaybackSessionHost.activeTarget?.onClose?.invoke()
                stopFromHost()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    override fun onCreate() {
        super.onCreate()
        PlayerSettings.load(applicationContext)
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.playback_notification_channel)
                .build()
                .apply { setSmallIcon(R.drawable.yt_play) }
        )
        PlaybackSessionHost.attachService(this)
        val target = PlaybackSessionHost.activeTarget
        if (target == null) {
            stopSelf()
            return
        }
        ensureSession(target)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val target = PlaybackSessionHost.activeTarget
        if (target == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureSession(target)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(
            session,
            startInForegroundRequired || PlaybackSessionHost.keepForegroundDuringTransition
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        PlaybackSessionHost.keepForegroundDuringTransition = false
        PlaybackSessionHost.activeTarget?.player?.pause()
        PlaybackSessionHost.pauseAllOnlinePlayers()
        stopFromHost()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        PlaybackSessionHost.attachService(null)
        mediaSession?.release()
        mediaSession = null
        attachedTarget = null
        super.onDestroy()
    }

    internal fun onTargetUpdated(target: PlaybackSessionHost.Target?) {
        if (target == null || !PlayerSettings.backgroundPlayback) {
            stopFromHost()
            return
        }
        ensureSession(target)
    }

    internal fun stopFromHost() {
        PlaybackSessionHost.keepForegroundDuringTransition = false
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun ensureSession(target: PlaybackSessionHost.Target) {
        val existing = mediaSession
        if (existing != null && attachedTarget === target) return
        val bridge = SessionBridgePlayer(target)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            target.contentIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val closeButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(getString(R.string.cancel))
            .setCustomIconResId(android.R.drawable.ic_menu_close_clear_cancel)
            .setSessionCommand(CLOSE_COMMAND)
            .build()
        if (existing == null) {
            mediaSession = MediaSession.Builder(this, bridge)
                .setCallback(sessionCallback)
                .setSessionActivity(pendingIntent)
                .setCustomLayout(listOf(closeButton))
                .build()
        } else {
            existing.setSessionActivity(pendingIntent)
            existing.setCustomLayout(listOf(closeButton))
            existing.player = bridge
        }
        attachedTarget = target
    }
}
