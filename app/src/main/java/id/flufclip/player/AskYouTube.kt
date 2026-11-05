package id.flufclip.player

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/*
 * "Ask" (YouTube AI, panel `PAyouchat`) untuk layar tonton. Bentuk data dan permintaan di sini berasal dari bukti di
 * docs/evidence/ask-ai-mode-2026-11-02/ (klien WEB, satu pertanyaan sungguhan pada 2026-11-02) dan dari kode web:
 *  - tombol + token muncul di respons `next` hanya bila server mengaktifkan Ask untuk akun/sesi itu;
 *  - pertanyaan = POST /youtubei/v1/get_panel {context, continuation=token, formData.inputComposerFormData{...}};
 *  - jawaban ada di `onResponseReceivedCommand.listMutationCommand.operations...insertItemSectionContent.contents`.
 * Belum diuji di perangkat: pertanyaan lanjutan dan tombol saran (bentuknya mengikuti fungsi kirim `chat input` di kode web).
 */

/** Ask tersedia untuk video ini. [token] milik `sendUserQueryCommand`; [route] hampir selalu `/youtubei/v1/get_panel`. */
internal data class AskPanel(
    val token: String,
    val route: String = AskParser.DEFAULT_ROUTE,
    val placeholder: String = "Ask a question...",
    val maxCharacters: Int = 500,
    val greeting: String? = null,
)

/** Tombol pertanyaan lanjutan dari server. [id] dikirim kembali sebagai `pendingSuggestedQueryIdentifier`. */
internal data class AskChip(val id: String?, val text: String)

internal data class AskAnswer(val text: String, val disclaimer: String?, val chips: List<AskChip>)

internal sealed interface AskResult {
    data class Ok(val answer: AskAnswer) : AskResult
    data class Failed(val message: String) : AskResult
}

/**
 * Potongan teks jawaban setelah tanda `*miring*` dan `**tebal**` dari server diurai. [seconds] terisi untuk penanda waktu
 * (`0:45`, `1:02:03`): di app YouTube asli penanda itu biru dan melompat ke posisi tersebut saat diketuk.
 */
internal data class AskSpan(val text: String, val bold: Boolean = false, val italic: Boolean = false, val seconds: Int? = null)

/** Satu baris jawaban. [marker] (`•` atau `1.`) null untuk baris biasa; baris tanpa marker dan tanpa isi adalah jarak antar paragraf. */
internal data class AskLine(val marker: String?, val spans: List<AskSpan>, val level: Int = 0) {
    val blank: Boolean get() = marker == null && spans.isEmpty()
}

private fun Any?.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject
private fun Any?.objects(key: String): List<JsonObject> = ((this as? JsonObject)?.get(key) as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
private fun Any?.text(key: String): String? = ((this as? JsonObject)?.get(key) as? String)?.takeIf { it.isNotBlank() }
private fun Any?.int(key: String): Int? = ((this as? JsonObject)?.get(key) as? Number)?.toInt()

private fun findFirst(node: Any?, key: String): Any? {
    when (node) {
        is JsonObject -> {
            node[key]?.let { return it }
            for (value in node.values) findFirst(value, key)?.let { return it }
        }
        is JsonArray -> for (value in node) findFirst(value, key)?.let { return it }
    }
    return null
}

/** Semua nilai bernama [key] di pohon; urutan JsonObject (HashMap) tidak terjamin, jadi pemanggil memilih menurut isi, bukan urutan. */
private fun findAll(node: Any?, key: String, out: MutableList<Any?> = mutableListOf()): List<Any?> {
    when (node) {
        is JsonObject -> for ((k, value) in node) { if (k == key) out += value; findAll(value, key, out) }
        is JsonArray -> for (value in node) findAll(value, key, out)
    }
    return out
}

internal object AskParser {
    const val DEFAULT_ROUTE = "/youtubei/v1/get_panel"
    private const val GET_PANEL = "CONTINUATION_REQUEST_TYPE_GET_PANEL"

    fun parseObject(json: String): JsonObject? = try {
        JsonParser.`object`().from(json)
    } catch (_: Exception) {
        null
    }

    /**
     * Panel Ask dari respons `next`. Null bila server tidak mengirimnya (akun/video tanpa Ask), atau bentuknya belum
     * didukung (misalnya route streaming) -- tombol Ask tidak boleh tampil dalam dua kasus itu.
     */
    fun panelFromNext(root: JsonObject): AskPanel? {
        val command = (findFirst(root, "sendUserQueryCommand") as? JsonObject)?.obj("innertubeCommand") ?: return null
        val continuation = command.obj("continuationCommand") ?: return null
        val token = continuation.text("token") ?: return null
        if (continuation.text("request") != GET_PANEL) return null
        val api = command.obj("commandMetadata").obj("webCommandMetadata").text("apiUrl")
        if (api != null && api != DEFAULT_ROUTE) return null
        val content = findAll(root, "inputComposerViewModel")
            .map { it.obj("inputFormField").obj("textFieldViewModel").obj("contentProperties") }
            .firstOrNull { it.text("placeholderText") != null }
        // Sapaan = item chat pertama yang punya teks dan bukan baris tombol saran.
        val greeting = findAll(root, "youChatItemViewModel").firstNotNullOfOrNull { item ->
            if (item.obj("chipsData") != null) null else item.obj("text").text("content")
        }
        return AskPanel(
            token = token,
            placeholder = content.text("placeholderText") ?: "Ask a question...",
            maxCharacters = content.int("maxCharacterCount")?.takeIf { it > 0 } ?: 500,
            greeting = greeting,
        )
    }

    /** Jawaban dari respons `get_panel`; null bila tidak ada teks jawaban. */
    fun answerFromPanel(root: JsonObject): AskAnswer? {
        val operations = root.obj("onResponseReceivedCommand").obj("listMutationCommand").obj("operations").objects("operations")
        var text: String? = null
        var disclaimer: String? = null
        val chips = mutableListOf<AskChip>()
        for (operation in operations) {
            for (item in operation.obj("insertItemSectionContent").objects("contents")) {
                val chat = item.obj("youChatItemViewModel")
                if (chat != null) {
                    val chipData = chat.obj("chipsData").objects("chipData")
                    if (chipData.isNotEmpty()) {
                        chipData.forEach { chip -> chip.obj("text").text("content")?.let { chips += AskChip(chip.text("id"), it) } }
                    } else if (text == null) {
                        text = chat.obj("text").text("content")
                    }
                }
                item.obj("chatItemViewModel").obj("content").obj("disclaimerItemViewModel").obj("linkfiedText").text("content")?.let { disclaimer = it }
            }
        }
        return text?.let { AskAnswer(it, disclaimer, chips) }
    }

    fun newClientMessageId(nowMs: Long): String = "youchat-$nowMs"

    /**
     * Field `continuation` dan `formData` untuk `get_panel`; pemanggil menambahkan `context`. [previousClientMessageId] adalah
     * `clientMessageId` giliran sebelumnya (nilai `stringEntity` yang dikirim server). Offset pemutaran dibulatkan ke detik
     * seperti klien web.
     */
    fun requestFields(
        token: String,
        question: String,
        clientMessageId: String,
        positionMs: Long,
        previousClientMessageId: String?,
        suggestionId: String?,
    ): Map<String, Any> {
        val form = linkedMapOf<String, Any>(
            "clientMessageId" to clientMessageId,
            "playerOffsetMs" to ((positionMs.coerceAtLeast(0L) / 1000L) * 1000L).toString(),
            "userInputText" to question,
        )
        previousClientMessageId?.takeIf { it.isNotBlank() }?.let { form["previousClientMessageId"] = it }
        suggestionId?.takeIf { it.isNotBlank() }?.let { form["pendingSuggestedQueryIdentifier"] = it }
        return linkedMapOf("continuation" to token, "formData" to mapOf("inputComposerFormData" to form))
    }

    private val timestamp = Regex("(?<![\\d:])(\\d{1,2}):([0-5]\\d)(?::([0-5]\\d))?(?![\\d:])")

    /** `m:ss` atau `h:mm:ss` menjadi detik; null bila bukan penanda waktu. */
    fun timestampSeconds(text: String): Int? {
        val match = timestamp.matchEntire(text) ?: return null
        val (first, second, third) = match.destructured
        return if (third.isEmpty()) first.toInt() * 60 + second.toInt() else first.toInt() * 3600 + second.toInt() * 60 + third.toInt()
    }

    /**
     * Teks jawaban sebagai potongan bergaya. Penanda waktu dipisah per ujung (rentang `0:00-0:45` menjadi dua tautan; tanda
     * hubungnya tetap teks biasa) dan tetap mewarisi tebal/miring dari potongan asalnya.
     */
    fun spans(raw: String): List<AskSpan> = markdownSpans(raw).flatMap { span ->
        val out = mutableListOf<AskSpan>()
        var last = 0
        for (match in timestamp.findAll(span.text)) {
            if (match.range.first > last) out += span.copy(text = span.text.substring(last, match.range.first))
            out += span.copy(text = match.value, seconds = timestampSeconds(match.value))
            last = match.range.last + 1
        }
        if (last < span.text.length) out += span.copy(text = span.text.substring(last))
        out
    }

    private val bulletLine = Regex("^( *)([*\\-•])\\s+(.*)$")
    private val numberedLine = Regex("^( *)(\\d{1,2})[.)]\\s+(.*)$")

    /**
     * Jawaban per baris, seperti app YouTube asli: butir (`* `, `- `, `• `) dan daftar bernomor (`1. `) memakai penanda terpisah
     * supaya baris lanjutannya menjorok (indentasi gantung); baris kosong berurutan menjadi satu jarak sebesar satu baris, tanpa
     * jarak di awal/akhir. Baris biasa berikutnya tanpa baris kosong menempel tanpa celah.
     */
    fun lines(raw: String): List<AskLine> {
        val out = mutableListOf<AskLine>()
        for (line in raw.lines()) {
            val text = line.trimEnd()
            if (text.isBlank()) {
                if (out.isNotEmpty() && !out.last().blank) out += AskLine(null, emptyList())
                continue
            }
            val bullet = bulletLine.matchEntire(text)
            val numbered = if (bullet == null) numberedLine.matchEntire(text) else null
            out += when {
                bullet != null -> AskLine("•", spans(bullet.groupValues[3]), level = (bullet.groupValues[1].length / 2).coerceAtMost(2))
                numbered != null -> AskLine(numbered.groupValues[2] + ".", spans(numbered.groupValues[3]), level = (numbered.groupValues[1].length / 2).coerceAtMost(2))
                else -> AskLine(null, spans(text.trimStart()))
            }
        }
        while (out.isNotEmpty() && out.last().blank) out.removeAt(out.lastIndex)
        return out
    }

    /** `**tebal**` dan `*miring*` dari satu baris teks jawaban; tanda yang tidak berpasangan dibiarkan. */
    private fun markdownSpans(raw: String): List<AskSpan> {
        val text = raw
        val out = mutableListOf<AskSpan>()
        val plain = StringBuilder()
        fun flush() { if (plain.isNotEmpty()) { out += AskSpan(plain.toString()); plain.clear() } }
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) { flush(); out += AskSpan(text.substring(i + 2, end), bold = true); i = end + 2; continue }
            }
            if (text[i] == '*' && i + 1 < text.length && !text[i + 1].isWhitespace()) {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1 && !text[end - 1].isWhitespace()) { flush(); out += AskSpan(text.substring(i + 1, end), italic = true); i = end + 1; continue }
            }
            plain.append(text[i])
            i++
        }
        flush()
        return out
    }

    fun errorMessage(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            Regex(":\\s*(401|403)\\b").containsMatchIn(message) -> "Sign in again to keep using Ask."
            Regex(":\\s*429\\b").containsMatchIn(message) -> "Too many questions right now. Try again in a moment."
            error is IOException -> "No connection. Check your network and try again."
            else -> "Ask couldn't answer. Try again."
        }
    }
}

internal object AskYouTube {
    /** Panel Ask untuk video [videoUrl]; null bila belum login atau server tidak mengirim Ask. Memakai ulang respons `next` yang sudah ada. */
    suspend fun panel(videoUrl: String, refresh: Boolean = false): AskPanel? = withContext(Dispatchers.IO) {
        if (!YouTubeAuth.isLoggedIn) return@withContext null
        val id = InnerTubePlayer.videoIdOf(videoUrl) ?: return@withContext null
        val json = try { InnerTubePlayer.nextRaw(id, refresh) } catch (_: Exception) { return@withContext null }
        AskParser.parseObject(json)?.let { AskParser.panelFromNext(it) }
    }

    suspend fun ask(
        panel: AskPanel,
        question: String,
        positionMs: Long,
        clientMessageId: String,
        previousClientMessageId: String?,
        suggestionId: String?,
    ): AskResult = withContext(Dispatchers.IO) {
        try {
            val fields = AskParser.requestFields(panel.token, question, clientMessageId, positionMs, previousClientMessageId, suggestionId)
            val answer = AskParser.parseObject(InnerTubePlayer.postRaw(panel.route, fields))?.let { AskParser.answerFromPanel(it) }
            if (answer != null) AskResult.Ok(answer) else AskResult.Failed("Ask didn't return an answer. Try rephrasing your question.")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AskResult.Failed(AskParser.errorMessage(e))
        }
    }
}
