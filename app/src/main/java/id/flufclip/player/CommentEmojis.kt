package id.flufclip.player

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser

internal data class EmojiMatch(
    val range: IntRange,
    val token: String,
    val url: String,
)

internal data class CommentNextMetadata(
    val emojis: Map<String, Map<String, String>> = emptyMap(),
    val avatars: Map<String, String> = emptyMap(),
)

internal object CommentEmojiParser {
    private fun JsonObject.optObj(key: String): JsonObject? = get(key) as? JsonObject
    private fun JsonObject.optArr(key: String): JsonArray? = get(key) as? JsonArray
    private fun JsonObject.optStr(key: String): String? = get(key) as? String
    private fun JsonObject.optIntVal(key: String): Int? = (get(key) as? Number)?.toInt()
    private fun JsonObject.optBoolVal(key: String): Boolean? = get(key) as? Boolean

    /**
     * Ekstrak peta `commentId -> (shortcut/token -> imageUrl)` dari respons `/youtubei/v1/next`
     * agar custom emoji channel membership tetap tampil meski NewPipeExtractor mengabaikan `attachmentRuns`.
     */
    fun parseFromNext(json: String): Map<String, Map<String, String>> = parseMetadataFromNext(json).emojis

    /**
     * Ekstrak sekaligus metadata custom emoji dan URL avatar komentator dari respons `/youtubei/v1/next`,
     * karena YouTube modern menaruh avatar di `commentEntityPayload.author.avatarThumbnailUrl` yang belum selalu dibaca NewPipeExtractor.
     */
    fun parseMetadataFromNext(json: String): CommentNextMetadata {
        val root = try {
            JsonParser.`object`().from(json)
        } catch (_: Exception) {
            return CommentNextMetadata()
        }
        val emojis = HashMap<String, MutableMap<String, String>>()
        val avatars = HashMap<String, String>()
        parseEntityPayloads(root, emojis, avatars)
        if (json.contains("\"commentRenderer\"")) {
            parseLegacyRenderers(root.optArr("onResponseReceivedEndpoints"), emojis, avatars)
        }
        return CommentNextMetadata(emojis, avatars)
    }

    private fun parseEntityPayloads(
        root: JsonObject,
        outEmojis: MutableMap<String, MutableMap<String, String>>,
        outAvatars: MutableMap<String, String>,
    ) {
        val mutations = root.optObj("frameworkUpdates")
            ?.optObj("entityBatchUpdate")
            ?.optArr("mutations") ?: return
        for (item in mutations) {
            val mutation = item as? JsonObject ?: continue
            val payload = mutation.optObj("payload")?.optObj("commentEntityPayload") ?: continue
            val props = payload.optObj("properties") ?: continue
            val commentId = props.optStr("commentId")?.takeIf { it.isNotBlank() }
                ?: mutation.optStr("entityKey")?.takeIf { it.isNotBlank() }
                ?: continue
            val avatarUrl = payload.optObj("author")?.optStr("avatarThumbnailUrl")?.trim()?.takeIf { it.isNotBlank() }
                ?.let(::normalizeUrl)
                ?: bestImageUrl(payload.optObj("avatar")?.optObj("image")?.optArr("sources"))
            if (!avatarUrl.isNullOrBlank()) {
                outAvatars[commentId] = avatarUrl
            }
            val contentObj = props.optObj("content") ?: continue
            val rawText = contentObj.optStr("content") ?: continue
            val attachments = contentObj.optArr("attachmentRuns") ?: continue
            for (runItem in attachments) {
                val run = runItem as? JsonObject ?: continue
                val start = run.optIntVal("startIndex") ?: continue
                val length = run.optIntVal("length") ?: continue
                if (start < 0 || length <= 0 || start + length > rawText.length) continue
                val token = rawText.substring(start, start + length)
                if (token.isBlank()) continue
                val sources = run.optObj("element")
                    ?.optObj("type")
                    ?.optObj("imageType")
                    ?.optObj("image")
                    ?.optArr("sources")
                val url = bestImageUrl(sources) ?: continue
                if (!shouldRenderInline(token, url, isCustom = false)) continue
                outEmojis.getOrPut(commentId) { LinkedHashMap() }[token] = url
            }
        }
    }

    private fun parseLegacyRenderers(
        node: Any?,
        outEmojis: MutableMap<String, MutableMap<String, String>>,
        outAvatars: MutableMap<String, String>,
    ) {
        when (node) {
            is JsonObject -> {
                val renderer = node.optObj("commentRenderer")
                if (renderer != null) {
                    val commentId = renderer.optStr("commentId")?.takeIf { it.isNotBlank() }
                    if (commentId != null) {
                        val avatarUrl = bestImageUrl(renderer.optObj("authorThumbnail")?.optArr("thumbnails"))
                        if (!avatarUrl.isNullOrBlank()) outAvatars[commentId] = avatarUrl
                    }
                    val runs = renderer.optObj("contentText")?.optArr("runs")
                    if (commentId != null && runs != null) {
                        for (runItem in runs) {
                            val run = runItem as? JsonObject ?: continue
                            val emoji = run.optObj("emoji") ?: continue
                            val url = bestImageUrl(emoji.optObj("image")?.optArr("thumbnails")) ?: continue
                            val token = run.optStr("text")?.takeIf { it.isNotBlank() }
                                ?: emoji.optArr("shortcuts")?.firstOrNull()?.let { it as? String }?.takeIf { it.isNotBlank() }
                                ?: continue
                            val isCustom = emoji.optBoolVal("isCustomEmoji") == true
                            if (!shouldRenderInline(token, url, isCustom = isCustom)) continue
                            outEmojis.getOrPut(commentId) { LinkedHashMap() }[token] = url
                        }
                    }
                }
                for (value in node.values) parseLegacyRenderers(value, outEmojis, outAvatars)
            }
            is JsonArray -> for (value in node) parseLegacyRenderers(value, outEmojis, outAvatars)
        }
    }

    private fun shouldRenderInline(token: String, url: String, isCustom: Boolean): Boolean {
        if (isCustom) return true
        if (token.startsWith(":") && token.endsWith(":") && token.length >= 3) return true
        // Biarkan emoji Unicode standar memakai font sistem agar tidak membebani jaringan, kecuali aset kustom/non-notoemoji.
        return !url.contains("fonts.gstatic.com/s/e/notoemoji")
    }

    private fun bestImageUrl(sources: JsonArray?): String? {
        if (sources == null || sources.isEmpty()) return null
        var bestUrl: String? = null
        var bestWidth = -1
        for (item in sources) {
            val obj = item as? JsonObject ?: continue
            val rawUrl = obj.optStr("url")?.trim()?.takeIf { it.isNotBlank() } ?: continue
            val width = obj.optIntVal("width") ?: 0
            if (bestUrl == null || width >= bestWidth) {
                bestUrl = normalizeUrl(rawUrl)
                bestWidth = width
            }
        }
        return bestUrl
    }

    private fun normalizeUrl(url: String): String =
        if (url.startsWith("//")) "https:$url" else url

    fun findMatches(
        text: String,
        emojis: Map<String, String>,
        protectedRanges: List<IntRange> = emptyList(),
    ): List<EmojiMatch> {
        if (text.isEmpty() || emojis.isEmpty()) return emptyList()
        val pattern = emojis.keys
            .filter { it.isNotEmpty() }
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        if (pattern.isEmpty()) return emptyList()
        val regex = Regex(pattern)
        return regex.findAll(text).mapNotNull { match ->
            val range = match.range
            val overlapsProtected = protectedRanges.any { p ->
                range.first <= p.last && p.first <= range.last
            }
            if (overlapsProtected) return@mapNotNull null
            val url = emojis[match.value] ?: return@mapNotNull null
            EmojiMatch(range = range, token = match.value, url = url)
        }.toList()
    }
}
