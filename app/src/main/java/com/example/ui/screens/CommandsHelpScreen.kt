package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppLanguage

private data class CommandExampleCategory(
    val titleEn: String,
    val titleHi: String,
    val isOffline: Boolean,
    val examples: List<Triple<String, String, String>> // (English, Hinglish, Hindi)
)

@Composable
fun CommandsHelpScreen(
    language: AppLanguage,
    onTryCommand: (String) -> Unit
) {
    val categories = listOf(
        CommandExampleCategory(
            titleEn = "Open Apps (Fuzzy Hindi/English Matching)",
            titleHi = "ऐप्स खोलें (ऑफलाइन)",
            isOffline = true,
            examples = listOf(
                Triple("Open WhatsApp", "WhatsApp kholo", "व्हाट्सएप खोलो"),
                Triple("Open YouTube", "YouTube chalu karo", "यूट्यूब खोलो"),
                Triple("Open Camera", "Camera kholo", "कैमरा खोलो")
            )
        ),
        CommandExampleCategory(
            titleEn = "Navigation & Gestures",
            titleHi = "नेविगेशन और स्क्रॉल (ऑफलाइन)",
            isOffline = true,
            examples = listOf(
                Triple("Go back", "Peeche jao", "वापस जाओ"),
                Triple("Go home", "Home screen jao", "होम जाओ"),
                Triple("Scroll down", "Neeche scroll karo", "नीचे स्क्रॉल करो"),
                Triple("Take screenshot", "Screenshot lo", "स्क्रीनशॉट लो"),
                Triple("Click Send", "Send par click karo", "सेंड पर क्लिक करो")
            )
        ),
        CommandExampleCategory(
            titleEn = "Dictation & Typing",
            titleHi = "बोलकर टाइप करें (ऑफलाइन)",
            isOffline = true,
            examples = listOf(
                Triple("Type this message: Reaching in 10 mins", "Likho main 10 minute mein aa raha hoon", "लिखो मैं अभी आ रहा हूँ")
            )
        ),
        CommandExampleCategory(
            titleEn = "System Controls & Toggles",
            titleHi = "सिस्टम कंट्रोल (ऑफलाइन)",
            isOffline = true,
            examples = listOf(
                Triple("Volume up / Volume 70%", "Awaz badhao", "आवाज़ बढ़ाओ"),
                Triple("Flashlight on / off", "Torch jalao", "टॉर्च जलाओ"),
                Triple("Brightness 80%", "Brightness badhao", "रोशनी बढ़ाओ"),
                Triple("Open Wi-Fi settings", "Bluetooth kholo", "एयरप्लेन मोड")
            )
        ),
        CommandExampleCategory(
            titleEn = "Calls, SMS, Alarms & Timers",
            titleHi = "कॉल, SMS, अलार्म और टाइमर (ऑफलाइन)",
            isOffline = true,
            examples = listOf(
                Triple("Call Rohit", "Rohit ko call karo", "रोहित को कॉल करो"),
                Triple("Send SMS to Mom saying I reached", "Mom ko sms bhejo ki main pahunch gaya", "मम्मी को एसएमएस भेजो कि मैं पहुंच गया"),
                Triple("Set alarm for 7:30 am", "Subah 6 baje ka alarm lagao", "सुबह 6 बजे का अलार्म लगाओ"),
                Triple("Set timer for 5 minutes", "5 minute ka timer lagao", "5 मिनट का टाइमर लगाओ")
            )
        ),
        CommandExampleCategory(
            titleEn = "Complex Multi-Step Tasks (Gemini AI Agent)",
            titleHi = "जटिल कार्य (जेमिनी AI एजेंट)",
            isOffline = false,
            examples = listOf(
                Triple("Send Rohit a good morning message on WhatsApp", "WhatsApp par Rohit ko good morning message bhejo", "व्हाट्सएप पर रोहित को गुड मॉर्निंग मैसेज भेजो"),
                Triple("Summarize what is on my screen", "Screen par jo hai uska summary batao", "स्क्रीन पर क्या लिखा है संक्षेप में बताओ"),
                Triple("Search for Arijit Singh songs on YouTube", "YouTube par Arijit Singh songs search karo", "यूट्यूब पर अरिजीत सिंह के गाने खोजो")
            )
        )
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = if (language == AppLanguage.HINDI) "समर्थित वॉइस कमांड" else "Supported Voice Commands",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (language == AppLanguage.HINDI) {
                        "किसी भी उदाहरण के सामने ▶ दबाकर तुरंत आज़माएं।"
                    } else {
                        "Tap ▶ on any example to test it immediately in English, Hinglish, or Hindi."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(categories) { category ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (category.isOffline) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (category.isOffline) Icons.Rounded.Bolt else Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (category.isOffline) "OFFLINE" else "GEMINI AI",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Text(
                                text = if (language == AppLanguage.HINDI) category.titleHi else category.titleEn,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        category.examples.forEach { (en, hinglish, hi) ->
                            val primarySample = when (language) {
                                AppLanguage.ENGLISH -> en
                                AppLanguage.HINGLISH -> hinglish
                                AppLanguage.HINDI -> hi
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = primarySample,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "$en  •  $hi",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    FilledTonalIconButton(onClick = { onTryCommand(primarySample) }) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Try $primarySample")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
