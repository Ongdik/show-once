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
                """"clickable":true,"editable":false,"textHidden":false,"visible":true,"textMasked":false}""",
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
            screen = ScreenInfo(1080, 2400, 0), wallTimeMs = 1L, eventTimeMs = 2L, elapsedRealtimeMs = 3L,
            nodes = listOf(nextButton), truncated = false, contentRecorded = true,
        )
        val summaryJson = SnapshotJson.summary(settingsSnapshot)
        assertTrue(summaryJson.contains("\"contentRecorded\":true,\"nodeCount\":1"))
        assertTrue(summaryJson.contains("\"screen\":{\"widthPx\":1080,\"heightPx\":2400,\"rotation\":0}"))
        assertFalse(summaryJson.contains("다음"))
    }

    @Test
    fun writesNullNodeWhenClickSourceIsMissing() {
        val clickWithoutSource = ClickRecord("com.app", ScreenInfo(1, 1, 0), 1L, 2L, 3L, node = null, contentRecorded = false)
        assertTrue(SnapshotJson.click(clickWithoutSource).endsWith("\"node\":null}"))
    }

    private val twoNodeSettingsSnapshot = Snapshot(
        reason = "windowChanged", packageName = "com.android.settings",
        screen = ScreenInfo(1080, 2400, 0), wallTimeMs = 1L, eventTimeMs = 2L, elapsedRealtimeMs = 3L,
        nodes = listOf(nextButton, nextButton.copy(index = 4, text = "취소")), truncated = false, contentRecorded = true,
    )

    @Test
    fun writesSnapshotWithAllNodesAsSingleFileLine() {
        val fileLine = SnapshotJson.snapshotLine(twoNodeSettingsSnapshot)
        assertFalse(fileLine.contains("\n"))
        assertTrue(fileLine.startsWith("{\"type\":\"screen\""))
        assertTrue(fileLine.contains("\"elapsedRealtimeMs\":3"))
        assertTrue(fileLine.contains("\"nodes\":[{\"index\":3,"))
        assertTrue(fileLine.contains("\"text\":\"취소\""))
        assertTrue(fileLine.endsWith("}]}"))
    }

    @Test
    fun writesSessionHeaderWithThreeClocksAppVersionAndSettings() {
        val header = SessionHeader(
            formatVersion = 1, wallTimeMs = 100L, uptimeMs = 200L, elapsedRealtimeMs = 300L,
            appVersionName = "1.0", appVersionCode = 1L,
            allowedPackages = listOf("io.metamask", "com.android.settings"),
            settleDelayMs = 500L, maxNodesPerSnapshot = 500, typedValueRememberMs = 60_000L,
        )
        assertEquals(
            """{"type":"session","formatVersion":1,"wallTimeMs":100,"uptimeMs":200,"elapsedRealtimeMs":300,""" +
                """"appVersionName":"1.0","appVersionCode":1,""" +
                """"allowedPackages":["com.android.settings","io.metamask"],""" +
                """"settleDelayMs":500,"maxNodesPerSnapshot":500,"typedValueRememberMs":60000}""",
            SnapshotJson.sessionHeader(header),
        )
    }
}
