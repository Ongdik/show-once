package com.ongdik.showonce.probe.snapshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotJsonTest {

    private val nextButton = NodeRecord(
        index = 3, depth = 2, text = "다음", description = null, viewId = "com.app:id/next",
        className = "android.widget.Button", bounds = RatioRect(0.1, 0.25, 0.6, 0.5),
        clickable = true, editable = false, textHidden = false,
    )

    @Test
    fun writesNodeAsSingleLineJson() {
        assertEquals(
            """{"index":3,"depth":2,"text":"다음","description":null,"viewId":"com.app:id/next",""" +
                """"className":"android.widget.Button","bounds":[0.1000,0.2500,0.6000,0.5000],""" +
                """"clickable":true,"editable":false,"textHidden":false}""",
            SnapshotJson.node(nextButton),
        )
    }

    @Test
    fun escapesQuotesAndBackslashes() {
        assertEquals("\"a\\\"b\\\\c\"", SnapshotJson.quote("a\"b\\c"))
    }

    @Test
    fun escapesNewlinesTabsAndControlCharacters() {
        assertEquals("\"1\\n2\\t3\\u0001\"", SnapshotJson.quote("1\n2\t3\u0001"))
    }

    @Test
    fun keepsKoreanAndEmojiAsIs() {
        assertEquals("\"확인 ✅\"", SnapshotJson.quote("확인 ✅"))
    }

    @Test
    fun summaryContainsNodeCountAndScreenInfoButNotNodeContent() {
        val settingsSnapshot = Snapshot(
            reason = "windowChanged", packageName = "com.android.settings",
            screen = ScreenInfo(1080, 2400, 0), wallTimeMs = 1L, eventTimeMs = 2L,
            nodes = listOf(nextButton), truncated = false,
        )
        val summaryJson = SnapshotJson.summary(settingsSnapshot)
        assertTrue(summaryJson.contains("\"nodeCount\":1"))
        assertTrue(summaryJson.contains("\"screen\":{\"widthPx\":1080,\"heightPx\":2400,\"rotation\":0}"))
        assertFalse(summaryJson.contains("다음"))
    }

    @Test
    fun writesNullNodeWhenClickSourceIsMissing() {
        val clickWithoutSource = ClickRecord("com.app", ScreenInfo(1, 1, 0), 1L, 2L, node = null)
        assertTrue(SnapshotJson.click(clickWithoutSource).endsWith("\"node\":null}"))
    }
}
