package com.ongdik.showonce.probe.pointer

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import com.ongdik.showonce.probe.snapshot.ScreenInfo

/**
 * 다른 앱 위에 포인터 창 하나를 띄우고, 옮기고, 내린다.
 *
 * 창 종류는 TYPE_ACCESSIBILITY_OVERLAY: 접근성 서비스만 띄울 수 있는 창이라 추가 권한이 없고,
 * 시스템이 믿을 수 있는 창으로 취급한다 (#20 결정).
 *
 * 터치가 아래 앱으로 통과하는 이유: 시스템은 터치가 오면 위층 창부터 받을지 묻는데,
 * FLAG_NOT_TOUCHABLE 창은 건너뛴다. 그래서 이 창은 보이기만 하고 터치는 아래 앱이 받는다.
 * FLAG_NOT_FOCUSABLE은 키보드 입력을 가로채지 않게 한다.
 *
 * WindowManager는 반드시 접근성 서비스 자신의 것(service.getSystemService)을 써야 이 창 종류를 띄울 수 있다.
 * 모든 함수는 화면 스레드에서 부른다. (10번에서 다른 스레드의 매칭 결과로 부를 때는 handler.post로 넘긴다)
 */
class PointerOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
) {

    private var pointerView: PointerView? = null
    /** 지금(또는 마지막으로) 띄운 고리 중심의 비율 위치 */
    var centerRatioX = 0.5
        private set
    var centerRatioY = 0.5
        private set

    val isShowing: Boolean get() = pointerView != null

    private val pointerSizePx: Int
        get() = (PointerView.POINTER_SIZE_DP * context.resources.displayMetrics.density).toInt()

    fun show(centerRatioX: Double, centerRatioY: Double, screen: ScreenInfo) {
        this.centerRatioX = centerRatioX
        this.centerRatioY = centerRatioY
        val layoutParams = layoutParamsFor(screen)
        val existingView = pointerView
        if (existingView == null) {
            val newView = PointerView(context)
            windowManager.addView(newView, layoutParams)
            pointerView = newView
        } else {
            windowManager.updateViewLayout(existingView, layoutParams)
        }
    }

    /** 회전 등으로 화면 크기가 바뀌면 같은 비율 위치로 다시 놓는다 */
    fun reposition(screen: ScreenInfo) {
        val existingView = pointerView ?: return
        windowManager.updateViewLayout(existingView, layoutParamsFor(screen))
    }

    fun hide() {
        val existingView = pointerView ?: return
        windowManager.removeView(existingView)
        pointerView = null
    }

    private fun layoutParamsFor(screen: ScreenInfo): WindowManager.LayoutParams {
        val position = PointerPlacement.windowPositionFor(centerRatioX, centerRatioY, screen, pointerSizePx)
        return WindowManager.LayoutParams(
            pointerSizePx,
            pointerSizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                // 상태 표시줄·내비게이션 바까지 포함한 화면 전체 좌표로 놓는다 (노드 위치와 같은 기준)
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.leftPx
            y = position.topPx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // 화면 위쪽 카메라 구멍(노치) 영역에도 놓을 수 있게
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }
}
