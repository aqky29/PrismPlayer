package id.flufclip.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DualTabModelTest {
    @Test
    fun focusSlotInFocusPolicyMutesOtherSlot() {
        val model = DualTabModel(initialAudioPolicy = "focus")
        assertEquals(1, model.focusedSlot)
        assertFalse(model.slot1.muted)
        assertTrue(model.slot2.muted)

        model.focusSlot(2)
        assertEquals(2, model.focusedSlot)
        assertTrue(model.slot1.muted)
        assertFalse(model.slot2.muted)
    }

    @Test
    fun mixPolicyUnmutesBothSlotsAndPreservesManualMuteOnFocusChange() {
        val model = DualTabModel(initialAudioPolicy = "focus")
        model.applyAudioPolicy("mix")
        assertFalse(model.slot1.muted)
        assertFalse(model.slot2.muted)

        model.toggleMute(1)
        assertTrue(model.slot1.muted)

        model.focusSlot(2)
        assertEquals(2, model.focusedSlot)
        assertTrue(model.slot1.muted)
        assertFalse(model.slot2.muted)
    }

    @Test
    fun swapAndPickingStateTransitionsWork() {
        val model = DualTabModel()
        assertFalse(model.swappedDisplay)
        model.swapSlots()
        assertTrue(model.swappedDisplay)
        model.swapSlots()
        assertFalse(model.swappedDisplay)

        assertNull(model.pickingSlot)
        model.startPicking(2)
        assertEquals(2, model.pickingSlot)
        model.clearPicking()
        assertNull(model.pickingSlot)
    }

    @Test
    fun toggleAudioForSlotInFocusPolicyUnmutesNonFocusedSlotInSingleTap() {
        val model = DualTabModel(initialAudioPolicy = "focus")
        assertEquals(1, model.focusedSlot)
        assertFalse(model.slot1.muted)
        assertTrue(model.slot2.muted)

        // Single tap on muted non-focused Slot 2 focuses and unmutes it (no double-toggle)
        model.toggleAudioForSlot(2)
        assertEquals(2, model.focusedSlot)
        assertTrue(model.slot1.muted)
        assertFalse(model.slot2.muted)

        // Tapping again on focused Slot 2 mutes it
        model.toggleAudioForSlot(2)
        assertEquals(2, model.focusedSlot)
        assertTrue(model.slot2.muted)

        // Tapping again unmutes it
        model.toggleAudioForSlot(2)
        assertFalse(model.slot2.muted)
    }

    @Test
    fun toggleAudioForSlotInMixPolicyTogglesIndependently() {
        val model = DualTabModel(initialAudioPolicy = "mix")
        assertFalse(model.slot1.muted)
        assertFalse(model.slot2.muted)

        model.toggleAudioForSlot(2)
        assertEquals(2, model.focusedSlot)
        assertFalse(model.slot1.muted)
        assertTrue(model.slot2.muted)

        model.toggleAudioForSlot(2)
        assertFalse(model.slot1.muted)
        assertFalse(model.slot2.muted)
    }

    @Test
    fun resolveTargetSlotPrioritizesPickingThenEmptyThenFocused() {
        val model = DualTabModel()
        assertEquals(1, model.resolveTargetSlot())

        model.startPicking(2)
        assertEquals(2, model.resolveTargetSlot())
        model.clearPicking()

        val fakeItem = org.schabi.newpipe.extractor.stream.StreamInfoItem(
            0,
            "https://www.youtube.com/watch?v=abc12345678",
            "Video 1",
            org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM,
        )
        model.updateSlot(1) { it.copy(item = fakeItem) }
        assertEquals(2, model.resolveTargetSlot())

        model.updateSlot(2) { it.copy(item = fakeItem) }
        model.focusSlot(1)
        assertEquals(1, model.resolveTargetSlot())
        model.focusSlot(2)
        assertEquals(2, model.resolveTargetSlot())
    }
}
