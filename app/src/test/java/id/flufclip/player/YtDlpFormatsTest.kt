package id.flufclip.player

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.stream.AudioTrackType

class YtDlpFormatsTest {
    private val formats = JSONArray(
        """
        [
          {"format_id":"18","url":"https://example.test/en.mp4","vcodec":"avc1","acodec":"mp4a","height":360,
           "language":"en-US","language_preference":10,"format_note":"360p"},
          {"format_id":"18-id","url":"https://example.test/id.mp4","vcodec":"avc1","acodec":"mp4a","height":360,
           "language":"id-ID","language_preference":-1,"format_note":"360p"},
          {"format_id":"137","url":"https://example.test/video.mp4","vcodec":"avc1","acodec":"none","height":1080},
          {"format_id":"140-id","url":"https://example.test/audio.m4a","vcodec":"none","acodec":"mp4a","abr":128,
           "language":"id-ID","language_preference":-1}
        ]
        """.trimIndent(),
    )

    @Test fun combinedFormatsExposeTheirLanguageAsEmbeddedAudio() {
        val parsed = parseYtDlpFormats(formats)
        assertEquals(2, parsed.progressive.size)
        assertEquals(1, parsed.videoOnly.size)
        assertEquals(1, parsed.audio.size)
        assertEquals(2, parsed.embeddedAudio.size)

        val indonesian = parsed.embeddedAudio.single { it.metadata.locale?.language == "id" }
        assertEquals(AudioTrackType.DUBBED, indonesian.metadata.type)
        assertEquals("https://example.test/id.mp4", indonesian.video.content)
        val english = parsed.embeddedAudio.single { it.metadata.locale?.language == "en" }
        assertEquals(AudioTrackType.ORIGINAL, english.metadata.type)
    }

    @Test fun separateAudioKeepsItsLanguageAndIsNotTreatedAsEmbedded() {
        val parsed = parseYtDlpFormats(formats)
        val audio = parsed.audio.single()
        assertEquals("id", audio.audioLocale?.language)
        assertEquals(AudioTrackType.DUBBED, audio.audioTrackType)
        assertTrue(parsed.embeddedAudio.none { it.video.content == audio.content })
    }

    @Test fun formatsWithoutLanguageFieldsStayUsable() {
        val parsed = parseYtDlpFormats(JSONArray("""[{"format_id":"22","url":"https://example.test/plain.mp4","vcodec":"avc1","acodec":"mp4a","height":720}]"""))
        assertEquals(1, parsed.progressive.size)
        assertTrue(parsed.embeddedAudio.isEmpty())
        assertFalse(parsed.progressive.single().isVideoOnly)
    }
}
