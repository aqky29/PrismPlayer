package id.flufclip.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Intent
import android.net.Uri
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

internal sealed class LiveChatActionPanel {
    data class Input(val config: ChatInputConfig) : LiveChatActionPanel()

    data class SignInRequired(
        val buttonText: String = "Sign in to chat",
        val subtext: String? = "All messages you send will appear publicly"
    ) : LiveChatActionPanel()

    data class Restricted(
        val message: String,
        val iconType: String? = null,
        val learnMoreLabel: String? = null,
        val learnMoreUrl: String? = null,
        val isSubscribersOnly: Boolean = false
    ) : LiveChatActionPanel()
}

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
    val isSuperChat: Boolean = false,
    val isMembership: Boolean = false,
    val membershipSubtext: String? = null,
    val membershipHeader: String? = null,
    val isSystemNotice: Boolean = false,
    val actionLabel: String? = null,
    val actionUrl: String? = null
)

internal data class ChatBatch(
    val messages: List<ChatMessage>,
    val continuation: String?,
    val timeout: Long,
    val offset: Long?,
    val inputConfig: ChatInputConfig? = null,
    val actionPanel: LiveChatActionPanel? = null,
    val pinnedMessage: ChatMessage? = null,
    val unpinBanner: Boolean = false,
    val modeNotice: String? = null
)

internal fun findJsonObject(value: Any?, key: String): JSONObject? {
    if (value is JSONObject) {
        value.optJSONObject(key)?.let { return it }
        val keys = value.keys()
        while (keys.hasNext()) findJsonObject(value.opt(keys.next()), key)?.let { return it }
    } else if (value is JSONArray) {
        for (i in 0 until value.length()) findJsonObject(value.opt(i), key)?.let { return it }
    }
    return null
}

internal fun parseJsonText(value: JSONObject?, emojis: MutableMap<String, String>? = null): String {
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

internal fun normalizeLiveChatUrl(rawUrl: String?): String? {
    val trimmed = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return when {
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("/") -> "https://www.youtube.com$trimmed"
        trimmed.startsWith("https://") || trimmed.startsWith("http://") -> trimmed
        else -> null
    }
}

internal fun parseChatInputRenderer(
    inputRenderer: JSONObject,
    fallbackAuthorName: String = "",
    fallbackAuthorPhoto: String? = null
): ChatInputConfig? {
    val sendEndpoint = findJsonObject(inputRenderer, "sendLiveChatMessageEndpoint") ?: return null
    val sendParams = sendEndpoint.optString("params").takeIf { it.isNotBlank() } ?: return null
    val authorName = parseJsonText(inputRenderer.optJSONObject("authorName")).ifBlank { fallbackAuthorName }
    val authorThumbs = inputRenderer.optJSONObject("authorPhoto")?.optJSONArray("thumbnails")
    val authorPhotoUrl = authorThumbs?.let {
        if (it.length() > 0) it.optJSONObject(it.length() - 1)?.optString("url")?.let { u -> normalizeLiveChatUrl(u) ?: u } else null
    } ?: fallbackAuthorPhoto

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
            userBadges.add(ChatBadge(type = "MEMBER", imageUrl = normalizeLiveChatUrl(customUrl) ?: customUrl, tooltip = tooltip ?: "Member"))
        }
    }

    val emojiList = mutableListOf<CustomChatEmoji>()
    val picker = findJsonObject(inputRenderer, "liveChatEmojiPickerRenderer")
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
                                    imageUrl = normalizeLiveChatUrl(eurl) ?: eurl,
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

private fun parseRestrictedParticipation(restricted: JSONObject): LiveChatActionPanel.Restricted {
    val msgObj = restricted.optJSONObject("message")
    val rawMessage = parseJsonText(msgObj).trim()
    val iconType = restricted.optJSONObject("icon")?.optString("iconType")?.takeIf { it.isNotBlank() }

    val explicitBtn = restricted.optJSONObject("learnMore")?.optJSONObject("buttonRenderer")
        ?: restricted.optJSONArray("buttons")?.let { arr ->
            (0 until arr.length())
                .mapNotNull { arr.optJSONObject(it)?.optJSONObject("buttonRenderer") }
                .firstOrNull { findJsonObject(it, "urlEndpoint") != null }
        }

    var learnMoreLabel = parseJsonText(explicitBtn?.optJSONObject("text")).trim().takeIf { it.isNotBlank() }
    var learnMoreUrl = normalizeLiveChatUrl(findJsonObject(explicitBtn, "urlEndpoint")?.optString("url"))

    if (learnMoreUrl == null) {
        val runs = msgObj?.optJSONArray("runs")
        if (runs != null) {
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val url = normalizeLiveChatUrl(findJsonObject(run, "urlEndpoint")?.optString("url"))
                if (url != null) {
                    val runLabel = run.optString("text").trim()
                    learnMoreUrl = url
                    if (learnMoreLabel == null && runLabel.isNotBlank()) learnMoreLabel = runLabel
                    break
                }
            }
        }
    }

    val cleanMessage = if (!learnMoreLabel.isNullOrBlank() && rawMessage.endsWith(learnMoreLabel, ignoreCase = true)) {
        rawMessage.dropLast(learnMoreLabel.length).trimEnd(' ', '•', '\u00A0')
    } else {
        rawMessage
    }

    val isSubscribersOnly =
        rawMessage.contains("subscriber", ignoreCase = true) ||
            rawMessage.contains("subscribe", ignoreCase = true) ||
            rawMessage.contains("pelanggan", ignoreCase = true) ||
            (learnMoreUrl?.contains("subs_only", ignoreCase = true) == true)

    return LiveChatActionPanel.Restricted(
        message = cleanMessage.ifBlank { "Chat is restricted" },
        iconType = iconType,
        learnMoreLabel = learnMoreLabel,
        learnMoreUrl = learnMoreUrl,
        isSubscribersOnly = isSubscribersOnly
    )
}

private fun parseActionMessageRenderer(msgObj: JSONObject): LiveChatActionPanel? {
    val btn = msgObj.optJSONObject("button")?.optJSONObject("buttonRenderer") ?: findJsonObject(msgObj, "buttonRenderer")
    val btnText = parseJsonText(btn?.optJSONObject("text")).trim()
    val hasSignIn =
        btn?.let { findJsonObject(it, "signInEndpoint") != null } == true ||
            findJsonObject(msgObj, "signInEndpoint") != null ||
            btnText.contains("sign in", ignoreCase = true) ||
            btnText.contains("masuk", ignoreCase = true)
    val subtext = parseJsonText(msgObj.optJSONObject("subtext")?.optJSONObject("messageSubtextRenderer")?.optJSONObject("text")).takeIf { it.isNotBlank() }
        ?: parseJsonText(msgObj.optJSONObject("subtext")).takeIf { it.isNotBlank() }
        ?: parseJsonText(msgObj.optJSONObject("text")).takeIf { it.isNotBlank() }

    if (hasSignIn) {
        return LiveChatActionPanel.SignInRequired(
            buttonText = btnText.ifBlank { "Sign in to chat" },
            subtext = subtext ?: "All messages you send will appear publicly"
        )
    }

    val mainText = parseJsonText(msgObj.optJSONObject("text")).trim()
    val restrictedText = mainText.ifBlank { subtext.orEmpty().trim() }
    if (restrictedText.isBlank()) return null
    val url = normalizeLiveChatUrl(findJsonObject(btn, "urlEndpoint")?.optString("url"))
        ?: if (btn != null && (findJsonObject(btn, "channelCreationFormEndpoint") != null || findJsonObject(btn, "createChannelEndpoint") != null)) {
            "https://www.youtube.com/create_channel"
        } else null
    return LiveChatActionPanel.Restricted(
        message = restrictedText,
        iconType = msgObj.optJSONObject("icon")?.optString("iconType")?.takeIf { it.isNotBlank() },
        learnMoreLabel = btnText.takeIf { it.isNotBlank() && url != null },
        learnMoreUrl = url,
        isSubscribersOnly = restrictedText.contains("subscriber", ignoreCase = true) || restrictedText.contains("pelanggan", ignoreCase = true)
    )
}

internal fun parseLiveChatActionPanel(
    root: JSONObject,
    fallbackAuthorName: String = "",
    fallbackAuthorPhoto: String? = null
): LiveChatActionPanel? {
    val rawPanel = root.optJSONObject("continuationContents")?.optJSONObject("liveChatContinuation")?.optJSONObject("actionPanel")
        ?: root.optJSONObject("contents")?.optJSONObject("liveChatRenderer")?.optJSONObject("actionPanel")
        ?: findJsonObject(root, "actionPanel")
        ?: root.optJSONObject("liveChatActionPanelRenderer")
    val panelObj = rawPanel?.let { it.optJSONObject("liveChatActionPanelRenderer") ?: it }

    if (panelObj != null) {
        panelObj.optJSONObject("liveChatMessageInputRenderer")?.let {
            parseChatInputRenderer(it, fallbackAuthorName, fallbackAuthorPhoto)?.let { cfg ->
                return LiveChatActionPanel.Input(cfg)
            }
        }
        panelObj.optJSONObject("liveChatRestrictedParticipationRenderer")?.let {
            return parseRestrictedParticipation(it)
        }
        panelObj.optJSONObject("messageRenderer")?.let {
            parseActionMessageRenderer(it)?.let { parsed -> return parsed }
        }
    }

    findJsonObject(root, "liveChatMessageInputRenderer")?.let {
        parseChatInputRenderer(it, fallbackAuthorName, fallbackAuthorPhoto)?.let { cfg ->
            return LiveChatActionPanel.Input(cfg)
        }
    }
    findJsonObject(root, "liveChatRestrictedParticipationRenderer")?.let {
        return parseRestrictedParticipation(it)
    }
    return null
}

internal fun parseLiveChatNoticeItem(modeObj: JSONObject, offsetMs: Long?): ChatMessage? {
    val msgText = listOf(
        parseJsonText(modeObj.optJSONObject("text")),
        parseJsonText(modeObj.optJSONObject("subtext")),
        parseJsonText(modeObj.optJSONObject("message"))
    ).filter { it.isNotBlank() }.joinToString(" ").trim()
    val isEligibleNotice =
        msgText.contains("mode", ignoreCase = true) ||
            msgText.contains("subscriber", ignoreCase = true) ||
            msgText.contains("member", ignoreCase = true) ||
            msgText.contains("pelanggan", ignoreCase = true) ||
            msgText.contains("anggota", ignoreCase = true) ||
            msgText.contains("slow", ignoreCase = true)
    if (!isEligibleNotice) return null

    val button = modeObj.optJSONObject("actionButton")?.optJSONObject("buttonRenderer")
        ?: findJsonObject(modeObj, "buttonRenderer")
    val actionLabel = parseJsonText(button?.optJSONObject("text")).trim().takeIf { it.isNotBlank() }
    val actionUrl = normalizeLiveChatUrl(findJsonObject(button, "urlEndpoint")?.optString("url"))
    val cleanMsg = if (!actionLabel.isNullOrBlank() && msgText.endsWith(actionLabel, ignoreCase = true)) {
        msgText.dropLast(actionLabel.length).trimEnd()
    } else {
        msgText
    }
    val noticeId = modeObj.optString("id").ifBlank { "${modeObj.optString("timestampUsec")}:$msgText" }
    return ChatMessage(
        id = noticeId,
        author = "YouTube",
        text = cleanMsg,
        offset = offsetMs,
        isSystemNotice = true,
        actionLabel = actionLabel,
        actionUrl = actionUrl
    )
}

/** YouTube's web chat endpoint is separate from its video comments. */
internal class ChatSession(private val videoId: String, private val live: Boolean, private val topChat: Boolean = false) {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).addNetworkInterceptor(accountHeaderInterceptor).build()
    private val agent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    private var context: JSONObject? = null
    private var apiKey = ""
    private var continuation: String? = null
    private var seekContinuation: String? = null
    private var offset = 0L
    var currentActionPanel: LiveChatActionPanel? = null
        private set
    val currentInputConfig: ChatInputConfig?
        get() = (currentActionPanel as? LiveChatActionPanel.Input)?.config

    private fun get(url: String, withAuth: Boolean = true): String {
        val desktopUrl = if (url.contains("?")) "$url&app=desktop&persist_app=1" else "$url?app=desktop&persist_app=1"
        val builder = Request.Builder().url(desktopUrl).header("User-Agent", agent)
        if (withAuth && YouTubeAuth.isLoggedIn) {
            YouTubeAuth.buildAuthHeaders().forEach { (k, v) -> builder.header(k, v) }
        }
        return client.newCall(builder.build()).execute().use {
            if (!it.isSuccessful) throw ChatHttpException(it.code, it.header("Retry-After")?.toLongOrNull()?.coerceIn(0L, 120L)?.times(1_000L))
            it.body?.string() ?: error("Respons chat kosong")
        }
    }
    private fun objectAfter(text: String, marker: String): JSONObject? {
        val start = text.indexOf(marker).takeIf { it >= 0 } ?: return null
        return runCatching {
            val next = JSONTokener(text.substring(start + marker.length).trimStart()).nextValue()
            when (next) {
                is JSONObject -> next
                is String -> {
                    val unescaped = Regex("\\\\x([0-9a-fA-F]{2})").replace(next) { m ->
                        m.groupValues[1].toInt(16).toChar().toString()
                    }
                    JSONObject(unescaped)
                }
                else -> null
            }
        }.getOrNull()
    }
    private fun extractInitialData(html: String): JSONObject? =
        objectAfter(html, "window[\"ytInitialData\"] = ")
            ?: objectAfter(html, "window['ytInitialData'] = ")
            ?: objectAfter(html, "var ytInitialData = ")
            ?: objectAfter(html, "ytInitialData = ")

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
    private fun chatEndpointUrl(path: String): String =
        if (apiKey.isNotBlank()) "https://www.youtube.com/youtubei/v1/live_chat/$path?key=$apiKey"
        else "https://www.youtube.com/youtubei/v1/live_chat/$path?prettyPrint=false"

    private fun postRaw(path: String, body: JSONObject): String {
        val builder = Request.Builder().url(chatEndpointUrl(path)).header("User-Agent", agent)
            .header("X-Youtube-Client-Name", "1")
            .header("X-Youtube-Client-Version", "2.20261030.00.00")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        if (YouTubeAuth.isLoggedIn) YouTubeAuth.buildAuthHeaders().forEach { (k, v) -> builder.header(k, v) }
        return client.newCall(builder.build()).execute().use {
            if (!it.isSuccessful) throw ChatHttpException(it.code, it.header("Retry-After")?.toLongOrNull()?.coerceIn(0L, 120L)?.times(1_000L))
            it.body?.string() ?: error("Respons chat kosong")
        }
    }
    private fun post(path: String, body: JSONObject): JSONObject = JSONObject(postRaw(path, body))
    private fun applyNativeBatch(decoded: NativeChatBatchDecoded): ChatBatch {
        continuation = decoded.batch.continuation
        if (!decoded.seekContinuation.isNullOrBlank()) seekContinuation = decoded.seekContinuation
        decoded.batch.offset?.let { offset = it }
        decoded.batch.actionPanel?.let { currentActionPanel = it }
        return decoded.batch
    }
    private fun parseResponseJson(rawJson: String, errorLabel: String = "Unrecognized chat response"): ChatBatch {
        if (NativeParsers.isAvailable) {
            runCatching {
                NativeParsers.parseLiveChatResponse(
                    rawJson,
                    topChat,
                    YouTubeAuth.profile.name,
                    YouTubeAuth.profile.avatarUrl
                )
            }.getOrNull()?.let { return applyNativeBatch(it) }
        }
        val data = JSONObject(rawJson)
        parseInputState(data)
        val renderer = data.optJSONObject("continuationContents")?.optJSONObject("liveChatContinuation")
            ?: error(errorLabel)
        return parse(renderer)
    }
    private fun ytcfg(html: String): JSONObject? = Regex("ytcfg\\.set\\(\\s*\\{").findAll(html).mapNotNull { match ->
        runCatching { JSONTokener(html.substring(match.range.last)).nextValue() as JSONObject }.getOrNull()
    }.firstOrNull { it.has("INNERTUBE_CONTEXT") && it.has("INNERTUBE_API_KEY") }

    private fun defaultWebContext(): JSONObject =
        JSONObject().put(
            "client",
            JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", "2.20261030.00.00")
                .put("userAgent", agent)
                .put("hl", "en")
                .put("gl", "US")
        )

    /** Fallback JSON murni via `/youtubei/v1/next` bila halaman HTML `/live_chat` gagal memuat `ytInitialData`. */
    private fun bootstrapViaInnerTubeNext(): ChatBatch {
        if (context == null) context = defaultWebContext()
        val nextReqBody = JSONObject()
            .put("context", context)
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        val nextBuilder = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/next?prettyPrint=false")
            .header("User-Agent", agent)
            .header("Content-Type", "application/json")
            .header("X-Youtube-Client-Name", "1")
            .header("X-Youtube-Client-Version", "2.20261030.00.00")
            .post(nextReqBody.toString().toRequestBody("application/json".toMediaType()))
        if (YouTubeAuth.isLoggedIn) YouTubeAuth.buildAuthHeaders().forEach { (k, v) -> nextBuilder.header(k, v) }
        val nextData = client.newCall(nextBuilder.build()).execute().use {
            if (!it.isSuccessful) throw ChatHttpException(it.code, it.header("Retry-After")?.toLongOrNull()?.coerceIn(0L, 120L)?.times(1_000L))
            JSONObject(it.body?.string() ?: error("Respons next kosong"))
        }
        val chat = findObject(nextData, "liveChatRenderer") ?: error("Chat is unavailable for this video")
        var token = extractContinuationToken(chat) ?: error("Chat is unavailable for this video")
        val path = if (live) "get_live_chat" else "get_live_chat_replay"
        fun fetch(t: String): JSONObject {
            val res = post(path, JSONObject().put("context", context).put("continuation", t))
            parseInputState(res)
            return res.optJSONObject("continuationContents")?.optJSONObject("liveChatContinuation")
                ?: error("Unrecognized chat response")
        }
        var renderer = fetch(token)
        val subMenu = findObject(renderer, "sortFilterSubMenuRenderer")?.optJSONArray("subMenuItems")
        val targetIdx = if (topChat) 0 else (if (subMenu != null && subMenu.length() > 1) 1 else 0)
        val chosenToken = subMenu?.optJSONObject(targetIdx)?.optJSONObject("continuation")
            ?.optJSONObject("reloadContinuationData")?.optString("continuation")?.takeIf { it.isNotBlank() }
        if (!live && !topChat && !chosenToken.isNullOrBlank()) {
            token = chosenToken
            renderer = fetch(chosenToken)
        }
        val batch = parse(renderer)
        if (live && !chosenToken.isNullOrBlank()) {
            continuation = chosenToken
        }
        if (!live && batch.messages.isEmpty() && batch.continuation == null && seekContinuation == null) {
            error("Live chat replay is unavailable for this video")
        }
        return batch
    }

    /** Replay: token bootstrap penuh dari watch page (continuations[0]), lalu POST langsung ke get_live_chat_replay. */
    private fun startReplay(): ChatBatch {
        val watch = runCatching { get("https://www.youtube.com/watch?v=$videoId") }.getOrNull()
        if (watch != null && NativeParsers.isAvailable) {
            val boot = runCatching {
                NativeParsers.parseLiveChatHtmlBootstrap(
                    watch,
                    isLive = false,
                    topChat = topChat,
                    fallbackAuthor = YouTubeAuth.profile.name,
                    fallbackPhoto = YouTubeAuth.profile.avatarUrl
                )
            }.getOrNull()
            if (boot != null && boot.found && !boot.watchContinuation.isNullOrBlank()) {
                apiKey = boot.apiKey.orEmpty()
                context = boot.context ?: defaultWebContext()
                var raw = postRaw("get_live_chat_replay", JSONObject().put("context", context).put("continuation", boot.watchContinuation))
                var decoded = runCatching {
                    NativeParsers.parseLiveChatResponse(raw, topChat, YouTubeAuth.profile.name, YouTubeAuth.profile.avatarUrl)
                }.getOrNull()
                if (decoded != null) {
                    if (!topChat && !decoded.chosenReloadToken.isNullOrBlank()) {
                        raw = postRaw("get_live_chat_replay", JSONObject().put("context", context).put("continuation", decoded.chosenReloadToken))
                        decoded = NativeParsers.parseLiveChatResponse(raw, topChat, YouTubeAuth.profile.name, YouTubeAuth.profile.avatarUrl)
                            ?: error("Unrecognized chat replay response")
                    }
                    val batch = applyNativeBatch(decoded)
                    if (batch.messages.isEmpty() && batch.continuation == null && seekContinuation == null) {
                        error("Live chat replay is unavailable for this video")
                    }
                    return batch
                }
            }
        }
        val watchData = watch?.let { extractInitialData(it) }
        val chat = watchData?.let { findObject(it, "liveChatRenderer") }
        val config = watch?.let { ytcfg(it) }
        if (chat == null || config == null) {
            return bootstrapViaInnerTubeNext()
        }
        apiKey = config.optString("INNERTUBE_API_KEY")
        context = config.optJSONObject("INNERTUBE_CONTEXT") ?: defaultWebContext()
        var token = extractContinuationToken(chat) ?: return bootstrapViaInnerTubeNext()
        fun fetch(t: String): JSONObject = post("get_live_chat_replay", JSONObject().put("context", context).put("continuation", t))
            .optJSONObject("continuationContents")?.optJSONObject("liveChatContinuation") ?: error("Unrecognized chat replay response")
        var renderer = fetch(token)
        // Menu filter lengkap (Top chat / Live chat) baru tersedia di respons ini.
        val subMenu = findObject(renderer, "sortFilterSubMenuRenderer")?.optJSONArray("subMenuItems")
        if (!topChat && subMenu != null && subMenu.length() > 1) {
            subMenu.optJSONObject(1)?.optJSONObject("continuation")?.optJSONObject("reloadContinuationData")?.optString("continuation")
                ?.takeIf { it.isNotBlank() }?.let { token = it; renderer = fetch(it) }
        }
        val batch = parse(renderer)
        // Replay yang tersedia selalu punya aksi/continuation; respons kosong berarti YouTube mematikan replay chat.
        if (batch.messages.isEmpty() && batch.continuation == null && seekContinuation == null) {
            error("Live chat replay is unavailable for this video")
        }
        return batch
    }
    fun start(): ChatBatch {
        if (!live) return startReplay()
        val endpoint = "live_chat"
        val html = runCatching { get("https://www.youtube.com/$endpoint?v=$videoId", withAuth = true) }.getOrNull().orEmpty()
        if (html.isNotEmpty() && NativeParsers.isAvailable) {
            val boot = runCatching {
                NativeParsers.parseLiveChatHtmlBootstrap(
                    html,
                    isLive = true,
                    topChat = topChat,
                    fallbackAuthor = YouTubeAuth.profile.name,
                    fallbackPhoto = YouTubeAuth.profile.avatarUrl
                )
            }.getOrNull()
            if (boot != null && boot.found && boot.batch != null) {
                apiKey = boot.apiKey.orEmpty()
                context = boot.context ?: defaultWebContext()
                val batch = applyNativeBatch(boot.batch)
                if (!boot.batch.chosenReloadToken.isNullOrBlank()) {
                    continuation = boot.batch.chosenReloadToken
                }
                return batch
            }
        }
        val data = extractInitialData(html)
        if (data == null) {
            return bootstrapViaInnerTubeNext()
        }
        val config = ytcfg(html)
        apiKey = config?.optString("INNERTUBE_API_KEY").orEmpty()
        context = config?.optJSONObject("INNERTUBE_CONTEXT") ?: defaultWebContext()

        parseInputState(data)

        val contents = data.optJSONObject("contents")
        val renderer = contents?.optJSONObject("liveChatRenderer")
            ?: data.optJSONObject("continuationContents")?.optJSONObject("liveChatContinuation")
            ?: return bootstrapViaInnerTubeNext()
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
    private fun findObject(value: Any?, key: String): JSONObject? = findJsonObject(value, key)

    fun next(): ChatBatch? {
        val token = continuation ?: return null
        val path = if (live) "get_live_chat" else "get_live_chat_replay"
        val body = JSONObject().put("context", context ?: defaultWebContext()).put("continuation", token)
        return parseResponseJson(postRaw(path, body), "Unrecognized chat response")
    }

    private fun parseInputState(root: JSONObject) {
        parseLiveChatActionPanel(
            root = root,
            fallbackAuthorName = YouTubeAuth.profile.name,
            fallbackAuthorPhoto = YouTubeAuth.profile.avatarUrl
        )?.let { currentActionPanel = it }
    }
    fun seek(targetOffsetMs: Long): ChatBatch? {
        if (live) return null
        val token = seekContinuation ?: continuation ?: return null
        val body = JSONObject()
            .put("context", context ?: defaultWebContext())
            .put("continuation", token)
            .put("currentPlayerState", JSONObject().put("playerOffsetMs", targetOffsetMs.coerceAtLeast(0L).toString()))
        return runCatching { parseResponseJson(postRaw("get_live_chat_replay", body)) }.getOrNull()
    }

    fun sendMessage(rawText: String, customEmojis: List<CustomChatEmoji>, revision: Int, headers: Map<String, String>): ChatMessage? {
        val config = currentInputConfig ?: return null
        val url = chatEndpointUrl("send_message")

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

        val reqContext = context ?: defaultWebContext()
        val clientVersion = reqContext.optJSONObject("client")?.optString("clientVersion")?.takeIf { it.isNotBlank() } ?: "2.20261030.00.00"
        val reqBody = JSONObject().apply {
            put("context", reqContext)
            put("params", config.sendParams)
            put("clientMessageId", java.util.UUID.randomUUID().toString())
            put("richMessage", JSONObject().put("textSegments", segments))
        }

        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", agent)
            .header("Content-Type", "application/json")
            .header("X-Youtube-Client-Name", "1")
            .header("X-Youtube-Client-Version", clientVersion)
            .header("X-Youtube-Bootstrap-Logged-In", "true")
            .header("Referer", "https://www.youtube.com/live_chat?v=$videoId")
            .post(reqBody.toString().toRequestBody("application/json".toMediaType()))

        check(YouTubeAuth.isLoggedIn && revision == YouTubeAuth.sessionRevision && headers.isNotEmpty()) { "Account changed" }
        headers.forEach { (k, v) -> builder.header(k, v) }

        val response = client.newCall(builder.build()).execute().use { res ->
            val rawBody = res.body?.string().orEmpty()
            val json = runCatching { JSONObject(rawBody) }.getOrNull()
            if (!res.isSuccessful) {
                val errMsg = json?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                error(errMsg ?: "YouTube merespons ${res.code}")
            }
            json ?: error("Empty send message response")
        }
        response.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }?.let { error(it) }

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
        parseInputState(renderer)
        val messages = mutableListOf<ChatMessage>()
        var pinnedMessage: ChatMessage? = null
        var unpinBanner = false
        var modeNotice: String? = null
        var lastOffset: Long? = null
        fun parseMessageObj(
            message: JSONObject,
            time: Long?,
            isSuperChat: Boolean,
            isMembership: Boolean,
            membershipSubtextOverride: String? = null
        ): ChatMessage? {
            val emojis = mutableMapOf<String, String>()
            val purchaseAmount = text(message.optJSONObject("purchaseAmountText"))
            val headerPrimary = text(message.optJSONObject("headerPrimaryText"), emojis)
            val headerSubtext = text(message.optJSONObject("headerSubtext"), emojis)
            val userMessage = text(message.optJSONObject("message"), emojis)

            val membershipHeader = if (isMembership) {
                headerPrimary.takeIf { it.isNotBlank() } ?: (if (headerSubtext.isBlank()) "Anggota baru" else null)
            } else null

            val membershipSub = membershipSubtextOverride ?: if (isMembership) {
                headerSubtext.takeIf { it.isNotBlank() } ?: (if (headerPrimary.isBlank()) "Baru saja bergabung menjadi anggota!" else null)
            } else null

            val content = when {
                isMembership -> {
                    when {
                        userMessage.isNotBlank() -> userMessage
                        membershipSub != null && membershipHeader != null -> "$membershipHeader • $membershipSub"
                        membershipSub != null -> membershipSub
                        membershipHeader != null -> membershipHeader
                        else -> "Bergabung menjadi anggota"
                    }
                }
                userMessage.isNotBlank() && headerSubtext.isNotBlank() -> "$headerSubtext • $userMessage"
                userMessage.isNotBlank() -> userMessage
                headerSubtext.isNotBlank() -> headerSubtext
                headerPrimary.isNotBlank() -> headerPrimary
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
                author = text(message.optJSONObject("authorName")).ifBlank {
                    message.optString("authorName").ifBlank { if (isMembership) "Member" else "Viewers" }
                },
                text = content,
                offset = time,
                emoji = emojis,
                authorPhotoUrl = photoUrl,
                badges = badgeList,
                badge = primaryBadge,
                purchaseAmount = purchaseAmount.takeIf { it.isNotBlank() },
                isSuperChat = isSuperChat,
                isMembership = isMembership,
                membershipSubtext = membershipSub,
                membershipHeader = membershipHeader
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
                val isBannerMembership = bannerContents?.has("liveChatMembershipItemRenderer") == true
                val bannerMsgObj = bannerContents?.let {
                    sequenceOf("liveChatTextMessageRenderer", "liveChatPaidMessageRenderer", "liveChatMembershipItemRenderer")
                        .mapNotNull { k -> it.optJSONObject(k) }.firstOrNull()
                }
                if (bannerMsgObj != null) {
                    parseMessageObj(bannerMsgObj, time, false, isBannerMembership)?.let {
                        pinnedMessage = it
                        unpinBanner = false
                    }
                }
                return
            }
            val item = action.optJSONObject("addChatItemAction")?.optJSONObject("item") ?: return
            val modeObj = item.optJSONObject("liveChatModeChangeMessageRenderer")
                ?: item.optJSONObject("liveChatViewerEngagementMessageRenderer")
            if (modeObj != null) {
                parseLiveChatNoticeItem(modeObj, time)?.let { messages += it }
                return
            }

            val giftPurchase = item.optJSONObject("liveChatSponsorshipsGiftPurchaseAnnouncementRenderer")
            if (giftPurchase != null) {
                val emojis = mutableMapOf<String, String>()
                val headerObj = giftPurchase.optJSONObject("header")?.optJSONObject("liveChatSponsorshipsHeaderRenderer")
                val primary = text(headerObj?.optJSONObject("primaryText"), emojis).ifBlank { "Membagikan Langganan Hadiah" }
                val userMsg = text(giftPurchase.optJSONObject("message"), emojis)
                val photoUrl = giftPurchase.optJSONObject("authorPhoto")?.optJSONArray("thumbnails")?.let { thumbs ->
                    if (thumbs.length() > 0) thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") else null
                }
                val author = text(giftPurchase.optJSONObject("authorName")).ifBlank {
                    giftPurchase.optString("authorName").ifBlank { "Sponsor" }
                }
                val badgeList = mutableListOf(ChatBadge(type = "MEMBER", imageUrl = null, tooltip = "Member"))
                messages += ChatMessage(
                    id = giftPurchase.optString("id", "${giftPurchase.optString("timestampUsec")}:$author:gift"),
                    author = author,
                    text = if (userMsg.isNotBlank()) userMsg else primary,
                    offset = time,
                    emoji = emojis,
                    authorPhotoUrl = photoUrl,
                    badges = badgeList,
                    badge = "MEMBER",
                    isMembership = true,
                    membershipHeader = primary,
                    membershipSubtext = userMsg.takeIf { it.isNotBlank() }
                )
                return
            }

            val giftRedemption = item.optJSONObject("liveChatSponsorshipsGiftRedemptionAnnouncementRenderer")
            if (giftRedemption != null) {
                val emojis = mutableMapOf<String, String>()
                val userMsg = text(giftRedemption.optJSONObject("message"), emojis).ifBlank { "Menerima langganan hadiah" }
                val photoUrl = giftRedemption.optJSONObject("authorPhoto")?.optJSONArray("thumbnails")?.let { thumbs ->
                    if (thumbs.length() > 0) thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") else null
                }
                val author = text(giftRedemption.optJSONObject("authorName")).ifBlank {
                    giftRedemption.optString("authorName").ifBlank { "Member" }
                }
                val badgeList = mutableListOf(ChatBadge(type = "MEMBER", imageUrl = null, tooltip = "Member"))
                messages += ChatMessage(
                    id = giftRedemption.optString("id", "${giftRedemption.optString("timestampUsec")}:$author:redeem"),
                    author = author,
                    text = userMsg,
                    offset = time,
                    emoji = emojis,
                    authorPhotoUrl = photoUrl,
                    badges = badgeList,
                    badge = "MEMBER",
                    isMembership = true,
                    membershipHeader = "Menerima Keanggotaan Hadiah",
                    membershipSubtext = userMsg
                )
                return
            }

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
        return ChatBatch(messages, continuation, timeoutMs, lastOffset, currentInputConfig, currentActionPanel, pinnedMessage, unpinBanner, modeNotice)
    }
    private fun text(value: JSONObject?, emojis: MutableMap<String, String>? = null): String = parseJsonText(value, emojis)
}

/** State holder for YouTube live chat / replay chat */
internal class LiveChatState(val videoUrl: String, val live: Boolean, val postLive: Boolean, private val scope: CoroutineScope) {
    val videoId = run {
        val uri = android.net.Uri.parse(videoUrl)
        (if (uri.host == "youtu.be") uri.lastPathSegment else uri.getQueryParameter("v") ?: uri.lastPathSegment).orEmpty()
    }
    var hasChat by mutableStateOf(live || postLive)
    var topChat by mutableStateOf(true)
    var pinnedMessage by mutableStateOf<ChatMessage?>(null)
    var modeNotice by mutableStateOf<String?>(null)
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true)
    var position by mutableLongStateOf(0L)
    var retryTrigger by mutableIntStateOf(0)
    var inputConfig by mutableStateOf<ChatInputConfig?>(null)
    var actionPanel by mutableStateOf<LiveChatActionPanel?>(null)
    var sending by mutableStateOf(false)
    var sendError by mutableStateOf<String?>(null)
    var activeSession: ChatSession? = null
    private var subscriptionRefreshQueued = false

    val visibleMessages: List<ChatMessage>
        get() = if (live) messages.takeLast(150) else messages.filter { it.offset != null && it.offset <= position }.takeLast(150)

    val superChats: List<ChatMessage>
        get() = visibleMessages.filter { it.isSuperChat || !it.purchaseAmount.isNullOrBlank() }.takeLast(15)

    fun retry() {
        subscriptionRefreshQueued = false
        retryTrigger++
    }

    fun refreshAfterSubscribe() {
        if (actionPanel is LiveChatActionPanel.Restricted && !subscriptionRefreshQueued) {
            subscriptionRefreshQueued = true
            retryTrigger++
        }
    }

    internal fun clearQueuedSubscribeRefresh() {
        subscriptionRefreshQueued = false
    }

    fun switchFilter(useTopChat: Boolean) {
        if (topChat != useTopChat) {
            topChat = useTopChat
            retryTrigger++
        }
    }

    suspend fun sendMessage(text: String, revision: Int, headers: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        if (revision != YouTubeAuth.sessionRevision || !YouTubeAuth.isLoggedIn) return@withContext false
        val sess = activeSession ?: return@withContext false
        if (text.isBlank() || sending) return@withContext false
        withContext(Dispatchers.Main) {
            sending = true
            sendError = null
        }
        try {
            val emojis = inputConfig?.customEmojis ?: emptyList()
            val sentMsg = sess.sendMessage(text, emojis, revision, headers)
            if (revision != YouTubeAuth.sessionRevision) return@withContext false
            if (sentMsg != null) {
                withContext(Dispatchers.Main) {
                    messages = (messages + sentMsg).distinctBy { it.id }
                }
            }
            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                sendError = e.message ?: "Failed to send message"
            }
            false
        } finally {
            withContext(Dispatchers.Main) {
                sending = false
            }
        }
    }
}

@Composable
internal fun rememberLiveChat(videoUrl: String, live: Boolean, postLive: Boolean, player: ExoPlayer): LiveChatState {
    val scope = rememberCoroutineScope()
    val state = remember(videoUrl, live, postLive, YouTubeAuth.sessionRevision) { LiveChatState(videoUrl, live, postLive, scope) }

    LaunchedEffect(state, state.retryTrigger) {
        if (state.videoId.isBlank()) return@LaunchedEffect
        state.messages = emptyList()
        state.pinnedMessage = null
        state.modeNotice = null
        state.error = null
        state.inputConfig = null
        state.actionPanel = if (state.live && !YouTubeAuth.isLoggedIn) LiveChatActionPanel.SignInRequired() else null
        state.hasChat = state.live || state.postLive
        // Video biasa tidak mungkin punya live chat maupun replay chat; jangan buka watch page hanya untuk mencarinya.
        if (!state.live && !state.postLive) { state.loading = false; return@LaunchedEffect }
        state.loading = true

        fun applyBatchMeta(batch: ChatBatch) {
            val resolvedPanel = when {
                !state.live -> null
                !YouTubeAuth.isLoggedIn -> (batch.actionPanel as? LiveChatActionPanel.SignInRequired)
                    ?: LiveChatActionPanel.SignInRequired()
                batch.actionPanel != null -> batch.actionPanel
                batch.inputConfig != null -> LiveChatActionPanel.Input(batch.inputConfig)
                else -> null
            }
            if (resolvedPanel != null) {
                state.actionPanel = resolvedPanel
                state.inputConfig = (resolvedPanel as? LiveChatActionPanel.Input)?.config
            }
            if (batch.unpinBanner) state.pinnedMessage = null
            if (batch.pinnedMessage != null) state.pinnedMessage = batch.pinnedMessage
            if (batch.modeNotice != null) state.modeNotice = batch.modeNotice
        }

        try {
            var session = ChatSession(state.videoId, state.live, state.topChat)
            state.activeSession = session
            var batch = retryChatRead { withContext(Dispatchers.IO) { session.start() } }
            state.clearQueuedSubscribeRefresh()
            state.hasChat = true
            applyBatchMeta(batch)

            val initialPos = player.currentPosition.coerceAtLeast(0L)
            state.position = initialPos
            if (!state.live && initialPos > (batch.offset ?: 0L) + 15_000L) {
                retryChatRead { withContext(Dispatchers.IO) { session.seek(initialPos) } }?.let { seekBatch ->
                    batch = seekBatch
                }
            }

            var minOffset = batch.messages.mapNotNull { it.offset }.minOrNull() ?: 0L
            var maxOffset = batch.offset ?: batch.messages.mapNotNull { it.offset }.maxOrNull() ?: 0L
            var previousPosition = player.currentPosition.coerceAtLeast(0L)

            suspend fun seekReplayTo(targetPos: Long): ChatBatch {
                val sought = try {
                    retryChatRead { withContext(Dispatchers.IO) { session.seek(targetPos) } }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
                if (sought != null) return sought
                session = ChatSession(state.videoId, false, state.topChat)
                state.activeSession = session
                val started = retryChatRead { withContext(Dispatchers.IO) { session.start() } }
                return if (targetPos > 15_000L) {
                    try { retryChatRead { withContext(Dispatchers.IO) { session.seek(targetPos) } } ?: started }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { started }
                } else started
            }

            while (true) {
                applyBatchMeta(batch)
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
                batch = retryChatRead { withContext(Dispatchers.IO) { session.next() } } ?: break
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.error = e.message ?: "Failed to load chat"
            state.loading = false
            if (!state.live) state.hasChat = false
        }
    }

    LaunchedEffect(state, player) {
        while (true) {
            state.position = player.currentPosition.coerceAtLeast(0L)
            delay(100)
        }
    }

    return state
}

/**
 * Kartu "Live chat" di layar tonton:
 * - Saat live aktif (`isLive == true`), menampilkan judul "Live chat" dan di bawahnya bar input semu
 *   (Avatar pengguna di kiri + kapsul abu-abu "Chat publicly as <Nama>...") persis aplikasi YouTube.
 * - Saat replay (`isLive == false`), menampilkan cuplikan pesan atau ajakan memutar video.
 */
@Composable
private fun SyncChatOnSubscribe(state: LiveChatState, subscribed: Boolean?) {
    var previousSubscribed by remember(state) { mutableStateOf(subscribed) }
    LaunchedEffect(state, subscribed) {
        if (subscribed == true && previousSubscribed == false) {
            state.refreshAfterSubscribe()
        }
        previousSubscribed = subscribed
    }
}

@Composable
internal fun LiveChatTeaser(
    state: LiveChatState,
    isLive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pageIndicator: (@Composable () -> Unit)? = null,
    subscribed: Boolean? = null
) {
    SyncChatOnSubscribe(state, subscribed)
    val latest = state.visibleMessages.lastOrNull { it.text.isNotBlank() }
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).clickable(onClick = onClick)) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isLive) "Live chat" else "Replay chat",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.weight(1f))
                pageIndicator?.invoke()
            }
            Box(Modifier.padding(top = 10.dp)) {
                when {
                    isLive -> {
                        val panel = state.actionPanel
                        val rawName = state.inputConfig?.authorName?.takeIf { it.isNotBlank() }
                            ?: (if (YouTubeAuth.isLoggedIn) YouTubeAuth.profile.name.takeIf { it.isNotBlank() } else null)
                        val displayName = rawName?.removePrefix("@")?.substringBefore("-")?.trim()?.takeIf { it.isNotBlank() }
                            ?: rawName?.removePrefix("@")?.trim()
                        val avatarUrl = state.inputConfig?.authorPhotoUrl?.takeIf { it.isNotBlank() }
                            ?: (if (YouTubeAuth.isLoggedIn) YouTubeAuth.profile.avatarUrl?.takeIf { it.isNotBlank() } else null)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when {
                                !YouTubeAuth.isLoggedIn || panel is LiveChatActionPanel.SignInRequired -> {
                                    val signInLabel = (panel as? LiveChatActionPanel.SignInRequired)?.buttonText ?: "Sign in to chat"
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF383838)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(Color(0xFF383838))
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = signInLabel,
                                            color = TextSecondary,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                panel is LiveChatActionPanel.Restricted -> {
                                    val isTimer = panel.iconType?.let {
                                        it.contains("TIMER", true) || it.contains("CLOCK", true) || it.contains("SLOW", true)
                                    } == true
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF383838)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isTimer) Icons.Default.WatchLater else Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    val teaserMessage = if (panel.isSubscribersOnly && subscribed == false) {
                                        "Subscribers-only mode. Subscribe to chat"
                                    } else {
                                        panel.message
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(Color(0xFF383838))
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = teaserMessage,
                                            color = TextSecondary,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                else -> {
                                    if (avatarUrl != null) {
                                        Avatar(avatarUrl, displayName ?: "You", 24)
                                    } else if (!displayName.isNullOrBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF33691E)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = displayName.first().uppercaseChar().toString(),
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF383838)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    val promptText = when {
                                        panel == null && !state.loading -> "Chat input unavailable"
                                        !displayName.isNullOrBlank() -> "Chat publicly as $displayName..."
                                        else -> "Chat publicly..."
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(Color(0xFF383838))
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = promptText,
                                            color = TextSecondary,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                    latest != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(latest.authorPhotoUrl, latest.author, 24)
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = Muted)) { append(latest.author) }
                                append("  ")
                                append(latest.text)
                            },
                            fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                    state.loading -> Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF3A3A3A)))
                    else -> Text("Play the video to see the conversation.", color = Muted, fontSize = 13.sp)
                }
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
            if (msg.isSystemNotice) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xB3181818))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (!msg.actionLabel.isNullOrBlank()) "${msg.text} ${msg.actionLabel}" else msg.text,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = Color(0xFFCCCCCC)
                    )
                }
                return@items
            }
            val overlayBg = when {
                msg.isSuperChat -> Color(0xE600695C)
                msg.isMembership -> Color(0xE61E8E3E)
                else -> Color(0xB3181818)
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(overlayBg)
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
 * Channel Membership / Sponsorship card in live chat matching YouTube dark theme
 */
@Composable
private fun MembershipMessageCard(msg: ChatMessage) {
    val memberGreen = Color(0xFF1E8E3E) // YouTube Official Membership Green (#1E8E3E)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(memberGreen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
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
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF0F5132)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = msg.author.firstOrNull()?.uppercase() ?: "★",
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
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
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
                                text = "⭐",
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                if (!msg.membershipHeader.isNullOrBlank()) {
                    Text(
                        text = msg.membershipHeader,
                        color = Color.White.copy(alpha = 0.88f),
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        lineHeight = 15.sp
                    )
                }
                if (!msg.membershipSubtext.isNullOrBlank()) {
                    Text(
                        text = msg.membershipSubtext,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    )
                } else if (msg.membershipHeader.isNullOrBlank()) {
                    Text(
                        text = "Baru saja bergabung menjadi anggota!",
                        color = Color.White.copy(alpha = 0.92f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
        val isCustomMessage = msg.text.isNotBlank() &&
            msg.text != msg.membershipHeader &&
            msg.text != msg.membershipSubtext &&
            msg.text != "${msg.membershipHeader} • ${msg.membershipSubtext}"
        if (isCustomMessage) {
            val inline = remember(msg.id, msg.emoji) {
                val map = mutableMapOf<String, InlineTextContent>()
                msg.emoji.forEach { (token, url) ->
                    map[token] = InlineTextContent(Placeholder(18.sp, 18.sp, PlaceholderVerticalAlign.TextCenter)) {
                        AsyncImage(model = url, contentDescription = "Emoji", modifier = Modifier.fillMaxSize())
                    }
                }
                map
            }

            val annotated = buildAnnotatedString {
                val tokens = Regex("\\uFFF0\\d+\\uFFF1")
                var start = 0
                tokens.findAll(msg.text).forEach { match ->
                    append(msg.text.substring(start, match.range.first))
                    appendInlineContent(match.value, "emoji")
                    start = match.range.last + 1
                }
                append(msg.text.substring(start))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x2E000000))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = annotated,
                    inlineContent = inline,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * System notice item (e.g. Subscribers-only mode / Engagement notices) inside the chat stream.
 * Flows seamlessly with the stream and scrolls away as new messages arrive.
 */
@Composable
private fun SystemNoticeItem(msg: ChatMessage) {
    val context = LocalContext.current
    val noticeAnnotated = remember(msg.text, msg.actionLabel, msg.actionUrl) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color(0xFFDDDDDD))) {
                append(msg.text)
            }
            val label = msg.actionLabel
            val url = msg.actionUrl
            if (!label.isNullOrBlank()) {
                if (msg.text.isNotEmpty()) append(" ")
                if (!url.isNullOrBlank()) {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "notice-action",
                            styles = TextLinkStyles(SpanStyle(color = Color(0xFF3EA6FF)))
                        ) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    ) {
                        append(label)
                    }
                } else {
                    withStyle(SpanStyle(color = Color(0xFFAAAAAA))) {
                        append(label)
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF272727))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = noticeAnnotated,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

/**
 * Standard YouTube live chat item matching YouTube Desktop/Tablet Dark UI:
 * 24dp Avatar on the left, @Author Name + Inline Badges + White Message Text flowing seamlessly.
 */
@Composable
private fun LiveChatMessageItem(msg: ChatMessage, compact: Boolean = false) {
    if (msg.isSystemNotice) {
        SystemNoticeItem(msg)
        return
    }

    if (!compact && (msg.isSuperChat || !msg.purchaseAmount.isNullOrBlank())) {
        SuperChatMessageCard(msg)
        return
    }

    if (!compact && msg.isMembership) {
        MembershipMessageCard(msg)
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

/**
 * Pinned Message Banner berbentuk kapsul 1 baris persis YouTube Mobile:
 * [ (Avatar 24dp)  (@author ✓ dalam pill putih)  Teks/Link biru 1 baris...   ••• ]
 */
/**
 * Pinned Message Banner above Input Bar matching YouTube Mobile / Tablet:
 * Rounded card with 8dp corner radius, solid #212121 background,
 * 24dp Avatar on the left, Author name flowing inline with badges/emojis and text over up to 2 lines,
 * and 3-dots button with popup options (Copy, Open link, Hide).
 */
@Composable
private fun PinnedChatBannerCard(
    msg: ChatMessage,
    onDismiss: () -> Unit,
    onScrollToMessage: ((String) -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val urlRegex = remember { Regex("https?://\\S+") }
    val firstUrl = remember(msg.text) { urlRegex.find(msg.text)?.value }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF212121))
            .border(BorderStroke(1.dp, Color(0xFF333333)), RoundedCornerShape(8.dp))
            .clickable {
                if (onScrollToMessage != null) {
                    onScrollToMessage(msg.id)
                } else {
                    isExpanded = !isExpanded
                }
            }
            .padding(start = 10.dp, end = 2.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!msg.authorPhotoUrl.isNullOrBlank()) {
            AsyncImage(
                model = msg.authorPhotoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
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

        Spacer(Modifier.width(8.dp))

        val authorColor = when (msg.badge) {
            "OWNER" -> Color(0xFFFFD600)
            "MODERATOR" -> Color(0xFF5E84F1)
            "MEMBER" -> Color(0xFF2BA640)
            else -> Color(0xFF3EA6FF)
        }

        val pinnedAnnotated = remember(msg.author, msg.text, authorColor) {
            buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = authorColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                ) {
                    append(if (msg.author.startsWith("@")) msg.author else "@${msg.author}")
                }
                append(" ")

                var lastIdx = 0
                urlRegex.findAll(msg.text).forEach { match ->
                    if (match.range.first > lastIdx) {
                        withStyle(SpanStyle(color = Color.White, fontSize = 13.sp)) {
                            append(msg.text.substring(lastIdx, match.range.first))
                        }
                    }
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF3EA6FF),
                            fontSize = 13.sp,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(match.value)
                    }
                    lastIdx = match.range.last + 1
                }
                if (lastIdx < msg.text.length) {
                    withStyle(SpanStyle(color = Color.White, fontSize = 13.sp)) {
                        append(msg.text.substring(lastIdx))
                    }
                }
            }
        }

        Text(
            text = pinnedAnnotated,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = if (isExpanded) 10 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.width(2.dp))

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.yt_reel_more),
                    contentDescription = "Pinned message options",
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = 90f }
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier
                    .background(Color(0xFF252525))
                    .border(BorderStroke(1.dp, Color(0xFF383838)), RoundedCornerShape(12.dp))
            ) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.yt_w_copy),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    text = { Text("Salin pesan", color = Color.White, fontSize = 14.sp) },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(msg.text))
                        showMenu = false
                    }
                )
                if (firstUrl != null) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.yt_w_share),
                                contentDescription = null,
                                tint = Color(0xFF3EA6FF),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        text = { Text("Buka tautan", color = Color(0xFF3EA6FF), fontSize = 14.sp) },
                        onClick = {
                            showMenu = false
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(firstUrl))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        }
                    )
                }
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    text = { Text("Sembunyikan pesan sematan", color = Color(0xFFFF5252), fontSize = 14.sp) },
                    onClick = {
                        showMenu = false
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatTuneFilterIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        val y1 = h * 0.33f
        val y2 = h * 0.67f
        val r = 2.6.dp.toPx()
        val c1x = w * 0.35f
        val c2x = w * 0.65f

        // Top slider line & circle
        drawLine(Color.White, Offset(w * 0.14f, y1), Offset(c1x - r, y1), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(c1x + r, y1), Offset(w * 0.86f, y1), strokeWidth = stroke, cap = StrokeCap.Round)
        drawCircle(Color.White, radius = r, center = Offset(c1x, y1), style = Stroke(width = stroke))

        // Bottom slider line & circle
        drawLine(Color.White, Offset(w * 0.14f, y2), Offset(c2x - r, y2), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(c2x + r, y2), Offset(w * 0.86f, y2), strokeWidth = stroke, cap = StrokeCap.Round)
        drawCircle(Color.White, radius = r, center = Offset(c2x, y2), style = Stroke(width = stroke))
    }
}

@Composable
private fun ChatCrownIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.14f, h * 0.78f)
            lineTo(w * 0.86f, h * 0.78f)
            lineTo(w * 0.90f, h * 0.30f)
            lineTo(w * 0.68f, h * 0.52f)
            lineTo(w * 0.50f, h * 0.22f)
            lineTo(w * 0.32f, h * 0.52f)
            lineTo(w * 0.10f, h * 0.30f)
            close()
        }
        drawPath(path, Color.White)
    }
}

@Composable
private fun ChatEmojiOutlineIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        drawCircle(tint, radius = w * 0.42f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = stroke))
        drawCircle(tint, radius = w * 0.055f, center = Offset(w * 0.36f, h * 0.42f))
        drawCircle(tint, radius = w * 0.055f, center = Offset(w * 0.64f, h * 0.42f))
        drawArc(
            color = tint,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.30f, h * 0.34f),
            size = Size(w * 0.40f, h * 0.38f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun ChatGiftBoxIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Purple gift body matching YouTube Live Gift button
        drawRoundRect(
            color = Color(0xFF9C27B0),
            topLeft = Offset(w * 0.18f, h * 0.42f),
            size = Size(w * 0.64f, h * 0.44f),
            cornerRadius = CornerRadius(w * 0.08f)
        )
        drawRoundRect(
            color = Color(0xFFBA68C8),
            topLeft = Offset(w * 0.14f, h * 0.28f),
            size = Size(w * 0.72f, h * 0.16f),
            cornerRadius = CornerRadius(w * 0.06f)
        )
        // White ribbon
        drawRect(
            color = Color.White,
            topLeft = Offset(w * 0.45f, h * 0.28f),
            size = Size(w * 0.10f, h * 0.58f)
        )
        val bowStroke = 1.5.dp.toPx()
        drawCircle(Color.White, radius = w * 0.09f, center = Offset(w * 0.41f, h * 0.22f), style = Stroke(bowStroke))
        drawCircle(Color.White, radius = w * 0.09f, center = Offset(w * 0.59f, h * 0.22f), style = Stroke(bowStroke))
    }
}

@Composable
private fun ChatSuperChatDollarIcon(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val stroke = 1.6.dp.toPx()
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.12f, h * 0.22f),
                size = Size(w * 0.76f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.10f),
                style = Stroke(width = stroke)
            )
        }
        Text(
            text = "$",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private val STANDARD_CHAT_EMOJIS = listOf(
    "❤️", "🔥", "😂", "👏", "🎉", "👍", "😍",
    "🙌", "🤣", "😭", "🥺", "🥰", "😎", "🥳",
    "🤔", "😴", "🤯", "😱", "💀", "🤡", "🙃",
    "🙏", "💯", "🚀", "🏆", "🥇", "🎮", "🍿",
    "☕", "⚡", "🌟", "💡", "💎", "✨", "💥",
    "🍻", "👀", "🫡", "🤝", "🫶", "🤩", "🤤",
    "😇", "🤫", "🤐", "🤨", "😐", "😒", "🙄",
    "😬", "🤥", "😌", "😔", "😷", "🥶", "🥵",
    "👎", "👊", "✊", "🤞", "✌️", "🤟", "👌"
)

private data class SuperChatTier(
    val amountText: String,
    val durationText: String,
    val maxChars: Int,
    val headerColor: Color,
    val bodyColor: Color
)

private val SUPER_CHAT_TIERS = listOf(
    SuperChatTier("Rp 25.000 ($2)", "Pin 2 menit", 50, Color(0xFF00B8D4), Color(0xFF0097A7)),
    SuperChatTier("Rp 50.000 ($5)", "Pin 5 menit", 150, Color(0xFF00BFA5), Color(0xFF00897B)),
    SuperChatTier("Rp 100.000 ($10)", "Pin 10 menit", 200, Color(0xFFFFB300), Color(0xFFFF8F00)),
    SuperChatTier("Rp 250.000 ($20)", "Pin 30 menit", 225, Color(0xFFE65100), Color(0xFFBF360C)),
    SuperChatTier("Rp 500.000 ($50)", "Pin 1 jam", 250, Color(0xFFC2185B), Color(0xFF880E4F)),
    SuperChatTier("Rp 1.000.000 ($100+)", "Pin 2 jam", 270, Color(0xFFD00000), Color(0xFF9B0000))
)

@Composable
private fun LiveChatGiftDialog(
    videoId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E2E2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        ChatGiftBoxIcon(Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Beri Langganan Hadiah",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Gift memberships",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Beli langganan channel untuk dibagikan secara acak kepada penonton lain di live stream ini.",
                    color = Color(0xFFCCCCCC),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(16.dp))

                // Quantity chips in 2 rows (3 on top, 2 on bottom)
                Text(
                    text = "Pilih Jumlah Hadiah",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                var selectedQty by remember { mutableIntStateOf(5) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 5, 10).forEach { qty ->
                            val isSelected = selectedQty == qty
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFF9C27B0) else Color(0xFF2C2C2C))
                                    .clickable { selectedQty = qty }
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$qty",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(20, 50).forEach { qty ->
                            val isSelected = selectedQty == qty
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFF9C27B0) else Color(0xFF2C2C2C))
                                    .clickable { selectedQty = qty }
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$qty",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Info card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF282828),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎁",
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Pemberian hadiah $selectedQty membership diproses melalui sistem pembayaran resmi akun YouTube Anda.",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal", color = Color.LightGray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            runCatching {
                                val url = if (videoId.isNotBlank()) "https://www.youtube.com/watch?v=$videoId" else "https://www.youtube.com"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
                    ) {
                        Text("Beli Hadiah di YouTube", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveChatSuperChatDialog(
    videoId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTierIndex by remember { mutableIntStateOf(1) }
    val tier = SUPER_CHAT_TIERS.getOrElse(selectedTierIndex) { SUPER_CHAT_TIERS[0] }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E2E2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        ChatSuperChatDollarIcon(Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Super Chat & Super Stickers",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Sorot pesan Anda di live chat",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Tier selection horizontal scroll
                Text(
                    text = "Pilih Tingkat / Nilai",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SUPER_CHAT_TIERS.size) { index ->
                        val t = SUPER_CHAT_TIERS[index]
                        val isSelected = selectedTierIndex == index
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) t.headerColor else Color(0xFF2C2C2C),
                            border = if (isSelected) BorderStroke(1.5.dp, Color.White) else null,
                            modifier = Modifier.clickable { selectedTierIndex = index }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = t.amountText.substringBefore(" ("),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = t.durationText,
                                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color(0xFFAAAAAA),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Authentic Super Chat Preview Card
                Text(
                    text = "Pratinjau Tampilan Pesan",
                    color = Color(0xFFAAAAAA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    // Header of Super Chat card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(tier.headerColor)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val authorName = if (YouTubeAuth.isLoggedIn) YouTubeAuth.profile.name.takeIf { it.isNotBlank() } ?: "Anda" else "Nama Anda"
                        val avatarUrl = YouTubeAuth.profile.avatarUrl
                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp).clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(authorName.firstOrNull()?.uppercase() ?: "A", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(authorName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(tier.amountText, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            tier.durationText,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        )
                    }
                    // Body of Super Chat card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(tier.bodyColor)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Halo! Terima kasih atas live stream yang seru ini! 🔥 (Maks ${tier.maxChars} karakter)",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Info banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF282828),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💳", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Pengiriman Super Chat diproses resmi via Google Play / YouTube Billing.",
                            color = Color(0xFFAAAAAA),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal", color = Color.LightGray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            runCatching {
                                val url = if (videoId.isNotBlank()) "https://www.youtube.com/watch?v=$videoId" else "https://www.youtube.com"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = tier.headerColor)
                    ) {
                        Text("Kirim di YouTube", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Vertical skeleton shimmer loading state for Live Chat
 * Matching YouTube's official layout/live_chat_content VerticalShimmerLoadingFrameLayout.
 */
@Composable
private fun LiveChatShimmerLoading(modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val brush = if (reduceMotion) {
        SolidColor(Color(0xFF232323))
    } else {
        val transition = rememberInfiniteTransition(label = "chat_shimmer")
        val translateAnim by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_offset"
        )
        val shimmerColors = listOf(
            Color(0xFF1E1E1E),
            Color(0xFF2B2B2B),
            Color(0xFF1E1E1E)
        )
        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnim - 250f, translateAnim - 250f),
            end = Offset(translateAnim, translateAnim)
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(8) { index ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .width(if (index % 2 == 0) 100.dp else 75.dp)
                            .height(11.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(brush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 3 == 0) 0.85f else if (index % 3 == 1) 0.65f else 0.45f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(brush)
                    )
                }
            }
        }
    }
}

/**
 * Full Live Chat Panel matching YouTube Mobile Dark UI (#0F0F0F):
 * - Drag handle at top (when draggable in mobile bottom sheet)
 * - Card border & insets on Wide Screen / Tablet (matching layout engagement_panel_background_inset)
 * - Header with "Live chat" title, "Top messages  👤 1.8K" subtitle, "👑 0 XP" pill, Tune filter button & Close "✕" button
 * - Shimmer skeleton loading state (matching VerticalShimmerLoadingFrameLayout)
 * - Auto-scrolling message stream with "Live chat paused ▾" pill
 * - Subscribers-only / Mode notice card (when active)
 * - 1-line pill Pinned banner card at bottom of stream
 * - Pill-shaped "Chat..." input bar with avatar, monochrome Emoji outline icon, Gift icon & Super Chat [$] icon
 */
@Composable
private fun LiveChatRestrictedBar(
    restricted: LiveChatActionPanel.Restricted,
    loading: Boolean,
    subscribed: Boolean?,
    onSubscribe: (() -> Unit)?
) {
    val context = LocalContext.current
    val isTimerIcon = restricted.iconType?.let {
        it.contains("TIMER", true) || it.contains("CLOCK", true) || it.contains("SLOW", true) || it.contains("HOURGLASS", true)
    } == true

    val annotatedMessage = remember(restricted.message, restricted.learnMoreLabel, restricted.learnMoreUrl) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color(0xFFCCCCCC))) {
                append(restricted.message)
            }
            val label = restricted.learnMoreLabel
            val url = restricted.learnMoreUrl
            if (!label.isNullOrBlank()) {
                append("  •  ")
                if (!url.isNullOrBlank()) {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "restricted-learn-more",
                            styles = TextLinkStyles(SpanStyle(color = CallToActionBlue, fontWeight = FontWeight.Medium))
                        ) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    ) {
                        append(label)
                    }
                } else {
                    withStyle(SpanStyle(color = TextSecondary)) {
                        append(label)
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = TextSecondary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (isTimerIcon) Icons.Default.WatchLater else Icons.Default.Lock,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = annotatedMessage,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (!loading && restricted.isSubscribersOnly && subscribed == false && onSubscribe != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .semantics { role = Role.Button }
                    .clickable(onClick = onSubscribe),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Subscribe",
                        color = Color(0xFF0F0F0F),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
internal fun LiveChatPanel(
    state: LiveChatState,
    draggable: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    watchingCount: Long = 0L,
    subscribed: Boolean? = null,
    onSubscribe: (() -> Unit)? = null
) {
    SyncChatOnSubscribe(state, subscribed)
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

    var headerDragOffset by remember { mutableFloatStateOf(0f) }

    val containerModifier = if (draggable) {
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Color(0xFF0F0F0F))
            .imePadding()
    } else {
        // Wide screen / Tablet engagement panel (flush with container, solid #0F0F0F, no padding gaps)
        modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .border(BorderStroke(1.dp, Color(0xFF272727)), RoundedCornerShape(0.dp))
            .imePadding()
    }

    Column(
        modifier = containerModifier
    ) {
        // Header Area with Drag Handle + Title/Subtitle + XP Badge + Filter Tune + Close
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (draggable) {
                        Modifier.pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { headerDragOffset = 0f },
                                onVerticalDrag = { _, dragAmount -> headerDragOffset += dragAmount },
                                onDragEnd = {
                                    if (headerDragOffset > 60.dp.toPx()) onDismiss()
                                    headerDragOffset = 0f
                                }
                            )
                        }
                    } else Modifier
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (draggable) {
                Box(
                    Modifier
                        .padding(top = 8.dp, bottom = 2.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(DragHandleInk)
                )
            } else {
                Spacer(Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (state.live) "Live chat" else "Live chat replay",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        val subLabel = when {
                            !state.live -> if (state.topChat) "Top messages" else "All messages"
                            state.topChat -> "Top messages"
                            else -> "Live chat"
                        }
                        Text(
                            text = subLabel,
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp
                        )
                        if (state.live && watchingCount > 0L) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFFAAAAAA),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = formatCount(watchingCount),
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (state.live) {
                    Row(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF272727))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChatCrownIcon(Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "0 XP",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showFilterMenu = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        ChatTuneFilterIcon(Modifier.size(22.dp))
                    }
                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false },
                        modifier = Modifier.background(Color(0xFF282828))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Top messages", color = TextPrimary, fontSize = 14.sp, fontWeight = if (state.topChat) FontWeight.Bold else FontWeight.Normal)
                                    Text("Some messages, such as potential spam, may not be visible", color = TextSecondary, fontSize = 11.sp)
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
                                    Text(
                                        if (state.live) "Live chat" else "All chat replay",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = if (!state.topChat) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text("All messages are visible", color = TextSecondary, fontSize = 11.sp)
                                }
                            },
                            onClick = {
                                showFilterMenu = false
                                state.switchFilter(false)
                            }
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = if (state.live) "Close live chat" else "Close replay chat",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFF272727), thickness = 1.dp)

        when {
            state.error != null -> Column(Modifier.padding(16.dp)) {
                Text(state.error!!, color = Color(0xFFAAAAAA), fontSize = 13.sp)
                TextButton(onClick = { state.retry() }) { Text("Try again", color = Color(0xFF3EA6FF)) }
            }
            state.loading && visible.isEmpty() -> LiveChatShimmerLoading()
            visible.isEmpty() -> Text(
                if (state.live) "No messages yet." else "No messages at this position.",
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
                Surface(
                    onClick = {
                        autoScroll = true
                        scope.launch { listState.scrollToItem(0) }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .heightIn(min = 44.dp)
                        .shadow(8.dp, CircleShape),
                    color = SurfaceElevated,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Scroll to bottom",
                            tint = CallToActionBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (state.live) "Live chat paused" else "Replay chat paused",
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Pinned Message Banner above Input Bar (2-line card matching YouTube)
        val pinned = state.pinnedMessage
        if (pinned != null) {
            PinnedChatBannerCard(
                msg = pinned,
                onDismiss = { state.pinnedMessage = null },
                onScrollToMessage = { targetId ->
                    val targetIndex = visible.asReversed().indexOfFirst { it.id == targetId || it.author == pinned.author }
                    if (targetIndex >= 0) {
                        scope.launch { listState.animateScrollToItem(targetIndex) }
                    }
                }
            )
        }

        if (state.live) {
            var inputText by remember { mutableStateOf("") }
            var showEmojiPicker by remember { mutableStateOf(false) }
            var showLoginDialog by remember { mutableStateOf(false) }
            var showGiftDialog by remember { mutableStateOf(false) }
            var showSuperChatDialog by remember { mutableStateOf(false) }

            if (showLoginDialog) {
                YouTubeLoginDialog(onDismiss = {
                    showLoginDialog = false
                    state.retry()
                })
            }

            if (showGiftDialog) {
                LiveChatGiftDialog(
                    videoId = state.videoId,
                    onDismiss = { showGiftDialog = false }
                )
            }

            if (showSuperChatDialog) {
                LiveChatSuperChatDialog(
                    videoId = state.videoId,
                    onDismiss = { showSuperChatDialog = false }
                )
            }

            HorizontalDivider(color = Color(0xFF272727), thickness = 1.dp)

            val actionPanel = state.actionPanel
            val config = (actionPanel as? LiveChatActionPanel.Input)?.config ?: state.inputConfig
            val canComposeInput = YouTubeAuth.isLoggedIn && config != null && actionPanel !is LiveChatActionPanel.Restricted && actionPanel !is LiveChatActionPanel.SignInRequired

            LaunchedEffect(canComposeInput) {
                if (!canComposeInput) {
                    showEmojiPicker = false
                    showGiftDialog = false
                    showSuperChatDialog = false
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F0F))
            ) {
                if (state.sendError != null) {
                    Text(
                        text = state.sendError!!,
                        color = ErrorText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                if (showEmojiPicker && canComposeInput) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 240.dp)
                            .background(Color(0xFF181818))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Emoji & Reactions",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (inputText.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            if (inputText.isNotEmpty()) {
                                                inputText = inputText.dropLast(1)
                                            }
                                        },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Backspace",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showEmojiPicker = false },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close emoji picker", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (config.customEmojis.isNotEmpty()) {
                            Text(
                                "Channel emoji (${config.customEmojis.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MembershipGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(config.customEmojis) { em ->
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Surface)
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
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Text(
                            "Popular emojis",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        val emojiRows = remember { STANDARD_CHAT_EMOJIS.chunked(7) }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(emojiRows) { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    row.forEach { emoji ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { inputText += emoji },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = emoji, fontSize = 20.sp)
                                        }
                                    }
                                    repeat(7 - row.size) {
                                        Spacer(Modifier.weight(1f).height(44.dp))
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Surface, thickness = 0.5.dp)
                }

                when {
                    !YouTubeAuth.isLoggedIn || actionPanel is LiveChatActionPanel.SignInRequired -> {
                        val signInPanel = (actionPanel as? LiveChatActionPanel.SignInRequired)
                            ?: LiveChatActionPanel.SignInRequired()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .semantics { role = Role.Button }
                                    .clickable { showLoginDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color(0xFF272727)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = signInPanel.buttonText,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (!signInPanel.subtext.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = signInPanel.subtext,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    !canComposeInput -> {
                        val restrictedPanel = (actionPanel as? LiveChatActionPanel.Restricted)
                            ?: LiveChatActionPanel.Restricted(
                                message = if (state.loading) "Checking chat availability..." else "Chat input unavailable"
                            )
                        LiveChatRestrictedBar(
                            restricted = restrictedPanel,
                            loading = state.loading && actionPanel == null,
                            subscribed = subscribed,
                            onSubscribe = onSubscribe
                        )
                    }

                    else -> {
                        val activeConfig = config!!
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Surface)
                                    .padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val userPhoto = YouTubeAuth.profile.avatarUrl ?: activeConfig.authorPhotoUrl
                                if (!userPhoto.isNullOrBlank()) {
                                    AsyncImage(
                                        model = userPhoto,
                                        contentDescription = "Your avatar",
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF383838)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF383838)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))

                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (inputText.isEmpty()) {
                                        Text(
                                            text = "Chat...",
                                            color = TextSubMuted,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = inputText,
                                        onValueChange = {
                                            if (it.length <= 250) inputText = it
                                        },
                                        enabled = !state.sending,
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = TextPrimary,
                                            fontSize = 14.sp
                                        ),
                                        cursorBrush = SolidColor(TextPrimary),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (inputText.length >= 150) {
                                    val remaining = 200 - inputText.length
                                    Text(
                                        text = "$remaining",
                                        color = if (remaining < 0) ErrorText else TextSecondary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { showEmojiPicker = !showEmojiPicker },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    ChatEmojiOutlineIcon(Modifier.size(20.dp))
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            val canSend = inputText.isNotBlank() && inputText.length <= 200 && !state.sending
                            if (canSend || state.sending) {
                                IconButton(
                                    onClick = {
                                        val textToSend = inputText
                                        val revision = YouTubeAuth.sessionRevision
                                        val headers = YouTubeAuth.buildAuthHeaders()
                                        scope.launch {
                                            val success = state.sendMessage(textToSend, revision, headers)
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
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(CallToActionBlue)
                                ) {
                                    if (state.sending) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Background,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send",
                                            tint = Background,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Surface)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = "Send gift"
                                        }
                                        .clickable { showGiftDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    ChatGiftBoxIcon(Modifier.size(22.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Surface)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = "Send Super Chat"
                                        }
                                        .clickable { showSuperChatDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    ChatSuperChatDollarIcon(Modifier.size(22.dp))
                                }
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
    postLive: Boolean = false,
    player: ExoPlayer,
    draggable: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberLiveChat(videoUrl, live, postLive, player)
    LiveChatPanel(state, draggable, onDismiss, modifier)
}
