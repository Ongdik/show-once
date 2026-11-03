package com.ongdik.showonce.probe.snapshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotJsonTest {

    private val node = NodeRecord(
        index = 3, depth = 2, text = "다음", description = null, viewId = "com.app:id/next",
        className = "android.widget.Button", bounds = RatioRect(0.1, 0.25, 0.6, 0.5),
        clickable = true, editable = false, textHidden = false,
    )

    @Test
    fun 노드를_JSON_한_줄로_만든다() {
        assertEquals(
            """{"index":3,"depth":2,"text":"다음","description":null,"viewId":"com.app:id/next",""" +
                """"className":"android.widget.Button","bounds":[0.1000,0.2500,0.6000,0.5000],""" +
                """"clickable":true,"editable":false,"textHidden":false}""",
            SnapshotJson.node(node),
        )
    }

    @Test
    fun 따옴표와_역슬래시를_이스케이프한다() {
        assertEquals("\"a\\\"b\\\\c\"", SnapshotJson.quote("a\"b\\c"))
    }

    @Test
    fun 줄바꿈과_제어_문자를_이스케이프한다() {
        assertEquals("\"1\\n2\\t3\\u0001\"", SnapshotJson.quote("1\n2\t3\u0001"))
    }

    @Test
    fun 한글과_이모지는_그대로_둔다() {
        assertEquals("\"확인 ✅\"", SnapshotJson.quote("확인 ✅"))
    }

    @Test
    fun 요약에는_노드_수와_화면_정보가_들어간다() {
        val s = Snapshot(
            reason = "windowChanged", packageName = "com.android.settings",
            screen = ScreenInfo(1080, 2400, 0), wallTimeMs = 1L, eventTimeMs = 2L,
            nodes = listOf(node), truncated = false,
        )
        val json = SnapshotJson.summary(s)
        assertTrue(json.contains("\"nodeCount\":1"))
        assertTrue(json.contains("\"screen\":{\"widthPx\":1080,\"heightPx\":2400,\"rotation\":0}"))
        assertFalse(json.contains("다음")) // 요약에는 노드 내용이 없다
    }

    @Test
    fun 클릭_노드가_없으면_null() {
        val c = ClickRecord("com.app", ScreenInfo(1, 1, 0), 1L, 2L, node = null)
        assertTrue(SnapshotJson.click(c).endsWith("\"node\":null}"))
    }
}
