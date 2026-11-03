package com.ongdik.showonce.probe.snapshot

/** 테스트용 가짜 노드. 실제 AccessibilityNodeInfo 대신 트리를 직접 만든다. */
data class FakeNode(
    override val text: CharSequence? = null,
    override val description: CharSequence? = null,
    override val viewId: String? = null,
    override val className: CharSequence? = "android.view.View",
    override val isClickable: Boolean = false,
    override val isEditable: Boolean = false,
    override val isPassword: Boolean = false,
    override val isShowingHintText: Boolean = false,
    override val isVisibleToUser: Boolean = true,
    override val boundsInScreen: PixelRect = PixelRect(0, 0, 0, 0),
    val children: List<FakeNode?> = emptyList(),
) : NodeView {
    override val childCount: Int get() = children.size
    override fun child(index: Int): NodeView? = children[index]
}
