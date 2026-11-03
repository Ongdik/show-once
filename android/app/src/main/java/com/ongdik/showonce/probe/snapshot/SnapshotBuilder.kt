package com.ongdik.showonce.probe.snapshot

import kotlin.math.round

/**
 * 노드 트리를 기록 목록으로 바꾼다. 안드로이드 API를 쓰지 않는 순수 코드.
 *
 * 순서는 깊이 우선(부모 → 첫 자식 → 그 자식 …). 접근성 트리는 대체로 화면 위→아래,
 * 왼쪽→오른쪽 순서로 자식이 놓이므로, 사람이 읽는 순서와 비슷해진다.
 */
object SnapshotBuilder {

    const val DEFAULT_MAX_NODES = 500

    /**
     * hiddenValues: 이번에 숨긴 입력란의 원래 글자. TypedValueMasker에 넘겨 다시 표시된 곳을 가리는 데만 쓰고,
     * 기록·로그에는 절대 넣지 않는다.
     */
    data class Result(val nodes: List<NodeRecord>, val truncated: Boolean, val hiddenValues: List<String>)

    private data class PendingNode(val node: NodeView, val depth: Int)

    fun collect(root: NodeView?, screen: ScreenInfo, maxNodes: Int = DEFAULT_MAX_NODES): Result {
        val collectedRecords = mutableListOf<NodeRecord>()
        val hiddenValues = mutableListOf<String>()
        if (root == null) return Result(collectedRecords, truncated = false, hiddenValues = hiddenValues)

        // 재귀 대신 스택: 아주 깊은 트리(웹뷰 등)에서도 스택 넘침이 없다.
        val pendingNodes = ArrayDeque<PendingNode>()
        pendingNodes.addLast(PendingNode(root, depth = 0))
        var truncated = false

        while (pendingNodes.isNotEmpty()) {
            if (collectedRecords.size >= maxNodes) {
                truncated = true
                break
            }
            val current = pendingNodes.removeLast()
            collectedRecords += toRecord(current.node, collectedRecords.size, current.depth, screen)
            hiddenTypedValue(current.node)?.let { hiddenValues += it }

            // 스택은 나중에 넣은 것이 먼저 나오므로, 자식을 뒤에서부터 넣어 첫 자식이 먼저 나오게 한다.
            for (childIndex in current.node.childCount - 1 downTo 0) {
                current.node.child(childIndex)?.let { pendingNodes.addLast(PendingNode(it, current.depth + 1)) }
            }
        }
        return Result(collectedRecords, truncated, hiddenValues)
    }

    fun toRecord(node: NodeView, index: Int, depth: Int, screen: ScreenInfo): NodeRecord {
        val shouldHideText = shouldHideText(node)
        return NodeRecord(
            index = index,
            depth = depth,
            text = if (shouldHideText) null else node.text.trimmedOrNull(),
            description = node.description.trimmedOrNull(),
            viewId = node.viewId?.takeIf { it.isNotBlank() },
            className = node.className?.toString(),
            bounds = toRatio(node.boundsInScreen, screen),
            clickable = node.isClickable,
            editable = node.isEditable,
            textHidden = shouldHideText && !node.text.isNullOrBlank(),
        )
    }

    /**
     * 입력란·비밀번호 칸의 text에는 사용자가 친 값이 들어 있을 수 있어 글자를 버린다.
     * 단, 비어 있는 칸이 보여 주는 안내 문구(hint)는 사용자 입력이 아니고 10번 안내에 필요해서 남긴다.
     */
    private fun shouldHideText(node: NodeView): Boolean =
        (node.isPassword || node.isEditable) && !node.isShowingHintText

    /** 숨긴 입력란의 원래 글자 (TypedValueMasker용). 숨기지 않았거나 비어 있으면 null */
    fun hiddenTypedValue(node: NodeView): String? =
        if (shouldHideText(node)) node.text.trimmedOrNull() else null

    /**
     * 누른 노드 하나의 기록. toRecord에 더해, 글자나 설명이 한 글자뿐이면 가린다.
     *
     * 은행 앱의 보안 키패드는 숫자 하나하나가 입력란이 아니라 누를 수 있는 버튼이다.
     * 누를 때마다 "3", "7"을 남기면 이어 붙여 비밀번호가 된다. 한 글자 버튼은 글자를 버리고 표시만 남긴다.
     * ("확인", "다음" 같은 일반 버튼은 두 글자 이상이라 그대로 남는다.)
     */
    fun toClickedNodeRecord(node: NodeView, screen: ScreenInfo): NodeRecord {
        val record = toRecord(node, index = 0, depth = 0, screen = screen)
        val looksLikeKeypadKey = record.text.isSingleCharacter() || record.description.isSingleCharacter()
        if (!looksLikeKeypadKey) return record
        return record.copy(text = null, description = null, textHidden = true)
    }

    private fun String?.isSingleCharacter(): Boolean = this != null && this.codePointCount(0, this.length) == 1

    /** 픽셀 → 0~1 비율. 화면 밖으로 일부 나간 노드도 있어서 0~1로 자른다. 소수 4자리(약 0.2px 단위). */
    fun toRatio(pixelRect: PixelRect, screen: ScreenInfo): RatioRect = RatioRect(
        left = pixelToRatio(pixelRect.left, screen.widthPx),
        top = pixelToRatio(pixelRect.top, screen.heightPx),
        right = pixelToRatio(pixelRect.right, screen.widthPx),
        bottom = pixelToRatio(pixelRect.bottom, screen.heightPx),
    )

    private fun pixelToRatio(pixel: Int, screenLengthPx: Int): Double {
        if (screenLengthPx <= 0) return 0.0
        val ratio = (pixel.toDouble() / screenLengthPx).coerceIn(0.0, 1.0)
        return round(ratio * 10_000) / 10_000
    }

    private fun CharSequence?.trimmedOrNull(): String? = this?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
