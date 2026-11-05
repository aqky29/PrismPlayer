package id.flufclip.player

import com.grack.nanojson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Memakai respons nyata (disensor) dari docs/evidence/ask-ai-mode-2026-11-02/ yang disalin ke src/test/resources/ask/.
 * Nilai token/id pada berkas itu diganti `<redacted>`; yang diuji adalah bentuk dan jalurnya.
 */
class AskYouTubeTest {
    private fun resource(name: String): JsonObject {
        val text = AskYouTubeTest::class.java.getResourceAsStream("/ask/$name")!!.bufferedReader().readText()
        return AskParser.parseObject(text)!!
    }

    /** Cuplikan `next` hanya berisi panel; bungkus di bawah satu akar seperti respons `next` sebenarnya. */
    private fun nextRoot(): JsonObject = JsonObject().apply { this["contents"] = resource("next_ask_panel_redacted.json")["engagementPanel"] }

    @Test fun panelFromNext_readsTokenAndComposerSettings() {
        val panel = AskParser.panelFromNext(nextRoot())
        assertNotNull(panel)
        assertEquals("<redacted>", panel!!.token)
        assertEquals("/youtubei/v1/get_panel", panel.route)
        assertEquals("Ask a question...", panel.placeholder)
        assertEquals(500, panel.maxCharacters)
        assertTrue(panel.greeting!!.startsWith("Hello! Curious"))
    }

    @Test fun panelFromNext_isNullWhenServerSendsNoAsk() {
        assertNull(AskParser.panelFromNext(AskParser.parseObject("""{"contents":{"twoColumnWatchNextResults":{}}}""")!!))
    }

    @Test fun panelFromNext_rejectsOtherContinuationTypesAndStreamingRoutes() {
        fun root(request: String, apiUrl: String?): JsonObject {
            val meta = apiUrl?.let { ""","commandMetadata":{"webCommandMetadata":{"apiUrl":"$it"}}""" }.orEmpty()
            return AskParser.parseObject("""{"x":{"sendUserQueryCommand":{"innertubeCommand":{"continuationCommand":{"token":"t","request":"$request"}$meta}}}}""")!!
        }
        assertNotNull(AskParser.panelFromNext(root("CONTINUATION_REQUEST_TYPE_GET_PANEL", null)))
        assertNotNull(AskParser.panelFromNext(root("CONTINUATION_REQUEST_TYPE_GET_PANEL", "/youtubei/v1/get_panel")))
        assertNull(AskParser.panelFromNext(root("CONTINUATION_REQUEST_TYPE_BROWSE", null)))
        assertNull(AskParser.panelFromNext(root("CONTINUATION_REQUEST_TYPE_GET_PANEL", "/youtubei/v1/streaming_panel")))
    }

    @Test fun panelFromNext_usesDefaultsWhenComposerIsMissing() {
        val panel = AskParser.panelFromNext(AskParser.parseObject("""{"sendUserQueryCommand":{"innertubeCommand":{"continuationCommand":{"token":"t","request":"CONTINUATION_REQUEST_TYPE_GET_PANEL"}}}}""")!!)!!
        assertEquals("Ask a question...", panel.placeholder)
        assertEquals(500, panel.maxCharacters)
        assertNull(panel.greeting)
    }

    @Test fun answerFromPanel_readsAnswerDisclaimerAndChips() {
        val answer = AskParser.answerFromPanel(resource("get_panel_response_redacted.json"))
        assertNotNull(answer)
        assertTrue(answer!!.text.startsWith("This video features the official 4K remastered music video for *Rick Astley*'s 1987"))
        assertTrue(answer.disclaimer!!.startsWith("AI can make mistakes"))
        assertEquals(
            listOf("Who produced this song?", "When did this video hit one billion views?", "What other songs are on this album?"),
            answer.chips.map { it.text },
        )
        assertTrue(answer.chips.all { it.id != null })
    }

    @Test fun answerFromPanel_isNullWithoutAnswerText() {
        assertNull(AskParser.answerFromPanel(AskParser.parseObject("""{"onResponseReceivedCommand":{}}""")!!))
        assertNull(AskParser.answerFromPanel(AskParser.parseObject("{}")!!))
    }

    @Test fun parseObject_returnsNullForInvalidJson() {
        assertNull(AskParser.parseObject("not json"))
        assertNull(AskParser.parseObject(""))
    }

    @Test fun requestFields_firstQuestionMatchesVerifiedShape() {
        val fields = AskParser.requestFields("TOKEN", "Summarize this video", "youchat-1", 12_345L, null, null)
        assertEquals(setOf("continuation", "formData"), fields.keys)
        assertEquals("TOKEN", fields["continuation"])
        @Suppress("UNCHECKED_CAST")
        val form = (fields["formData"] as Map<String, Any>)["inputComposerFormData"] as Map<String, Any>
        assertEquals(
            mapOf("clientMessageId" to "youchat-1", "playerOffsetMs" to "12000", "userInputText" to "Summarize this video"),
            form,
        )
    }

    @Test fun requestFields_followUpAndSuggestionCarryPreviousIdAndChipId() {
        val fields = AskParser.requestFields("T", "Who produced this song?", "youchat-2", -5L, "youchat-1", "chip-7")
        @Suppress("UNCHECKED_CAST")
        val form = (fields["formData"] as Map<String, Any>)["inputComposerFormData"] as Map<String, Any>
        assertEquals("youchat-1", form["previousClientMessageId"])
        assertEquals("chip-7", form["pendingSuggestedQueryIdentifier"])
        assertEquals("0", form["playerOffsetMs"])
        assertFalse(AskParser.requestFields("T", "q", "c", 0L, "", "")["formData"].toString().contains("previous"))
    }

    @Test fun newClientMessageId_usesYouchatPrefix() = assertEquals("youchat-1793624005148", AskParser.newClientMessageId(1793624005148L))

    @Test fun spans_parseItalicAndBold() {
        assertEquals(
            listOf(AskSpan("official "), AskSpan("Rick Astley", italic = true), AskSpan("'s hit")),
            AskParser.spans("official *Rick Astley*'s hit"),
        )
        assertEquals(listOf(AskSpan("bold", bold = true), AskSpan(" and "), AskSpan("it", italic = true)), AskParser.spans("**bold** and *it*"))
    }

    @Test fun spans_leaveUnpairedAndSpacedStarsAlone() {
        assertEquals(listOf(AskSpan("2 * 3 * 4")), AskParser.spans("2 * 3 * 4"))
        assertEquals(listOf(AskSpan("a *b")), AskParser.spans("a *b"))
        assertEquals(emptyList<AskSpan>(), AskParser.spans(""))
    }

    @Test fun spans_linkEachTimestampEvenInsideRanges() {
        assertEquals(
            listOf(AskSpan("sub-agen ("), AskSpan("0:00", seconds = 0), AskSpan("-"), AskSpan("0:45", seconds = 45), AskSpan(").")),
            AskParser.spans("sub-agen (0:00-0:45)."),
        )
        assertEquals(listOf(AskSpan("1:02:03", seconds = 3723)), AskParser.spans("1:02:03"))
        assertEquals(listOf(AskSpan("lihat "), AskSpan("12:05", seconds = 725), AskSpan(" dan "), AskSpan("3:12", seconds = 192)), AskParser.spans("lihat 12:05 dan 3:12"))
    }

    @Test fun spans_timestampKeepsBoldAndItalic() {
        assertEquals(
            listOf(AskSpan("a ", bold = true), AskSpan("1:17", bold = true, seconds = 77), AskSpan(" b", bold = true)),
            AskParser.spans("**a 1:17 b**"),
        )
        assertEquals(listOf(AskSpan("1:17", italic = true, seconds = 77)), AskParser.spans("*1:17*"))
    }

    @Test fun spans_ignoreThingsThatAreNotTimestamps() {
        assertEquals(listOf(AskSpan("rasio 16:9 dan skor 3:75 serta 1:2:3")), AskParser.spans("rasio 16:9 dan skor 3:75 serta 1:2:3"))
        assertNull(AskParser.timestampSeconds("16:9"))
        assertNull(AskParser.timestampSeconds("abc"))
        assertEquals(45, AskParser.timestampSeconds("0:45"))
    }

    @Test fun lines_splitBulletsNumbersAndParagraphGaps() {
        val lines = AskParser.lines("Intro (0:00-0:45).\n\n\nPoin:\n\n* **Tujuan:** a\n- b\n   1. c\n10) d\n\nEnd\n\n")
        assertEquals(
            listOf(
                AskLine(null, AskParser.spans("Intro (0:00-0:45).")),
                AskLine(null, emptyList()),
                AskLine(null, listOf(AskSpan("Poin:"))),
                AskLine(null, emptyList()),
                AskLine("•", listOf(AskSpan("Tujuan:", bold = true), AskSpan(" a"))),
                AskLine("•", listOf(AskSpan("b"))),
                AskLine("1.", listOf(AskSpan("c")), level = 1),
                AskLine("10.", listOf(AskSpan("d"))),
                AskLine(null, emptyList()),
                AskLine(null, listOf(AskSpan("End"))),
            ),
            lines,
        )
        assertTrue(lines[1].blank)
    }

    @Test fun lines_doNotTreatBoldOrItalicStartAsBullet() {
        assertEquals(listOf(AskLine(null, listOf(AskSpan("Tujuan:", bold = true), AskSpan(" a")))), AskParser.lines("**Tujuan:** a"))
        assertEquals(listOf(AskLine(null, listOf(AskSpan("miring", italic = true), AskSpan(" teks")))), AskParser.lines("*miring* teks"))
        assertEquals(emptyList<AskLine>(), AskParser.lines("\n \n"))
    }

    @Test fun errorMessage_mapsHttpStatusAndNetworkErrors() {
        assertEquals("Sign in again to keep using Ask.", AskParser.errorMessage(IllegalStateException("YouTube player error: 403")))
        assertEquals("Sign in again to keep using Ask.", AskParser.errorMessage(IllegalStateException("YouTube player error: 401")))
        assertEquals("Too many questions right now. Try again in a moment.", AskParser.errorMessage(IllegalStateException("YouTube player error: 429")))
        assertEquals("No connection. Check your network and try again.", AskParser.errorMessage(IOException("timeout")))
        assertEquals("Ask couldn't answer. Try again.", AskParser.errorMessage(IllegalStateException("YouTube player error: 500")))
    }

    @Test fun watchActions_showAskOnlyWhenServerOffersIt() {
        assertEquals(
            listOf(WatchActionKind.RATING, WatchActionKind.SHARE, WatchActionKind.ASK, WatchActionKind.DOWNLOAD, WatchActionKind.SAVE, WatchActionKind.MORE),
            watchActions(live = false, chatAvailable = false, replayAvailable = false, downloadable = true, askAvailable = true),
        )
        assertFalse(WatchActionKind.ASK in watchActions(live = false, chatAvailable = false, replayAvailable = false, downloadable = true))
        assertEquals(
            listOf(WatchActionKind.RATING, WatchActionKind.LIVE_CHAT, WatchActionKind.SHARE, WatchActionKind.ASK, WatchActionKind.SAVE, WatchActionKind.MORE),
            watchActions(live = true, chatAvailable = true, replayAvailable = false, downloadable = true, askAvailable = true),
        )
    }
}
