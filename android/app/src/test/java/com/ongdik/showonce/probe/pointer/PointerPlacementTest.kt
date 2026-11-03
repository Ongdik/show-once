package com.ongdik.showonce.probe.pointer

import com.ongdik.showonce.probe.snapshot.ScreenInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class PointerPlacementTest {

    private val portraitScreen = ScreenInfo(widthPx = 1080, heightPx = 2400, rotation = 0)
    private val pointerSizePx = 168 // 64dp × 2.625 (xxhdpi)

    @Test
    fun placesPointerCenterAtGivenRatio() {
        val position = PointerPlacement.windowPositionFor(0.5, 0.5, portraitScreen, pointerSizePx)
        assertEquals(PointerWindowPosition(leftPx = 540 - 84, topPx = 1200 - 84), position)
    }

    @Test
    fun keepsPointerInsideScreenNearTopLeftCorner() {
        val position = PointerPlacement.windowPositionFor(0.0, 0.0, portraitScreen, pointerSizePx)
        assertEquals(PointerWindowPosition(leftPx = 0, topPx = 0), position)
    }

    @Test
    fun keepsPointerInsideScreenNearBottomRightCorner() {
        val position = PointerPlacement.windowPositionFor(1.0, 1.0, portraitScreen, pointerSizePx)
        assertEquals(PointerWindowPosition(leftPx = 1080 - 168, topPx = 2400 - 168), position)
    }

    @Test
    fun clampsRatioOutsideZeroToOne() {
        val position = PointerPlacement.windowPositionFor(-0.3, 1.7, portraitScreen, pointerSizePx)
        assertEquals(PointerWindowPosition(leftPx = 0, topPx = 2400 - 168), position)
    }

    @Test
    fun usesSameRatioAfterRotationToLandscape() {
        val landscapeScreen = ScreenInfo(widthPx = 2400, heightPx = 1080, rotation = 1)
        val position = PointerPlacement.windowPositionFor(0.25, 0.5, landscapeScreen, pointerSizePx)
        assertEquals(PointerWindowPosition(leftPx = 600 - 84, topPx = 540 - 84), position)
    }

    @Test
    fun placesPointerAtOriginWhenPointerIsLargerThanScreen() {
        val tinyScreen = ScreenInfo(widthPx = 100, heightPx = 100, rotation = 0)
        val position = PointerPlacement.windowPositionFor(0.5, 0.5, tinyScreen, pointerSizePx)
        assertEquals(PointerWindowPosition(leftPx = 0, topPx = 0), position)
    }
}
