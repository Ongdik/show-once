package com.ongdik.showonce.probe.snapshot

/**
 * 노드 하나의 기록. 여기 있는 필드만 남기고, 그 밖의 속성은 기록하지 않는다 (이슈 #14).
 *
 * 입력란이나 비밀번호 칸은 사용자가 친 값이 text에 들어 있을 수 있어서 글자를 버리고
 * textHidden=true로만 표시한다. 칸의 위치와 설명은 남긴다 (10번에서 "이 칸을 누르세요" 안내용).
 */
data class NodeRecord(
    val index: Int,
    val depth: Int,
    val text: String?,
    val description: String?,
    val viewId: String?,
    val className: String?,
    val bounds: RatioRect,
    val clickable: Boolean,
    val editable: Boolean,
    val textHidden: Boolean,
)

/** 화면 크기와 회전 방향. 비율 좌표를 다시 픽셀로 돌리거나 가로·세로를 구분할 때 필요하다. */
data class ScreenInfo(val widthPx: Int, val heightPx: Int, val rotation: Int)

/**
 * 한 시점의 화면 전체 기록.
 * - wallTimeMs: 시스템 시각(사람이 읽는 시각)
 * - eventTimeMs: 이벤트 시각(AccessibilityEvent.eventTime, 기기 부팅 후 경과 ms)
 */
data class Snapshot(
    val reason: String,
    val packageName: String?,
    val screen: ScreenInfo,
    val wallTimeMs: Long,
    val eventTimeMs: Long,
    val nodes: List<NodeRecord>,
    val truncated: Boolean,
)

/** 누른 노드 하나의 기록 */
data class ClickRecord(
    val packageName: String?,
    val screen: ScreenInfo,
    val wallTimeMs: Long,
    val eventTimeMs: Long,
    val node: NodeRecord?,
)
