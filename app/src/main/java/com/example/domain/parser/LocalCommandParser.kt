package com.example.domain.parser

/**
 * 100% offline rule-based intent + slot parser for Hindi (Devanagari), English, and Hinglish.
 * Fast, deterministic, zero network required.
 * Routes multi-step or open-ended instructions to [ParsedCommand.ComplexAiTask].
 */
class LocalCommandParser {

    fun parse(rawInput: String): ParsedCommand {
        val original = rawInput.trim()
        if (original.isEmpty()) return ParsedCommand.ComplexAiTask("")

        val normalized = normalizeInput(original)

        // 1. Emergency stop / cancel takes highest priority
        if (isStopCommand(normalized)) {
            return ParsedCommand.StopTask
        }

        // 2. Multi-step / complex task indicators (e.g., "send Rohit a good morning message on WhatsApp")
        if (isExplicitlyComplexTask(normalized)) {
            return ParsedCommand.ComplexAiTask(original)
        }

        // 3. Dictation / Type text ("type this message: ...", "likho ...", "लिखो ...")
        parseTypeText(original, normalized)?.let { return it }

        // 4. SMS ("send sms to Rohit saying hello", "रोहित को एसएमएस भेजो ...")
        parseSendSms(original, normalized)?.let { return it }

        // 5. Phone Call ("call Rohit", "रोहित को कॉल करो", "Rohit ko phone lagao")
        parseCallContact(original, normalized)?.let { return it }

        // 6. Alarm & Timer
        parseAlarm(normalized)?.let { return it }
        parseTimer(normalized)?.let { return it }

        // 7. Flashlight / Torch
        parseFlashlight(normalized)?.let { return it }

        // 8. Volume
        parseVolume(normalized)?.let { return it }

        // 9. Brightness
        parseBrightness(normalized)?.let { return it }

        // 10. Do Not Disturb
        parseDnd(normalized)?.let { return it }

        // 11. Global navigation actions (Back, Home, Recents, Screenshot, Lock, Notifications, Quick Settings, Power)
        parseGlobalAction(normalized)?.let { return it }

        // 12. Scroll & Swipe
        parseScrollOrSwipe(normalized)?.let { return it }

        // 13. Read screen aloud
        if (matchesAny(
                normalized,
                "read screen", "screen padho", "स्क्रीन पढ़ो", "what is on screen", "screen par kya hai", "स्क्रीन पर क्या है"
            )
        ) {
            return ParsedCommand.ReadScreen
        }

        // 14. Camera
        if (matchesAny(
                normalized,
                "open camera", "camera kholo", "कैमरा खोलो", "take photo", "photo khicho", "फोटो खींचो", "camera chalu karo"
            )
        ) {
            return ParsedCommand.OpenCamera
        }

        // 15. System settings / toggles (Wi-Fi, Bluetooth, Mobile Data, Airplane Mode, Display, Sound, Battery)
        parseSettingsPage(normalized)?.let { return it }

        // 16. Click / Long-click UI element
        parseClickTarget(original, normalized)?.let { return it }

        // 17. Open App ("open WhatsApp", "व्हाट्सएप खोलो", "YouTube chalu karo")
        parseOpenApp(original, normalized)?.let { return it }

        // 18. Everything else goes to Gemini AI Agent
        return ParsedCommand.ComplexAiTask(original)
    }

    fun isAffirmative(input: String): Boolean {
        val n = normalizeInput(input)
        return n in setOf(
            "yes", "yeah", "yep", "confirm", "confirmed", "proceed", "ok", "okay", "do it",
            "haan", "han", "ha", "ji", "ji haan", "kar do", "kardo", "theek hai", "thik hai",
            "हां", "हाँ", "जी", "जी हां", "जी हाँ", "कर दो", "ठीक है", "पुष्टि करें"
        )
    }

    fun isNegative(input: String): Boolean {
        val n = normalizeInput(input)
        return n in setOf(
            "no", "nope", "cancel", "stop", "abort", "dont", "don't", "reject",
            "nahi", "nahin", "na", "mat karo", "ruko", "band karo", "cancel karo",
            "नहीं", "ना", "मत करो", "रुको", "बंद करो", "रद्द करें"
        )
    }

    private fun isStopCommand(n: String): Boolean {
        return n in setOf(
            "stop", "cancel", "abort", "stop task", "cancel task", "stop assistant",
            "ruko", "ruk jao", "band karo", "task band karo", "cancel karo",
            "रुको", "रुक जाओ", "बंद करो", "रद्द करो", "टास्क बंद करो"
        )
    }

    private fun isExplicitlyComplexTask(n: String): Boolean {
        // Sending messages inside 3rd-party chat apps (WhatsApp, Telegram, Instagram, Gmail) needs the UI agent
        val thirdPartyMessagingApps = listOf(
            "whatsapp", "व्हाट्सएप", "वॉट्सऐप", "वाट्सएप",
            "telegram", "टेलीग्राम", "instagram", "इंस्टाग्राम",
            "gmail", "जीमेल", "email", "ईमेल"
        )
        val hasThirdPartyApp = thirdPartyMessagingApps.any { n.contains(it) }
        val hasActionBeyondOpen = listOf(
            "send", "message", "msg", "reply", "bhejo", "भेजो", "search", "khojo", "खोजो",
            "play", "chalao", "चलाओ", "book", "order", "find", "check", "summarize", "explain", "tell me"
        ).any { n.contains(it) }

        if (hasThirdPartyApp && hasActionBeyondOpen) return true

        // Multi-step connectors ("and then", "aur phir", "फिर")
        if (n.contains(" and then ") || n.contains(" aur phir ") || n.contains(" और फिर ")) {
            return true
        }

        // Complex intents like YouTube search/play, ride booking, food ordering, shopping
        val complexPrefixes = listOf(
            "book ", "order ", "summarize ", "summarise ", "search for ", "play ", "find ",
            "translate ", "explain ", "compare ", "youtube par ", "यूट्यूब पर ", "google par "
        )
        if (complexPrefixes.any { n.startsWith(it) || n.contains(" $it") }) {
            return true
        }
        return false
    }

    private fun parseTypeText(original: String, n: String): ParsedCommand.TypeText? {
        val prefixes = listOf(
            "type this message:", "type this message", "type message:", "type message",
            "dictate:", "dictate", "type:", "type ", "write ",
            "likho:", "likho ", "type karo:", "type karo ",
            "लिखो:", "लिखो ", "टाइप करो:", "टाइप करो ", "यह मैसेज लिखो:", "यह मैसेज लिखो "
        )
        for (prefix in prefixes) {
            if (n.startsWith(prefix)) {
                val content = original.drop(prefix.length).trim().trimStart(':', '-', ' ')
                if (content.isNotBlank()) return ParsedCommand.TypeText(content)
            }
        }
        // Hindi suffix pattern: "<text> लिखो" or "<text> टाइप करो"
        val suffixes = listOf(" likho", " type karo", " लिखो", " टाइप करो")
        for (suffix in suffixes) {
            if (n.endsWith(suffix) && original.length > suffix.length + 1) {
                val content = original.dropLast(suffix.length).trim()
                if (content.isNotBlank()) return ParsedCommand.TypeText(content)
            }
        }
        return null
    }

    private fun parseSendSms(original: String, n: String): ParsedCommand.SendSms? {
        // English: "send sms to Rohit saying hello there" / "text Mom I am home"
        val enRegex = Regex(
            pattern = """^(?:send\s+(?:an?\s+)?sms|send\s+text|sms|text)\s+(?:to\s+)?([^\s,]+(?:\s+[^\s,]+)?)\s+(?:saying|that|message|:)\s+(.+)$""",
            option = RegexOption.IGNORE_CASE
        )
        enRegex.find(original)?.let { m ->
            val recipient = m.groupValues[1].trim()
            val body = m.groupValues[2].trim()
            if (recipient.isNotBlank() && body.isNotBlank()) {
                return ParsedCommand.SendSms(recipient, body)
            }
        }

        // Hinglish: "Rohit ko sms bhejo ki main aa raha hoon"
        val hinglishRegex = Regex(
            pattern = """^(.+?)\s+ko\s+(?:sms|message|msg)\s+bhejo\s+(?:ki\s+)?(.+)$""",
            option = RegexOption.IGNORE_CASE
        )
        hinglishRegex.find(original)?.let { m ->
            val recipient = m.groupValues[1].trim()
            val body = m.groupValues[2].trim()
            if (recipient.isNotBlank() && body.isNotBlank()) {
                return ParsedCommand.SendSms(recipient, body)
            }
        }

        // Hindi: "रोहित को एसएमएस भेजो कि मैं पहुंच गया" / "मम्मी को मैसेज भेजो कि ..."
        val hiRegex = Regex("""^(.+?)\s+को\s+(?:एसएमएस|मैसेज|संदेश)\s+भेजो\s+(?:कि\s+)?(.+)$""")
        hiRegex.find(original)?.let { m ->
            val recipient = m.groupValues[1].trim()
            val body = m.groupValues[2].trim()
            if (recipient.isNotBlank() && body.isNotBlank()) {
                return ParsedCommand.SendSms(recipient, body)
            }
        }
        return null
    }

    private fun parseCallContact(original: String, n: String): ParsedCommand.CallContact? {
        // English: "call Rohit", "phone Mom", "dial Papa"
        val enRegex = Regex("""^(?:please\s+)?(?:call|phone|dial)\s+(.+)$""", RegexOption.IGNORE_CASE)
        enRegex.find(original)?.let { m ->
            val name = m.groupValues[1].trim()
            val lowerName = name.lowercase()
            val excluded = listOf("logs", "history", "lock", "settings", "restart", "off", "band")
            if (name.isNotBlank() && excluded.none { lowerName == it || lowerName.startsWith("$it ") }) {
                return ParsedCommand.CallContact(name)
            }
        }
        // Hinglish: "Rohit ko call karo", "Papa ko phone lagao"
        val hinglishRegex = Regex(
            """^(.+?)\s+ko\s+(?:call|phone)\s+(?:karo|lagao|milao|karna)$""",
            RegexOption.IGNORE_CASE
        )
        hinglishRegex.find(original)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.isNotBlank()) return ParsedCommand.CallContact(name)
        }
        // Hindi: "रोहित को कॉल करो", "पापा को फोन लगाओ"
        val hiRegex = Regex("""^(.+?)\s+को\s+(?:कॉल|फ़ोन|फोन)\s+(?:करो|लगाओ|मिलाओ|करें)$""")
        hiRegex.find(original)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.isNotBlank()) return ParsedCommand.CallContact(name)
        }
        return null
    }

    private fun parseAlarm(n: String): ParsedCommand.SetAlarm? {
        if (!containsAny(n, "alarm", "अलार्म")) return null
        val timeRegex = Regex("""(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm|baje|बजे)?""")
        val match = timeRegex.find(n) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val meridiem = match.groupValues[3].lowercase()
        val isEvening = meridiem == "pm" || containsAny(n, "sham", "शाम", "raat", "रात", "evening", "night")
        val isMorning = meridiem == "am" || containsAny(n, "subah", "सुबह", "morning")
        if (isEvening && hour in 1..11) hour += 12
        if (isMorning && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return ParsedCommand.SetAlarm(hour, minute)
    }

    private fun parseTimer(n: String): ParsedCommand.SetTimer? {
        if (!containsAny(n, "timer", "टाइमर")) return null
        val regex = Regex("""(\d+)\s*(second|seconds|sec|सेकंड|minute|minutes|min|मिनट|hour|hours|hr|घंटा|घंटे)""")
        val m = regex.find(n) ?: return null
        val amount = m.groupValues[1].toIntOrNull() ?: return null
        val unit = m.groupValues[2].lowercase()
        val seconds = when {
            unit.startsWith("sec") || unit.contains("सेकंड") -> amount
            unit.startsWith("hour") || unit.startsWith("hr") || unit.contains("घंट") -> amount * 3600
            else -> amount * 60
        }
        return if (seconds > 0) ParsedCommand.SetTimer(seconds) else null
    }

    private fun parseFlashlight(n: String): ParsedCommand.Flashlight? {
        if (!containsAny(n, "flashlight", "torch", "flash light", "टॉर्च", "फ्लैशलाइट", "लाइट")) return null
        val turnOff = containsAny(n, "off", "band", "बंद", "disable", "bujha", "बुझा")
        val turnOn = containsAny(n, "on", "chalu", "jalao", "जलाओ", "चालू", "enable", "start", "open")
        return when {
            turnOff -> ParsedCommand.Flashlight(false)
            turnOn -> ParsedCommand.Flashlight(true)
            else -> null
        }
    }

    private fun parseVolume(n: String): ParsedCommand.VolumeChange? {
        if (matchesAny(n, "mute", "silent", "chup", "आवाज़ बंद", "आवाज बंद", "म्यूट")) {
            return ParsedCommand.VolumeChange(VolumeAction.MUTE)
        }
        if (!containsAny(n, "volume", "sound", "awaz", "awaaz", "आवाज", "आवाज़", "वॉल्यूम")) return null

        val percentMatch = Regex("""(\d{1,3})\s*%?""").find(n)
        if (percentMatch != null && containsAny(n, "set", "to", "par", "पर", "%", "percent")) {
            val pct = percentMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 100)
            if (pct != null) return ParsedCommand.VolumeChange(VolumeAction.SET_PERCENT, pct)
        }
        return when {
            containsAny(n, "mute", "zero", "band", "बंद", "silent") ->
                ParsedCommand.VolumeChange(VolumeAction.MUTE)
            containsAny(n, "max", "full", "पूरा", "फुल") ->
                ParsedCommand.VolumeChange(VolumeAction.MAX)
            containsAny(n, "up", "increase", "higher", "louder", "badhao", "tez", "बढ़ाओ", "तेज", "ज़्यादा", "ज्यादा") ->
                ParsedCommand.VolumeChange(VolumeAction.UP)
            containsAny(n, "down", "decrease", "lower", "quieter", "kam", "dheere", "कम", "धीमा", "घटाओ") ->
                ParsedCommand.VolumeChange(VolumeAction.DOWN)
            else -> null
        }
    }

    private fun parseBrightness(n: String): ParsedCommand.BrightnessChange? {
        if (!containsAny(n, "brightness", "roshni", "रोशनी", "ब्राइटनेस", "चमक")) return null
        val percentMatch = Regex("""(\d{1,3})\s*%?""").find(n)
        if (percentMatch != null) {
            val pct = percentMatch.groupValues[1].toIntOrNull()?.coerceIn(5, 100)
            if (pct != null) return ParsedCommand.BrightnessChange(BrightnessAction.SET_PERCENT, pct)
        }
        return when {
            containsAny(n, "up", "increase", "more", "full", "max", "badhao", "tez", "बढ़ाओ", "ज़्यादा", "ज्यादा") ->
                ParsedCommand.BrightnessChange(BrightnessAction.UP)
            containsAny(n, "down", "decrease", "less", "low", "dim", "kam", "कम", "घटाओ") ->
                ParsedCommand.BrightnessChange(BrightnessAction.DOWN)
            else -> null
        }
    }

    private fun parseDnd(n: String): ParsedCommand.DoNotDisturb? {
        if (!containsAny(n, "do not disturb", "dnd", "डू नॉट डिस्टर्ब")) return null
        val off = containsAny(n, "off", "disable", "band", "बंद")
        return ParsedCommand.DoNotDisturb(!off)
    }

    private fun parseGlobalAction(n: String): ParsedCommand.GlobalAction? {
        return when {
            matchesAny(n, "back", "go back", "peeche", "peeche jao", "piche jao", "wapas", "wapas jao", "वापस", "वापस जाओ", "पीछे जाओ") ->
                ParsedCommand.GlobalAction(GlobalNavAction.BACK)

            matchesAny(n, "home", "go home", "home screen", "home jao", "होम", "होम जाओ", "होम स्क्रीन") ->
                ParsedCommand.GlobalAction(GlobalNavAction.HOME)

            matchesAny(n, "recents", "recent apps", "open recents", "app switcher", "रीसेंट ऐप्स", "हाल के ऐप्स") ->
                ParsedCommand.GlobalAction(GlobalNavAction.RECENTS)

            matchesAny(n, "notifications", "open notifications", "show notifications", "notification panel", "नोटिफिकेशन खोलो", "नोटिफिकेशन दिखाओ") ->
                ParsedCommand.GlobalAction(GlobalNavAction.NOTIFICATIONS)

            matchesAny(n, "quick settings", "control center", "क्विक सेटिंग्स") ->
                ParsedCommand.GlobalAction(GlobalNavAction.QUICK_SETTINGS)

            matchesAny(n, "power dialog", "power menu", "restart menu", "पावर मेन्यू") ->
                ParsedCommand.GlobalAction(GlobalNavAction.POWER_DIALOG)

            matchesAny(n, "lock screen", "lock phone", "phone lock karo", "screen lock karo", "फोन लॉक करो", "स्क्रीन लॉक करो") ->
                ParsedCommand.GlobalAction(GlobalNavAction.LOCK_SCREEN)

            containsAny(n, "screenshot", "screen shot", "स्क्रीनशॉट") ->
                ParsedCommand.GlobalAction(GlobalNavAction.SCREENSHOT)

            else -> null
        }
    }

    private fun parseScrollOrSwipe(n: String): ParsedCommand? {
        val isSwipe = containsAny(n, "swipe", "स्वाइप")
        val isScroll = containsAny(n, "scroll", "स्क्रॉल") ||
            matchesAny(n, "neeche jao", "upar jao", "नीचे जाओ", "ऊपर जाओ")

        if (!isSwipe && !isScroll) return null

        val dir = when {
            containsAny(n, "down", "neeche", "niche", "नीचे") -> ScrollDirection.DOWN
            containsAny(n, "up", "upar", "ऊपर") -> ScrollDirection.UP
            containsAny(n, "left", "baaye", "baye", "बाएं", "बाएँ") -> ScrollDirection.LEFT
            containsAny(n, "right", "daaye", "daye", "दाएं", "दाएँ") -> ScrollDirection.RIGHT
            else -> ScrollDirection.DOWN
        }
        return if (isSwipe) ParsedCommand.Swipe(dir) else ParsedCommand.Scroll(dir)
    }

    private fun parseSettingsPage(n: String): ParsedCommand.OpenSettings? {
        return when {
            containsAny(n, "wifi", "wi-fi", "वाईफाई", "वाई-फाई") ->
                ParsedCommand.OpenSettings(SettingsPage.WIFI)
            containsAny(n, "bluetooth", "ब्लूटूथ") ->
                ParsedCommand.OpenSettings(SettingsPage.BLUETOOTH)
            containsAny(n, "mobile data", "internet data", "मोबाइल डेटा", "डेटा चालू", "डेटा बंद") ->
                ParsedCommand.OpenSettings(SettingsPage.MOBILE_DATA)
            containsAny(n, "airplane mode", "flight mode", "एयरप्लेन मोड", "फ्लाइट मोड") ->
                ParsedCommand.OpenSettings(SettingsPage.AIRPLANE_MODE)
            containsAny(n, "display settings", "डिस्प्ले सेटिंग्स") ->
                ParsedCommand.OpenSettings(SettingsPage.DISPLAY)
            containsAny(n, "sound settings", "साउंड सेटिंग्स") ->
                ParsedCommand.OpenSettings(SettingsPage.SOUND)
            containsAny(n, "battery settings", "बैटरी सेटिंग्स") ->
                ParsedCommand.OpenSettings(SettingsPage.BATTERY)
            containsAny(n, "accessibility settings", "एक्सेसिबिलिटी सेटिंग्स") ->
                ParsedCommand.OpenSettings(SettingsPage.ACCESSIBILITY)
            matchesAny(n, "open settings", "settings", "settings kholo", "सेटिंग्स खोलो", "सेटिंग्स") ->
                ParsedCommand.OpenSettings(SettingsPage.MAIN)
            else -> null
        }
    }

    private fun parseClickTarget(original: String, n: String): ParsedCommand? {
        // Long press
        val longPressEn = Regex("""^(?:long press|long click|press and hold)\s+(.+)$""", RegexOption.IGNORE_CASE)
        longPressEn.find(original)?.let { m ->
            val target = m.groupValues[1].trim()
            if (target.isNotBlank()) return ParsedCommand.LongClickTarget(target)
        }

        // Click / Tap English
        val clickEn = Regex("""^(?:click|tap|press|select)\s+(?:on\s+)?(.+)$""", RegexOption.IGNORE_CASE)
        clickEn.find(original)?.let { m ->
            val target = m.groupValues[1].trim()
            if (target.isNotBlank()) return ParsedCommand.ClickTarget(target)
        }

        // Hinglish: "Send par click karo", "Search dabao"
        val clickHinglish = Regex(
            """^(.+?)\s+(?:par\s+click\s+karo|pe\s+click\s+karo|click\s+karo|par\s+tap\s+karo|dabao)$""",
            RegexOption.IGNORE_CASE
        )
        clickHinglish.find(original)?.let { m ->
            val target = m.groupValues[1].trim()
            if (target.isNotBlank()) return ParsedCommand.ClickTarget(target)
        }

        // Hindi: "सेंड पर क्लिक करो", "ओके दबाओ"
        val clickHindi = Regex("""^(.+?)\s+(?:पर\s+क्लिक\s+करो|क्लिक\s+करो|पर\s+टैप\s+करो|दबाओ)$""")
        clickHindi.find(original)?.let { m ->
            val target = m.groupValues[1].trim()
            if (target.isNotBlank()) return ParsedCommand.ClickTarget(target)
        }

        return null
    }

    private fun parseOpenApp(original: String, n: String): ParsedCommand.OpenApp? {
        // English: "open WhatsApp", "launch YouTube", "start Spotify"
        val enRegex = Regex("""^(?:please\s+)?(?:open|launch|start)\s+(.+)$""", RegexOption.IGNORE_CASE)
        enRegex.find(original)?.let { m ->
            val app = m.groupValues[1].trim()
            if (app.isNotBlank()) return ParsedCommand.OpenApp(app)
        }

        // Hinglish: "WhatsApp kholo", "YouTube chalu karo", "Instagram open karo"
        val hinglishRegex = Regex(
            """^(.+?)\s+(?:kholo|khol\s+do|open\s+karo|chalu\s+karo|start\s+karo|chalao)$""",
            RegexOption.IGNORE_CASE
        )
        hinglishRegex.find(original)?.let { m ->
            val app = m.groupValues[1].trim()
            if (app.isNotBlank()) return ParsedCommand.OpenApp(app)
        }

        // Hindi: "व्हाट्सएप खोलो", "यूट्यूब चालू करो"
        val hiRegex = Regex("""^(.+?)\s+(?:खोलो|खोल\s+दो|चालू\s+करो|ओपन\s+करो|खोलें)$""")
        hiRegex.find(original)?.let { m ->
            val app = m.groupValues[1].trim()
            if (app.isNotBlank()) return ParsedCommand.OpenApp(app)
        }
        return null
    }

    private fun normalizeInput(text: String): String {
        return text
            .lowercase()
            .replace(Regex("""[?!.,।]+$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun matchesAny(n: String, vararg exacts: String): Boolean {
        return exacts.any { n == it }
    }

    private fun containsAny(n: String, vararg tokens: String): Boolean {
        return tokens.any { n.contains(it) }
    }
}
