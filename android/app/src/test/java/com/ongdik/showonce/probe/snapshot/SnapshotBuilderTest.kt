package com.ongdik.showonce.probe.snapshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotBuilderTest {

    private val screen = ScreenInfo(widthPx = 1000, heightPx = 2000, rotation = 0)

    @Test
    fun 깊이_우선_순서와_깊이를_남긴다() {
        val root = FakeNode(
            text = "루트",
            children = listOf(
                FakeNode(text = "A", children = listOf(FakeNode(text = "A1"))),
                FakeNode(text = "B"),
            ),
        )
        val nodes = SnapshotBuilder.collect(root, screen).nodes
        assertEquals(listOf("루트", "A", "A1", "B"), nodes.map { it.text })
        assertEquals(listOf(0, 1, 2, 1), nodes.map { it.depth })
        assertEquals(listOf(0, 1, 2, 3), nodes.map { it.index })
    }

    @Test
    fun 위치를_화면_대비_비율로_바꾼다() {
        val node = FakeNode(boundsInScreen = PixelRect(100, 500, 600, 1000))
        val b = SnapshotBuilder.collect(node, screen).nodes.single().bounds
        assertEquals(RatioRect(0.1, 0.25, 0.6, 0.5), b)
    }

    @Test
    fun 화면_밖으로_나간_부분은_0과_1로_자른다() {
        val node = FakeNode(boundsInScreen = PixelRect(-50, -10, 1200, 2500))
        val b = SnapshotBuilder.collect(node, screen).nodes.single().bounds
        assertEquals(RatioRect(0.0, 0.0, 1.0, 1.0), b)
    }

    @Test
    fun 화면_크기가_0이면_비율도_0() {
        val b = SnapshotBuilder.toRatio(PixelRect(10, 10, 20, 20), ScreenInfo(0, 0, 0))
        assertEquals(RatioRect(0.0, 0.0, 0.0, 0.0), b)
    }

    @Test
    fun 비밀번호_칸의_글자는_버리고_표시만_남긴다() {
        val node = FakeNode(text = "hunter2", description = "비밀번호", isPassword = true)
        val r = SnapshotBuilder.collect(node, screen).nodes.single()
        assertNull(r.text)
        assertTrue(r.textHidden)
        assertEquals("비밀번호", r.description)
    }

    @Test
    fun 입력란에_친_글자도_버린다() {
        val node = FakeNode(text = "홍길동", isEditable = true)
        val r = SnapshotBuilder.collect(node, screen).nodes.single()
        assertNull(r.text)
        assertTrue(r.textHidden)
        assertTrue(r.editable)
    }

    @Test
    fun 빈_입력란은_숨긴_글자가_없다고_표시한다() {
        val r = SnapshotBuilder.collect(FakeNode(text = "", isEditable = true), screen).nodes.single()
        assertFalse(r.textHidden)
    }

    @Test
    fun 공백뿐인_글자와_id는_없음으로_본다() {
        val r = SnapshotBuilder.collect(FakeNode(text = "  ", viewId = " "), screen).nodes.single()
        assertNull(r.text)
        assertNull(r.viewId)
    }

    @Test
    fun 최대_개수를_넘으면_자르고_표시한다() {
        val root = FakeNode(children = List(10) { FakeNode(text = "$it") })
        val result = SnapshotBuilder.collect(root, screen, maxNodes = 5)
        assertEquals(5, result.nodes.size)
        assertTrue(result.truncated)
    }

    @Test
    fun 정확히_최대_개수면_자르지_않은_것으로_본다() {
        val root = FakeNode(children = List(4) { FakeNode() })
        val result = SnapshotBuilder.collect(root, screen, maxNodes = 5)
        assertEquals(5, result.nodes.size)
        assertFalse(result.truncated)
    }

    @Test
    fun 사라진_자식은_건너뛴다() {
        val root = FakeNode(children = listOf(null, FakeNode(text = "남은 것")))
        val nodes = SnapshotBuilder.collect(root, screen).nodes
        assertEquals(listOf(null, "남은 것"), nodes.map { it.text })
    }

    @Test
    fun 루트가_없으면_빈_목록() {
        val result = SnapshotBuilder.collect(null, screen)
        assertTrue(result.nodes.isEmpty())
        assertFalse(result.truncated)
    }
}
