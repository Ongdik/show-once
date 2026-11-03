package com.ongdik.showonce.probe.snapshot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAllowListTest {

    private val probeAllowList = AppAllowList.forProbe()

    @Test
    fun allowsContentOfListedApps() {
        assertTrue(probeAllowList.isContentAllowed("com.android.settings"))
        assertTrue(probeAllowList.isContentAllowed("io.metamask"))
    }

    @Test
    fun doesNotListProbeAppItself() {
        assertFalse(probeAllowList.isContentAllowed("com.ongdik.showonce.probe"))
    }

    @Test
    fun deniesContentOfUnlistedAppsByDefault() {
        assertFalse(probeAllowList.isContentAllowed("com.kakao.talk"))
        assertFalse(probeAllowList.isContentAllowed("com.android.deskclock"))
    }

    @Test
    fun deniesContentWhenPackageNameIsMissing() {
        assertFalse(probeAllowList.isContentAllowed(null))
    }

    @Test
    fun deniesPackageThatOnlySharesPrefixWithListedApp() {
        assertFalse(probeAllowList.isContentAllowed("com.android.settings.fake"))
        assertFalse(probeAllowList.isContentAllowed("io.metamask.clone"))
    }
}
