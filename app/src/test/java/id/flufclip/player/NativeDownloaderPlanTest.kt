package id.flufclip.player

import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

class NativeDownloaderPlanTest {
    private fun infoWith(
        type: StreamType = StreamType.VIDEO_STREAM,
        videoStreams: List<VideoStream> = emptyList(),
        videoOnlyStreams: List<VideoStream> = emptyList(),
        audioStreams: List<AudioStream> = emptyList(),
    ): StreamInfo = StreamInfo(
        0,
        "https://www.youtube.com/watch?v=test1234567",
        "https://www.youtube.com/watch?v=test1234567",
        type,
        "test1234567",
        "Test",
        0,
    ).apply {
        duration = 120
        this.videoStreams = videoStreams
        this.videoOnlyStreams = videoOnlyStreams
        this.audioStreams = audioStreams
    }

    private fun video(id: String, height: Int, format: MediaFormat, videoOnly: Boolean, bitrate: Int): VideoStream =
        VideoStream.Builder()
            .setId(id)
            .setContent("https://example.com/$id", true)
            .setMediaFormat(format)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setResolution("${height}p")
            .setIsVideoOnly(videoOnly)
            .setItagItem(buildVideoItagItem(id.toInt(), !videoOnly, format, "${height}p", height = height, bitrateBps = bitrate))
            .build()

    private fun audio(id: String, format: MediaFormat, bitrate: Int): AudioStream =
        AudioStream.Builder()
            .setId(id)
            .setContent("https://example.com/$id", true)
            .setMediaFormat(format)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setAverageBitrate(bitrate / 1000)
            .setItagItem(buildAudioItagItem(id.toInt(), format, bitrate / 1000, bitrateBps = bitrate))
            .build()

    @Test fun selectsMuxMp4ForAdaptive1080pWithM4aAudio() {
        val info = infoWith(
            videoStreams = listOf(video("18", 360, MediaFormat.MPEG_4, false, 600_000)),
            videoOnlyStreams = listOf(video("137", 1080, MediaFormat.MPEG_4, true, 4_000_000)),
            audioStreams = listOf(
                audio("140", MediaFormat.M4A, 128_000),
                audio("251", MediaFormat.WEBMA, 160_000),
            ),
        )
        val plan = selectRustDirectDownloadPlan(info, maxHeight = 1080, audioOnly = false)
        assertEquals(RustDownloadPlan.MuxMp4("https://example.com/137", "https://example.com/140"), plan)
    }

    @Test fun selectsCombinedMp4WhenRequestedHeightMatchesProgressive() {
        val info = infoWith(
            videoStreams = listOf(video("18", 360, MediaFormat.MPEG_4, false, 600_000)),
            videoOnlyStreams = listOf(video("137", 1080, MediaFormat.MPEG_4, true, 4_000_000)),
            audioStreams = listOf(audio("140", MediaFormat.M4A, 128_000)),
        )
        val plan = selectRustDirectDownloadPlan(info, maxHeight = 360, audioOnly = false)
        assertEquals(RustDownloadPlan.CombinedMp4("https://example.com/18"), plan)
    }

    @Test fun fallsBackToYtDlpWhenBestQualityIsHigherResWebmOrAudioOnlyMp3() {
        val info = infoWith(
            videoOnlyStreams = listOf(
                video("137", 1080, MediaFormat.MPEG_4, true, 4_000_000),
                video("271", 1440, MediaFormat.WEBM, true, 8_000_000),
            ),
            audioStreams = listOf(audio("140", MediaFormat.M4A, 128_000)),
        )
        assertNull(selectRustDirectDownloadPlan(info, maxHeight = 0, audioOnly = false))
        assertNull(selectRustDirectDownloadPlan(info, maxHeight = 1080, audioOnly = true))
        assertEquals(
            RustDownloadPlan.MuxMp4("https://example.com/137", "https://example.com/140"),
            selectRustDirectDownloadPlan(info, maxHeight = 1080, audioOnly = false),
        )
    }

    @Test fun rejectsLiveStreams() {
        val live = infoWith(
            type = StreamType.LIVE_STREAM,
            videoStreams = listOf(video("18", 360, MediaFormat.MPEG_4, false, 600_000)),
        )
        assertNull(selectRustDirectDownloadPlan(live, maxHeight = 0, audioOnly = false))
    }
}
