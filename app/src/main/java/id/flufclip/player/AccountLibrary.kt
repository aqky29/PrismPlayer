package id.flufclip.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Data from the signed-in account; never mixed with the local watch-history cache. */
internal data class AccountPlaylist(val id: String, val title: String, val thumbnail: String = "", val subtitle: String = "")
internal data class AccountPlaylistsPage(val playlists: List<AccountPlaylist>, val continuation: String?)
internal data class AccountPlaylistHeader(
    val owner: String, val privacy: String, val count: String, val views: String,
    val backgroundArgb: Long?, val backgroundImage: String? = null,
    val gradient: List<Pair<Float, Long>> = emptyList(),
    val title: String = "",
    val thumbnail: String = "",
)
internal data class AccountPlaylistFilter(val title: String, val params: String?, val selected: Boolean)
internal data class AccountPlaylistPage(
    val videos: List<WatchedVideo>, val continuation: String?,
    val header: AccountPlaylistHeader? = null, val filters: List<AccountPlaylistFilter> = emptyList(),
)
internal data class PlaylistChoice(
    val id: String,
    val title: String,
    val selected: Boolean,
    val add: JSONObject?,
    val remove: JSONObject?,
    val privacy: String = "",
    val thumbnail: String = "",
)

internal object AccountLibrary {
    private val cachedLock = Any()
    private var cachedRevision = -1
    private var cachedPlaylists = emptyMap<String, AccountPlaylist>()

    internal fun cachedPlaylistMeta(id: String, revision: Int): AccountPlaylist? = synchronized(cachedLock) {
        if (cachedRevision == revision) cachedPlaylists[id.removePrefix("VL")] else null
    }

    private fun rememberPlaylists(list: List<AccountPlaylist>, revision: Int) {
        synchronized(cachedLock) {
            cachedRevision = revision
            cachedPlaylists = list.associateBy { it.id.removePrefix("VL") }
        }
    }

    suspend fun playlists(): AccountPlaylistsPage = withContext(Dispatchers.IO) {
        val rev = YouTubeAuth.sessionRevision
        val json = HomeFeed.accountRequest("browse", JSONObject().put("browseId", "FElibrary"))
        val aggregation = HomeFeed.accountRequest("browse", JSONObject().put("browseId", "FEplaylist_aggregation"))
        val playlists = (parsePlaylists(json) + parsePlaylists(aggregation)).distinctBy { it.id }
        // Built-in YouTube collections have stable IDs even when the shelf omits an empty collection.
        val all = playlists + listOf(AccountPlaylist("LL", "Liked videos", subtitle = "Private"),
            AccountPlaylist("WL", "Watch later", subtitle = "Private")).filter { builtin -> playlists.none { it.id == builtin.id } }
        rememberPlaylists(all, rev)
        AccountPlaylistsPage(all, continuation(aggregation))
    }

    suspend fun morePlaylists(token: String): AccountPlaylistsPage = withContext(Dispatchers.IO) {
        val json = HomeFeed.accountRequest("browse", JSONObject().put("continuation", token))
        AccountPlaylistsPage(parsePlaylists(json), continuation(json)?.takeUnless { it == token })
    }

    suspend fun videos(playlist: AccountPlaylist, continuation: String? = null, params: String? = null, shortsOnly: Boolean = false): AccountPlaylistPage = withContext(Dispatchers.IO) {
        val cleanId = playlist.id.removePrefix("VL")
        val fields = JSONObject()
        if (continuation == null) {
            fields.put("browseId", "VL$cleanId")
            if (!params.isNullOrBlank()) fields.put("params", params)
        }
        else fields.put("continuation", continuation)
        val requiresAccount = cleanId in setOf("LL", "WL", "YS")
        val json = if (requiresAccount) HomeFeed.accountRequest("browse", fields) else HomeFeed.publicOrAccountRequest("browse", fields)
        val page = parseVideos(json, shortsOnly || cleanId == "YS", playlistId = cleanId, fallbackTitle = playlist.title)
        if (cleanId !in setOf("LL", "WL", "YS") && page.videos.isNotEmpty()) {
            val resolvedTitle = page.header?.title?.ifBlank { playlist.title } ?: playlist.title
            val resolvedCount = page.header?.count.orEmpty()
            val resolvedOwner = page.header?.owner.orEmpty()
            WatchHistory.rememberPlaylistVideos(
                null,
                PlaylistRef(cleanId, resolvedTitle, resolvedCount, resolvedOwner),
                page.videos.map { it.url }
            )
        }
        page
    }

    suspend fun choices(videoId: String): List<PlaylistChoice> = withContext(Dispatchers.IO) {
        require(videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) { "Invalid video" }
        val json = HomeFeed.accountRequest("playlist/get_add_to_playlist", JSONObject().put("videoIds", JSONArray().put(videoId)))
        parseChoices(json)
    }

    suspend fun setChoice(videoId: String, choice: PlaylistChoice, revision: Int) = withContext(Dispatchers.IO) {
        val command = if (choice.selected) choice.remove else choice.add
        val edit = command?.let { findObject(it, "playlistEditEndpoint") }
            ?: if (!choice.selected) JSONObject().put("playlistId", choice.id)
                .put("actions", JSONArray().put(JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId)))
            else error("YouTube did not provide a remove action for this playlist")
        val actions = edit.optJSONArray("actions") ?: error("Missing playlist action")
        val id = edit.optString("playlistId").ifBlank { choice.id }
        check(id == choice.id && actions.length() > 0) { "Invalid playlist action" }
        val result = HomeFeed.accountRequest("browse/edit_playlist", JSONObject().put("playlistId", id).put("actions", actions), revision)
        val status = result.optString("status")
        check(status == "STATUS_SUCCEEDED" || (status.isBlank() && result.optJSONArray("playlistEditResults")?.length()?.let { it > 0 } == true)) { "YouTube did not confirm the playlist update" }
    }

    suspend fun create(title: String, privacy: String, videoId: String, revision: Int): String = withContext(Dispatchers.IO) {
        // The server can attach creation parameters to a video-specific command.
        var serverFields: JSONObject? = null
        try {
            val next = HomeFeed.accountRequest("next", JSONObject().put("videoId", videoId), revision)
            visit(next, skipMenus = false) { node ->
                val endpoint = node.optJSONObject("createPlaylistServiceEndpoint") ?: return@visit
                val ids = endpoint.optJSONArray("videoIds") ?: return@visit
                if ((0 until ids.length()).any { ids.optString(it) == videoId }) serverFields = JSONObject(endpoint.toString())
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* The create endpoint may also accept the explicit fields below. */ }
        val fields = (serverFields ?: JSONObject()).put("title", title.trim()).put("privacyStatus", privacy).put("videoIds", JSONArray().put(videoId))
        val result = HomeFeed.accountRequest("playlist/create", fields, revision)
        result.optString("playlistId").takeIf { it.isNotBlank() }
            ?: error("YouTube did not confirm that the playlist was created")
    }

    internal fun parsePlaylists(json: JSONObject): List<AccountPlaylist> {
        val result = linkedMapOf<String, AccountPlaylist>()
        val root = json.opt("contents") ?: json.opt("continuationContents") ?: json.opt("onResponseReceivedActions")
            ?: json.opt("onResponseReceivedEndpoints") ?: error("Library response not recognized")
        visit(root) { node ->
            val old = node.optJSONObject("playlistRenderer") ?: node.optJSONObject("gridPlaylistRenderer")
            val modern = node.optJSONObject("lockupViewModel")?.takeIf {
                val ct = it.optString("contentType")
                ct.contains("PLAYLIST") || ct.contains("PODCAST")
            }
            val renderer = old ?: modern ?: return@visit
            val id = (if (old != null) renderer.optString("playlistId") else renderer.optString("contentId")).removePrefix("VL")
            if (id.isBlank()) return@visit
            val metadata = renderer.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
            val title = label(renderer.optJSONObject("title")).ifBlank { metadata?.optJSONObject("title")?.optString("content").orEmpty() }
            if (title.isBlank()) return@visit
            val modernSubtitle = metadata?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")
                ?.optJSONArray("metadataRows")?.optJSONObject(0)?.optJSONArray("metadataParts")?.optJSONObject(0)
                ?.optJSONObject("text")?.optString("content").orEmpty()
            val subtitle = label(renderer.optJSONObject("shortBylineText")).ifBlank { label(renderer.optJSONObject("videoCountText")) }
                .ifBlank { modernSubtitle }.ifBlank { if (id == "LL" || id == "WL") "Private" else "" }
            val image = renderer.optJSONObject("contentImage") ?: renderer.optJSONObject("thumbnail") ?: renderer
            result.putIfAbsent(id, AccountPlaylist(id, title, thumbnail(image), subtitle))
        }
        return result.values.toList()
    }

    internal fun parseVideos(
        json: JSONObject,
        shortsOnly: Boolean = false,
        playlistId: String? = null,
        fallbackTitle: String = ""
    ): AccountPlaylistPage {
        val root = json.opt("contents") ?: json.opt("continuationContents") ?: json.opt("onResponseReceivedActions")
            ?: json.opt("onResponseReceivedEndpoints") ?: error("Playlist response not recognized")
        val rawHeader = json.optJSONObject("header")?.optJSONObject("playlistHeaderRenderer")
        val pageHeader = json.optJSONObject("header")?.optJSONObject("pageHeaderRenderer")
        val sidebar = json.optJSONObject("sidebar")?.optJSONObject("playlistSidebarRenderer")
        val header = parsePlaylistHeader(rawHeader, pageHeader, sidebar)
        val effectivePlaylistTitle = header?.title?.ifBlank { fallbackTitle } ?: fallbackTitle
        val effectiveCount = header?.count.orEmpty()

        val videos = mutableListOf<WatchedVideo>()
        var next: String? = null
        var recognized = rawHeader != null || pageHeader != null || sidebar != null
        visit(root) { node ->
            if (node.has("playlistVideoListRenderer") || node.has("playlistHeaderRenderer") || node.has("playlistVideoRenderer") || node.has("continuationItemRenderer")) recognized = true
            node.optJSONObject("continuationItemRenderer")?.optJSONObject("continuationEndpoint")
                ?.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }?.let { next = it }
            node.optJSONObject("shortsLockupViewModel")?.let { short ->
                HomeFeed.parseShortsCard(short)?.let { card ->
                    recognized = true
                    videos.add(WatchedVideo("https://www.youtube.com/shorts/${card.videoId}", card.title, "", card.thumbnailUrl, 0L, short = true, viewsText = card.viewsText,
                        playlistId = playlistId, playlistTitle = effectivePlaylistTitle.takeIf { it.isNotBlank() }, playlistVideoCount = effectiveCount.takeIf { it.isNotBlank() }))
                }
            }
            val old = node.optJSONObject("playlistVideoRenderer") ?: node.optJSONObject("videoRenderer")
            val lockup = node.optJSONObject("lockupViewModel")?.takeIf { it.optString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO" }
            val parsed = if (old != null) HomeFeed.parseVideoRenderer(old) else lockup?.let(HomeFeed::parseLockupViewModel)
            if (parsed != null) {
                recognized = true
                var short = shortsOnly
                visit(old ?: lockup!!) { part ->
                    if (part.has("reelWatchEndpoint") || part.optString("style").contains("SHORTS")) short = true
                }
                val url = if (short) "https://www.youtube.com/shorts/${videoKey(parsed.url)}" else parsed.url
                val owner = old?.optJSONObject("shortBylineText")?.let(::label).orEmpty()
                    .ifBlank { parsed.uploaderName.orEmpty() }
                    .ifBlank { header?.owner.orEmpty() }
                videos.add(WatchedVideo(url, parsed.name.orEmpty(), owner, parsed.thumbnails.lastOrNull()?.url.orEmpty(), 0L,
                    duration = parsed.duration.coerceAtLeast(0) * 1000, short = short,
                    channelUrl = parsed.uploaderUrl,
                    viewsText = if (parsed.viewCount >= 0) formatCount(parsed.viewCount) else "", publishedText = parsed.textualUploadDate.orEmpty(),
                    playlistId = playlistId, playlistTitle = effectivePlaylistTitle.takeIf { it.isNotBlank() }, playlistVideoCount = effectiveCount.takeIf { it.isNotBlank() }))
            }
        }
        check(recognized) { "YouTube returned an unrecognized playlist" }
        val filters = mutableListOf<AccountPlaylistFilter>()
        visit(root) { node ->
            val chip = node.optJSONObject("chipViewModel") ?: return@visit
            val title = chip.optString("text")
            val endpoint = findObject(chip, "browseEndpoint") ?: return@visit
            if (title.isNotBlank() && endpoint.optString("browseId").startsWith("VL")) {
                filters.add(AccountPlaylistFilter(title, endpoint.optString("params").takeIf { it.isNotBlank() }, chip.optBoolean("selected")))
            }
        }
        val finalHeader = if (header != null && header.thumbnail.isBlank() && videos.isNotEmpty()) {
            header.copy(thumbnail = videos.first().thumbnail)
        } else header
        return AccountPlaylistPage(videos, next, finalHeader, filters.distinctBy { it.title })
    }

    private fun parsePlaylistHeader(
        rawHeader: JSONObject?,
        pageHeader: JSONObject?,
        sidebar: JSONObject?
    ): AccountPlaylistHeader? {
        if (rawHeader != null) {
            val cinematic = rawHeader.optJSONObject("cinematicContainer")?.optJSONObject("cinematicContainerRenderer")
            val config = cinematic?.optJSONObject("config")
            val color = config?.takeIf { c -> c.has("darkThemeBackgroundColor") }?.optLong("darkThemeBackgroundColor")
            val image = cinematic?.optJSONObject("backgroundImageConfig")?.let(::thumbnail)
            val gradient = cinematic?.optJSONArray("gradientColorConfig")?.let { stops ->
                (0 until stops.length()).mapNotNull { index ->
                    val stop = stops.optJSONObject(index) ?: return@mapNotNull null
                    val value = stop.optLong("darkThemeColor", Long.MIN_VALUE)
                    val location = stop.optDouble("startLocation", -1.0)
                    if (value == Long.MIN_VALUE || location < 0) null else location.toFloat() to value
                }.sortedBy { it.first }
            }.orEmpty()
            val count = label(rawHeader.optJSONObject("numVideosText"))
            val title = label(rawHeader.optJSONObject("title"))
            val thumb = rawHeader.optJSONObject("playlistHeaderBanner")?.let(::thumbnail).orEmpty()
            return AccountPlaylistHeader(
                owner = label(rawHeader.optJSONObject("ownerText")),
                privacy = rawHeader.optString("privacy"),
                count = count,
                views = label(rawHeader.optJSONObject("viewCountText")),
                backgroundArgb = color,
                backgroundImage = image,
                gradient = gradient,
                title = title,
                thumbnail = thumb
            )
        }
        if (pageHeader == null && sidebar == null) return null

        var title = pageHeader?.optString("pageTitle").orEmpty()
        var owner = ""
        var count = ""
        var views = ""
        var privacy = ""
        var thumb = ""

        val viewModel = pageHeader?.optJSONObject("content")?.optJSONObject("pageHeaderViewModel")
        if (viewModel != null) {
            if (title.isBlank()) {
                title = viewModel.optJSONObject("title")?.optJSONObject("dynamicTextViewModel")
                    ?.optJSONObject("text")?.optString("content").orEmpty()
            }
            thumb = viewModel.optJSONObject("heroImage")?.let(::thumbnail).orEmpty()
            val rows = viewModel.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")
                ?.optJSONArray("metadataRows")
            if (rows != null) {
                for (i in 0 until rows.length()) {
                    val parts = rows.optJSONObject(i)?.optJSONArray("metadataParts") ?: continue
                    for (j in 0 until parts.length()) {
                        val partObj = parts.optJSONObject(j)
                        val content = (partObj?.optJSONObject("text")
                            ?: partObj?.optJSONObject("avatarStack")?.optJSONObject("avatarStackViewModel")?.optJSONObject("text"))
                            ?.optString("content").orEmpty().trim()
                        if (content.isBlank()) continue
                        val lower = content.lowercase()
                        when {
                            i == 0 && owner.isBlank() -> owner = content.removePrefix("by ").removePrefix("oleh ").trim()
                            lower.contains("video") || lower.contains("episode") -> if (count.isBlank()) count = content
                            lower.contains("view") || lower.contains("ditonton") -> if (views.isBlank()) views = content
                            lower in setOf("public", "unlisted", "private", "publik", "pribadi") -> privacy = content
                        }
                    }
                }
            }
        }

        val items = sidebar?.optJSONArray("items")
        if (items != null) {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                item.optJSONObject("playlistSidebarPrimaryInfoRenderer")?.let { primary ->
                    if (title.isBlank()) title = label(primary.optJSONObject("title"))
                    if (thumb.isBlank()) thumb = primary.optJSONObject("thumbnailRenderer")?.let(::thumbnail).orEmpty()
                    val stats = primary.optJSONArray("stats")
                    if (stats != null) {
                        for (s in 0 until stats.length()) {
                            val statText = label(stats.optJSONObject(s)).trim()
                            val lower = statText.lowercase()
                            when {
                                (lower.contains("video") || lower.contains("episode")) && count.isBlank() -> count = statText
                                (lower.contains("view") || lower.contains("ditonton")) && views.isBlank() -> views = statText
                            }
                        }
                    }
                }
                item.optJSONObject("playlistSidebarSecondaryInfoRenderer")?.let { secondary ->
                    val ownerTitle = label(secondary.optJSONObject("videoOwner")?.optJSONObject("videoOwnerRenderer")?.optJSONObject("title"))
                    if (owner.isBlank() && ownerTitle.isNotBlank()) owner = ownerTitle
                }
            }
        }

        return AccountPlaylistHeader(
            owner = owner,
            privacy = privacy,
            count = count,
            views = views,
            backgroundArgb = null,
            backgroundImage = thumb.takeIf { it.isNotBlank() },
            gradient = emptyList(),
            title = title,
            thumbnail = thumb
        )
    }

    internal fun parseChoices(json: JSONObject): List<PlaylistChoice> {
        val result = linkedMapOf<String, PlaylistChoice>()
        var found = false
        visit(json, skipMenus = false) { node ->
            if (node.has("addToPlaylistRenderer")) found = true
            val option = node.optJSONObject("playlistAddToOptionRenderer") ?: return@visit
            found = true
            val id = option.optString("playlistId").removePrefix("VL")
            val title = label(option.optJSONObject("title"))
            if (id.isBlank() || title.isBlank()) return@visit
            val selected = option.optBoolean("containsVideo") || option.optString("containsSelectedVideos") in setOf("ALL", "ALL_SELECTED")
            val service = option.optJSONObject("serviceEndpoint")
            val rawPrivacy = option.optString("privacy").ifBlank {
                option.optJSONObject("privacyIcon")?.optString("iconType").orEmpty()
            }.uppercase()
            val privacy = when {
                rawPrivacy.contains("PRIVATE") || rawPrivacy == "LOCK" -> "Private"
                rawPrivacy.contains("UNLISTED") -> "Unlisted"
                rawPrivacy.contains("PUBLIC") -> "Public"
                id == "WL" || id == "LL" -> "Private"
                else -> ""
            }
            val thumb = thumbnail(option)
            result[id] = PlaylistChoice(
                id,
                title,
                selected,
                option.optJSONObject("addToPlaylistServiceEndpoint") ?: option.optJSONObject("onAddCommand") ?: option.optJSONObject("addCommand") ?: service.takeUnless { selected },
                option.optJSONObject("removeFromPlaylistServiceEndpoint") ?: option.optJSONObject("onRemoveCommand") ?: option.optJSONObject("removeCommand") ?: service.takeIf { selected },
                privacy = privacy,
                thumbnail = thumb,
            )
        }
        check(found) { "YouTube did not return playlist choices" }
        return result.values.toList()
    }

    private fun label(json: JSONObject?): String = json?.optString("simpleText")?.takeIf { it.isNotBlank() }
        ?: json?.optString("content")?.takeIf { it.isNotBlank() }
        ?: json?.optJSONArray("runs")?.let { a -> (0 until a.length()).joinToString("") { a.optJSONObject(it)?.optString("text").orEmpty() } }.orEmpty()

    private fun thumbnail(json: JSONObject): String {
        var found = ""
        visit(json) { obj ->
            val url = obj.optString("url")
            if (url.startsWith("https://") && (url.contains("ytimg.com/") || url.contains("ggpht.com/"))) found = url
        }
        return found
    }

    private fun findObject(root: JSONObject, key: String): JSONObject? {
        var found: JSONObject? = null
        visit(root, skipMenus = false) { if (found == null) found = it.optJSONObject(key) }
        return found
    }

    private fun continuation(json: JSONObject): String? {
        var token: String? = null
        val root = json.opt("contents") ?: json.opt("continuationContents") ?: json.opt("onResponseReceivedActions")
            ?: json.opt("onResponseReceivedEndpoints") ?: return null
        visit(root) { node ->
            node.optJSONObject("continuationItemRenderer")?.optJSONObject("continuationEndpoint")
                ?.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }?.let { token = it }
        }
        return token
    }

    private fun visit(value: Any?, skipMenus: Boolean = true, block: (JSONObject) -> Unit) {
        when (value) {
            is JSONObject -> {
                block(value)
                value.keys().forEach { key ->
                    if (key != "trackingParams" && key != "frameworkUpdates" && (!skipMenus || key != "menu"))
                        visit(value.opt(key), skipMenus, block)
                }
            }
            is JSONArray -> for (i in 0 until value.length()) visit(value.opt(i), skipMenus, block)
        }
    }
}
