package com.example.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCommandParserTest {

    private val parser = LocalCommandParser()

    @Test
    fun `parses open app in English, Hinglish, and Hindi`() {
        assertEquals(ParsedCommand.OpenApp("WhatsApp"), parser.parse("open WhatsApp"))
        assertEquals(ParsedCommand.OpenApp("WhatsApp"), parser.parse("WhatsApp kholo"))
        assertEquals(ParsedCommand.OpenApp("व्हाट्सएप"), parser.parse("व्हाट्सएप खोलो"))
        assertEquals(ParsedCommand.OpenApp("YouTube"), parser.parse("YouTube chalu karo"))
    }

    @Test
    fun `parses global navigation and screenshot actions`() {
        assertEquals(ParsedCommand.GlobalAction(GlobalNavAction.BACK), parser.parse("peeche jao"))
        assertEquals(ParsedCommand.GlobalAction(GlobalNavAction.BACK), parser.parse("वापस जाओ"))
        assertEquals(ParsedCommand.GlobalAction(GlobalNavAction.HOME), parser.parse("go home"))
        assertEquals(ParsedCommand.GlobalAction(GlobalNavAction.SCREENSHOT), parser.parse("स्क्रीनशॉट लो"))
        assertEquals(ParsedCommand.GlobalAction(GlobalNavAction.LOCK_SCREEN), parser.parse("phone lock karo"))
        assertEquals(ParsedCommand.GlobalAction(GlobalNavAction.NOTIFICATIONS), parser.parse("open notifications"))
    }

    @Test
    fun `parses scroll and swipe directions`() {
        assertEquals(ParsedCommand.Scroll(ScrollDirection.DOWN), parser.parse("neeche scroll karo"))
        assertEquals(ParsedCommand.Scroll(ScrollDirection.UP), parser.parse("ऊपर स्क्रॉल करो"))
        assertEquals(ParsedCommand.Swipe(ScrollDirection.LEFT), parser.parse("swipe left"))
    }

    @Test
    fun `parses volume, brightness, and flashlight controls`() {
        assertEquals(ParsedCommand.VolumeChange(VolumeAction.UP), parser.parse("आवाज़ बढ़ाओ"))
        assertEquals(ParsedCommand.VolumeChange(VolumeAction.DOWN), parser.parse("volume down"))
        assertEquals(ParsedCommand.VolumeChange(VolumeAction.MUTE), parser.parse("mute"))
        assertEquals(ParsedCommand.VolumeChange(VolumeAction.SET_PERCENT, 75), parser.parse("set volume to 75%"))
        assertEquals(ParsedCommand.BrightnessChange(BrightnessAction.SET_PERCENT, 60), parser.parse("brightness 60%"))
        assertEquals(ParsedCommand.Flashlight(true), parser.parse("टॉर्च जलाओ"))
        assertEquals(ParsedCommand.Flashlight(false), parser.parse("flashlight off"))
    }

    @Test
    fun `parses dictation and typing modes`() {
        assertEquals(
            ParsedCommand.TypeText("I will reach in 10 minutes"),
            parser.parse("type this message: I will reach in 10 minutes")
        )
        assertEquals(
            ParsedCommand.TypeText("मैं आ रहा हूँ"),
            parser.parse("लिखो मैं आ रहा हूँ")
        )
        assertEquals(
            ParsedCommand.TypeText("hello friend"),
            parser.parse("hello friend likho")
        )
    }

    @Test
    fun `parses calls, sms, alarms, and timers`() {
        assertEquals(ParsedCommand.CallContact("Rohit"), parser.parse("call Rohit"))
        assertEquals(ParsedCommand.CallContact("रोहित"), parser.parse("रोहित को कॉल करो"))
        assertEquals(
            ParsedCommand.SendSms("Mom", "I reached safely"),
            parser.parse("send sms to Mom saying I reached safely")
        )
        assertEquals(
            ParsedCommand.SendSms("रोहित", "मैं आ रहा हूँ"),
            parser.parse("रोहित को एसएमएस भेजो कि मैं आ रहा हूँ")
        )
        assertEquals(ParsedCommand.SetAlarm(6, 30), parser.parse("set alarm for 6:30 am"))
        assertEquals(ParsedCommand.SetTimer(300), parser.parse("5 मिनट का टाइमर लगाओ"))
    }

    @Test
    fun `parses stop command and confirmation responses`() {
        assertEquals(ParsedCommand.StopTask, parser.parse("stop"))
        assertEquals(ParsedCommand.StopTask, parser.parse("रुको"))
        assertTrue(parser.isAffirmative("हाँ"))
        assertTrue(parser.isAffirmative("yes"))
        assertTrue(parser.isNegative("नहीं"))
        assertFalse(parser.isAffirmative("cancel"))
    }

    @Test
    fun `routes complex multi-step commands to Gemini AI agent`() {
        val complex1 = parser.parse("send Rohit a good morning message on WhatsApp")
        assertTrue(complex1 is ParsedCommand.ComplexAiTask)

        val complex2 = parser.parse("व्हाट्सएप पर रोहित को गुड मॉर्निंग मैसेज भेजो")
        assertTrue(complex2 is ParsedCommand.ComplexAiTask)

        val complex3 = parser.parse("open YouTube and then search for lo-fi beats")
        assertTrue(complex3 is ParsedCommand.ComplexAiTask)
    }
}
