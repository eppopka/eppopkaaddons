import dev.eppopkaaddons.ui.hud.hotbar.HotbarLayout
import dev.eppopkaaddons.ui.hud.hotbar.HotbarSelection
import dev.eppopkaaddons.ui.hud.hotbar.HotbarViewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HotbarLayoutTest {
    @Test
    fun horizontalGeometryHasNineSpacedSlots() {
        val l = HotbarLayout(false, 22f, 2f, 3f, false, true, false)
        assertEquals(220f, l.width)
        assertEquals(28f, l.height)
        assertEquals(3f, l.slot(0f).x)
        assertEquals(195f, l.slot(8f).x)
        assertEquals(217f, l.slot(8f).right)
    }

    @Test
    fun verticalGeometryTransposesWithoutRotatingItems() {
        val h = HotbarLayout(false, 22f, 2f, 3f, true, true, true)
        val v = h.copy(vertical = true)
        assertEquals(h.width, v.height)
        assertEquals(h.height, v.width)
        for (i in 0..8) {
            assertEquals(h.slot(i.toFloat()).x, v.slot(i.toFloat()).y)
            assertEquals(h.slot(i.toFloat()).y, v.slot(i.toFloat()).x)
            assertEquals(22f, v.slot(i.toFloat()).width)
            assertEquals(22f, v.slot(i.toFloat()).height)
        }
    }

    @Test
    fun everyAuxiliaryCombinationIsContainedAndNonOverlapping() {
        for (vertical in listOf(false, true)) for (handed in listOf(false, true)) {
            for (offhand in listOf(false, true)) for (attack in listOf(false, true)) {
                for (size in listOf(18f, 22f, 32f)) for (gap in listOf(0f, 2f, 10f)) {
                    val l = HotbarLayout(vertical, size, gap, 3f, offhand, handed, attack)
                    val cells = (0..8).map { l.slot(it.toFloat()) } + listOfNotNull(l.offhand, l.attackIndicator)
                    for (box in cells) {
                        assertTrue(box.x >= 0 && box.y >= 0)
                        assertTrue(box.right <= l.width && box.bottom <= l.height)
                    }
                    for (i in cells.indices) for (j in i + 1 until cells.size) {
                        val a = cells[i]; val b = cells[j]
                        assertTrue(a.right <= b.x || b.right <= a.x || a.bottom <= b.y || b.bottom <= a.y)
                    }
                }
            }
        }
    }

    @Test
    fun offhandAndAttackRespectHandedness() {
        val l = HotbarLayout(false, 20f, 0f, 1f, true, true, true)
        assertTrue(l.offhand!!.right < l.slot(0f).x)
        assertTrue(l.attackIndicator!!.x > l.slot(8f).right)
        val mirrored = l.copy(offhandBefore = false)
        assertTrue(mirrored.attackIndicator!!.right < mirrored.slot(0f).x)
        assertTrue(mirrored.offhand!!.x > mirrored.slot(8f).right)
    }

    @Test
    fun vanillaHorizontalKeepsItsNativePanelDimensions() {
        val l = HotbarLayout(false, 20f, 0f, 1f, false, true, false)
        assertEquals(182f, l.mainPanel.width)
        assertEquals(22f, l.mainPanel.height)
    }

    @Test
    fun referenceViewportMatchesHudEditorScaling() {
        val a = HotbarViewport.fromGui(480, 270)
        assertEquals(.5f, a.scale)
        assertEquals(960f, a.width)
        assertEquals(540f, a.height)
        val wide = HotbarViewport.fromGui(1280, 540)
        assertEquals(1f, wide.scale)
        assertEquals(1280f, wide.width)
    }

    @Test
    fun oversizedVerticalLayoutFitsIncludingEffectsAndLinkedLabels() {
        val viewport = HotbarViewport.fromGui(480, 270)
        val layout = HotbarLayout(true, 32f, 10f, 12f, true, true, true)
        val scale = viewport.fittedScale(3f, layout, 6f, 42f, 8f)
        assertTrue(scale > 0f && scale < 3f)
        assertTrue((layout.width + 12f) * scale <= viewport.width + .001f)
        assertTrue((layout.height + 50f) * scale <= viewport.height + .001f)
    }

    @Test
    fun selectionStartsAtCurrentSlotAndReducedMotionSnaps() {
        val s = HotbarSelection()
        assertEquals(5f, s.update(5, .016f, 16f, true))
        val moving = s.update(8, .016f, 16f, true)
        assertTrue(moving > 5f && moving < 8f)
        assertEquals(2f, s.update(2, .016f, 16f, false))
        s.reset()
        assertEquals(7f, s.update(7, 0f, 16f, true))
    }

    @Test
    fun selectionIsFrameRateIndependentAndNeverOvershoots() {
        val a = HotbarSelection(); val b = HotbarSelection()
        a.update(0, 0f, 12f, true); b.update(0, 0f, 12f, true)
        repeat(30) { a.update(8, 1f / 60f, 12f, true) }
        repeat(60) { b.update(8, 1f / 120f, 12f, true) }
        assertEquals(a.value, b.value, .0001f)
        assertTrue(a.value in 0f..8f && b.value in 0f..8f)
    }
}
