package com.ongdik.showonce.probe.snapshot

/**
 * 같은 화면을 반복해서 기록하지 않게 거른다.
 *
 * 화면 내용 변경 이벤트는 시계, 깜빡이는 커서, 애니메이션 때문에 자주 온다.
 * 노드의 글자·설명·id·위치가 직전 스냅샷과 같으면 같은 화면으로 보고 건너뛴다.
 * 시각은 비교에서 뺀다 (매번 다르므로).
 */
class RepeatFilter {

    private var lastScreenSignature: Int? = null

    /** 새 화면이면 true(기록할 것), 직전과 같으면 false */
    fun isNew(packageName: String?, nodes: List<NodeRecord>): Boolean {
        val screenSignature = signature(packageName, nodes)
        if (screenSignature == lastScreenSignature) return false
        lastScreenSignature = screenSignature
        return true
    }

    companion object {
        fun signature(packageName: String?, nodes: List<NodeRecord>): Int {
            val nodeSummaries = nodes.map { "${it.text}|${it.description}|${it.viewId}|${it.bounds}" }
            return (listOf(packageName) + nodeSummaries).hashCode()
        }
    }
}
