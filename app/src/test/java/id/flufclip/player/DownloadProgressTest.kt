package id.flufclip.player

import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

class DownloadProgressTest {
    private fun mbFormat(bytes: Long): String = "${bytes / (1000L * 1000L)} MB"

    private fun testStreamInfo(
        durationSeconds: Long = 100L,
        videoStreams: List<VideoStream> = emptyList(),
        videoOnlyStreams: List<VideoStream> = emptyList(),
        audioStreams: List<AudioStream> = emptyList()
    ): StreamInfo {
        return StreamInfo(
            0,
            "https://example.com/watch?v=test",
            "https://example.com/watch?v=test",
            StreamType.VIDEO_STREAM,
            "test",
            "Test video",
            0
        ).apply {
            duration = durationSeconds
            this.videoStreams = videoStreams
            this.videoOnlyStreams = videoOnlyStreams
            this.audioStreams = audioStreams
        }
    }

    @Test fun metadataAndUnknownDownloadLinesDoNotInventZeroPercent() {
        assertNull(downloadSignal("[youtube] Extracting URL", -1))
        val transfer = downloadSignal("[download] Destination: video.mp4", -1)!!
        assertEquals(DownloadPhase.TRANSFERRING, transfer.phase)
        assertNull(transfer.percent)
        assertNull(transfer.etaSeconds)
        assertTrue(transfer.newDestination)
    }
    @Test fun transferPercentAndEtaAreOnlyUsedForMatchingLines() {
        val exact = downloadSignal("[download] 42.5% of 10MiB ETA 00:12", 12)!!
        assertEquals(42, exact.percent)
        assertEquals(12L, exact.etaSeconds)
        assertEquals(10L * 1024L * 1024L, exact.totalBytes)
        assertFalse(exact.totalEstimated)
        assertNotNull(exact.downloadedBytes)

        val estimated = downloadSignal("[download] 50.0% of ~10.50MiB at 1.00MiB/s ETA 00:05", 5)!!
        assertEquals(50, estimated.percent)
        assertEquals(5L, estimated.etaSeconds)
        assertTrue(estimated.totalEstimated)
        assertEquals((10.5 * 1024.0 * 1024.0).toLong(), estimated.totalBytes)

        val end = downloadSignal("[download] 100% of 10MiB in 00:05", 12)!!
        assertEquals(100, end.percent)
        assertNull(end.etaSeconds)
        assertEquals(DownloadPhase.TRANSFERRING, end.phase)
        assertEquals(10L * 1024L * 1024L, end.downloadedBytes)
        assertEquals(10L * 1024L * 1024L, end.totalBytes)
    }
    @Test fun templateBytesMarkerOverridesTextAndHandlesEstimatedOrUnknownTotal() {
        val exactMarker = downloadSignal(
            "[download]  25.0% of   10.00MiB at    2.00MiB/s ETA 00:04 |bytes=2621440/10485760/0|",
            4
        )!!
        assertEquals(25, exactMarker.percent)
        assertEquals(2_621_440L, exactMarker.downloadedBytes)
        assertEquals(10_485_760L, exactMarker.totalBytes)
        assertFalse(exactMarker.totalEstimated)

        val estimatedMarker = downloadSignal(
            "[download]  20.0% of ~ 10.00MiB at    1.00MiB/s ETA 00:08 |bytes=2000000/0/10000000|",
            8
        )!!
        assertEquals(20, estimatedMarker.percent)
        assertEquals(2_000_000L, estimatedMarker.downloadedBytes)
        assertEquals(10_000_000L, estimatedMarker.totalBytes)
        assertTrue(estimatedMarker.totalEstimated)

        val unknownTotalMarker = downloadSignal(
            "[download]  N/A% of N/A at    1.00MiB/s ETA Unknown |bytes=3000000/0/0|",
            -1
        )!!
        assertNull(unknownTotalMarker.percent)
        assertEquals(3_000_000L, unknownTotalMarker.downloadedBytes)
        assertNull(unknownTotalMarker.totalBytes)
        assertFalse(unknownTotalMarker.totalEstimated)
    }
    @Test fun transferProgressMessageFormatsPercentAndDownloadedSizes() {
        assertEquals(
            "Downloading · 42% · 4 MB / 10 MB",
            formatTransferProgressMessage("Downloading", 42, 4_000_000L, 10_000_000L, false, ::mbFormat)
        )
        assertEquals(
            "Downloading · 42% · 4 MB / ~10 MB",
            formatTransferProgressMessage("Downloading", 42, 4_000_000L, 10_000_000L, true, ::mbFormat)
        )
        assertEquals(
            "Downloading · 4 MB",
            formatTransferProgressMessage("Downloading", null, 4_000_000L, null, false, ::mbFormat)
        )
        assertEquals(
            "Downloading audio · 75%",
            formatTransferProgressMessage("Downloading audio", 75, null, null, false, ::mbFormat)
        )
        assertEquals(
            "Downloading…",
            formatTransferProgressMessage("Downloading", null, null, null, false, ::mbFormat)
        )
    }
    @Test fun downloadQualityOptionsShowOnlyAvailableQualitiesAndEstimateSizes() {
        val v1080 = VideoStream.Builder()
            .setId("137")
            .setContent("https://example.com/1080", true)
            .setMediaFormat(MediaFormat.MPEG_4)
            .setResolution("1080p")
            .setIsVideoOnly(true)
            .setItagItem(
                buildVideoItagItem(
                    itagId = 137,
                    hasAudio = false,
                    mediaFormat = MediaFormat.MPEG_4,
                    resolution = "1080p",
                    width = 1920,
                    height = 1080,
                    fps = 30,
                    bitrateBps = 4_000_000,
                    contentLength = 50_000_000L
                )
            )
            .build()
        val v720BitrateOnly = VideoStream.Builder()
            .setId("136")
            .setContent("https://example.com/720", true)
            .setMediaFormat(MediaFormat.MPEG_4)
            .setResolution("720p")
            .setIsVideoOnly(true)
            .setItagItem(
                buildVideoItagItem(
                    itagId = 136,
                    hasAudio = false,
                    mediaFormat = MediaFormat.MPEG_4,
                    resolution = "720p",
                    width = 1280,
                    height = 720,
                    fps = 30,
                    bitrateBps = 2_000_000,
                    contentLength = -1L
                )
            )
            .build()
        val v360Progressive = VideoStream.Builder()
            .setId("18")
            .setContent("https://example.com/360", true)
            .setMediaFormat(MediaFormat.MPEG_4)
            .setResolution("360p")
            .setIsVideoOnly(false)
            .setItagItem(
                buildVideoItagItem(
                    itagId = 18,
                    hasAudio = true,
                    mediaFormat = MediaFormat.MPEG_4,
                    resolution = "360p",
                    width = 640,
                    height = 360,
                    fps = 30,
                    bitrateBps = 800_000,
                    contentLength = 12_000_000L
                )
            )
            .build()
        val audio128 = AudioStream.Builder()
            .setId("140")
            .setContent("https://example.com/audio", true)
            .setMediaFormat(MediaFormat.M4A)
            .setAverageBitrate(128)
            .setItagItem(
                buildAudioItagItem(
                    itagId = 140,
                    mediaFormat = MediaFormat.M4A,
                    avgBitrateKbps = 128,
                    bitrateBps = 128_000,
                    contentLength = 5_000_000L
                )
            )
            .build()

        val options = buildDownloadQualityOptions(
            testStreamInfo(
                durationSeconds = 100L,
                videoStreams = listOf(v360Progressive),
                videoOnlyStreams = listOf(v1080, v720BitrateOnly),
                audioStreams = listOf(audio128)
            )
        )

        val heights = options.map { it.height }
        assertFalse(heights.contains(2160))
        assertFalse(heights.contains(1440))
        assertFalse(heights.contains(480))
        assertTrue(heights.containsAll(listOf(0, 1080, 720, 360, -1)))

        val best = options.first { it.height == 0 }
        val opt1080 = options.first { it.height == 1080 }
        val opt720 = options.first { it.height == 720 }
        val opt360 = options.first { it.height == 360 }
        val audioOnly = options.first { it.isAudioOnly }

        assertEquals(55_000_000L, opt1080.estimatedBytes)
        assertEquals(opt1080.estimatedBytes, best.estimatedBytes)
        // 720p bitrate fallback: (2_000_000 bps * 100s / 8 = 25_000_000) + 5_000_000 audio = 30_000_000
        assertEquals(30_000_000L, opt720.estimatedBytes)
        // 360p progressive stream includes audio: 12_000_000
        assertEquals(12_000_000L, opt360.estimatedBytes)
        assertEquals(5_000_000L, audioOnly.estimatedBytes)
    }
    @Test fun hlsOnlyStreamsWithoutSizeOrBitrateLeaveEstimateNull() {
        val hlsOnly = VideoStream.Builder()
            .setId("hls-480")
            .setContent("https://example.com/480.m3u8", true)
            .setDeliveryMethod(DeliveryMethod.HLS)
            .setMediaFormat(MediaFormat.MPEG_4)
            .setResolution("480p")
            .setIsVideoOnly(false)
            .build()

        val options = buildDownloadQualityOptions(
            testStreamInfo(
                durationSeconds = 60L,
                videoStreams = listOf(hlsOnly)
            )
        )

        val opt480 = options.first { it.height == 480 }
        assertNull(opt480.estimatedBytes)
        assertNull(options.first { it.height == 0 }.estimatedBytes)
        val audioOnly = options.firstOrNull { it.isAudioOnly }
        assertNotNull(audioOnly)
        assertNull(audioOnly!!.estimatedBytes)
        assertTrue(buildDownloadQualityOptions(testStreamInfo()).isEmpty())
    }
    @Test fun ffmpegAndMergeNeverReuseTransferEtaOrFakeNinetyNinePercent() {
        listOf("[Merger] Merging formats", "[ExtractAudio] Destination: audio.mp3", "size= 1234kB time=00:00:01").forEach { line ->
            val signal = downloadSignal(line, 2)!!
            assertEquals(DownloadPhase.PROCESSING, signal.phase)
            assertNull(signal.percent)
            assertNull(signal.etaSeconds)
        }
    }
    @Test fun completedOrInvalidResumePositionsRestartInsteadOfOpeningAtEnd() {
        assertEquals(0L, downloadResumePosition(-1, 10000))
        assertEquals(0L, downloadResumePosition(10000, 10000))
        assertEquals(3000L, downloadResumePosition(3000, 10000))
        assertEquals(3000L, downloadResumePosition(3000, 0))
    }
    @Test fun legacySavedFileKeepsItsNameWithoutNeedingOnlineMetadata() {
        val legacy = SavedDownload("content://downloads/1", "video.mp4", "video/mp4", 10)
        assertEquals("video.mp4", legacy.title)
        assertEquals("Original title", legacy.copy(video = DownloadVideo(title = "Original title")).title)
    }
    @Test fun downloadRowDisplaySimplifiesPostTransferPhasesIntoFinalizing() {
        val preparing = downloadRowDisplay(DownloadState(running = true, phase = DownloadPhase.PREPARING, message = "Preparing download…"))
        assertEquals("Preparing…", preparing.statusText)
        assertNull(preparing.progressPercent)

        val transferring = downloadRowDisplay(
            DownloadState(running = true, phase = DownloadPhase.TRANSFERRING, message = "Downloading · 42% · 4 MB / 10 MB", progress = 42, etaSeconds = 9)
        )
        assertEquals("Downloading · 42% · 4 MB / 10 MB · 9s left", transferring.statusText)
        assertEquals(42, transferring.progressPercent)

        listOf(DownloadPhase.PROCESSING, DownloadPhase.COPYING, DownloadPhase.VERIFYING).forEach { phase ->
            val display = downloadRowDisplay(DownloadState(running = true, phase = phase, message = "Saving file · 80%", progress = 80))
            assertEquals("Finalizing…", display.statusText)
            assertNull(display.progressPercent)
            assertFalse(display.isError)
        }

        val failed = downloadRowDisplay(DownloadState(running = false, phase = DownloadPhase.FAILED, message = "Network error"))
        assertEquals("Network error", failed.statusText)
        assertTrue(failed.isError)

        val canceled = downloadRowDisplay(DownloadState(running = false, phase = DownloadPhase.CANCELLED, message = ""))
        assertEquals("Download canceled.", canceled.statusText)
        assertTrue(canceled.isCancelled)
    }
}
