package com.example.domain.safety

/**
 * Sanitizes visible UI text before building an ephemeral ScreenTree for Gemini.
 * Skips password nodes and masks sensitive numbers (cards, OTPs, CVVs, UPI PINs).
 */
object ScreenTreeSanitizer {

    private val CARD_NUMBER_REGEX = Regex("""\b(?:\d[ -]*?){13,19}\b""")
    private val OTP_REGEX = Regex("""(?i)\b(?:otp|pin|cvv|code|passcode)\s*[:=-]?\s*\d{3,8}\b""")
    private val SENSITIVE_ID_KEYWORDS = listOf(
        "password", "passwd", "passcode", "pin_input", "otp", "cvv", "card_number", "secret", "mpin", "upi_pin"
    )

    fun isSensitiveNode(
        isPassword: Boolean,
        viewId: String,
        contentDescription: String
    ): Boolean {
        if (isPassword) return true
        val lowerId = viewId.lowercase()
        val lowerDesc = contentDescription.lowercase()
        return SENSITIVE_ID_KEYWORDS.any { kw ->
            lowerId.contains(kw) || lowerDesc.contains(kw)
        }
    }

    fun sanitizeText(raw: String): String {
        if (raw.isBlank()) return ""
        var cleaned = raw.trim().take(120)
        cleaned = CARD_NUMBER_REGEX.replace(cleaned, "[MASKED_CARD]")
        cleaned = OTP_REGEX.replace(cleaned, "[MASKED_CODE]")
        return cleaned
    }
}
