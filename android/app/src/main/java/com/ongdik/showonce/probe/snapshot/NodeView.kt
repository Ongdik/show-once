package com.ongdik.showonce.probe.snapshot

/**
 * 화면의 노드 하나를 읽는 데 필요한 것만 모은 인터페이스.
 *
 * 실제 기기에서는 AccessibilityNodeInfo를 감싼 어댑터가 이것을 구현하고,
 * 테스트에서는 가짜 트리가 구현한다. AccessibilityNodeInfo는 PC 단위 테스트에서 만들 수 없어서,
 * 트리 순회·입력값 제외·비율 변환을 이 인터페이스 기준으로 작성하고 테스트한다.
 */
interface NodeView {
    val text: CharSequence?
    val description: CharSequence?
    val viewId: String?
    val className: CharSequence?
    val isClickable: Boolean
    val isEditable: Boolean
    val isPassword: Boolean

    /** 입력란이 비어 있어 안내 문구(hint, 예: "검색어 입력")를 text로 보여 주는 중인지. 사용자 입력이 아니다. */
    val isShowingHintText: Boolean

    /** 지금 화면에 보이는지. 스크롤 밖 노드도 트리에는 있어서, 이게 없으면 화면 가장자리의 납작한 상자로 기록된다 (#14). */
    val isVisibleToUser: Boolean

    /** 화면 기준 픽셀 위치 */
    val boundsInScreen: PixelRect

    val childCount: Int

    /** 자식 노드. 시스템이 그 사이 사라진 노드는 null로 줄 수 있다. */
    fun child(index: Int): NodeView?
}

data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** 화면 크기 대비 비율(0~1) 위치. CLAUDE.md 7번: 좌표는 픽셀이 아니라 비율로 저장한다. */
data class RatioRect(val left: Double, val top: Double, val right: Double, val bottom: Double)
