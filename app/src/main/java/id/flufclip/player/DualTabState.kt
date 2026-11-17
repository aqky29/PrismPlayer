package id.flufclip.player

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

internal data class DualTabSlotState(
    val id: Int,
    val item: StreamInfoItem? = null,
    val info: StreamInfo? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val muted: Boolean = false,
)

/**
 * Pure state container for Dual Tab Mode so slot/focus/audio/swap transitions
 * can be unit-tested on the JVM without instantiating ExoPlayer.
 */
@Stable
internal class DualTabModel(initialAudioPolicy: String = "focus") {
    var slot1 by mutableStateOf(DualTabSlotState(id = 1, muted = false))
    var slot2 by mutableStateOf(DualTabSlotState(id = 2, muted = initialAudioPolicy != "mix"))
    var focusedSlot by mutableStateOf(1)
        private set
    var pickingSlot by mutableStateOf<Int?>(null)
        private set
    var swappedDisplay by mutableStateOf(false)
        private set
    var audioPolicy by mutableStateOf(if (initialAudioPolicy == "mix") "mix" else "focus")
        private set

    fun slotOf(slotId: Int): DualTabSlotState = if (slotId == 2) slot2 else slot1

    fun updateSlot(slotId: Int, transform: (DualTabSlotState) -> DualTabSlotState) {
        if (slotId == 2) slot2 = transform(slot2) else slot1 = transform(slot1)
    }

    fun focusSlot(slotId: Int) {
        val target = if (slotId == 2) 2 else 1
        focusedSlot = target
        if (audioPolicy == "focus") {
            slot1 = slot1.copy(muted = target != 1)
            slot2 = slot2.copy(muted = target != 2)
        }
    }

    fun toggleMute(slotId: Int) {
        updateSlot(slotId) { it.copy(muted = !it.muted) }
    }

    fun setAudioPolicy(policy: String) {
        val normalized = if (policy == "mix") "mix" else "focus"
        audioPolicy = normalized
        if (normalized == "focus") {
            slot1 = slot1.copy(muted = focusedSlot != 1)
            slot2 = slot2.copy(muted = focusedSlot != 2)
        } else {
            slot1 = slot1.copy(muted = false)
            slot2 = slot2.copy(muted = false)
        }
    }

    fun swapSlots() {
        swappedDisplay = !swappedDisplay
    }

    fun startPicking(slotId: Int) {
        pickingSlot = if (slotId == 2) 2 else 1
    }

    fun clearPicking() {
        pickingSlot = null
    }

    fun reset() {
        slot1 = DualTabSlotState(id = 1, muted = false)
        slot2 = DualTabSlotState(id = 2, muted = audioPolicy != "mix")
        focusedSlot = 1
        pickingSlot = null
        swappedDisplay = false
    }
}

internal data class DualShortsBackup(
    val item: StreamInfoItem,
    val positionMs: Long,
)

@Stable
internal class DualTabController(initialAudioPolicy: String = PlayerSettings.dualAudioPolicy) {
    val model = DualTabModel(initialAudioPolicy)
    val trackState1 = PlaybackTrackState()
    val trackState2 = PlaybackTrackState()
    var secondaryAllocated by mutableStateOf(PlaybackSessionHost.currentSecondaryPlayer() != null)
        private set

    private var slot1Job: Job? = null
    private var slot2Job: Job? = null
    private var shortsBackup: DualShortsBackup? = null

    fun trackStateFor(slotId: Int): PlaybackTrackState = if (slotId == 2) trackState2 else trackState1

    fun effectiveHeightFor(slotId: Int, maxVideoHeight: Int): Int =
        if (slotId == 2) minOf(maxVideoHeight, 720) else maxVideoHeight

    fun syncAudio(primary: ExoPlayer, secondary: ExoPlayer?) {
        PlaybackSessionHost.applyDualAudioRouting(
            primary = primary,
            secondary = secondary,
            focusedSlot = model.focusedSlot,
            audioPolicy = model.audioPolicy,
            slot1Muted = model.slot1.muted,
            slot2Muted = model.slot2.muted,
        )
    }

    fun focusSlot(slotId: Int, primary: ExoPlayer, secondary: ExoPlayer?) {
        model.focusSlot(slotId)
        syncAudio(primary, secondary)
    }

    fun toggleMute(slotId: Int, primary: ExoPlayer, secondary: ExoPlayer?) {
        model.toggleMute(slotId)
        syncAudio(primary, secondary)
    }

    fun setAudioPolicy(policy: String, primary: ExoPlayer, secondary: ExoPlayer?) {
        PlayerSettings.updateDualAudioPolicy(policy)
        model.setAudioPolicy(policy)
        syncAudio(primary, secondary)
    }

    fun adoptSingleToSlot1(info: StreamInfo, sourceTrackState: PlaybackTrackState) {
        val item = StreamInfoItem(
            ServiceList.YouTube.serviceId,
            info.url,
            info.name.orEmpty(),
            info.streamType ?: StreamType.VIDEO_STREAM,
        ).apply {
            uploaderName = info.uploaderName
            duration = info.duration
            thumbnails = info.thumbnails
        }
        trackState1.copyFrom(sourceTrackState)
        model.slot1 = DualTabSlotState(
            id = 1,
            item = item,
            info = info,
            loading = false,
            error = null,
            muted = model.audioPolicy == "focus" && model.focusedSlot != 1,
        )
    }

    fun loadIntoSlot(
        slotId: Int,
        item: StreamInfoItem,
        context: Context,
        scope: CoroutineScope,
        maxVideoHeight: Int,
        primary: ExoPlayer,
        secondaryProvider: () -> ExoPlayer,
        resumeMs: Long = 0L,
        autoPlay: Boolean = true,
    ) {
        val targetSlot = if (slotId == 2) 2 else 1
        if (targetSlot == 1) slot1Job?.cancel() else slot2Job?.cancel()
        model.updateSlot(targetSlot) {
            it.copy(item = item, loading = true, error = null)
        }
        val job = scope.launch {
            try {
                val resolved = VideoRepository.video(item.url, context)
                val targetPlayer = if (targetSlot == 2) {
                    secondaryProvider().also { secondaryAllocated = true }
                } else {
                    primary
                }
                val targetTracks = trackStateFor(targetSlot)
                val height = effectiveHeightFor(targetSlot, maxVideoHeight)

                targetTracks.selectedSubtitle = null
                targetTracks.lastSubtitle = null
                targetTracks.qualityMode = "auto"
                targetTracks.manualAudioOverride = false
                targetTracks.attemptedAudioFallback = null

                targetPlayer.pauseAtEndOfMediaItems = false
                targetPlayer.trackSelectionParameters = targetPlayer.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .build()

                val (initial, audio) = pickStreams(resolved, height)
                configurePreferredAudio(targetPlayer, resolved, PlayerSettings.audioPreference)
                targetPlayer.setPlaybackSpeed(1f)
                val startPos = if (resolved.isOngoingLive()) 0L else resumeMs.coerceAtLeast(0L)
                targetTracks.playQuality(targetPlayer, context, height, resolved, initial, audio, startPos, autoPlay)
                targetTracks.selectedAudio = audio

                model.updateSlot(targetSlot) {
                    it.copy(item = item, info = resolved, loading = false, error = null)
                }
                syncAudio(primary, PlaybackSessionHost.currentSecondaryPlayer())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                model.updateSlot(targetSlot) {
                    it.copy(loading = false, error = e.message ?: "Failed to load video")
                }
            }
        }
        if (targetSlot == 1) slot1Job = job else slot2Job = job
    }

    fun handlePlayerErrorForFallback(
        slotId: Int,
        error: PlaybackException,
        context: Context,
        maxVideoHeight: Int,
        player: ExoPlayer,
    ): Boolean {
        if (slotId != 2) return false
        val info = model.slot2.info ?: return false
        val msg = error.message.orEmpty()
        val isDecoderIssue = error.errorCode in listOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        ) || msg.contains("MediaCodec", ignoreCase = true)
        if (!isDecoderIssue) return false

        val currentHeight = trackState2.quality?.height ?: player.videoFormat?.height ?: effectiveHeightFor(2, maxVideoHeight)
        val fallbackHeight = when {
            currentHeight > 480 -> 480
            currentHeight > 360 -> 360
            else -> return false
        }
        return runCatching {
            val (stream, audio) = pickStreams(info, fallbackHeight)
            val pos = player.currentPosition.coerceAtLeast(0L)
            trackState2.playQuality(player, context, fallbackHeight, info, stream, audio, pos, player.playWhenReady)
            trackState2.selectedAudio = audio
            model.updateSlot(2) { it.copy(error = null) }
            true
        }.getOrDefault(false)
    }

    fun saveSlot1BeforeShorts(positionMs: Long) {
        val currentItem = model.slot1.item ?: model.slot1.info?.let { info ->
            StreamInfoItem(
                ServiceList.YouTube.serviceId,
                info.url,
                info.name.orEmpty(),
                info.streamType ?: StreamType.VIDEO_STREAM,
            ).apply {
                uploaderName = info.uploaderName
                duration = info.duration
                thumbnails = info.thumbnails
            }
        }
        if (currentItem != null) {
            shortsBackup = DualShortsBackup(currentItem, positionMs.coerceAtLeast(0L))
        }
        PlaybackSessionHost.currentSecondaryPlayer()?.pause()
    }

    fun hasSavedSlot1ForShorts(): Boolean = shortsBackup != null

    fun restoreSlot1AfterShorts(
        context: Context,
        scope: CoroutineScope,
        maxVideoHeight: Int,
        primary: ExoPlayer,
    ) {
        val backup = shortsBackup ?: return
        shortsBackup = null
        loadIntoSlot(
            slotId = 1,
            item = backup.item,
            context = context,
            scope = scope,
            maxVideoHeight = maxVideoHeight,
            primary = primary,
            secondaryProvider = { PlaybackSessionHost.obtainSecondaryPlayer(context) },
            resumeMs = backup.positionMs,
            autoPlay = true,
        )
    }

    fun releaseAll(primary: ExoPlayer, stopPrimary: Boolean) {
        slot1Job?.cancel()
        slot2Job?.cancel()
        shortsBackup = null
        if (stopPrimary) {
            primary.stop()
            primary.clearMediaItems()
        }
        primary.volume = 1f
        primary.setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
        PlaybackSessionHost.releaseSecondaryPlayer()
        secondaryAllocated = false
        model.reset()
    }
}

internal fun PlaybackTrackState.copyFrom(other: PlaybackTrackState) {
    qualityMode = other.qualityMode
    quality = other.quality
    selectedAudio = other.selectedAudio
    forceDirectPlayback = other.forceDirectPlayback
    manualAudioOverride = other.manualAudioOverride
    attemptedAudioFallback = other.attemptedAudioFallback
    selectedSubtitle = other.selectedSubtitle
    lastSubtitle = other.lastSubtitle
    attachedSubtitle = other.attachedSubtitle
    attachedSubtitleVideo = other.attachedSubtitleVideo
}
