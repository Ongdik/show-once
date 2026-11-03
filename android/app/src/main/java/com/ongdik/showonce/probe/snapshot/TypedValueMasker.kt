package com.ongdik.showonce.probe.snapshot

/**
 * 입력란에서 숨긴 값이 다른 글자로 다시 보일 때 가린다.
 *
 * 예: 검색창에 "abc123"을 치면 입력란 글자는 SnapshotBuilder가 숨기지만,
 * 앱이 "No results for abc123"처럼 다시 보여 주면 그 글자는 남는다 (#14 알려진 한계).
 * 숨긴 값을 메모리에만 잠깐 들고 있다가, 다른 노드의 글자·설명에 같은 값이 있으면 •••로 바꾼다.
 *
 * - 숨긴 값은 이 객체 안에만 있고 기록·로그·파일 어디에도 쓰지 않는다.
 * - 최근 MAX_REMEMBERED_VALUES개, REMEMBER_DURATION_MS 동안만 기억한다.
 * - MIN_VALUE_LENGTH 미만은 가리지 않는다 ("a" 같은 값까지 가리면 일반 글자가 다 깨진다).
 *
 * 한계: 같은 문자열만 잡는다. 대소문자 변경, 서식(01012345678 → 010-1234-5678), 일부만 보이는 경우는 못 잡는다.
 */
class TypedValueMasker {

    private data class RememberedValue(val value: String, val rememberedAtMs: Long)

    private val rememberedValues = ArrayDeque<RememberedValue>()

    fun remember(hiddenValues: List<String>, nowMs: Long) {
        for (hiddenValue in hiddenValues) {
            val trimmedValue = hiddenValue.trim()
            if (trimmedValue.length < MIN_VALUE_LENGTH) continue
            rememberedValues.removeAll { it.value == trimmedValue }
            rememberedValues.addLast(RememberedValue(trimmedValue, nowMs))
            while (rememberedValues.size > MAX_REMEMBERED_VALUES) rememberedValues.removeFirst()
        }
    }

    /** 기억한 값이 들어 있는 글자·설명을 가린 기록. 가렸으면 textMasked=true */
    fun mask(record: NodeRecord, nowMs: Long): NodeRecord {
        forgetExpired(nowMs)
        if (rememberedValues.isEmpty()) return record
        val maskedText = maskText(record.text)
        val maskedDescription = maskText(record.description)
        val anythingMasked = maskedText != record.text || maskedDescription != record.description
        if (!anythingMasked) return record
        return record.copy(text = maskedText, description = maskedDescription, textMasked = true)
    }

    fun maskAll(records: List<NodeRecord>, nowMs: Long): List<NodeRecord> =
        records.map { record -> mask(record, nowMs) }

    private fun maskText(text: String?): String? {
        if (text == null) return null
        // 긴 값부터 가려야 "abc123"이 "abc"에 먼저 가려져 일부만 남는 일이 없다.
        return rememberedValues
            .map { it.value }
            .sortedByDescending { it.length }
            .fold(text) { partlyMasked, rememberedValue -> partlyMasked.replace(rememberedValue, MASK) }
    }

    private fun forgetExpired(nowMs: Long) {
        rememberedValues.removeAll { nowMs - it.rememberedAtMs > REMEMBER_DURATION_MS }
    }

    companion object {
        const val MASK = "•••"
        const val MIN_VALUE_LENGTH = 3
        const val MAX_REMEMBERED_VALUES = 10
        const val REMEMBER_DURATION_MS = 60_000L
    }
}
