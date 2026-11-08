package com.rootdeck.floaty.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootdeck.floaty.theme.FloatyTheme

private const val GITHUB_NEW_ISSUE_URL = "https://github.com/Official-RootDeck/Floaty/issues/new"

class IssueActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FloatyTheme {
                IssueScreen(
                    onBack = { finish() },
                    onCreateIssue = { openGitHubIssueForm() }
                )
            }
        }
    }

    private fun openGitHubIssueForm() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_NEW_ISSUE_URL))
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open GitHub. Please try again.", Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueScreen(
    onBack: () -> Unit,
    onCreateIssue: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GitHub Issues", fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("issue_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF12161A))
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Found a bug or have an idea?",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Create an issue on the official Floaty GitHub repository. GitHub will open in your browser, where you can sign in and write your report.",
                color = Color(0xFFB0BEC5),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onCreateIssue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("issue_create_github_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF124D4A),
                    contentColor = Color.White
                )
            ) {
                Text("Create an Issue", fontWeight = FontWeight.Bold)
            }
        }
    }
}
