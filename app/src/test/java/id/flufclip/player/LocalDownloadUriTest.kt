package id.flufclip.player

import org.junit.Assert.*
import org.junit.Test

class LocalDownloadUriTest {
    @Test fun savedDocumentsAndLocalFilesCanPlayOffline() {
        assertTrue(isLocalDownloadUri("content://com.android.externalstorage.documents/document/primary%3ADownload%2Fvideo.mp4"))
        assertTrue(isLocalDownloadUri("file:///storage/emulated/0/Download/video.mp4"))
    }
    @Test fun onlineAndRemoteFileLocationsNeverBecomeOfflinePlayback() {
        listOf("https://www.youtube.com/watch?v=test", "https://cdn.example/video.mp4", "http://localhost/video", "file://server/video.mp4", "data:video/mp4,abc").forEach { assertFalse(it, isLocalDownloadUri(it)) }
    }
    @Test fun invalidOrMissingDocumentLocationsAreRejected() {
        listOf("", "/storage/video.mp4", "content:///document", "content://", "file:relative.mp4", "not a uri").forEach { assertFalse(it, isLocalDownloadUri(it)) }
    }
}
