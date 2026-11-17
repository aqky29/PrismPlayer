package id.flufclip.player

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class NativeParsersCodecTest {
    private fun DataOutputStream.writeStr(s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        writeInt(b.size)
        write(b)
    }

    private fun DataOutputStream.writeOptStr(s: String?) {
        if (s == null) writeByte(0) else {
            writeByte(1)
            writeStr(s)
        }
    }

    @Test fun decodesBootstrapWithInputPanelAndSuperChatMessage() {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeByte(1) // found
            out.writeOptStr("AIzaKey")
            out.writeOptStr("""{"client":{"clientName":"WEB"}}""")
            out.writeOptStr(null) // watch_continuation
            out.writeByte(1) // has_batch

            // batch
            out.writeOptStr("CONT_NEXT")
            out.writeOptStr("CONT_SEEK")
            out.writeOptStr("CONT_RELOAD")
            out.writeLong(2000L)
            out.writeByte(1)
            out.writeLong(15_000L)
            out.writeByte(0) // unpin_banner
            out.writeOptStr(null) // mode_notice

            // action_panel: Input(1)
            out.writeByte(1)
            out.writeStr("SEND_PARAM")
            out.writeStr("@Tester")
            out.writeOptStr("https://yt3.ggpht.com/me.jpg")
            out.writeInt(1)
            out.writeStr("MEMBER")
            out.writeOptStr("https://yt3.ggpht.com/badge.png")
            out.writeOptStr("Member")
            out.writeInt(1)
            out.writeStr("UC/e1")
            out.writeStr(":hi:")
            out.writeStr("https://yt3.ggpht.com/e1.png")
            out.writeByte(1)

            // pinned: none
            out.writeByte(0)

            // 1 message
            out.writeInt(1)
            out.writeStr("m1")
            out.writeStr("Bob")
            out.writeStr("Hello \uFFF00\uFFF1")
            out.writeByte(1)
            out.writeLong(15_000L)
            out.writeInt(1)
            out.writeStr("\uFFF00\uFFF1")
            out.writeStr("https://yt3.ggpht.com/em.png")
            out.writeOptStr("https://yt3.ggpht.com/bob.jpg")
            out.writeInt(0) // badges
            out.writeOptStr(null) // badge
            out.writeOptStr("$10.00")
            out.writeByte(1) // isSuperChat
            out.writeOptStr(null)
            out.writeOptStr(null)
            out.writeOptStr(null)
            out.writeOptStr(null)
        }

        val decoded = NativeParsers.decodeBootstrap(baos.toByteArray())
        assertTrue(decoded.found)
        assertEquals("AIzaKey", decoded.apiKey)
        assertEquals("WEB", decoded.context?.getJSONObject("client")?.getString("clientName"))
        val b = decoded.batch!!
        assertEquals("CONT_NEXT", b.batch.continuation)
        assertEquals("CONT_SEEK", b.seekContinuation)
        assertEquals("CONT_RELOAD", b.chosenReloadToken)
        assertEquals(15_000L, b.batch.offset)
        val input = b.batch.actionPanel as LiveChatActionPanel.Input
        assertEquals("SEND_PARAM", input.config.sendParams)
        assertEquals(":hi:", input.config.customEmojis.single().shortcut)
        val msg = b.batch.messages.single()
        assertEquals("Bob", msg.author)
        assertTrue(msg.isSuperChat)
        assertEquals("$10.00", msg.purchaseAmount)
        assertEquals("https://yt3.ggpht.com/em.png", msg.emoji["\uFFF00\uFFF1"])
    }
}
