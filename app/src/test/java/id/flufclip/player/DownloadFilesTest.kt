package id.flufclip.player

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun generatedNamesStayInsideTheDefaultFolder() {
        val name = downloadFileName("../contoh/video: bagus?", false)
        assertEquals(name, File(name).name)
        assertTrue(name.endsWith(".mp4"))
        assertEquals("video.mp3", downloadFileName("...", true))
        assertEquals("video-yt-downloader", DOWNLOAD_SUBDIRECTORY)
    }

    @Test fun unicodeNamesFitFilesystemLimitsIncludingDuplicateSuffix() {
        val name = downloadFileName("字".repeat(300), false)
        assertTrue(name.toByteArray(Charsets.UTF_8).size + 7 <= 255)
        assertTrue(name.endsWith(".mp4"))
    }

    @Test fun repeatedDownloadsNeverOverwriteExistingFiles() {
        val directory = File(temporary.root, DOWNLOAD_SUBDIRECTORY)
        val original = reserveDownloadFile(directory, "video.mp4")
        original.writeText("keep original video")
        val second = reserveDownloadFile(directory, "video.mp4")
        second.writeText("second")
        val third = reserveDownloadFile(directory, "video.mp4")
        assertEquals("video (1).mp4", second.name)
        assertEquals("video (2).mp4", third.name)
        assertEquals("keep original video", original.readText())
        assertEquals("second", second.readText())
        assertEquals(directory.canonicalFile, third.parentFile.canonicalFile)
    }

    @Test fun reservationRejectsTraversalAndNonDirectoryDestinations() {
        assertThrows(IllegalArgumentException::class.java) { reserveDownloadFile(temporary.root, "../outside.mp4") }
        val notDirectory = temporary.newFile("existing-file")
        assertThrows(IllegalStateException::class.java) { reserveDownloadFile(notDirectory, "video.mp4") }
    }
}
