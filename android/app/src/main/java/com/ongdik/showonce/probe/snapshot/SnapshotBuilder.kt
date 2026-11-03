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

    data class Result(val nodes: List<NodeRecord>, val truncated: Boolean)

    fun collect(root: NodeView?, screen: ScreenInfo, maxNodes: Int = DEFAULT_MAX_NODES): Result {
        val out = mutableListOf<NodeRecord>()
        if (root == null) return Result(out, truncated = false)

        // 재귀 대신 스택: 아주 깊은 트리(웹뷰 등)에서도 스택 넘침이 없다.
        val stack = ArrayDeque<Pair<NodeView, Int>>()
        stack.addLast(root to 0)
        var truncated = false

        while (stack.isNotEmpty()) {
            if (out.size >= maxNodes) {
                truncated = true
                break
            }
            val (node, depth) = stack.removeLast()
            out += toRecord(node, out.size, depth, screen)

            // 스택은 나중에 넣은 것이 먼저 나오므로, 자식을 뒤에서부터 넣어 첫 자식이 먼저 나오게 한다.
            for (i in node.childCount - 1 downTo 0) {
                node.child(i)?.let { stack.addLast(it to depth + 1) }
            }
        }
        return Result(out, truncated)
    }

    fun toRecord(node: NodeView, index: Int, depth: Int, screen: ScreenInfo): NodeRecord {
        // 입력란·비밀번호 칸의 text에는 사용자가 친 값이 들어 있을 수 있다. 글자는 버리고 표시만 남긴다.
        val hide = node.isPassword || node.isEditable
        return NodeRecord(
            index = index,
            depth = depth,
            text = if (hide) null else node.text.cleaned(),
            description = node.description.cleaned(),
            viewId = node.viewId?.takeIf { it.isNotBlank() },
            className = node.className?.toString(),
            bounds = toRatio(node.boundsInScreen, screen),
            clickable = node.isClickable,
            editable = node.isEditable,
            textHidden = hide && !node.text.isNullOrBlank(),
        )
    }

    /** 픽셀 → 0~1 비율. 화면 밖으로 일부 나간 노드도 있어서 0~1로 자른다. 소수 4자리(약 0.2px 단위). */
    fun toRatio(rect: PixelRect, screen: ScreenInfo): RatioRect {
        fun r(v: Int, size: Int): Double {
            if (size <= 0) return 0.0
            val x = (v.toDouble() / size).coerceIn(0.0, 1.0)
            return round(x * 10_000) / 10_000
        }
        return RatioRect(
            left = r(rect.left, screen.widthPx),
            top = r(rect.top, screen.heightPx),
            right = r(rect.right, screen.widthPx),
            bottom = r(rect.bottom, screen.heightPx),
        )
    }

    private fun CharSequence?.cleaned(): String? = this?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
