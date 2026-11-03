package com.ongdik.showonce.probe.pointer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * "다음에 누를 곳"을 짚는 고리.
 *
 * - 가운데가 비어 있다: 바로 아래 버튼 글자를 가리지 않게.
 * - 진한 주황 고리 + 흰 테두리: 밝은 화면과 어두운 화면 모두에서 보이게.
 * 부모님 눈에 잘 띄는지는 9번 사용자 테스트에서 확인한다.
 */
class PointerView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = OUTLINE_STROKE_DP * density
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = RING_COLOR
        strokeWidth = RING_STROKE_DP * density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        // 흰 테두리를 고리보다 넓게 먼저 그리고, 그 위에 주황 고리를 그린다.
        val ringRadius = (minOf(width, height) / 2f) - (outlinePaint.strokeWidth / 2f)
        canvas.drawCircle(centerX, centerY, ringRadius, outlinePaint)
        canvas.drawCircle(centerX, centerY, ringRadius, ringPaint)
    }

    companion object {
        const val POINTER_SIZE_DP = 64
        private const val RING_STROKE_DP = 6f
        private const val OUTLINE_STROKE_DP = 10f
        private val RING_COLOR = Color.rgb(0xE6, 0x51, 0x00) // 진한 주황
    }
}
