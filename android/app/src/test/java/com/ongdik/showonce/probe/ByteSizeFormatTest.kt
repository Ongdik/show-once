package com.ongdik.showonce.probe

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteSizeFormatTest {

    @Test
    fun showsBytesBelowOneKilobyte() {
        assertEquals("0 B", formatByteSize(0))
        assertEquals("1023 B", formatByteSize(1023))
    }

    @Test
    fun showsKilobytesWithOneDecimal() {
        assertEquals("1.0 KB", formatByteSize(1024))
        assertEquals("33.8 KB", formatByteSize(34_567))
    }

    @Test
    fun showsMegabytesWithOneDecimal() {
        assertEquals("5.0 MB", formatByteSize(5L * 1024 * 1024))
    }
}
