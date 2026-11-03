package com.ongdik.showonce.probe.snapshot

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 실제 기기의 AccessibilityNodeInfo를 NodeView로 감싼다. 값을 옮겨 주기만 하고 판단은 하지 않는다.
 * (입력값 제외, 비율 변환 같은 판단은 테스트되는 SnapshotBuilder에 있다.)
 */
class AccessibilityNodeView(private val node: AccessibilityNodeInfo) : NodeView {
    override val text: CharSequence? get() = node.text
    override val description: CharSequence? get() = node.contentDescription
    override val viewId: String? get() = node.viewIdResourceName
    override val className: CharSequence? get() = node.className
    override val isClickable: Boolean get() = node.isClickable
    override val isEditable: Boolean get() = node.isEditable
    override val isPassword: Boolean get() = node.isPassword

    override val boundsInScreen: PixelRect
        get() {
            val r = Rect()
            node.getBoundsInScreen(r)
            return PixelRect(r.left, r.top, r.right, r.bottom)
        }

    override val childCount: Int get() = node.childCount

    override fun child(index: Int): NodeView? = node.getChild(index)?.let { AccessibilityNodeView(it) }
}
