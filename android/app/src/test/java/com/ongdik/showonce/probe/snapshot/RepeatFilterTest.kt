package com.ongdik.showonce.probe.snapshot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatFilterTest {

    private fun n(text: String?, left: Double = 0.0) = NodeRecord(
        0, 0, text, null, null, null, RatioRect(left, 0.0, 1.0, 1.0),
        clickable = false, editable = false, textHidden = false,
    )

    @Test
    fun 처음_화면은_새_화면() {
        assertTrue(RepeatFilter().isNew("app", listOf(n("a"))))
    }

    @Test
    fun 같은_내용이_다시_오면_건너뛴다() {
        val f = RepeatFilter()
        f.isNew("app", listOf(n("a")))
        assertFalse(f.isNew("app", listOf(n("a"))))
    }

    @Test
    fun 글자가_바뀌면_새_화면() {
        val f = RepeatFilter()
        f.isNew("app", listOf(n("a")))
        assertTrue(f.isNew("app", listOf(n("b"))))
    }

    @Test
    fun 위치만_바뀌어도_새_화면() {
        val f = RepeatFilter()
        f.isNew("app", listOf(n("a", left = 0.0)))
        assertTrue(f.isNew("app", listOf(n("a", left = 0.5))))
    }

    @Test
    fun 다른_앱의_같은_내용은_새_화면() {
        val f = RepeatFilter()
        f.isNew("app1", listOf(n("a")))
        assertTrue(f.isNew("app2", listOf(n("a"))))
    }
}
