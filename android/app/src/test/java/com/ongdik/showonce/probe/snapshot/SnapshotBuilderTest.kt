package com.ongdik.showonce.probe.snapshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotBuilderTest {

    private val screen = ScreenInfo(widthPx = 1000, heightPx = 2000, rotation = 0)

    private fun singleRecordOf(node: FakeNode): NodeRecord =
        SnapshotBuilder.collect(node, screen).nodes.single()

    @Test
    fun collectsNodesInDepthFirstOrderWithDepthAndIndex() {
        val root = FakeNode(
            text = "root",
            children = listOf(
                FakeNode(text = "A", children = listOf(FakeNode(text = "A1"))),
                FakeNode(text = "B"),
            ),
        )
        val records = SnapshotBuilder.collect(root, screen).nodes
        assertEquals(listOf("root", "A", "A1", "B"), records.map { it.text })
        assertEquals(listOf(0, 1, 2, 1), records.map { it.depth })
        assertEquals(listOf(0, 1, 2, 3), records.map { it.index })
    }

    @Test
    fun convertsPixelBoundsToScreenRatio() {
        val record = singleRecordOf(FakeNode(boundsInScreen = PixelRect(100, 500, 600, 1000)))
        assertEquals(RatioRect(0.1, 0.25, 0.6, 0.5), record.bounds)
    }

    @Test
    fun clampsBoundsOutsideScreenToZeroAndOne() {
        val record = singleRecordOf(FakeNode(boundsInScreen = PixelRect(-50, -10, 1200, 2500)))
        assertEquals(RatioRect(0.0, 0.0, 1.0, 1.0), record.bounds)
    }

    @Test
    fun returnsZeroRatioWhenScreenSizeIsZero() {
        val ratioBounds = SnapshotBuilder.toRatio(PixelRect(10, 10, 20, 20), ScreenInfo(0, 0, 0))
        assertEquals(RatioRect(0.0, 0.0, 0.0, 0.0), ratioBounds)
    }

    @Test
    fun hidesPasswordFieldTextButKeepsDescription() {
        val record = singleRecordOf(FakeNode(text = "hunter2", description = "Password", isPassword = true))
        assertNull(record.text)
        assertTrue(record.textHidden)
        assertEquals("Password", record.description)
    }

    @Test
    fun hidesTextTypedIntoEditableField() {
        val record = singleRecordOf(FakeNode(text = "홍길동", isEditable = true))
        assertNull(record.text)
        assertTrue(record.textHidden)
        assertTrue(record.editable)
    }

    @Test
    fun marksEmptyEditableFieldAsNothingHidden() {
        val record = singleRecordOf(FakeNode(text = "", isEditable = true))
        assertFalse(record.textHidden)
    }

    @Test
    fun treatsBlankTextAndViewIdAsMissing() {
        val record = singleRecordOf(FakeNode(text = "  ", viewId = " "))
        assertNull(record.text)
        assertNull(record.viewId)
    }

    @Test
    fun truncatesAndMarksWhenNodeCountExceedsLimit() {
        val root = FakeNode(children = List(10) { childNumber -> FakeNode(text = "$childNumber") })
        val result = SnapshotBuilder.collect(root, screen, maxNodes = 5)
        assertEquals(5, result.nodes.size)
        assertTrue(result.truncated)
    }

    @Test
    fun doesNotMarkTruncatedWhenNodeCountEqualsLimit() {
        val root = FakeNode(children = List(4) { FakeNode() })
        val result = SnapshotBuilder.collect(root, screen, maxNodes = 5)
        assertEquals(5, result.nodes.size)
        assertFalse(result.truncated)
    }

    @Test
    fun skipsChildrenThatDisappeared() {
        val root = FakeNode(children = listOf(null, FakeNode(text = "remaining")))
        val records = SnapshotBuilder.collect(root, screen).nodes
        assertEquals(listOf(null, "remaining"), records.map { it.text })
    }

    @Test
    fun returnsEmptyResultWhenRootIsMissing() {
        val result = SnapshotBuilder.collect(null, screen)
        assertTrue(result.nodes.isEmpty())
        assertFalse(result.truncated)
    }
}
