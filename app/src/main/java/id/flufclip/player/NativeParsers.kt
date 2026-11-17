package id.flufclip.player

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class NativeChatBatchDecoded(
    val batch: ChatBatch,
    val seekContinuation: String?,
    val chosenReloadToken: String?,
)

internal data class NativeLiveChatBootstrap(
    val found: Boolean,
    val apiKey: String?,
    val context: JSONObject?,
    val watchContinuation: String?,
    val batch: NativeChatBatchDecoded?,
)

internal object NativeParsers {
    val isAvailable: Boolean get() = NativeDownloader.isAvailable

    @JvmStatic external fun nativeExtractYtInitialData(html: String): String?

    @JvmStatic
    external fun nativeParseLiveChatHtmlBootstrap(
        html: String,
        isLive: Boolean,
        topChat: Boolean,
        fallbackAuthor: String,
        fallbackPhoto: String?,
    ): ByteArray

    @JvmStatic
    external fun nativeParseLiveChatResponse(
        json: String,
        topChat: Boolean,
        fallbackAuthor: String,
        fallbackPhoto: String?,
    ): ByteArray

    fun extractYtInitialData(html: String): String? =
        if (!isAvailable) null else runCatching { nativeExtractYtInitialData(html) }.getOrNull()

    fun parseLiveChatHtmlBootstrap(
        html: String,
        isLive: Boolean,
        topChat: Boolean,
        fallbackAuthor: String,
        fallbackPhoto: String?,
    ): NativeLiveChatBootstrap? {
        if (!isAvailable) return null
        val bytes = nativeParseLiveChatHtmlBootstrap(html, isLive, topChat, fallbackAuthor, fallbackPhoto)
        return decodeBootstrap(bytes)
    }

    fun parseLiveChatResponse(
        json: String,
        topChat: Boolean,
        fallbackAuthor: String,
        fallbackPhoto: String?,
    ): NativeChatBatchDecoded? {
        if (!isAvailable) return null
        val bytes = nativeParseLiveChatResponse(json, topChat, fallbackAuthor, fallbackPhoto)
        return decodeBatch(ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN))
    }

    internal fun decodeBootstrap(bytes: ByteArray): NativeLiveChatBootstrap {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val found = buf.get().toInt() != 0
        val apiKey = readOptStr(buf)
        val contextJson = readOptStr(buf)
        val context = contextJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val watchContinuation = readOptStr(buf)
        val hasBatch = buf.get().toInt() != 0
        val batch = if (hasBatch) decodeBatch(buf) else null
        return NativeLiveChatBootstrap(found, apiKey, context, watchContinuation, batch)
    }

    internal fun decodeBatch(buf: ByteBuffer): NativeChatBatchDecoded {
        val continuation = readOptStr(buf)
        val seekContinuation = readOptStr(buf)
        val chosenReloadToken = readOptStr(buf)
        val timeoutMs = buf.getLong()
        val hasLastOffset = buf.get().toInt() != 0
        val lastOffset = if (hasLastOffset) buf.getLong() else null
        val unpinBanner = buf.get().toInt() != 0
        val modeNotice = readOptStr(buf)

        val panelKind = buf.get().toInt() and 0xFF
        val actionPanel: LiveChatActionPanel? = when (panelKind) {
            1 -> {
                val sendParams = readStr(buf)
                val authorName = readStr(buf)
                val authorPhotoUrl = readOptStr(buf)
                val badgeCount = buf.getInt()
                val badges = List(badgeCount) { readBadge(buf) }
                val emojiCount = buf.getInt()
                val emojis = List(emojiCount) {
                    CustomChatEmoji(
                        emojiId = readStr(buf),
                        shortcut = readStr(buf),
                        imageUrl = readStr(buf),
                        isCustomEmoji = buf.get().toInt() != 0,
                    )
                }
                LiveChatActionPanel.Input(
                    ChatInputConfig(
                        sendParams = sendParams,
                        authorName = authorName,
                        authorPhotoUrl = authorPhotoUrl,
                        badges = badges,
                        customEmojis = emojis,
                    )
                )
            }
            2 -> LiveChatActionPanel.SignInRequired(
                buttonText = readStr(buf),
                subtext = readOptStr(buf),
            )
            3 -> LiveChatActionPanel.Restricted(
                message = readStr(buf),
                iconType = readOptStr(buf),
                learnMoreLabel = readOptStr(buf),
                learnMoreUrl = readOptStr(buf),
                isSubscribersOnly = buf.get().toInt() != 0,
            )
            else -> null
        }

        val hasPinned = buf.get().toInt() != 0
        val pinnedMessage = if (hasPinned) readMessage(buf) else null

        val msgCount = buf.getInt()
        val messages = List(msgCount) { readMessage(buf) }
        val inputConfig = (actionPanel as? LiveChatActionPanel.Input)?.config

        return NativeChatBatchDecoded(
            batch = ChatBatch(
                messages = messages,
                continuation = continuation,
                timeout = timeoutMs,
                offset = lastOffset,
                inputConfig = inputConfig,
                actionPanel = actionPanel,
                pinnedMessage = pinnedMessage,
                unpinBanner = unpinBanner,
                modeNotice = modeNotice,
            ),
            seekContinuation = seekContinuation,
            chosenReloadToken = chosenReloadToken,
        )
    }

    private fun readStr(buf: ByteBuffer): String {
        val len = buf.getInt()
        if (len <= 0) return ""
        val bytes = ByteArray(len)
        buf.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readOptStr(buf: ByteBuffer): String? =
        if (buf.get().toInt() == 0) null else readStr(buf)

    private fun readBadge(buf: ByteBuffer): ChatBadge = ChatBadge(
        type = readStr(buf),
        imageUrl = readOptStr(buf),
        tooltip = readOptStr(buf),
    )

    private fun readMessage(buf: ByteBuffer): ChatMessage {
        val id = readStr(buf)
        val author = readStr(buf)
        val text = readStr(buf)
        val hasOffset = buf.get().toInt() != 0
        val offset = if (hasOffset) buf.getLong() else null
        val emojiCount = buf.getInt()
        val emojis = if (emojiCount == 0) emptyMap() else LinkedHashMap<String, String>(emojiCount).apply {
            repeat(emojiCount) {
                put(readStr(buf), readStr(buf))
            }
        }
        val authorPhotoUrl = readOptStr(buf)
        val badgeCount = buf.getInt()
        val badges = if (badgeCount == 0) emptyList() else List(badgeCount) { readBadge(buf) }
        val badge = readOptStr(buf)
        val purchaseAmount = readOptStr(buf)
        val flags = buf.get().toInt() and 0xFF
        val membershipSubtext = readOptStr(buf)
        val membershipHeader = readOptStr(buf)
        val actionLabel = readOptStr(buf)
        val actionUrl = readOptStr(buf)
        return ChatMessage(
            id = id,
            author = author,
            text = text,
            offset = offset,
            emoji = emojis,
            authorPhotoUrl = authorPhotoUrl,
            badges = badges,
            badge = badge,
            purchaseAmount = purchaseAmount,
            isSuperChat = (flags and 1) != 0,
            isMembership = (flags and 2) != 0,
            isSystemNotice = (flags and 4) != 0,
            membershipSubtext = membershipSubtext,
            membershipHeader = membershipHeader,
            actionLabel = actionLabel,
            actionUrl = actionUrl,
        )
    }
}
