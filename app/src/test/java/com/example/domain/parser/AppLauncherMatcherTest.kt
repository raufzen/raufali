package com.example.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLauncherMatcherTest {

    private val sampleApps = listOf(
        InstalledAppInfo("com.whatsapp", "WhatsApp"),
        InstalledAppInfo("com.google.android.youtube", "YouTube"),
        InstalledAppInfo("com.instagram.android", "Instagram"),
        InstalledAppInfo("com.android.chrome", "Google Chrome"),
        InstalledAppInfo("com.google.android.apps.nbu.paisa.user", "Google Pay"),
        InstalledAppInfo("net.one97.paytm", "Paytm"),
        InstalledAppInfo("com.android.settings", "Settings"),
        InstalledAppInfo("com.google.android.apps.maps", "Google Maps")
    )

    private val matcher = AppLauncherMatcher { sampleApps }

    @Test
    fun `matches Hindi Devanagari aliases to exact English app labels`() {
        assertEquals("com.whatsapp", matcher.findBestMatch("व्हाट्सएप")?.packageName)
        assertEquals("com.whatsapp", matcher.findBestMatch("वॉट्सऐप")?.packageName)
        assertEquals("com.whatsapp", matcher.findBestMatch("वाट्सएप")?.packageName)
        assertEquals("com.google.android.youtube", matcher.findBestMatch("यूट्यूब")?.packageName)
        assertEquals("com.instagram.android", matcher.findBestMatch("इंस्टाग्राम")?.packageName)
        assertEquals("com.android.settings", matcher.findBestMatch("सेटिंग्स")?.packageName)
        assertEquals("net.one97.paytm", matcher.findBestMatch("पेटीएम")?.packageName)
        assertEquals("com.google.android.apps.nbu.paisa.user", matcher.findBestMatch("गूगल पे")?.packageName)
    }

    @Test
    fun `matches Hinglish shorthand and fuzzy typos`() {
        assertEquals("com.whatsapp", matcher.findBestMatch("watsapp")?.packageName)
        assertEquals("com.whatsapp", matcher.findBestMatch("whatsap app")?.packageName)
        assertEquals("com.google.android.youtube", matcher.findBestMatch("utube")?.packageName)
        assertEquals("com.instagram.android", matcher.findBestMatch("insta")?.packageName)
        assertEquals("com.android.chrome", matcher.findBestMatch("chrome")?.packageName)
        assertEquals("com.google.android.apps.maps", matcher.findBestMatch("maps")?.packageName)
    }

    @Test
    fun `returns null when no installed app reasonably matches`() {
        assertNull(matcher.findBestMatch("xyzzy unknown app 999"))
        assertNull(matcher.findBestMatch(""))
    }
}
