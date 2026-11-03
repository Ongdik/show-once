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
    fun hidesSingleCharacterTextOfClickedKeypadKey() {
        val keypadKey = FakeNode(text = "7", isClickable = true)
        val clickRecord = SnapshotBuilder.toClickedNodeRecord(keypadKey, screen)
        assertNull(clickRecord.text)
        assertTrue(clickRecord.textHidden)
    }

    @Test
    fun hidesSingleCharacterDescriptionOfClickedKeypadKey() {
        val keypadKey = FakeNode(description = "3", isClickable = true)
        val clickRecord = SnapshotBuilder.toClickedNodeRecord(keypadKey, screen)
        assertNull(clickRecord.description)
        assertTrue(clickRecord.textHidden)
    }

    @Test
    fun keepsTextOfClickedNormalButton() {
        val confirmButton = FakeNode(text = "확인", isClickable = true)
        val clickRecord = SnapshotBuilder.toClickedNodeRecord(confirmButton, screen)
        assertEquals("확인", clickRecord.text)
        assertFalse(clickRecord.textHidden)
    }

    @Test
    fun masksTypedValueEchoedInAnotherTextViewWhenHiddenValuesArePassedToMasker() {
        // #14의 알려진 한계("No results for abc123"이 남는다)를 #16에서 TypedValueMasker로 막았다.
        val searchScreen = FakeNode(
            children = listOf(
                FakeNode(text = "abc123", isEditable = true),
                FakeNode(text = "No results for abc123"),
            ),
        )
        val result = SnapshotBuilder.collect(searchScreen, screen)
        val masker = TypedValueMasker()
        masker.remember(result.hiddenValues, nowMs = 0)
        val maskedRecords = masker.maskAll(result.nodes, nowMs = 0)

        val searchField = maskedRecords.single { it.editable }
        val echoedResult = maskedRecords.single { it.text?.startsWith("No results") == true }
        assertNull(searchField.text)
        assertEquals("No results for •••", echoedResult.text)
        assertTrue(echoedResult.textMasked)
    }

    @Test
    fun returnsHiddenTypedValuesOnlyInResultNotInRecords() {
        val result = SnapshotBuilder.collect(FakeNode(text = "abc123", isEditable = true), screen)
        assertEquals(listOf("abc123"), result.hiddenValues)
        assertTrue(result.nodes.none { it.text == "abc123" || it.description == "abc123" })
    }

    @Test
    fun doesNotPrintHiddenTypedValuesWhenResultIsLogged() {
        val result = SnapshotBuilder.collect(FakeNode(text = "abc123", isEditable = true), screen)
        assertFalse(result.toString().contains("abc123"))
    }

    @Test
    fun keepsHintTextOfEmptyEditableField() {
        val emptySearchField = FakeNode(text = "Search settings", isEditable = true, isShowingHintText = true)
        val record = singleRecordOf(emptySearchField)
        assertEquals("Search settings", record.text)
        assertFalse(record.textHidden)
        assertTrue(SnapshotBuilder.collect(emptySearchField, screen).hiddenValues.isEmpty())
    }

    @Test
    fun marksNodeScrolledOffScreenAsNotVisible() {
        // #14에서 본 문제: 스크롤 밖 문장이 [0.1093,0.9738,0.7259,0.9738]처럼 높이 0 상자로 기록됐다.
        val offScreenSentence = FakeNode(
            text = "below the fold",
            boundsInScreen = PixelRect(100, 2100, 700, 2200),
            isVisibleToUser = false,
        )
        val record = singleRecordOf(offScreenSentence)
        assertFalse(record.visible)
        assertEquals(record.bounds.top, record.bounds.bottom, 0.0)
    }

    @Test
    fun returnsEmptyResultWhenRootIsMissing() {
        val result = SnapshotBuilder.collect(null, screen)
        assertTrue(result.nodes.isEmpty())
        assertFalse(result.truncated)
    }
}
