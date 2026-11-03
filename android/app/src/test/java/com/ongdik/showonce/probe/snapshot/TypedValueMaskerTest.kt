package com.ongdik.showonce.probe.snapshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypedValueMaskerTest {

    private fun textRecord(text: String?, description: String? = null) = NodeRecord(
        index = 0, depth = 0, text = text, description = description, viewId = null, className = null,
        bounds = RatioRect(0.0, 0.0, 1.0, 1.0),
        clickable = false, editable = false, textHidden = false,
    )

    @Test
    fun masksTypedValueEchoedInAnotherText() {
        val masker = TypedValueMasker()
        masker.remember(listOf("abc123"), nowMs = 0)
        val masked = masker.mask(textRecord("No results for abc123"), nowMs = 1_000)
        assertEquals("No results for •••", masked.text)
        assertTrue(masked.textMasked)
    }

    @Test
    fun masksTypedValueEchoedInDescription() {
        val masker = TypedValueMasker()
        masker.remember(listOf("홍길동"), nowMs = 0)
        val masked = masker.mask(textRecord(text = null, description = "홍길동님께 보내기"), nowMs = 0)
        assertEquals("•••님께 보내기", masked.description)
    }

    @Test
    fun leavesUnrelatedTextUnchanged() {
        val masker = TypedValueMasker()
        masker.remember(listOf("abc123"), nowMs = 0)
        val unchanged = masker.mask(textRecord("Display size & text"), nowMs = 0)
        assertEquals("Display size & text", unchanged.text)
        assertFalse(unchanged.textMasked)
    }

    @Test
    fun ignoresValuesShorterThanMinimumLength() {
        val masker = TypedValueMasker()
        masker.remember(listOf("ab"), nowMs = 0)
        assertEquals("about", masker.mask(textRecord("about"), nowMs = 0).text)
    }

    @Test
    fun forgetsValuesAfterRememberDuration() {
        val masker = TypedValueMasker()
        masker.remember(listOf("abc123"), nowMs = 0)
        val afterDuration = TypedValueMasker.REMEMBER_DURATION_MS + 1
        assertEquals("abc123", masker.mask(textRecord("abc123"), nowMs = afterDuration).text)
    }

    @Test
    fun keepsOnlyMostRecentValuesWhenLimitIsExceeded() {
        val masker = TypedValueMasker()
        val manyValues = (1..TypedValueMasker.MAX_REMEMBERED_VALUES + 1).map { valueNumber -> "value$valueNumber" }
        masker.remember(manyValues, nowMs = 0)
        assertEquals("value1 x", masker.mask(textRecord("value1 x"), nowMs = 0).text)
        assertEquals("••• x", masker.mask(textRecord("value11 x"), nowMs = 0).text)
    }

    @Test
    fun masksLongerValueFirstWhenValuesOverlap() {
        val masker = TypedValueMasker()
        masker.remember(listOf("abc", "abc123"), nowMs = 0)
        assertEquals("found •••", masker.mask(textRecord("found abc123"), nowMs = 0).text)
    }

    @Test
    fun knownLimitationDoesNotMaskReformattedValue() {
        // 알려진 한계: 서식이 바뀐 값은 같은 문자열이 아니라서 못 잡는다.
        val masker = TypedValueMasker()
        masker.remember(listOf("01012345678"), nowMs = 0)
        assertEquals("010-1234-5678", masker.mask(textRecord("010-1234-5678"), nowMs = 0).text)
    }
}
