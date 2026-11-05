package id.flufclip.player

import org.junit.Assert.*
import org.junit.Test

class DownloadProgressTest {
    @Test fun metadataAndUnknownDownloadLinesDoNotInventZeroPercent() {
        assertNull(downloadSignal("[youtube] Extracting URL", -1))
        val transfer = downloadSignal("[download] Destination: video.mp4", -1)!!
        assertEquals(DownloadPhase.TRANSFERRING, transfer.phase)
        assertNull(transfer.percent)
        assertNull(transfer.etaSeconds)
    }
    @Test fun transferPercentAndEtaAreOnlyUsedForMatchingLines() {
        assertEquals(42, downloadSignal("[download] 42.5% of 10MiB ETA 00:12", 12)!!.percent)
        assertEquals(12L, downloadSignal("[download] 42.5% of 10MiB ETA 00:12", 12)!!.etaSeconds)
        val end = downloadSignal("[download] 100% of 10MiB in 00:05", 12)!!
        assertEquals(100, end.percent)
        assertNull(end.etaSeconds)
        assertEquals(DownloadPhase.TRANSFERRING, end.phase)
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
}
