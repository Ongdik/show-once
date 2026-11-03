package com.ongdik.showonce.probe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityStatusTest {

    private val probePackage = "com.ongdik.showonce.probe"
    private val probeServiceClass = "com.ongdik.showonce.probe.ProbeAccessibilityService"
    private val talkBackService = "com.google.android.marvin.talkback/.TalkBackService"

    private fun isProbeEnabled(enabledServices: String?) =
        AccessibilityStatus.isEnabled(enabledServices, probePackage, probeServiceClass)

    @Test
    fun returnsDisabledWhenEnabledServiceListIsNullOrEmpty() {
        assertFalse(isProbeEnabled(null))
        assertFalse(isProbeEnabled(""))
    }

    @Test
    fun returnsEnabledWhenServiceIsStoredWithFullClassName() {
        assertTrue(isProbeEnabled("$probePackage/$probeServiceClass"))
    }

    @Test
    fun returnsEnabledWhenServiceIsStoredWithShortClassName() {
        assertTrue(isProbeEnabled("$probePackage/.ProbeAccessibilityService"))
    }

    @Test
    fun findsProbeServiceAmongOtherEnabledServices() {
        assertTrue(isProbeEnabled("$talkBackService:$probePackage/$probeServiceClass"))
    }

    @Test
    fun returnsDisabledWhenOnlyOtherAppServicesAreEnabled() {
        assertFalse(isProbeEnabled(talkBackService))
    }

    @Test
    fun returnsDisabledWhenOnlyPackagePrefixMatches() {
        // 10번의 실제 앱(com.ongdik.showonce)과 헷갈리지 않아야 한다
        val realAppService = "com.ongdik.showonce/com.ongdik.showonce.ProbeAccessibilityService"
        assertFalse(isProbeEnabled(realAppService))
    }
}
