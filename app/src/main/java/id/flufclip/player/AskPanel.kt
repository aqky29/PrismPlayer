package id.flufclip.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Warna dan ukuran diukur dari screenshot panel "Ask about this video" di app YouTube asli (tablet, 360 dpi = 2,25 px/dp).
private val AskPill = Color(0xFF272727)
private val AskUserBubble = Color(0xFFF1F1F1)
private val AskUserText = Color(0xFF0F0F0F)
private val AskOutline = Color(0xFF444444)
private val AskSendIdle = Color(0xFF7A7A7A)
private val AskError = Color(0xFFFF8A80)
private val AskBody = TextStyle(color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
private val AskLineGap = 20.dp

/** Path ikon `SPARK` (sama dengan yt_w_spark.xml); dipakai untuk bintang Gemini berwarna pada chip di bawah kotak input. */
private const val SPARK_PATH = "M12,2c0.39,0 0.71,0.303 0.763,0.69a10.001,10.001 0,0 0,8.547 8.533c0.386,0.052 0.69,0.372 0.69,0.762s-0.304,0.71 -0.69,0.763a10,10 0,0 0,-8.551 8.565A0.78,0.78 0,0 1,12 22a0.78,0.78 0,0 1,-0.759 -0.688,10.002 10.002 0,0 0,-8.552 -8.565 0.784,0.784 0,0 1,-0.689 -0.763c0,-0.39 0.303,-0.709 0.69,-0.761a10.002,10.002 0,0 0,8.547 -8.534A0.784,0.784 0,0 1,12 2Z"

internal data class AskTurn(
    val question: String,
    val suggestionId: String? = null,
    val answer: AskAnswer? = null,
    val error: String? = null,
)

/**
 * Satu percakapan Ask untuk satu video; dibuat ulang bila video berganti. [position] dibaca di thread utama saat pertanyaan
 * dikirim. Pertanyaan lanjutan membawa `clientMessageId` giliran yang berhasil sebelumnya sebagai `previousClientMessageId`.
 */
internal class AskSession(
    private val videoUrl: String,
    initial: AskPanel,
    private val scope: CoroutineScope,
    private val position: () -> Long,
) {
    var panel by mutableStateOf(initial)
        private set
    val turns = mutableStateListOf<AskTurn>()
    var busy by mutableStateOf(false)
        private set
    private var lastClientMessageId: String? = null

    fun send(question: String, suggestionId: String? = null) {
        val text = question.trim().take(panel.maxCharacters)
        if (text.isEmpty() || busy) return
        turns += AskTurn(text, suggestionId)
        busy = true
        val index = turns.lastIndex
        scope.launch { deliver(index) }
    }

    /** Kirim ulang giliran terakhir yang gagal; token panel diambil ulang dari `next` terbaru lebih dulu. */
    fun retry() {
        val index = turns.lastIndex
        if (index < 0 || busy || turns[index].error == null) return
        busy = true
        turns[index] = turns[index].copy(error = null)
        scope.launch {
            AskYouTube.panel(videoUrl, refresh = true)?.let { panel = it }
            deliver(index)
        }
    }

    private suspend fun deliver(index: Int) {
        val turn = turns[index]
        val clientMessageId = AskParser.newClientMessageId(System.currentTimeMillis())
        when (val result = AskYouTube.ask(panel, turn.question, position(), clientMessageId, lastClientMessageId, turn.suggestionId)) {
            is AskResult.Ok -> {
                lastClientMessageId = clientMessageId
                turns[index] = turn.copy(answer = result.answer, error = null)
            }
            is AskResult.Failed -> turns[index] = turn.copy(error = result.message)
        }
        busy = false
    }
}

/** Penanda waktu menjadi tautan biru (seperti app YouTube asli) yang memanggil [onSeek] dengan detiknya. */
private fun askAnnotated(spans: List<AskSpan>, onSeek: (Int) -> Unit): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        val style = SpanStyle(fontWeight = if (span.bold) FontWeight.Bold else null, fontStyle = if (span.italic) FontStyle.Italic else null)
        val seconds = span.seconds
        if (seconds != null) {
            val link = LinkAnnotation.Clickable("ask-time-$seconds", TextLinkStyles(style.merge(SpanStyle(color = LinkBlue)))) { onSeek(seconds) }
            withLink(link) { append(span.text) }
        } else {
            withStyle(style) { append(span.text) }
        }
    }
}

private fun copyAnswer(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Ask answer", text))
    Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
}

@Composable
internal fun AskPanel(session: AskSession, draggable: Boolean, onDismiss: () -> Unit, onSeek: (Int) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    // Item 0 = sapaan; giliran ke-n berada di indeks n + 1. Gulir ke awal giliran terbaru supaya pertanyaannya tetap terlihat.
    LaunchedEffect(session.turns.size) { if (session.turns.isNotEmpty()) listState.animateScrollToItem(session.turns.size) }
    LaunchedEffect(session.turns.lastOrNull()?.answer) { if (session.turns.lastOrNull()?.answer != null) listState.animateScrollToItem(session.turns.size) }
    val submit = {
        val text = input
        if (text.isNotBlank() && !session.busy) { session.send(text); input = "" }
    }
    EngagementPanel("Ask about this video", null, draggable, onDismiss, modifier.imePadding(), titleWeight = FontWeight.Medium) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "greeting") { AskSparkText(session.panel.greeting ?: "Hello! Curious about what you're watching? I'm here to help.", onSeek) }
            itemsIndexed(session.turns, key = { index, _ -> index }) { index, turn ->
                AskTurnView(
                    turn = turn,
                    latest = index == session.turns.lastIndex,
                    busy = session.busy,
                    onChip = { session.send(it.text, it.id) },
                    onRetry = session::retry,
                    onCopy = { copyAnswer(context, it) },
                    onSeek = onSeek,
                )
            }
        }
        AskComposer(session.panel, input, !session.busy, { input = it }, submit)
    }
}

/** Bintang SPARK di atas teks (bukan di sampingnya), seperti jawaban di app YouTube asli. */
@Composable
private fun AskSparkText(text: String, onSeek: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.yt_w_spark), null, Modifier.size(16.dp))
        AskLines(text, onSeek)
    }
}

/** Teks jawaban per baris: butir/nomor berindentasi gantung, baris kosong = jarak satu baris. */
@Composable
private fun AskLines(text: String, onSeek: (Int) -> Unit) {
    val lines = remember(text) { AskParser.lines(text) }
    Column(Modifier.fillMaxWidth()) {
        lines.forEach { line ->
            val marker = line.marker
            when {
                line.blank -> Spacer(Modifier.height(AskLineGap))
                marker != null -> Row(Modifier.padding(start = (line.level * 16).dp)) {
                    Text(marker, style = AskBody, modifier = Modifier.width(if (marker == "•") 14.dp else 22.dp))
                    Text(askAnnotated(line.spans, onSeek), style = AskBody, modifier = Modifier.weight(1f))
                }
                else -> Text(askAnnotated(line.spans, onSeek), style = AskBody)
            }
        }
    }
}

@Composable
private fun AskTurnView(turn: AskTurn, latest: Boolean, busy: Boolean, onChip: (AskChip) -> Unit, onRetry: () -> Unit, onCopy: (String) -> Unit, onSeek: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            // Gelembung terang dengan sudut kanan-bawah hampir siku; di app asli ada satu baris kosong di bawah teks, ditiru dengan "\n".
            Text(
                turn.question + "\n", style = AskBody.copy(color = AskUserText),
                modifier = Modifier.widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 4.dp))
                    .background(AskUserBubble).padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        val answer = turn.answer
        val error = turn.error
        when {
            answer != null -> AskAnswerView(answer, showChips = latest && !busy, onChip = onChip, onCopy = onCopy, onSeek = onSeek)
            error != null -> AskErrorView(error, onRetry = if (latest) onRetry else null)
            else -> AskThinking()
        }
    }
}

@Composable
private fun AskAnswerView(answer: AskAnswer, showChips: Boolean, onChip: (AskChip) -> Unit, onCopy: (String) -> Unit, onSeek: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.yt_w_spark), null, Modifier.size(16.dp))
        AskLines(answer.text, onSeek)
        answer.disclaimer?.let { Text(it, color = Muted, fontSize = 12.sp, lineHeight = 16.sp) }
        IconButton(onClick = { onCopy(answer.text) }, Modifier.size(32.dp)) {
            Image(painterResource(R.drawable.yt_w_copy), "Copy", Modifier.size(18.dp), alpha = .8f)
        }
        if (showChips && answer.chips.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                answer.chips.forEach { chip -> AskChipView(chip) { onChip(chip) } }
            }
        }
    }
}

@Composable
private fun AskChipView(chip: AskChip, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).border(1.dp, Color(0xFF3F3F3F), shape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.yt_w_spark), null, Modifier.size(16.dp))
        Text(chip.text, color = Color.White, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun AskThinking() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        Text("Thinking…", color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun AskErrorView(message: String, onRetry: (() -> Unit)?) {
    Column {
        Text(message, color = AskError, fontSize = 14.sp)
        if (onRetry != null) TextButton(onClick = onRetry) { Text("Try again", color = Color.White) }
    }
}

/** Kotak input pil, ikon kirim outline tanpa lingkaran, dan chip "Ask ✦ Gemini" di bawahnya. */
@Composable
private fun AskComposer(panel: AskPanel, input: String, enabled: Boolean, onInput: (String) -> Unit, onSend: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(AskPill).padding(horizontal = 16.dp, vertical = 9.dp)) {
                if (input.isEmpty()) Text(panel.placeholder, color = Color(0xFF9E9E9E), fontSize = 14.sp, lineHeight = 20.sp, maxLines = 1)
                BasicTextField(
                    value = input,
                    onValueChange = { onInput(it.take(panel.maxCharacters)) },
                    enabled = enabled,
                    textStyle = AskBody,
                    cursorBrush = SolidColor(Color.White),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val canSend = input.isNotBlank() && enabled
            IconButton(onClick = onSend, enabled = canSend, modifier = Modifier.size(48.dp)) {
                Image(painterResource(R.drawable.yt_w_send), "Send", Modifier.size(24.dp), colorFilter = ColorFilter.tint(if (canSend) Color.White else AskSendIdle))
            }
        }
        val shape = RoundedCornerShape(50)
        Row(
            Modifier.padding(top = 6.dp, end = 8.dp).clip(shape).border(1.dp, AskOutline, shape).padding(horizontal = 16.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Ask", color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
            GeminiSpark(13.dp)
            Text("Gemini", color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

/** Bintang empat warna (kanan biru, bawah hijau, kiri kuning, atas merah) dengan gradien menyapu, bentuk SPARK. */
@Composable
private fun GeminiSpark(size: Dp) {
    val path = remember { PathParser().parsePathString(SPARK_PATH).toPath() }
    val colors = remember { listOf(Color(0xFF3485FD), Color(0xFF3DB58A), Color(0xFFE5B73B), Color(0xFFEA5D6D), Color(0xFF3485FD)) }
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        withTransform({ scale(unit, unit, pivot = Offset.Zero) }) {
            drawPath(path, Brush.sweepGradient(colors, center = Offset(12f, 12f)))
        }
    }
}
