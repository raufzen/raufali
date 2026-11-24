package com.example.domain.model

/**
 * All 10 permissions guided during step-by-step onboarding and the Permissions Dashboard.
 */
enum class PermissionType(
    val titleEn: String,
    val titleHi: String,
    val whyEn: String,
    val whyHi: String,
    val isCore: Boolean
) {
    ACCESSIBILITY(
        titleEn = "Accessibility Service",
        titleHi = "एक्सेसिबिलिटी सेवा (Accessibility)",
        whyEn = "Reads buttons on your screen and taps, scrolls, or types for you by voice. Password fields are always skipped and never stored.",
        whyHi = "आपकी आवाज़ से स्क्रीन पर बटन दबाने, स्क्रॉल करने और टाइप करने के लिए ज़रूरी है। पासवर्ड फ़ील्ड हमेशा सुरक्षित और छिपे रहते हैं।",
        isCore = true
    ),
    MICROPHONE(
        titleEn = "Microphone",
        titleHi = "माइक्रोफ़ोन (Microphone)",
        whyEn = "Listens to your Hindi, English, and Hinglish voice commands and wake word offline.",
        whyHi = "आपके हिंदी, अंग्रेज़ी और हिंग्लिश वॉइस कमांड और वेक वर्ड को सुनने के लिए ज़रूरी है।",
        isCore = true
    ),
    OVERLAY(
        titleEn = "Draw Over Other Apps",
        titleHi = "अन्य ऐप्स के ऊपर दिखाएं (Overlay)",
        whyEn = "Shows the draggable floating mic button over WhatsApp, YouTube, or any app so you can talk anytime.",
        whyHi = "किसी भी ऐप के ऊपर फ्लोटिंग माइक बटन दिखाने के लिए ताकि आप कभी भी टैप करके बोल सकें।",
        isCore = true
    ),
    NOTIFICATIONS(
        titleEn = "Notifications",
        titleHi = "नोटिफिकेशन (Notifications)",
        whyEn = "Keeps the assistant foreground service alive reliably and shows live AI task progress.",
        whyHi = "असिस्टेंट सर्विस को बैकग्राउंड में चालू रखने और AI टास्क की लाइव स्थिति दिखाने के लिए।",
        isCore = true
    ),
    CONTACTS(
        titleEn = "Contacts",
        titleHi = "कॉन्टैक्ट्स (Contacts)",
        whyEn = "Finds phone numbers when you say 'Call Rohit' or 'रोहित को कॉल करो'.",
        whyHi = "जब आप 'रोहित को कॉल करो' बोलते हैं, तो उनका नंबर खोजने के लिए।",
        isCore = false
    ),
    PHONE_CALL(
        titleEn = "Phone Calls",
        titleHi = "फ़ोन कॉल (Phone Call)",
        whyEn = "Places direct voice-initiated phone calls hands-free.",
        whyHi = "हाथ लगाए बिना सीधे फ़ोन कॉल लगाने के लिए।",
        isCore = false
    ),
    SMS(
        titleEn = "Send SMS",
        titleHi = "एसएमएस भेजें (SMS)",
        whyEn = "Sends text messages by voice after asking for your explicit confirmation.",
        whyHi = "आपकी पुष्टि के बाद आवाज़ से SMS संदेश भेजने के लिए।",
        isCore = false
    ),
    BATTERY_OPTIMIZATION(
        titleEn = "Ignore Battery Optimization",
        titleHi = "बैटरी ऑप्टिमाइज़ेशन छूट",
        whyEn = "Prevents Android from killing the wake-word listener and Accessibility Service in the background.",
        whyHi = "ताकि फोन बैकग्राउंड में वेक-वर्ड और वॉइस कंट्रोल सेवा को बंद न करे।",
        isCore = false
    ),
    WRITE_SETTINGS(
        titleEn = "Modify System Settings",
        titleHi = "सिस्टम सेटिंग्स बदलें",
        whyEn = "Lets you change screen brightness directly by voice.",
        whyHi = "आवाज़ से स्क्रीन की ब्राइटनेस (रोशनी) कम या ज़्यादा करने के लिए।",
        isCore = false
    ),
    BLUETOOTH(
        titleEn = "Bluetooth Devices",
        titleHi = "ब्लूटूथ (Bluetooth)",
        whyEn = "Checks Bluetooth status and opens device pairing controls by voice.",
        whyHi = "ब्लूटूथ की स्थिति जांचने और वॉइस से कंट्रोल खोलने के लिए।",
        isCore = false
    )
}

data class PermissionStatusItem(
    val type: PermissionType,
    val isGranted: Boolean
)

data class OemAutostartGuide(
    val manufacturerKey: String,
    val brandTitle: String,
    val stepsEn: String,
    val stepsHi: String
)
