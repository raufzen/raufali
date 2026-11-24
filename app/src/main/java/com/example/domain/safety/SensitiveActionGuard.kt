package com.example.domain.safety

import com.example.domain.model.ConfirmationRequest
import com.example.domain.model.ScreenTree
import com.example.domain.model.SensitiveCategory
import java.util.UUID

/**
 * Enforces explicit voice + on-screen user confirmation before executing sensitive actions:
 * payments/money transfers, uninstalling apps, factory reset, deleting data,
 * sending messages/SMS, or changing passwords/security settings.
 */
class SensitiveActionGuard {

    fun inspectToolAction(
        toolName: String,
        targetOrText: String,
        screenTree: ScreenTree?
    ): ConfirmationRequest? {
        val combined = buildString {
            append(toolName.lowercase())
            append(" ")
            append(targetOrText.lowercase())
            append(" ")
            append(screenTree?.packageName?.lowercase().orEmpty())
            append(" ")
            append(screenTree?.windowTitle?.lowercase().orEmpty())
        }

        // 1. Direct SMS send always requires confirmation
        if (toolName.equals("send_sms", ignoreCase = true)) {
            return buildRequest(
                category = SensitiveCategory.SEND_MESSAGE,
                summary = "Send SMS: $targetOrText",
                promptEn = "Confirm sending this SMS ($targetOrText)? Say 'Yes' or tap Confirm.",
                promptHi = "क्या आप यह SMS ($targetOrText) भेजना चाहते हैं? 'हाँ' बोलें या कन्फ़र्म दबाएं।"
            )
        }

        // 2. Payment / UPI / Bank transfer actions
        val isPaymentAction = matchesAny(
            targetOrText.lowercase(),
            "pay", "pay now", "send money", "transfer", "proceed to pay", "confirm payment",
            "upi pin", "make payment", "भुगतान", "पैसे भेजें", "पे करें", "ट्रांसफर"
        ) || (isPaymentApp(screenTree?.packageName) && matchesAny(
            targetOrText.lowercase(), "send", "confirm", "proceed", "submit", "भेजें"
        ))
        if (isPaymentAction) {
            return buildRequest(
                category = SensitiveCategory.PAYMENT,
                summary = "Payment action: $targetOrText",
                promptEn = "Sensitive payment detected ($targetOrText). Say 'Confirm' or tap Confirm to proceed.",
                promptHi = "भुगतान से जुड़ा एक्शन मिला ($targetOrText)। आगे बढ़ने के लिए 'हाँ' बोलें या कन्फ़र्म दबाएं।"
            )
        }

        // 3. Factory reset
        if (matchesAny(
                combined,
                "factory reset", "erase all data", "reset phone", "wipe data", "फ़ैक्टरी रीसेट", "सब कुछ मिटाएं"
            )
        ) {
            return buildRequest(
                category = SensitiveCategory.FACTORY_RESET,
                summary = "Factory Reset: $targetOrText",
                promptEn = "Warning: Factory reset will erase your device! Confirm only if you are sure.",
                promptHi = "चेतावनी: फ़ैक्टरी रीसेट से फोन का पूरा डेटा मिट जाएगा! केवल पक्का होने पर ही कन्फ़र्म करें।"
            )
        }

        // 4. Uninstall app
        if (matchesAny(
                targetOrText.lowercase(),
                "uninstall", "remove app", "अनइंस्टॉल", "ऐप हटाएं"
            )
        ) {
            return buildRequest(
                category = SensitiveCategory.UNINSTALL,
                summary = "Uninstall: $targetOrText",
                promptEn = "Confirm uninstalling this app ($targetOrText)? Say 'Yes' or tap Confirm.",
                promptHi = "क्या आप इस ऐप को अनइंस्टॉल ($targetOrText) करना चाहते हैं? 'हाँ' बोलें या कन्फ़र्म दबाएं।"
            )
        }

        // 5. Delete data / chats / files
        if (matchesAny(
                targetOrText.lowercase(),
                "delete", "delete all", "clear data", "permanently delete", "erase", "remove permanently",
                "डिलीट", "हटाएं", "मिटाएं"
            )
        ) {
            return buildRequest(
                category = SensitiveCategory.DELETE_DATA,
                summary = "Delete action: $targetOrText",
                promptEn = "Confirm deleting data ($targetOrText)? Say 'Yes' or tap Confirm.",
                promptHi = "क्या आप यह डेटा हटाना ($targetOrText) चाहते हैं? 'हाँ' बोलें या कन्फ़र्म दबाएं।"
            )
        }

        // 6. Password / PIN / Security changes
        if (matchesAny(
                combined,
                "change password", "reset password", "new password", "update pin", "two-step",
                "पासवर्ड बदलें", "नया पासवर्ड"
            )
        ) {
            return buildRequest(
                category = SensitiveCategory.PASSWORD_SECURITY,
                summary = "Password/Security change: $targetOrText",
                promptEn = "Security change detected ($targetOrText). Confirm to continue.",
                promptHi = "पासवर्ड या सुरक्षा बदलाव मिला ($targetOrText)। जारी रखने के लिए कन्फ़र्म करें।"
            )
        }

        // 7. Sending messages inside chat apps (WhatsApp / Telegram / Messages)
        if (toolName.equals("click", ignoreCase = true) &&
            isMessagingApp(screenTree?.packageName) &&
            matchesAny(targetOrText.lowercase(), "send", "भेजें")
        ) {
            return buildRequest(
                category = SensitiveCategory.SEND_MESSAGE,
                summary = "Tap Send in ${screenTree?.packageName?.substringAfterLast('.') ?: "chat"}",
                promptEn = "Ready to send this message. Say 'Yes' or tap Confirm to send.",
                promptHi = "मैसेज भेजने के लिए तैयार है। भेजने के लिए 'हाँ' बोलें या कन्फ़र्म दबाएं।"
            )
        }

        return null
    }

    private fun isPaymentApp(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val lower = pkg.lowercase()
        return listOf(
            "paytm", "phonepe", "nbu.paisa", "bhim", "cred", "mobikwik", "freecharge", "bank", "upi", "sbi", "hdfc", "icici"
        ).any { lower.contains(it) }
    }

    private fun isMessagingApp(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val lower = pkg.lowercase()
        return listOf("whatsapp", "telegram", "messaging", "mms", "signal", "instagram").any { lower.contains(it) }
    }

    private fun matchesAny(haystack: String, vararg needles: String): Boolean {
        return needles.any { haystack.contains(it) }
    }

    private fun buildRequest(
        category: SensitiveCategory,
        summary: String,
        promptEn: String,
        promptHi: String
    ): ConfirmationRequest {
        return ConfirmationRequest(
            id = UUID.randomUUID().toString(),
            category = category,
            actionSummary = summary,
            promptEn = promptEn,
            promptHi = promptHi
        )
    }
}
