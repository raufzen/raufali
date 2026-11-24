package com.example.domain.safety

import com.example.domain.model.ScreenTree
import com.example.domain.model.SensitiveCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveActionGuardTest {

    private val guard = SensitiveActionGuard()

    @Test
    fun `detects payment, uninstall, delete, reset, and sms actions`() {
        val payment = guard.inspectToolAction("click", "Pay Now ₹500", ScreenTree("net.one97.paytm"))
        assertNotNull(payment)
        assertEquals(SensitiveCategory.PAYMENT, payment?.category)

        val uninstall = guard.inspectToolAction("click", "Uninstall", ScreenTree("com.android.packageinstaller"))
        assertEquals(SensitiveCategory.UNINSTALL, uninstall?.category)

        val reset = guard.inspectToolAction("click", "Erase all data (factory reset)", ScreenTree("com.android.settings"))
        assertEquals(SensitiveCategory.FACTORY_RESET, reset?.category)

        val delete = guard.inspectToolAction("click", "Delete permanently", ScreenTree("com.google.android.apps.photos"))
        assertEquals(SensitiveCategory.DELETE_DATA, delete?.category)

        val sms = guard.inspectToolAction("send_sms", "Rohit: Hello", null)
        assertEquals(SensitiveCategory.SEND_MESSAGE, sms?.category)
    }

    @Test
    fun `allows harmless navigation without confirmation`() {
        val benign = guard.inspectToolAction("click", "Search", ScreenTree("com.google.android.youtube"))
        assertNull(benign)
    }

    @Test
    fun `sanitizer skips password fields and masks cards and OTPs`() {
        assertTrue(ScreenTreeSanitizer.isSensitiveNode(isPassword = true, viewId = "et_input", contentDescription = ""))
        assertTrue(ScreenTreeSanitizer.isSensitiveNode(isPassword = false, viewId = "upi_pin_box", contentDescription = ""))
        assertEquals("Your [MASKED_CODE] is ready", ScreenTreeSanitizer.sanitizeText("Your OTP: 482910 is ready"))
        assertEquals("Card [MASKED_CARD]", ScreenTreeSanitizer.sanitizeText("Card 4532 1234 5678 9012"))
    }
}
