package id.flufclip.player

import androidx.media3.common.C
import androidx.media3.common.Tracks
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import java.util.Locale

/** A playback preference, independent of the language used by the app or Android UI. */
internal enum class AudioPreference(val key: String, val label: String) {
    INDONESIAN("indonesian", "Bahasa Indonesia"),
    ORIGINAL("original", "Original audio"),
    DEVICE("device", "Device language");

    companion object {
        fun fromKey(key: String?): AudioPreference = entries.firstOrNull { it.key == key } ?: INDONESIAN
    }
}

/** Keep an available HLS source adaptive instead of silently switching it to direct streams. */
internal fun allowAutomaticDirectAudioFallback(hasHls: Boolean, hasDash: Boolean): Boolean = !hasHls || hasDash

internal data class AudioTrackMetadata(
    val id: String? = null,
    val name: String? = null,
    val locale: Locale? = null,
    val type: AudioTrackType? = null,
)

internal data class ManifestAudioFormat<T>(
    val group: T,
    val groupIndex: Int,
    val index: Int,
    val language: String?,
    val label: String?,
    val roleFlags: Int,
    val selected: Boolean,
)

internal data class ManifestAudioChoice<T>(
    val group: T,
    val indices: List<Int>,
    val label: String,
    val key: String,
    val selected: Boolean,
)

/** HLS/DASH may publish audio tracks only in the manifest, not in StreamInfo.audioStreams. */
internal fun manifestAudioFormats(tracks: Tracks): List<ManifestAudioFormat<Tracks.Group>> = buildList {
    tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEachIndexed { groupIndex, group ->
        (0 until group.length).filter(group::isTrackSupported).forEach { index ->
            val format = group.getTrackFormat(index)
            add(ManifestAudioFormat(group, groupIndex, index, format.language, format.label, format.roleFlags, group.isTrackSelected(index)))
        }
    }
}

internal fun <T> manifestAudioChoices(formats: List<ManifestAudioFormat<T>>): List<ManifestAudioChoice<T>> {
    val groups = formats.groupBy { format ->
        val language = audioLocale(format.language)?.toLanguageTag().orEmpty()
        val label = format.label?.trim().orEmpty()
        val key = if (language.isBlank() && label.isBlank()) "unknown:${format.groupIndex}"
            else "$language|${label.lowercase(Locale.ROOT)}|${format.roleFlags}"
        format.groupIndex to key
    }
    return groups.map { (identity, variants) ->
        val first = variants.first()
        val locale = audioLocale(first.language)
        var name = first.label?.trim()?.takeIf { it.isNotEmpty() }
            ?: locale?.getDisplayName(Locale.ENGLISH)?.takeIf { it.isNotBlank() }
            ?: "Audio track ${first.groupIndex + 1}"
        if (first.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0 && !name.contains("description", true)) name += " · Audio description"
        ManifestAudioChoice(first.group, variants.map { it.index }, name, identity.second, variants.any { it.selected })
    }.sortedByDescending { it.selected }.distinctBy { it.key }
}

private fun JSONObject.textOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

/** Audio tags can include a region (id-ID), legacy underscores, or yt-dlp's -desc suffix. */
internal fun audioLocale(tag: String?): Locale? {
    val cleaned = tag?.trim()?.replace('_', '-')?.removeSuffix("-desc") ?: return null
    if (!Regex("^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$").matches(cleaned)) return null
    return Locale.forLanguageTag(cleaned).takeIf { it.language.isNotBlank() && it.language != "und" }
}

internal fun audioLanguageTag(audio: AudioStream?): String? =
    audio?.audioLocale?.toLanguageTag()?.takeIf { it.isNotBlank() && it != "und" }

internal fun audioTrackIdentity(audio: AudioStream): String =
    audio.audioTrackId?.takeIf { it.isNotBlank() }
        ?: listOf(audioLanguageTag(audio), audio.audioTrackType?.name, audio.audioTrackName).joinToString("|")

private fun isMainAudio(type: AudioTrackType?): Boolean =
    type != AudioTrackType.DESCRIPTIVE && type != AudioTrackType.SECONDARY

private fun desiredAudioLanguage(preference: AudioPreference, device: Locale): String? = when (preference) {
    AudioPreference.INDONESIAN -> "id"
    AudioPreference.ORIGINAL -> null
    AudioPreference.DEVICE -> device.toLanguageTag().takeIf { it.isNotBlank() && it != "und" }
}

private fun <T> selectByLanguage(
    tracks: List<T>, preference: AudioPreference, device: Locale,
    locale: (T) -> Locale?, type: (T) -> AudioTrackType?,
): T? {
    val normal = tracks.filter { isMainAudio(type(it)) }
    val desired = audioLocale(desiredAudioLanguage(preference, device))
    if (desired != null) {
        val matching = normal.filter { locale(it)?.language == desired.language }
        matching.minWithOrNull(compareBy<T>(
            { if (locale(it)?.toLanguageTag()?.equals(desired.toLanguageTag(), true) == true) 0 else 1 },
            { if (type(it) == AudioTrackType.ORIGINAL) 0 else 1 },
        ))?.let { return it }
    }
    return normal.firstOrNull { type(it) == AudioTrackType.ORIGINAL }
        ?: normal.firstOrNull()
        ?: tracks.firstOrNull()
}

/** Pick the requested language when it exists; otherwise keep the original or the first ordinary track. */
internal fun selectAudioTrack(tracks: List<AudioStream>, preference: AudioPreference, device: Locale): AudioStream? =
    selectByLanguage(tracks, preference, device, { it.audioLocale }, { it.audioTrackType })

internal fun selectEmbeddedAudio(tracks: List<EmbeddedAudioTrack>, preference: AudioPreference, device: Locale): EmbeddedAudioTrack? =
    selectByLanguage(tracks, preference, device, { it.metadata.locale }, { it.metadata.type })

/** Media3 can discover languages in an HLS/DASH manifest even when the extractor has no AudioStream list. */
internal fun preferredAudioLanguages(preference: AudioPreference, device: Locale, tracks: List<AudioStream>): List<String> {
    val original = tracks.firstOrNull { it.audioTrackType == AudioTrackType.ORIGINAL }?.let(::audioLanguageTag)
    return listOfNotNull(desiredAudioLanguage(preference, device), original).distinctBy { it.lowercase(Locale.ROOT) }
}

/** If the manifest lacks an available preferred language, a direct audio stream can still provide it. */
internal fun shouldUseSeparateAudio(formats: List<ManifestAudioFormat<*>>, audio: AudioStream?, preference: AudioPreference, device: Locale): Boolean {
    if (audio == null) return false
    return manifestMissingPreferredAudio(formats, audio.audioLocale, audio.audioTrackType, preference, device)
}

internal fun shouldUseEmbeddedAudio(formats: List<ManifestAudioFormat<*>>, audio: EmbeddedAudioTrack?, preference: AudioPreference, device: Locale): Boolean {
    if (audio == null) return false
    return manifestMissingPreferredAudio(formats, audio.metadata.locale, audio.metadata.type, preference, device)
}

private fun manifestMissingPreferredAudio(
    formats: List<ManifestAudioFormat<*>>, locale: Locale?, type: AudioTrackType?, preference: AudioPreference, device: Locale,
): Boolean {
    if (!isMainAudio(type)) return false
    val desired = if (preference == AudioPreference.ORIGINAL) {
        locale?.toLanguageTag()?.takeIf { type == AudioTrackType.ORIGINAL }
    } else desiredAudioLanguage(preference, device)
    val desiredLocale = audioLocale(desired) ?: return false
    if (locale?.language != desiredLocale.language) return false
    // An unlabeled main track may already be the requested language. Its absence is not proven.
    if (formats.any { it.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO == 0 && audioLocale(it.language) == null }) return false
    return formats.isNotEmpty() && formats.none {
        it.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO == 0 && audioLocale(it.language)?.language == desiredLocale.language
    }
}

/** Preserve youtubei's actual track id; audioIsDefault alone does not mean the track is original. */
internal fun innerTubeAudioMetadata(format: JSONObject): AudioTrackMetadata {
    val track = format.optJSONObject("audioTrack") ?: return AudioTrackMetadata()
    val id = track.textOrNull("id")
    val name = track.textOrNull("displayName")
    val typeFromXtags = format.textOrNull("xtags")?.let {
        runCatching { YoutubeParsingHelper.extractAudioTrackType(it) }.getOrNull()
    }
    val type = typeFromXtags ?: when {
        name?.contains("descriptive", ignoreCase = true) == true -> AudioTrackType.DESCRIPTIVE
        name?.contains("original", ignoreCase = true) == true -> AudioTrackType.ORIGINAL
        name?.contains("dubbed", ignoreCase = true) == true -> AudioTrackType.DUBBED
        else -> null
    }
    return AudioTrackMetadata(id, name, audioLocale(id?.substringBefore('.')), type)
}

/** yt-dlp exposes language metadata in its JSON, but its Android VideoFormat mapper drops these fields. */
internal fun ytDlpAudioMetadata(format: JSONObject): AudioTrackMetadata {
    val language = format.textOrNull("language")
    val locale = audioLocale(language)
    val note = format.textOrNull("format_note")
    val preference = if (format.has("language_preference") && !format.isNull("language_preference"))
        format.optInt("language_preference", Int.MIN_VALUE) else Int.MIN_VALUE
    val type = when {
        language?.endsWith("-desc", ignoreCase = true) == true || preference <= -10 && preference != Int.MIN_VALUE -> AudioTrackType.DESCRIPTIVE
        preference == 10 || note?.contains("(original)", ignoreCase = true) == true -> AudioTrackType.ORIGINAL
        locale != null && preference == -1 -> AudioTrackType.DUBBED
        else -> null
    }
    // The language and role group multiple bitrates of the same track without inventing a language.
    val id = locale?.let { "${it.toLanguageTag()}|${type?.name ?: "unknown"}" }
    return AudioTrackMetadata(id = id, locale = locale, type = type)
}

internal fun AudioStream.Builder.withAudioMetadata(metadata: AudioTrackMetadata): AudioStream.Builder = apply {
    metadata.id?.let(::setAudioTrackId)
    metadata.name?.let(::setAudioTrackName)
    metadata.locale?.let(::setAudioLocale)
    metadata.type?.let(::setAudioTrackType)
}
