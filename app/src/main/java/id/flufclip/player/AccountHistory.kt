package id.flufclip.player

import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AccountHistoryEntry(val video: WatchedVideo, val section: String?, val viewsText: String = "", val removeToken: String? = null, val shortsShelfId: String? = null)
internal data class AccountHistoryPage(val entries: List<AccountHistoryEntry>, val continuation: String?)

/** Show up to two Shorts per shelf in the preview and collapse multiple videos from the same playlist into one Playlist card. */
internal fun accountHistoryPreview(entries: List<AccountHistoryEntry>): List<WatchedVideo> {
    val shelfCounts = mutableMapOf<String, Int>()
    val seenPlaylists = mutableSetOf<String>()
    return entries.asSequence().filter { entry ->
        val pid = entry.video.playlistId?.takeIf { it.isNotBlank() }
        if (pid != null && !seenPlaylists.add(pid)) return@filter false
        entry.shortsShelfId?.let { shelf ->
            val count = shelfCounts.getOrDefault(shelf, 0)
            shelfCounts[shelf] = count + 1
            count < 2
        } ?: true
    }.take(10).map { it.video }.toList()
}

/** Parse only the browse content; navigation menus can also contain video renderers. */
internal object AccountHistory {
    suspend fun load(continuation: String? = null): AccountHistoryPage = withContext(Dispatchers.IO) { parse(HomeFeed.historyResponse(continuation)) }

    suspend fun remove(token: String, revision: Int) {
        val result = HomeFeed.accountRequest("feedback", JSONObject().put("feedbackTokens", JSONArray().put(token)), revision)
        check((result.optJSONArray("feedbackResponses")?.length() ?: 0) > 0) { "YouTube did not confirm history removal" }
    }

    internal fun parse(json: JSONObject): AccountHistoryPage {
        val entries = mutableListOf<AccountHistoryEntry>()
        var next: String? = null
        var recognized = false
        var section: String? = null
        fun label(value: JSONObject?): String = value?.optString("simpleText")?.takeIf { it.isNotBlank() }
            ?: value?.optJSONArray("runs")?.let { runs ->
                (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
            }.orEmpty()

        fun visit(node: Any?, shelfId: String? = null) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i), shelfId)
                is JSONObject -> {
                    node.optJSONObject("continuationItemRenderer")?.let {
                        next = next ?: it.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")
                            ?.optString("token")?.takeIf(String::isNotBlank)
                        recognized = true
                        return
                    }
                    node.optJSONObject("itemSectionRenderer")?.let {
                        val heading = label(it.optJSONObject("header")?.optJSONObject("itemSectionHeaderRenderer")?.optJSONObject("title"))
                            .ifBlank { label(it.optJSONObject("header")?.optJSONObject("itemSectionHeaderRenderer")?.optJSONObject("text")) }
                        if (heading.isNotBlank()) section = heading
                        recognized = true
                        visit(it.optJSONArray("contents"))
                        return
                    }
                    node.optJSONObject("reelShelfRenderer")?.let { shelf ->
                        visit(shelf.optJSONArray("items"), java.util.UUID.randomUUID().toString())
                        return
                    }
                    node.optJSONObject("shortsLockupViewModel")?.let { shorts ->
                        HomeFeed.parseShortsCard(shorts)?.let { card ->
                            entries.add(AccountHistoryEntry(WatchedVideo("https://www.youtube.com/shorts/${card.videoId}", card.title, "", card.thumbnailUrl, 0L, short = true), section, card.viewsText, removeToken(shorts), shelfId))
                            recognized = true
                        }
                        return
                    }
                    val renderer = node.optJSONObject("videoRenderer") ?: node.optJSONObject("compactVideoRenderer")
                    val lockup = node.optJSONObject("lockupViewModel")
                    if (renderer != null || lockup != null) {
                        val item = if (renderer != null) HomeFeed.parseVideoRenderer(renderer) else HomeFeed.parseLockupViewModel(lockup!!)
                        if (item != null) {
                            val meta = HomeFeed.metaOf(item.url)
                            val progressFrac = lockup?.let { HomeFeed.historyProgress(it) }
                                ?: (meta?.progressPercent?.takeIf { it in 1..99 }?.div(100f))
                            val durMs = item.duration.coerceAtLeast(0L) * 1000L
                            val pos = meta?.resumePositionMs?.takeIf { it > 0L }
                                ?: (progressFrac?.let { (it * durMs).toLong() } ?: 0L)
                            val rawVideo = WatchedVideo(
                                url = item.url,
                                title = item.name.orEmpty(),
                                uploader = item.uploaderName.orEmpty(),
                                thumbnail = item.thumbnails.lastOrNull()?.url.orEmpty(),
                                position = pos,
                                duration = durMs,
                                short = item.isShortFormContent,
                                channelUrl = item.uploaderUrl,
                                progressFraction = progressFrac,
                                playlistId = meta?.playlistId,
                                playlistTitle = meta?.playlistTitle,
                                playlistVideoCount = meta?.playlistVideoCountText
                            )
                            entries.add(AccountHistoryEntry(WatchHistory.enrichVideo(rawVideo), section, removeToken = removeToken(renderer ?: lockup!!)))
                            recognized = true
                        }
                        return
                    }
                    if (node.has("sectionListRenderer") || node.has("richGridRenderer")) recognized = true
                    // messageRenderer may be a signed-out prompt; do not claim it is an empty history.
                    for (key in node.keys()) {
                        if (key !in setOf("navigationEndpoint", "trackingParams", "header", "menu", "frameworkUpdates")) visit(node.opt(key), shelfId)
                    }
                }
            }
        }
        val roots = json.optJSONObject("contents") ?: json.optJSONArray("onResponseReceivedActions")
            ?: json.optJSONArray("onResponseReceivedEndpoints") ?: json.optJSONObject("continuationContents")
            ?: throw IllegalStateException("Respons History tidak dikenali")
        visit(roots)
        if (!recognized || (entries.isEmpty() && next == null)) throw IllegalStateException("History kosong atau sesi tidak dapat diverifikasi. Buka YouTube untuk memeriksa akun.")
        return AccountHistoryPage(entries, next)
    }

    /** Only use the token attached to a clearly labelled remove-from-history menu item. */
    private fun removeToken(renderer: JSONObject): String? {
        var result: String? = null
        fun scan(node: Any?, insideRemove: Boolean = false) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) scan(node.opt(i), insideRemove)
                is JSONObject -> {
                    val text = node.optJSONObject("text") ?: node.optJSONObject("title")
                    val label = text?.optString("simpleText").orEmpty() + text?.optString("content").orEmpty() +
                        (text?.optJSONArray("runs")?.let { a -> (0 until a.length()).joinToString("") { a.optJSONObject(it)?.optString("text").orEmpty() } }.orEmpty())
                    val remove = insideRemove || (label.contains("remove", true) && label.contains("history", true)) ||
                        (label.contains("hapus", true) && (label.contains("histori", true) || label.contains("riwayat", true)))
                    if (remove) node.optJSONObject("feedbackEndpoint")?.optString("feedbackToken")?.takeIf { it.isNotBlank() }?.let { result = it }
                    if (result == null) node.keys().forEach { scan(node.opt(it), remove) }
                }
            }
        }
        scan(renderer)
        return result
    }
}
