package com.ongdik.showonce.probe.snapshot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatFilterTest {

    private fun nodeWith(text: String?, left: Double = 0.0) = NodeRecord(
        index = 0, depth = 0, text = text, description = null, viewId = null, className = null,
        bounds = RatioRect(left, 0.0, 1.0, 1.0),
        clickable = false, editable = false, textHidden = false,
    )

    @Test
    fun treatsFirstScreenAsNew() {
        assertTrue(RepeatFilter().isNew("app", listOf(nodeWith("a"))))
    }

    @Test
    fun skipsScreenWithSameContentAsPrevious() {
        val repeatFilter = RepeatFilter()
        repeatFilter.isNew("app", listOf(nodeWith("a")))
        assertFalse(repeatFilter.isNew("app", listOf(nodeWith("a"))))
    }

    @Test
    fun treatsChangedTextAsNewScreen() {
        val repeatFilter = RepeatFilter()
        repeatFilter.isNew("app", listOf(nodeWith("a")))
        assertTrue(repeatFilter.isNew("app", listOf(nodeWith("b"))))
    }

    @Test
    fun treatsChangedPositionAsNewScreen() {
        val repeatFilter = RepeatFilter()
        repeatFilter.isNew("app", listOf(nodeWith("a", left = 0.0)))
        assertTrue(repeatFilter.isNew("app", listOf(nodeWith("a", left = 0.5))))
    }

    @Test
    fun treatsSameContentFromAnotherAppAsNewScreen() {
        val repeatFilter = RepeatFilter()
        repeatFilter.isNew("app1", listOf(nodeWith("a")))
        assertTrue(repeatFilter.isNew("app2", listOf(nodeWith("a"))))
    }
}
