package com.ongdik.showonce.probe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityStatusTest {

    private val pkg = "com.ongdik.showonce.probe"
    private val cls = "com.ongdik.showonce.probe.ProbeAccessibilityService"

    @Test
    fun 목록이_비었으면_꺼짐() {
        assertFalse(AccessibilityStatus.isEnabled(null, pkg, cls))
        assertFalse(AccessibilityStatus.isEnabled("", pkg, cls))
    }

    @Test
    fun 클래스_전체_이름으로_저장되면_켜짐() {
        assertTrue(AccessibilityStatus.isEnabled("$pkg/$cls", pkg, cls))
    }

    @Test
    fun 클래스를_줄여서_저장해도_켜짐() {
        assertTrue(AccessibilityStatus.isEnabled("$pkg/.ProbeAccessibilityService", pkg, cls))
    }

    @Test
    fun 다른_서비스와_함께_있어도_찾는다() {
        val list = "com.google.android.marvin.talkback/.TalkBackService:$pkg/$cls"
        assertTrue(AccessibilityStatus.isEnabled(list, pkg, cls))
    }

    @Test
    fun 다른_앱의_서비스만_있으면_꺼짐() {
        val list = "com.google.android.marvin.talkback/.TalkBackService"
        assertFalse(AccessibilityStatus.isEnabled(list, pkg, cls))
    }

    @Test
    fun 패키지_이름이_앞부분만_같으면_꺼짐() {
        // 10번의 실제 앱(com.ongdik.showonce)과 헷갈리지 않아야 한다
        val list = "com.ongdik.showonce/com.ongdik.showonce.ProbeAccessibilityService"
        assertFalse(AccessibilityStatus.isEnabled(list, pkg, cls))
    }
}
