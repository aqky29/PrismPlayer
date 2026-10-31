package id.flufclip.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.TimeUnit

internal data class CustomChatEmoji(
    val emojiId: String,
    val shortcut: String,
    val imageUrl: String,
    val isCustomEmoji: Boolean = true
)

internal data class ChatInputConfig(
    val sendParams: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val badges: List<ChatBadge> = emptyList(),
    val customEmojis: List<CustomChatEmoji> = emptyList()
)

internal data class ChatBadge(
    val type: String,
    val imageUrl: String? = null,
    val tooltip: String? = null
)

internal data class ChatMessage(
    val id: String,
    val author: String,
    val text: String,
    val offset: Long?,
    val emoji: Map<String, String> = emptyMap(),
    val authorPhotoUrl: String? = null,
    val badges: List<ChatBadge> = emptyList(),
    val badge: String? = null,
    val purchaseAmount: String? = null,
    val isSuperChat: Boolean = false
)

internal data class ChatBatch(
    val messages: List<ChatMessage>,
    val continuation: String?,
    val timeout: Long,
    val offset: Long?,
    val inputConfig: ChatInputConfig? = null,
    val pinnedMessage: ChatMessage? = null,
    val unpinBanner: Boolean = false
)

/** YouTube's web chat endpoint is separate from its video comments. */
internal class ChatSession(private val videoId: String, private val live: Boolean, private val topChat: Boolean = false) {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val agent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    private var context: JSONObject? = null
    private var apiKey = ""
    private var continuation: String? = null
    private var seekContinuation: String? = null
    private var offset = 0L
    var currentInputConfig: ChatInputConfig? = null
        private set

    private fun get(url: String): String {
        val builder = Request.Builder().url(url).header("User-Agent", agent)
        if (YouTubeAuth.isLoggedIn) {
            YouTubeAuth.buildAuthHeaders().forEach { (k, v) -> builder.header(k, v) }
        }
        return client.newCall(builder.build()).execute().use {
            if (!it.isSuccessful) error("YouTube merespons ${it.code}")
            it.body?.string() ?: error("Respons chat kosong")
        }
    }
    private fun objectAfter(text: String, marker: String): JSONObject? {
        val start = text.indexOf(marker).takeIf { it >= 0 } ?: return null
        return runCatching { JSONTokener(text.substring(start + marker.length).trimStart()).nextValue() as JSONObject }.getOrNull()
    }
    /** Di watch page, subMenuItems hanya berisi stub filter tanpa ID video, jadi pakai continuations[] (token bootstrap penuh). */
    private fun extractContinuationToken(chatRenderer: JSONObject): String? {
        val conts = chatRenderer.optJSONArray("continuations")
        if (conts != null) {
            for (i in 0 until conts.length()) {
                val c = conts.optJSONObject(i) ?: continue
                val token = sequenceOf("reloadContinuationData", "liveChatReplayContinuationData", "timedContinuationData", "invalidationContinuationData")
                    .mapNotNull { c.optJSONObject(it)?.optString("continuation")?.takeIf { s -> s.isNotBlank() } }
                    .firstOrNull()
                if (!token.isNullOrBlank()) return token
            }
        }
        return null
    }
    private fun post(path: String, body: JSONObject): JSONObject {
        val builder = Request.Builder().url("https://www.youtube.com/youtubei/v1/live_chat/$path?key=$apiKey").header("User-Agent", agent)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        if (YouTubeAuth.isLoggedIn) YouTubeAuth.buildAuthHeaders().forEach { (k, v) -> builder.header(k, v) }
        return client.newCall(builder.build()).execute().use {
            if (!it.isSuccessful) error("YouTube merespons ${it.code}")
            JSONObject(it.body?.string() ?: error("Respons chat kosong"))
        }
    }
    private fun ytcfg(html: String): JSONObject? = Regex("ytcfg\\.set\\(\\s*\\{").findAll(html).mapNotNull { match ->
        runCatching { JSONTokener(html.substring(match.range.last)).nextValue() as JSONObject }.getOrNull()
    }.firstOrNull { it.has("INNERTUBE_CONTEXT") && it.has("INNERTUBE_API_KEY") }

    /** Replay: token bootstrap penuh dari watch page (continuations[0]), lalu POST langsung ke get_live_chat_replay. */
    private fun startReplay(): ChatBatch {
        val watch = get("https://www.youtube.com/watch?v=$videoId")
        val watchData = objectAfter(watch, "var ytInitialData = ") ?: objectAfter(watch, "window[\"ytInitialData\"] = ") ?: error("Data replay chat tidak tersedia")
        val chat = findObject(watchData, "liveChatRenderer") ?: error("Replay chat tidak tersedia untuk video ini")
        val config = ytcfg(watch) ?: error("Konfigurasi chat tidak tersedia")
        apiKey = config.optString("INNERTUBE_API_KEY")
        context = config.optJSONObject("INNERTUBE_CONTEXT")
        if (apiKey.isBlank() || context == null) error("Konfigurasi chat tidak lengkap")
        var token = extractContinuationToken(chat) ?: error("Replay chat tidak tersedia untuk video ini")
        fun fetch(t: String): JSONObject = post("get_live_chat_replay", JSONObject().put("context", context).put("continuation", t))
            .optJSONObject("continuationContents")?.optJSONObject("liveChatContinuation") ?: error("Respons replay chat tidak dikenali")
        var renderer = fetch(token)
        // Menu filter lengkap (Top chat / Live chat) baru tersedia di respons ini.
        val subMenu = findObject(renderer, "sortFilterSubMenuRenderer")?.optJSONArray("subMenuItems")
        if (!topChat && subMenu != null && subMenu.length() > 1) {
            subMenu.optJSONObject(1)?.optJSONObject("continuation")?.optJSONObject("reloadContinuationData")?.optString("continuation")
                ?.takeIf { it.isNotBlank() }?.let { token = it; renderer = fetch(it) }
        }
        return parse(renderer)
    }
    fun start(): ChatBatch {
        if (!live) return startReplay()
        val endpoint = "live_chat"
        val html = get("https://www.youtube.com/$endpoint?v=$videoId")
        val data = objectAfter(html, "window[\"ytInitialData\"] = ") ?: objectAfter(html, "var ytInitialData = ") ?: error("Data chat tidak tersedia")
        val config = Regex("ytcfg\\.set\\(\\s*\\{").findAll(html).mapNotNull { match ->
            runCatching { JSONTokener(html.substring(match.range.last)).nextValue() as JSONObject }.getOrNull()
        }.firstOrNull { it.has("INNERTUBE_CONTEXT") && it.has("INNERTUBE_API_KEY") }
            ?: error("Konfigurasi chat tidak tersedia")
        apiKey = config.optString("INNERTUBE_API_KEY")
        context = config.optJSONObject("INNERTUBE_CONTEXT")
        if (apiKey.isBlank() || context == null) error("Konfigurasi chat tidak lengkap")

        val inputRenderer = findObject(data, "liveChatMessageInputRenderer")
        if (inputRenderer != null) {
            currentInputConfig = parseInputConfig(inputRenderer)
        }

        val contents = data.optJSONObject("contents")
        val renderer = contents?.optJSONObject("liveChatRenderer") ?: data.optJSONObject("continuationContents")?.optJSONObject("liveChatContinuation")
            ?: error(text(contents?.optJSONObject("messageRenderer")?.optJSONObject("text")).ifBlank { "Chat atau replay tidak tersedia untuk video ini" })
        if (live) {
            val subMenu = findObject(renderer, "sortFilterSubMenuRenderer")?.optJSONArray("subMenuItems")
            val targetIdx = if (topChat) 0 else (if (subMenu != null && subMenu.length() > 1) 1 else 0)
            val chosenToken = subMenu?.optJSONObject(targetIdx)?.optJSONObject("continuation")?.optJSONObject("reloadContinuationData")?.optString("continuation")?.takeIf { it.isNotBlank() }
            val initialBatch = parse(renderer)
            if (!chosenToken.isNullOrBlank()) {
                continuation = chosenToken
            }
            return initialBatch
        }
        return parse(renderer)
    }
    private fun findObject(value: Any?, key: String): JSONObject? {
        if (value is JSONObject) {
            value.optJSONObject(key)?.let { return it }
            val keys = value.keys()
            while (keys.hasNext()) findObject(value.opt(keys.next()), key)?.let { return it }
        } else if (value is JSONArray) {
            for (i in 0 until value.length()) findObject(value.opt(i), key)?.let { return it }
        }
        return null
    }
    fun next(): ChatBatch? {
        val token = continuation ?: return null
        val url = "https://www.youtube.com/youtubei/v1/live_chat/" + if (live) "get_live_chat?key=$apiKey" else "get_live_chat_replay?key=$apiKey"
        val body = JSONObject().put("context", context).put("continuation", token)
        val builder = Request.Builder().url(url).header("User-Agent", agent).post(body.toString().toRequestBody("application/json".toMediaType()))
        if (YouTubeAuth.isLoggedIn) {
            YouTubeAuth.buildAuthHeaders().forEach { (k, v) -> builder.header(k, v) }
        }
        val data = client.newCall(builder.build()).execute().use {
            if (!it.isSuccessful) error("YouTube merespons ${it.code}")
            JSONObject(it.body?.string() ?: error("Respons chat kosong"))
        }
        if (currentInputConfig == null) {
            val inputRenderer = findObject(data, "liveChatMessageInputRenderer")
            if (inputRenderer != null) currentInputConfig = parseInputConfig(inputRenderer)
        }
        val renderer = data.optJSONObject("continuationContents")?.optJSONObject("liveChatContinuation") ?: error("Respons chat tidak dikenali")
        return parse(renderer)
    }
    fun seek(targetOffsetMs: Long): ChatBatch? {
        if (live) return null
        val token = seekContinuation ?: continuation ?: return null
        val url = "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat_replay?key=$apiKey"
        val body = JSONObject()
            .put("context", context)
            .put("continuation", token)
            .put("currentPlayerState", JSONObject().put("playerOffsetMs", targetOffsetMs.coerceAtLeast(0L).toString()))
        val builder = Request.Builder().url(url).header("User-Agent", agent).post(body.toString().toRequestBody("application/json".toMediaType()))
        if (YouTubeAuth.isLoggedIn) {
            YouTubeAuth.buildAuthHeaders().forEach { (k, v) -> builder.header(k, v) }
        }
        val data = client.newCall(builder.build()).execute().use {
            if (!it.isSuccessful) error("YouTube merespons ${it.code}")
            JSONObject(it.body?.string() ?: error("Respons chat kosong"))
        }
        val renderer = data.optJSONObject("continuationContents")?.optJSONObject("liveChatContinuation") ?: return null
        return parse(renderer)
    }

    private fun parseInputConfig(inputRenderer: JSONObject): ChatInputConfig? {
        val sendEndpoint = findObject(inputRenderer, "sendLiveChatMessageEndpoint") ?: return null
        val sendParams = sendEndpoint.optString("params").takeIf { it.isNotBlank() } ?: return null
        val authorName = text(inputRenderer.optJSONObject("authorName")).ifBlank { YouTubeAuth.profile.name }
        val authorThumbs = inputRenderer.optJSONObject("authorPhoto")?.optJSONArray("thumbnails")
        val authorPhotoUrl = authorThumbs?.let {
            if (it.length() > 0) it.optJSONObject(it.length() - 1)?.optString("url") else null
        } ?: YouTubeAuth.profile.avatarUrl

        val userBadges = mutableListOf<ChatBadge>()
        val rawBadges = inputRenderer.optJSONArray("authorBadges")
        if (rawBadges != null) {
            for (b in 0 until rawBadges.length()) {
                val bObj = rawBadges.optJSONObject(b)?.let {
                    it.optJSONObject("liveChatAuthorBadgeRenderer") ?: it.optJSONObject("authorBadgeRenderer")
                } ?: continue
                val tooltip = bObj.optString("tooltip").takeIf { it.isNotBlank() }
                val thumbs = bObj.optJSONObject("customThumbnail")?.optJSONArray("thumbnails")
                    ?: bObj.optJSONObject("image")?.optJSONArray("thumbnails")
                val customUrl = thumbs?.let {
                    if (it.length() > 0) it.optJSONObject(it.length() - 1)?.optString("url") else null
                }?.takeIf { it.isNotBlank() }
                val resolvedUrl = when {
                    customUrl == null -> null
                    customUrl.startsWith("//") -> "https:$customUrl"
                    else -> customUrl
                }
                userBadges.add(ChatBadge(type = "MEMBER", imageUrl = resolvedUrl, tooltip = tooltip ?: "Member"))
            }
        }

        val emojiList = mutableListOf<CustomChatEmoji>()
        val picker = findObject(inputRenderer, "liveChatEmojiPickerRenderer")
        if (picker != null) {
            val categories = picker.optJSONArray("categories")
            if (categories != null) {
                for (c in 0 until categories.length()) {
                    val cat = categories.optJSONObject(c)?.optJSONObject("liveChatEmojiPickerCategoryRenderer") ?: continue
                    val emojis = cat.optJSONArray("emojis")
                    if (emojis != null) {
                        for (e in 0 until emojis.length()) {
                            val em = emojis.optJSONObject(e) ?: continue
                            val eid = em.optString("emojiId")
                            val shortcuts = em.optJSONArray("shortcuts")
                            val shortcut = shortcuts?.optString(0).orEmpty()
                            val ethumbs = em.optJSONObject("image")?.optJSONArray("thumbnails")
                            val eurl = ethumbs?.let {
                                if (it.length() > 0) it.optJSONObject(it.length() - 1)?.optString("url") else null
                            }.orEmpty()
                            if (eid.isNotBlank() && eurl.isNotBlank()) {
                                emojiList.add(
                                    CustomChatEmoji(
                                        emojiId = eid,
                                        shortcut = shortcut.ifBlank { ":emoji:" },
                                        imageUrl = if (eurl.startsWith("//")) "https:$eurl" else eurl,
                                        isCustomEmoji = em.optBoolean("isCustomEmoji", true)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        return ChatInputConfig(
            sendParams = sendParams,
            authorName = authorName,
            authorPhotoUrl = authorPhotoUrl,
            badges = userBadges,
            customEmojis = emojiList
        )
    }

    fun sendMessage(rawText: String, customEmojis: List<CustomChatEmoji>): ChatMessage? {
        val config = currentInputConfig ?: return null
        if (apiKey.isBlank()) return null
        val url = "https://www.youtube.com/youtubei/v1/live_chat/send_message?key=$apiKey"

        val segments = JSONArray()
        var remaining = rawText.trim()
        val shortcutMap = customEmojis.associateBy { it.shortcut }

        while (remaining.isNotEmpty()) {
            val colonIdx = remaining.indexOf(':')
            if (colonIdx < 0) {
                segments.put(JSONObject().put("text", remaining))
                break
            }
            if (colonIdx > 0) {
                segments.put(JSONObject().put("text", remaining.substring(0, colonIdx)))
                remaining = remaining.substring(colonIdx)
            }
            val nextColon = remaining.indexOf(':', 1)
            if (nextColon > 0) {
                val candidate = remaining.substring(0, nextColon + 1)
                val matchedEmoji = shortcutMap[candidate]
                if (matchedEmoji != null) {
                    val seg = JSONObject()
                    seg.put("emoji", JSONObject().put("emojiId", matchedEmoji.emojiId))
                    seg.put("emojiId", matchedEmoji.emojiId)
                    segments.put(seg)
                    remaining = remaining.substring(nextColon + 1)
                } else {
                    segments.put(JSONObject().put("text", remaining.substring(0, 1)))
                    remaining = remaining.substring(1)
                }
            } else {
                segments.put(JSONObject().put("text", remaining))
                break
            }
        }

        val reqBody = JSONObject().apply {
            put("context", context)
            put("params", config.sendParams)
            put("clientMessageId", java.util.UUID.randomUUID().toString())
            put("richMessage", JSONObject().put("textSegments", segments))
        }

        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", agent)
            .header("Content-Type", "application/json")
            .post(reqBody.toString().toRequestBody("application/json".toMediaType()))

        if (YouTubeAuth.isLoggedIn) {
            YouTubeAuth.buildAuthHeaders().forEach { (k, v) -> builder.header(k, v) }
        }

        val response = client.newCall(builder.build()).execute().use { res ->
            if (!res.isSuccessful) error("YouTube merespons ${res.code}")
            JSONObject(res.body?.string() ?: error("Respons kirim pesan kosong"))
        }

        val actions = response.optJSONArray("actions")
        if (actions != null && actions.length() > 0) {
            for (i in 0 until actions.length()) {
                val act = actions.optJSONObject(i) ?: continue
                val item = act.optJSONObject("addChatItemAction")?.optJSONObject("item")
                    ?: act.optJSONObject("replayChatItemAction")?.optJSONArray("actions")?.optJSONObject(0)?.optJSONObject("addChatItemAction")?.optJSONObject("item")
                if (item != null) {
                    val msgObj = sequenceOf("liveChatTextMessageRenderer", "liveChatPaidMessageRenderer", "liveChatMembershipItemRenderer")
                        .mapNotNull { item.optJSONObject(it) }.firstOrNull()
                    if (msgObj != null) {
                        val emojis = mutableMapOf<String, String>()
                        val userMsg = text(msgObj.optJSONObject("message"), emojis)
                        val photoUrl = msgObj.optJSONObject("authorPhoto")?.optJSONArray("thumbnails")?.let { thumbs ->
                            if (thumbs.length() > 0) thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") else null
                        } ?: config.authorPhotoUrl
                        return ChatMessage(
                            id = msgObj.optString("id", java.util.UUID.randomUUID().toString()),
                            author = text(msgObj.optJSONObject("authorName")).ifBlank { config.authorName },
                            text = userMsg.ifBlank { rawText },
                            offset = null,
                            emoji = emojis,
                            authorPhotoUrl = photoUrl,
                            badges = config.badges,
                            badge = if (config.badges.any { it.type == "MEMBER" }) "MEMBER" else null
                        )
                    }
                }
            }
        }

        return ChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            author = config.authorName,
            text = rawText,
            offset = null,
            authorPhotoUrl = config.authorPhotoUrl,
            badges = config.badges,
            badge = if (config.badges.any { it.type == "MEMBER" }) "MEMBER" else null
        )
    }
    private fun parse(renderer: JSONObject): ChatBatch {
        val messages = mutableListOf<ChatMessage>()
        var pinnedMessage: ChatMessage? = null
        var unpinBanner = false
        var lastOffset: Long? = null
        fun parseMessageObj(message: JSONObject, time: Long?, isSuperChat: Boolean, isMembership: Boolean): ChatMessage? {
            val emojis = mutableMapOf<String, String>()
            val purchaseAmount = text(message.optJSONObject("purchaseAmountText"))
            val headerSubtext = text(message.optJSONObject("headerSubtext"), emojis)
            val userMessage = text(message.optJSONObject("message"), emojis)
            val content = when {
                userMessage.isNotBlank() && headerSubtext.isNotBlank() -> "$headerSubtext • $userMessage"
                userMessage.isNotBlank() -> userMessage
                headerSubtext.isNotBlank() -> headerSubtext
                purchaseAmount.isNotBlank() -> purchaseAmount
                else -> ""
            }
            if (content.isBlank()) return null
            val photoUrl = message.optJSONObject("authorPhoto")?.optJSONArray("thumbnails")?.let { thumbs ->
                if (thumbs.length() > 0) thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") else null
            }
            val badgeList = mutableListOf<ChatBadge>()
            val badges = message.optJSONArray("authorBadges")
            if (badges != null) {
                for (b in 0 until badges.length()) {
                    val bObj = badges.optJSONObject(b)?.let {
                        it.optJSONObject("liveChatAuthorBadgeRenderer") ?: it.optJSONObject("authorBadgeRenderer")
                    } ?: continue

                    val iconType = bObj.optJSONObject("icon")?.optString("iconType").orEmpty()
                    val tooltip = bObj.optString("tooltip").ifBlank {
                        bObj.optJSONObject("accessibility")?.optJSONObject("accessibilityData")?.optString("label").orEmpty()
                    }.takeIf { it.isNotBlank() }

                    val thumbs = bObj.optJSONObject("customThumbnail")?.optJSONArray("thumbnails")
                        ?: bObj.optJSONObject("image")?.optJSONArray("thumbnails")
                    val customUrl = thumbs?.let {
                        if (it.length() > 0) it.optJSONObject(it.length() - 1)?.optString("url") else null
                    }?.takeIf { it.isNotBlank() }
                        ?: bObj.optJSONObject("customThumbnail")?.optString("url")?.takeIf { it.isNotBlank() }

                    val resolvedUrl = when {
                        customUrl == null -> null
                        customUrl.startsWith("//") -> "https:$customUrl"
                        else -> customUrl
                    }

                    when {
                        !resolvedUrl.isNullOrBlank() -> {
                            badgeList += ChatBadge(
                                type = "MEMBER",
                                imageUrl = resolvedUrl,
                                tooltip = tooltip ?: "Member"
                            )
                        }
                        iconType == "MODERATOR" -> {
                            badgeList += ChatBadge(
                                type = "MODERATOR",
                                imageUrl = null,
                                tooltip = tooltip ?: "Moderator"
                            )
                        }
                        iconType == "OWNER" -> {
                            badgeList += ChatBadge(
                                type = "OWNER",
                                imageUrl = null,
                                tooltip = tooltip ?: "Owner"
                            )
                        }
                        iconType == "VERIFIED" -> {
                            badgeList += ChatBadge(
                                type = "VERIFIED",
                                imageUrl = null,
                                tooltip = tooltip ?: "Verified"
                            )
                        }
                        iconType == "MEMBER" -> {
                            badgeList += ChatBadge(
                                type = "MEMBER",
                                imageUrl = null,
                                tooltip = tooltip ?: "Member"
                            )
                        }
                        tooltip?.lowercase()?.contains("member") == true -> {
                            badgeList += ChatBadge(
                                type = "MEMBER",
                                imageUrl = null,
                                tooltip = tooltip
                            )
                        }
                    }
                }
            }
            if (isMembership && badgeList.none { it.type == "MEMBER" }) {
                badgeList += ChatBadge(type = "MEMBER", imageUrl = null, tooltip = "Member")
            }
            val primaryBadge = when {
                badgeList.any { it.type == "OWNER" } -> "OWNER"
                badgeList.any { it.type == "MODERATOR" } -> "MODERATOR"
                badgeList.any { it.type == "MEMBER" } -> "MEMBER"
                badgeList.isNotEmpty() -> badgeList.first().type
                else -> null
            }
            return ChatMessage(
                id = message.optString("id", "${message.optString("timestampUsec")}:$content"),
                author = text(message.optJSONObject("authorName")).ifBlank { "Penonton" },
                text = content,
                offset = time,
                emoji = emojis,
                authorPhotoUrl = photoUrl,
                badges = badgeList,
                badge = primaryBadge,
                purchaseAmount = purchaseAmount.takeIf { it.isNotBlank() },
                isSuperChat = isSuperChat
            )
        }
        fun parseAction(action: JSONObject, time: Long?) {
            val replay = action.optJSONObject("replayChatItemAction")
            if (replay != null) {
                val current = replay.optString("videoOffsetTimeMsec").toLongOrNull()?.coerceAtLeast(0L)
                if (current != null) { lastOffset = current; offset = current }
                val nested = replay.optJSONArray("actions") ?: JSONArray()
                for (i in 0 until nested.length()) nested.optJSONObject(i)?.let { parseAction(it, current ?: 0L) }
                return
            }
            if (action.has("removeBannerForLiveChatCommand")) {
                unpinBanner = true
                pinnedMessage = null
                return
            }
            val bannerCmd = action.optJSONObject("addBannerToLiveChatCommand")
            if (bannerCmd != null) {
                val bannerContents = bannerCmd.optJSONObject("bannerRenderer")
                    ?.optJSONObject("liveChatBannerRenderer")
                    ?.optJSONObject("contents")
                val bannerMsgObj = bannerContents?.let {
                    sequenceOf("liveChatTextMessageRenderer", "liveChatPaidMessageRenderer", "liveChatMembershipItemRenderer")
                        .mapNotNull { k -> it.optJSONObject(k) }.firstOrNull()
                }
                if (bannerMsgObj != null) {
                    parseMessageObj(bannerMsgObj, time, false, false)?.let {
                        pinnedMessage = it
                        unpinBanner = false
                    }
                }
                return
            }
            val item = action.optJSONObject("addChatItemAction")?.optJSONObject("item") ?: return
            val isSuperChat = item.has("liveChatPaidMessageRenderer") || item.has("liveChatPaidStickerRenderer")
            val isMembership = item.has("liveChatMembershipItemRenderer")
            val message = sequenceOf("liveChatTextMessageRenderer", "liveChatPaidMessageRenderer", "liveChatMembershipItemRenderer", "liveChatPaidStickerRenderer")
                .mapNotNull { item.optJSONObject(it) }.firstOrNull() ?: return
            parseMessageObj(message, time, isSuperChat, isMembership)?.let { messages += it }
        }
        val actions = renderer.optJSONArray("actions") ?: JSONArray()
        for (i in 0 until actions.length()) actions.optJSONObject(i)?.let { parseAction(it, null) }
        val continuations = renderer.optJSONArray("continuations")
        var nextContObj: JSONObject? = null
        var isInvalidation = false
        if (continuations != null) {
            for (i in 0 until continuations.length()) {
                val c = continuations.optJSONObject(i) ?: continue
                c.optJSONObject("playerSeekContinuationData")?.optString("continuation")?.takeIf { it.isNotBlank() }?.let {
                    seekContinuation = it
                }
                if (nextContObj == null) {
                    val inv = c.optJSONObject("invalidationContinuationData")
                    if (inv != null) {
                        nextContObj = inv
                        isInvalidation = true
                    } else {
                        nextContObj = sequenceOf("timedContinuationData", "liveChatReplayContinuationData", "reloadContinuationData")
                            .mapNotNull { c.optJSONObject(it) }
                            .firstOrNull()
                    }
                }
            }
        }
        continuation = nextContObj?.optString("continuation")?.takeIf { it.isNotBlank() }
        val timeoutMs = when {
            isInvalidation -> 2000L
            nextContObj != null -> nextContObj.optLong("timeoutMs", 2500L).coerceIn(1000L, 5000L)
            else -> 2500L
        }
        return ChatBatch(messages, continuation, timeoutMs, lastOffset, currentInputConfig, pinnedMessage, unpinBanner)
    }
    private fun text(value: JSONObject?, emojis: MutableMap<String, String>? = null): String {
        if (value == null) return ""
        value.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = value.optJSONArray("runs") ?: return ""
        return (0 until runs.length()).joinToString("") { i ->
            val run = runs.optJSONObject(i)
            run?.optString("text")?.takeIf { it.isNotEmpty() } ?: run?.optJSONObject("emoji")?.let { emoji ->
                val shortcut = emoji.optJSONArray("shortcuts")?.optString(0).orEmpty()
                val thumbs = emoji.optJSONObject("image")?.optJSONArray("thumbnails")
                val url = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty()
                if (emojis != null && url.startsWith("https://")) {
                    val token = "\uFFF0${emojis.size}\uFFF1"
                    emojis[token] = url
                    token
                } else shortcut
            }.orEmpty()
        }
    }
}

/** State holder for YouTube live chat / replay chat */
internal class LiveChatState(val videoUrl: String, val live: Boolean, private val scope: CoroutineScope) {
    val videoId = run {
        val uri = android.net.Uri.parse(videoUrl)
        (if (uri.host == "youtu.be") uri.lastPathSegment else uri.getQueryParameter("v") ?: uri.lastPathSegment).orEmpty()
    }
    var hasChat by mutableStateOf(live)
    var topChat by mutableStateOf(true)
    var pinnedMessage by mutableStateOf<ChatMessage?>(null)
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true)
    var position by mutableLongStateOf(0L)
    var retryTrigger by mutableIntStateOf(0)
    var inputConfig by mutableStateOf<ChatInputConfig?>(null)
    var sending by mutableStateOf(false)
    var sendError by mutableStateOf<String?>(null)
    var activeSession: ChatSession? = null

    val visibleMessages: List<ChatMessage>
        get() = if (live) messages.takeLast(150) else messages.filter { it.offset != null && it.offset <= position }.takeLast(150)

    val superChats: List<ChatMessage>
        get() = visibleMessages.filter { it.isSuperChat || !it.purchaseAmount.isNullOrBlank() }.takeLast(15)

    fun retry() {
        retryTrigger++
    }

    fun switchFilter(useTopChat: Boolean) {
        if (topChat != useTopChat) {
            topChat = useTopChat
            retryTrigger++
        }
    }

    suspend fun sendMessage(text: String): Boolean = withContext(Dispatchers.IO) {
        val sess = activeSession ?: return@withContext false
        if (text.isBlank() || sending) return@withContext false
        sending = true
        sendError = null
        try {
            val emojis = inputConfig?.customEmojis ?: emptyList()
            val sentMsg = sess.sendMessage(text, emojis)
            if (sentMsg != null) {
                withContext(Dispatchers.Main) {
                    messages = (messages + sentMsg).distinctBy { it.id }
                }
            }
            true
        } catch (e: Exception) {
            sendError = e.message ?: "Gagal mengirim pesan"
            false
        } finally {
            sending = false
        }
    }
}

@Composable
internal fun rememberLiveChat(videoUrl: String, live: Boolean, player: ExoPlayer): LiveChatState {
    val scope = rememberCoroutineScope()
    val state = remember(videoUrl, live) { LiveChatState(videoUrl, live, scope) }

    LaunchedEffect(state, state.retryTrigger) {
        if (state.videoId.isBlank()) return@LaunchedEffect
        state.messages = emptyList()
        state.pinnedMessage = null
        state.error = null
        state.loading = true
        state.hasChat = state.live
        try {
            var session = ChatSession(state.videoId, state.live, state.topChat)
            state.activeSession = session
            var batch = withContext(Dispatchers.IO) { session.start() }
            state.hasChat = true
            if (batch.inputConfig != null) state.inputConfig = batch.inputConfig
            if (batch.unpinBanner) state.pinnedMessage = null
            if (batch.pinnedMessage != null) state.pinnedMessage = batch.pinnedMessage

            val initialPos = player.currentPosition.coerceAtLeast(0L)
            state.position = initialPos
            if (!state.live && initialPos > (batch.offset ?: 0L) + 15_000L) {
                withContext(Dispatchers.IO) { session.seek(initialPos) }?.let { seekBatch ->
                    batch = seekBatch
                }
            }

            var minOffset = batch.messages.mapNotNull { it.offset }.minOrNull() ?: 0L
            var maxOffset = batch.offset ?: batch.messages.mapNotNull { it.offset }.maxOrNull() ?: 0L
            var previousPosition = player.currentPosition.coerceAtLeast(0L)

            suspend fun seekReplayTo(targetPos: Long): ChatBatch {
                val sought = withContext(Dispatchers.IO) {
                    runCatching { session.seek(targetPos) }.getOrNull()
                }
                if (sought != null) return sought
                session = ChatSession(state.videoId, false, state.topChat)
                state.activeSession = session
                val started = withContext(Dispatchers.IO) { session.start() }
                return if (targetPos > 15_000L) {
                    withContext(Dispatchers.IO) { runCatching { session.seek(targetPos) }.getOrNull() } ?: started
                } else started
            }

            while (true) {
                if (batch.inputConfig != null) state.inputConfig = batch.inputConfig
                if (batch.unpinBanner) state.pinnedMessage = null
                if (batch.pinnedMessage != null) state.pinnedMessage = batch.pinnedMessage
                state.loading = false

                if (state.live) {
                    state.messages = (state.messages + batch.messages).distinctBy { it.id }.takeLast(500)
                    delay(batch.timeout)
                } else {
                    state.messages = (state.messages + batch.messages).distinctBy { it.id }.takeLast(800)
                    batch.messages.mapNotNull { it.offset }.minOrNull()?.let { minOffset = minOf(minOffset, it) }
                    batch.offset?.let { maxOffset = maxOf(maxOffset, it) }

                    if (batch.continuation == null) {
                        var soughtBatch: ChatBatch? = null
                        while (soughtBatch == null) {
                            val pos = player.currentPosition.coerceAtLeast(0L)
                            state.position = pos
                            if (pos + 4_000L < previousPosition || (pos < minOffset && previousPosition >= minOffset)) {
                                soughtBatch = seekReplayTo(pos)
                                state.messages = emptyList()
                                minOffset = soughtBatch.messages.mapNotNull { it.offset }.minOrNull() ?: pos
                                maxOffset = soughtBatch.offset ?: pos
                            }
                            previousPosition = pos
                            delay(200)
                        }
                        batch = soughtBatch
                        continue
                    }

                    var didSeek = false
                    while (maxOffset > player.currentPosition + 10_000L) {
                        val pos = player.currentPosition.coerceAtLeast(0L)
                        state.position = pos
                        val jumpedBackward = pos + 4_000L < previousPosition
                        val jumpedForward = pos > maxOffset + 15_000L
                        if (jumpedBackward || jumpedForward) {
                            batch = seekReplayTo(pos)
                            state.messages = emptyList()
                            minOffset = batch.messages.mapNotNull { it.offset }.minOrNull() ?: pos
                            maxOffset = batch.offset ?: pos
                            previousPosition = pos
                            didSeek = true
                            break
                        }
                        previousPosition = pos
                        delay(200)
                    }
                    if (didSeek) continue

                    val pos = player.currentPosition.coerceAtLeast(0L)
                    state.position = pos
                    val jumpedBackward = pos + 4_000L < previousPosition
                    val jumpedForward = pos > maxOffset + 15_000L
                    if (jumpedBackward || jumpedForward) {
                        batch = seekReplayTo(pos)
                        state.messages = emptyList()
                        minOffset = batch.messages.mapNotNull { it.offset }.minOrNull() ?: pos
                        maxOffset = batch.offset ?: pos
                        previousPosition = pos
                        continue
                    }
                    previousPosition = pos
                    delay(150)
                }
                batch = withContext(Dispatchers.IO) { session.next() } ?: break
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.error = e.message ?: "Chat gagal dimuat"
            state.loading = false
        }
    }

    LaunchedEffect(videoUrl) {
        while (true) {
            state.position = player.currentPosition.coerceAtLeast(0L)
            delay(100)
        }
    }

    return state
}

/**
 * YouTube-style Live Chat Teaser Box on Watch Screen (replacing comments when live).
 * Inspired by official YouTube layout/live_chat_docked_header_container.
 */
@Composable
internal fun LiveChatTeaser(
    state: LiveChatState,
    isLive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = Surface
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLive) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF0000))
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (isLive) "Live chat" else "Replay chat",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.topChat) "Teratas" else "Semua pesan",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Buka Live Chat",
                    tint = Muted
                )
            }

        }
    }
}

/**
 * Floating semi-transparent Live Chat Overlay for Fullscreen Landscape mode.
 * Inspired by official YouTube layout/live_chat_overlay.
 */
@Composable
internal fun LiveChatFullscreenOverlay(
    state: LiveChatState,
    modifier: Modifier = Modifier
) {
    val messages = state.visibleMessages.takeLast(25)
    if (messages.isEmpty() && !state.loading) return

    val listState = rememberLazyListState()
    val latestOverlayId = messages.lastOrNull()?.id
    var autoScroll by remember { mutableStateOf(true) }

    val isDragging by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(listState) {
        snapshotFlow { Triple(isDragging, listState.isScrollInProgress, listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset) }
            .collect { (dragging, scrolling, position) ->
                if (dragging) autoScroll = false
                else if (!scrolling && position.first == 0 && position.second < 40) autoScroll = true
            }
    }
    LaunchedEffect(latestOverlayId, messages.size, autoScroll) {
        if (latestOverlayId != null && autoScroll) listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier
            .width(300.dp)
            .fillMaxHeight(0.60f)
            .padding(start = 16.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(messages.asReversed(), key = { it.id }) { msg ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xB3181818))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    if (!msg.authorPhotoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = msg.authorPhotoUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(top = 2.dp, end = 6.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp, end = 6.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF383838)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = msg.author.firstOrNull()?.uppercase() ?: "•",
                                color = Color.LightGray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val authorColor = when (msg.badge) {
                        "OWNER" -> Color(0xFFFFD600)
                        "MODERATOR" -> Color(0xFF8AB4F8)
                        "MEMBER" -> Color(0xFF81C995)
                        else -> Color(0xFFCCCCCC)
                    }

                    val inline = remember(msg.id, msg.emoji, msg.badges) {
                        val map = mutableMapOf<String, InlineTextContent>()
                        msg.emoji.forEach { (token, url) ->
                            map[token] = InlineTextContent(Placeholder(16.sp, 16.sp, PlaceholderVerticalAlign.TextCenter)) {
                                AsyncImage(model = url, contentDescription = "Emoji", modifier = Modifier.fillMaxSize())
                            }
                        }
                        msg.badges.forEachIndexed { index, badge ->
                            if (!badge.imageUrl.isNullOrBlank()) {
                                val badgeKey = "fs_badge_${msg.id}_$index"
                                map[badgeKey] = InlineTextContent(Placeholder(14.sp, 14.sp, PlaceholderVerticalAlign.Center)) {
                                    AsyncImage(
                                        model = badge.imageUrl,
                                        contentDescription = badge.tooltip ?: "Badge membership",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                        }
                        map
                    }

                    val annotated = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = authorColor, fontSize = 12.sp)) {
                            append(msg.author)
                        }
                        if (msg.badges.isNotEmpty()) {
                            msg.badges.forEachIndexed { index, badge ->
                                append(" ")
                                if (!badge.imageUrl.isNullOrBlank()) {
                                    val badgeKey = "fs_badge_${msg.id}_$index"
                                    appendInlineContent(badgeKey, badge.tooltip ?: "Badge")
                                } else {
                                    withStyle(SpanStyle(fontSize = 10.sp, color = authorColor)) {
                                        when (badge.type) {
                                            "MODERATOR" -> append("🔧")
                                            "OWNER" -> append("👑")
                                            "VERIFIED" -> append("✓")
                                            "MEMBER" -> append("⭐")
                                            else -> append("•")
                                        }
                                    }
                                }
                            }
                        } else if (msg.badge != null) {
                            append(" ")
                            withStyle(SpanStyle(fontSize = 10.sp, color = authorColor)) {
                                when (msg.badge) {
                                    "MODERATOR" -> append("🔧")
                                    "OWNER" -> append("👑")
                                    "MEMBER" -> append("⭐")
                                    else -> append("•")
                                }
                            }
                        }
                        append("  ")
                        val tokens = Regex("\\uFFF0\\d+\\uFFF1")
                        var start = 0
                        tokens.findAll(msg.text).forEach { match ->
                            append(msg.text.substring(start, match.range.first))
                            appendInlineContent(match.value, "emoji")
                            start = match.range.last + 1
                        }
                        append(msg.text.substring(start))
                    }

                    Text(
                        text = annotated,
                        inlineContent = inline,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

/**
 * Super Chat / Super Sticker card in live chat
 */
@Composable
private fun SuperChatMessageCard(msg: ChatMessage) {
    val tierColor = Color(0xFF00897B) // YouTube Teal/Green Super Chat
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tierColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!msg.authorPhotoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = msg.authorPhotoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF004D40)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = msg.author.firstOrNull()?.uppercase() ?: "$",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = msg.author,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    msg.badges.forEach { badge ->
                        Spacer(Modifier.width(4.dp))
                        if (!badge.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = badge.imageUrl,
                                contentDescription = badge.tooltip,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(14.dp).clip(RoundedCornerShape(2.dp))
                            )
                        } else {
                            Text(
                                text = when (badge.type) {
                                    "MODERATOR" -> "🔧"
                                    "OWNER" -> "👑"
                                    "VERIFIED" -> "✓"
                                    else -> "⭐"
                                },
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Text(
                    text = msg.purchaseAmount ?: "Super Chat",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
        if (msg.text.isNotBlank() && msg.text != msg.purchaseAmount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x33000000))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = msg.text,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * Standard YouTube live chat item matching YouTube Desktop/Tablet Dark UI:
 * 24dp Avatar on the left, @Author Name + Inline Badges + White Message Text flowing seamlessly.
 */
@Composable
private fun LiveChatMessageItem(msg: ChatMessage, compact: Boolean = false) {
    if (!compact && (msg.isSuperChat || !msg.purchaseAmount.isNullOrBlank())) {
        SuperChatMessageCard(msg)
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 0.dp else 4.dp, vertical = if (compact) 2.dp else 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (!msg.authorPhotoUrl.isNullOrBlank()) {
            AsyncImage(
                model = msg.authorPhotoUrl,
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 1.dp, end = 10.dp)
                    .size(24.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .padding(top = 1.dp, end = 10.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF383838)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = msg.author.trimStart('@').firstOrNull()?.uppercase() ?: "•",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        val authorColor = when (msg.badge) {
            "OWNER" -> Color(0xFFFFD600)
            "MODERATOR" -> Color(0xFF5E84F1)
            "MEMBER" -> Color(0xFF2BA640)
            else -> Color(0xFF9E9E9E)
        }

        val inline = remember(msg.id, msg.emoji, msg.badges) {
            val map = mutableMapOf<String, InlineTextContent>()
            msg.emoji.forEach { (token, url) ->
                map[token] = InlineTextContent(Placeholder(18.sp, 18.sp, PlaceholderVerticalAlign.TextCenter)) {
                    AsyncImage(model = url, contentDescription = "Emoji", modifier = Modifier.fillMaxSize())
                }
            }
            msg.badges.forEachIndexed { index, badge ->
                if (!badge.imageUrl.isNullOrBlank()) {
                    val badgeKey = "badge_${msg.id}_$index"
                    map[badgeKey] = InlineTextContent(Placeholder(15.sp, 15.sp, PlaceholderVerticalAlign.Center)) {
                        AsyncImage(
                            model = badge.imageUrl,
                            contentDescription = badge.tooltip ?: "Badge membership",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
            map
        }

        val annotated = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Medium,
                    color = authorColor,
                    fontSize = 13.sp
                )
            ) {
                append(msg.author)
            }

            if (msg.badges.isNotEmpty()) {
                msg.badges.forEachIndexed { index, badge ->
                    append(" ")
                    if (!badge.imageUrl.isNullOrBlank()) {
                        val badgeKey = "badge_${msg.id}_$index"
                        appendInlineContent(badgeKey, badge.tooltip ?: "Badge")
                    } else {
                        val badgeColor = when (badge.type) {
                            "OWNER" -> Color(0xFFFFD600)
                            "MODERATOR" -> Color(0xFF5E84F1)
                            "MEMBER" -> Color(0xFF2BA640)
                            else -> Color.LightGray
                        }
                        withStyle(
                            SpanStyle(
                                fontSize = 11.sp,
                                color = badgeColor
                            )
                        ) {
                            when (badge.type) {
                                "MODERATOR" -> append("🛡️")
                                "OWNER" -> append("👑")
                                "VERIFIED" -> append("✓")
                                "MEMBER" -> append("⭐")
                                else -> append("•")
                            }
                        }
                    }
                }
            } else if (msg.badge != null) {
                append(" ")
                withStyle(
                    SpanStyle(
                        fontSize = 11.sp,
                        color = authorColor
                    )
                ) {
                    when (msg.badge) {
                        "MODERATOR" -> append("🛡️")
                        "OWNER" -> append("👑")
                        "MEMBER" -> append("⭐")
                        else -> append("•")
                    }
                }
            }

            append("  ")

            val tokens = Regex("\\uFFF0\\d+\\uFFF1")
            var start = 0
            tokens.findAll(msg.text).forEach { match ->
                append(msg.text.substring(start, match.range.first))
                appendInlineContent(match.value, "emoji")
                start = match.range.last + 1
            }
            append(msg.text.substring(start))
        }

        Text(
            text = annotated,
            inlineContent = inline,
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            color = Color.White,
            maxLines = if (compact) 2 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PinnedChatBannerCard(
    msg: ChatMessage,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF212121))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            LiveChatMessageItem(msg, compact = true)
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Opsi pesan disematkan",
                tint = Color(0xFFAAAAAA),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Full Live Chat Panel matching YouTube Desktop/Tablet Dark UI (#0F0F0F):
 * - Header with "Top chat v" / "Live chat v" dropdown & Close "X" button
 * - Auto-scrolling message stream
 * - Pinned banner card at bottom of stream
 * - Pill-shaped "Chat as ..." input bar with inline Emoji button & action buttons
 */
@Composable
internal fun LiveChatPanel(
    state: LiveChatState,
    draggable: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val visible = state.visibleMessages
    val listState = rememberLazyListState()
    val latestId = visible.lastOrNull()?.id
    var autoScroll by remember { mutableStateOf(true) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val isDragging by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(listState) {
        snapshotFlow { Triple(isDragging, listState.isScrollInProgress, listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset) }
            .collect { (dragging, scrolling, position) ->
                if (dragging) autoScroll = false
                else if (!scrolling && position.first == 0 && position.second < 40) autoScroll = true
            }
    }
    LaunchedEffect(latestId, visible.size, autoScroll) {
        if (latestId != null && autoScroll) listState.scrollToItem(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .imePadding()
    ) {
        // Header Bar matching YouTube Desktop ("Top chat v" ... "⋮" "✕")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showFilterMenu = true }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val headerLabel = when {
                        !state.live -> if (state.topChat) "Top chat replay" else "Live chat replay"
                        state.topChat -> "Top chat"
                        else -> "Live chat"
                    }
                    Text(
                        text = headerLabel,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Pilih filter chat",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false },
                    modifier = Modifier.background(Color(0xFF282828))
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Top chat", color = Color.White, fontSize = 14.sp, fontWeight = if (state.topChat) FontWeight.Bold else FontWeight.Normal)
                                Text("Pesan yang paling relevan ditampilkan", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                            }
                        },
                        onClick = {
                            showFilterMenu = false
                            state.switchFilter(true)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Live chat", color = Color.White, fontSize = 14.sp, fontWeight = if (!state.topChat) FontWeight.Bold else FontWeight.Normal)
                                Text("Semua pesan ditampilkan apa adanya", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                            }
                        },
                        onClick = {
                            showFilterMenu = false
                            state.switchFilter(false)
                        }
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            IconButton(
                onClick = { state.retry() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Muat ulang chat",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Tutup Live Chat",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        HorizontalDivider(color = Color(0xFF272727), thickness = 1.dp)

        when {
            state.error != null -> Column(Modifier.padding(16.dp)) {
                Text(state.error!!, color = Color(0xFFAAAAAA), fontSize = 13.sp)
                TextButton(onClick = { state.retry() }) { Text("Coba lagi", color = Color(0xFF3EA6FF)) }
            }
            state.loading && visible.isEmpty() -> LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF2BA640),
                trackColor = Color(0xFF272727)
            )
            visible.isEmpty() -> Text(
                if (state.live) "Belum ada pesan." else "Belum ada pesan pada posisi ini.",
                color = Color(0xFF9E9E9E),
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Ticker for Super Chats
        if (state.superChats.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.superChats.takeLast(10), key = { "sc_${it.id}" }) { sc ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF00796B))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!sc.authorPhotoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = sc.authorPhotoUrl,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp).clip(CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = sc.purchaseAmount ?: sc.author,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            HorizontalDivider(color = Color(0xFF272727), thickness = 0.5.dp)
        }

        // Messages Stream
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                items(visible.asReversed(), key = { it.id }) { msg ->
                    LiveChatMessageItem(msg)
                }
            }

            if (!autoScroll && listState.canScrollBackward) {
                FloatingActionButton(
                    onClick = {
                        autoScroll = true
                        scope.launch { listState.scrollToItem(0) }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .size(34.dp),
                    containerColor = Color(0xFF2BA640),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll ke bawah", modifier = Modifier.size(20.dp))
                }
            }
        }

        // Pinned Message Banner above Input Bar (matching YouTube Desktop screenshot)
        val pinned = state.pinnedMessage
        if (pinned != null) {
            PinnedChatBannerCard(
                msg = pinned,
                onDismiss = { state.pinnedMessage = null }
            )
        }

        if (state.live) {
            var inputText by remember { mutableStateOf("") }
            var showEmojiPicker by remember { mutableStateOf(false) }
            var showLoginDialog by remember { mutableStateOf(false) }

            if (showLoginDialog) {
                YouTubeLoginDialog(onDismiss = {
                    showLoginDialog = false
                    state.retry()
                })
            }

            HorizontalDivider(color = Color(0xFF272727), thickness = 1.dp)

            val config = state.inputConfig
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F0F))
            ) {
                if (state.sendError != null) {
                    Text(
                        text = state.sendError!!,
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                if (showEmojiPicker && config != null && config.customEmojis.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(128.dp)
                            .background(Color(0xFF181818))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Emoji Khusus Channel (${config.customEmojis.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2BA640),
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { showEmojiPicker = false },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup Emoji", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(config.customEmojis) { em ->
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF272727))
                                        .clickable {
                                            inputText += if (inputText.endsWith(" ") || inputText.isEmpty()) em.shortcut + " " else " " + em.shortcut + " "
                                        }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = em.imageUrl,
                                        contentDescription = em.shortcut,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFF272727), thickness = 0.5.dp)
                }

                // Bottom Pill Input Row matching YouTube Desktop ("Chat as a ... 😊" + action circle)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!YouTubeAuth.isLoggedIn) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF212121))
                                .clickable { showLoginDialog = true }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Chat...",
                                color = Color(0xFF9E9E9E),
                                fontSize = 13.sp
                            )
                            Text("😊", fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF212121))
                                .clickable { showLoginDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FavoriteBorder,
                                contentDescription = "Login",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        val isMember = config?.badges?.any { it.type == "MEMBER" } == true
                        val placeholderText = when {
                            config == null -> "Menyiapkan chat..."
                            isMember -> "Chat as a member..."
                            config.authorName.isNotBlank() -> "Chat as ${config.authorName}..."
                            else -> "Chat..."
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF212121))
                                .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = placeholderText,
                                        color = Color(0xFF9E9E9E),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    enabled = !state.sending && config != null,
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 13.sp
                                    ),
                                    cursorBrush = SolidColor(Color.White),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (config != null && config.customEmojis.isNotEmpty()) {
                                        showEmojiPicker = !showEmojiPicker
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("😊", fontSize = 16.sp)
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        val canSend = inputText.isNotBlank() && !state.sending && config != null
                        if (canSend || state.sending) {
                            IconButton(
                                onClick = {
                                    val textToSend = inputText
                                    scope.launch {
                                        val success = state.sendMessage(textToSend)
                                        if (success) {
                                            inputText = ""
                                            showEmojiPicker = false
                                            autoScroll = true
                                            listState.scrollToItem(0)
                                        }
                                    }
                                },
                                enabled = canSend,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2BA640))
                            ) {
                                if (state.sending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Kirim",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF212121)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.FavoriteBorder,
                                    contentDescription = "Reaksi",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Backwards-compatibility wrapper for LiveChatPanel */
@Composable
internal fun LiveChatPanel(
    videoUrl: String,
    live: Boolean,
    player: ExoPlayer,
    draggable: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberLiveChat(videoUrl, live, player)
    LiveChatPanel(state, draggable, onDismiss, modifier)
}
