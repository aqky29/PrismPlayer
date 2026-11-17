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
        model.setAudioPolicy("mix")
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
}
