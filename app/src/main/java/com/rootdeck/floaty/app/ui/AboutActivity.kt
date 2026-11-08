package com.rootdeck.floaty.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.rootdeck.floaty.theme.FloatyTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AboutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FloatyTheme {
                AboutScreen(onBack = { finish() })
            }
        }
    }
}

data class AboutParsedData(
    val displayText: String,
    val contactDeveloperUrl: String?,
    val supportUsUrl: String?
)

fun parseAboutContent(rawText: String): AboutParsedData {
    val contactRegex = Regex("""\{contact_developer=\{?([^}]+)\}?\}""", RegexOption.IGNORE_CASE)
    val supportRegex = Regex("""\{support_us=\{?([^}]+)\}?\}""", RegexOption.IGNORE_CASE)

    var contactUrl: String? = null
    var supportUrl: String? = null

    contactRegex.find(rawText)?.let { match ->
        contactUrl = match.groupValues[1].trim()
    }

    supportRegex.find(rawText)?.let { match ->
        supportUrl = match.groupValues[1].trim()
    }

    // Clean up the tags and leftover comma from the text
    var cleanText = rawText
        .replace(contactRegex, "")
        .replace(supportRegex, "")
        .replace(Regex(""",\s*\n"""), "\n")
        .trimEnd()

    return AboutParsedData(cleanText, contactUrl, supportUrl)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val parsedData = remember {
        val raw = try {
            context.assets.open("about.txt").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            "FLOATY v1.4.06\nHigh-Performance Floating Capture Engine"
        }
        parseAboutContent(raw)
    }

    fun openUrl(url: String) {
        try {
            val formatted = if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("mailto:")) {
                "https://$url"
            } else {
                url
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formatted))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open link: $url", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Floaty", fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("about_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E242B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF12161A))
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = parsedData.displayText,
                    color = Color(0xFFE0E0E0),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 19.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("about_text_content")
                )

                if (parsedData.contactDeveloperUrl != null || parsedData.supportUsUrl != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = Color(0xFF333D47), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (parsedData.contactDeveloperUrl != null) {
                            Button(
                                onClick = { openUrl(parsedData.contactDeveloperUrl) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("btn_contact_developer"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF124D4A),
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    "Contact Developer",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (parsedData.supportUsUrl != null) {
                            Button(
                                onClick = { openUrl(parsedData.supportUsUrl) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("btn_support_us"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2B333C),
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    "Support Us",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
