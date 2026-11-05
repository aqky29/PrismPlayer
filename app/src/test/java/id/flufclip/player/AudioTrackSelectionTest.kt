package id.flufclip.player

import androidx.media3.common.C
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import java.util.Locale

class AudioTrackSelectionTest {
    @Test fun hlsRemainsAdaptiveUnlessTheCurrentSourceIsDash() {
        assertFalse(allowAutomaticDirectAudioFallback(hasHls = true, hasDash = false))
        assertTrue(allowAutomaticDirectAudioFallback(hasHls = false, hasDash = true))
        assertTrue(allowAutomaticDirectAudioFallback(hasHls = true, hasDash = true))
    }
    private fun track(id: String, tag: String?, type: AudioTrackType?): AudioStream = AudioStream.Builder()
        .setId(id).setContent("https://example.test/$id", true).setMediaFormat(MediaFormat.M4A)
        .withAudioMetadata(AudioTrackMetadata(id = id, locale = audioLocale(tag), type = type)).build()

    private val english = track("english", "en-US", AudioTrackType.ORIGINAL)
    private val indonesian = track("indonesian", "id-ID", AudioTrackType.DUBBED)
    private val description = track("description", "id", AudioTrackType.DESCRIPTIVE)
    private val device = Locale.US

    @Test fun indonesianWinsOverEnglishOriginalEvenOnAnEnglishDevice() {
        assertSame(indonesian, selectAudioTrack(listOf(english, description, indonesian), AudioPreference.INDONESIAN, device))
        assertEquals(listOf("id", "en-US"), preferredAudioLanguages(AudioPreference.INDONESIAN, device, listOf(english, indonesian)))
    }

    @Test fun unavailableLanguageFallsBackToOriginalRatherThanDescription() {
        assertSame(english, selectAudioTrack(listOf(description, english), AudioPreference.INDONESIAN, device))
        assertSame(english, selectAudioTrack(listOf(english), AudioPreference.INDONESIAN, device))
    }

    @Test fun originalAndDeviceModesFollowTheirOwnPreferences() {
        assertSame(english, selectAudioTrack(listOf(indonesian, english), AudioPreference.ORIGINAL, Locale("id", "ID")))
        assertSame(indonesian, selectAudioTrack(listOf(english, indonesian), AudioPreference.DEVICE, Locale("id", "ID")))
        assertEquals(listOf("id-ID", "en-US"), preferredAudioLanguages(AudioPreference.DEVICE, Locale("id", "ID"), listOf(english, indonesian)))
        assertEquals(listOf("en-US"), preferredAudioLanguages(AudioPreference.ORIGINAL, device, listOf(english)))
        assertTrue(preferredAudioLanguages(AudioPreference.ORIGINAL, device, emptyList()).isEmpty())
    }

    @Test fun languageVariantsAndLegacyIndonesianTagsMatch() {
        assertEquals("id", audioLocale("in_ID")?.language)
        assertEquals("id", audioLocale("id-desc")?.language)
        assertNull(audioLocale("und"))
        assertNull(audioLocale("not a language"))
        assertEquals(AudioPreference.INDONESIAN, AudioPreference.fromKey(null))
        assertEquals(AudioPreference.ORIGINAL, AudioPreference.fromKey("original"))
    }

    @Test fun youtubeMetadataPreservesTracksWithoutMistakingDefaultForOriginal() {
        val original = innerTubeAudioMetadata(JSONObject("""{"audioTrack":{"id":"en-US.4","displayName":"English (original)","audioIsDefault":true}}"""))
        val dub = innerTubeAudioMetadata(JSONObject("""{"audioTrack":{"id":"id-ID.3","displayName":"Indonesian","audioIsDefault":true}}"""))
        assertEquals("en-US.4", original.id)
        assertEquals("en", original.locale?.language)
        assertEquals(AudioTrackType.ORIGINAL, original.type)
        assertEquals("id", dub.locale?.language)
        assertNull(dub.type)
        assertEquals(AudioTrackMetadata(), innerTubeAudioMetadata(JSONObject("{}")))
    }

    @Test fun ytdlpMetadataRetainsLanguagesAndDistinguishesOriginalAndDescriptions() {
        val original = ytDlpAudioMetadata(JSONObject("""{"language":"en-US","language_preference":10}"""))
        val dub = ytDlpAudioMetadata(JSONObject("""{"language":"id-ID","language_preference":-1}"""))
        val described = ytDlpAudioMetadata(JSONObject("""{"language":"id-desc","language_preference":-10}"""))
        val default = ytDlpAudioMetadata(JSONObject("""{"language":"id","language_preference":5}"""))
        assertEquals(AudioTrackType.ORIGINAL, original.type)
        assertEquals(AudioTrackType.DUBBED, dub.type)
        assertEquals("id", dub.locale?.language)
        assertEquals(AudioTrackType.DESCRIPTIVE, described.type)
        assertNull(default.type)
        assertEquals(AudioTrackMetadata(), ytDlpAudioMetadata(JSONObject("{}")))
        assertNotEquals(original.id, dub.id)
    }

    @Test fun manifestTracksRemainSelectableWithoutExtractorAudioMetadata() {
        val english = listOf(ManifestAudioFormat("english", 0, 0, "en", null, 0, false), ManifestAudioFormat("english", 0, 1, "en", null, 0, true))
        val indonesianFormat = ManifestAudioFormat("indonesian", 1, 0, "id-ID", null, 0, false)
        val description = ManifestAudioFormat("description", 2, 0, "id", null, C.ROLE_FLAG_DESCRIBES_VIDEO, false)
        val choices = manifestAudioChoices(english + indonesianFormat + description)
        assertEquals(3, choices.size)
        assertEquals(listOf(0, 1), choices.first { it.group == "english" }.indices)
        assertTrue(choices.first { it.group == "english" }.selected)
        assertTrue(choices.first { it.group == "indonesian" }.label.contains("Indonesian"))
        assertTrue(choices.first { it.group == "description" }.label.contains("description", ignoreCase = true))
        assertTrue(shouldUseSeparateAudio(english, indonesian, AudioPreference.INDONESIAN, device))
        assertTrue(shouldUseSeparateAudio(english + description, indonesian, AudioPreference.INDONESIAN, device))
        assertFalse(shouldUseSeparateAudio(english + indonesianFormat, indonesian, AudioPreference.INDONESIAN, device))
        val unknown = ManifestAudioFormat("unknown", 3, 0, null, null, 0, true)
        assertFalse(shouldUseSeparateAudio(english + unknown, indonesian, AudioPreference.INDONESIAN, device))
        assertFalse(shouldUseSeparateAudio(emptyList(), indonesian, AudioPreference.INDONESIAN, device))
        assertFalse(shouldUseSeparateAudio(english, this.description, AudioPreference.INDONESIAN, device))
    }
}
