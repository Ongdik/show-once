package com.ongdik.showonce.probe.pointer

import com.ongdik.showonce.probe.snapshot.ScreenInfo
import kotlin.math.roundToInt

/** 포인터 창의 왼쪽 위 픽셀 위치 (WindowManager.LayoutParams의 x, y) */
data class PointerWindowPosition(val leftPx: Int, val topPx: Int)

/**
 * 0~1 비율 위치를 포인터 창의 픽셀 위치로 바꾼다. 안드로이드 API를 쓰지 않는 순수 코드.
 *
 * 기록과 같은 기준(화면 전체 크기 대비 비율)으로 받아서, 그 점이 고리의 **중심**이 되게 놓는다.
 * 10번에서 노드 위치(비율)를 그대로 넘기면 그 버튼 한가운데를 짚게 하기 위해서다.
 * 화면 가장자리 가까이를 짚어도 고리가 화면 밖으로 나가지 않게 자른다.
 */
object PointerPlacement {

    fun windowPositionFor(
        centerRatioX: Double,
        centerRatioY: Double,
        screen: ScreenInfo,
        pointerSizePx: Int,
    ): PointerWindowPosition {
        val centerXPx = (centerRatioX.coerceIn(0.0, 1.0) * screen.widthPx).roundToInt()
        val centerYPx = (centerRatioY.coerceIn(0.0, 1.0) * screen.heightPx).roundToInt()
        val halfSizePx = pointerSizePx / 2
        val maxLeftPx = (screen.widthPx - pointerSizePx).coerceAtLeast(0)
        val maxTopPx = (screen.heightPx - pointerSizePx).coerceAtLeast(0)
        return PointerWindowPosition(
            leftPx = (centerXPx - halfSizePx).coerceIn(0, maxLeftPx),
            topPx = (centerYPx - halfSizePx).coerceIn(0, maxTopPx),
        )
    }
}
