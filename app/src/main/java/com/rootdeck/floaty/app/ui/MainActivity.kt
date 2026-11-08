package com.rootdeck.floaty.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.rootdeck.floaty.theme.FloatyTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.rootdeck.floaty.R
import com.rootdeck.floaty.app.capture.ScreenshotAccessibilityService
import com.rootdeck.floaty.app.prefs.SettingsRepo
import com.rootdeck.floaty.app.service.FloatyService
import com.rootdeck.floaty.app.storage.FolderStore

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepo: SettingsRepo
    private var refreshTrigger = mutableIntStateOf(0)
    private var openedAccessibilitySetting = false
    private var restrictedDialogShown = false
    private lateinit var updateManager: com.rootdeck.floaty.app.system.update.GitHubUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepo = SettingsRepo(this)
        updateManager = com.rootdeck.floaty.app.system.update.GitHubUpdateManager(this)

        // Ensure default media directories exist immediately
        FolderStore.ensureDefaultFoldersCreated(this)

        FirstRunFlow(this).maybeRun {
            if (canDrawOverlay(this)) {
                FloatyService.startFloaty(this)
            }
        }

        setContent {
            FloatyTheme {
                MainScreen(
                    onOpenAbout = { startActivity(Intent(this, AboutActivity::class.java)) },
                    onOpenIssue = { startActivity(Intent(this, IssueActivity::class.java)) },
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onShowVersion = { checkForUpdates() },
                    onOpenAccessibility = {
                        openedAccessibilitySetting = true
                        ScreenshotAccessibilityService.openAccessibilitySettings(this)
                    },
                    refreshKey = refreshTrigger.intValue
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTrigger.intValue++

        if (::updateManager.isInitialized) {
            updateManager.resumePendingInstall()
        }


        if (canDrawOverlay(this)) {
            FloatyService.startFloaty(this)
        }

        if (openedAccessibilitySetting &&
            ScreenshotAccessibilityService.instance == null &&
            !restrictedDialogShown &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            restrictedDialogShown = true
            informAboutRestrictedSettings()
        }
    }

    private fun checkForUpdates() {
        Toast.makeText(this, "Checking for updates…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val update = updateManager.checkForUpdate()
                if (update == null) {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("Floaty is up to date")
                        .setMessage("You are running the latest available release.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("Update available: ${update.version}")
                        .setMessage("Download ${update.assetName} and install it?")
                        .setNegativeButton("Later", null)
                        .setPositiveButton("Download and install") { _, _ ->
                            lifecycleScope.launch {
                                try {
                                    updateManager.downloadAndInstall(update)
                                } catch (error: Exception) {
                                    showUpdateError(error)
                                }
                            }
                        }
                        .show()
                }
            } catch (error: Exception) {
                showUpdateError(error)
            }
        }
    }

    private fun showUpdateError(error: Exception) {
        val message = error.message?.takeIf { it.isNotBlank() } ?: "An unexpected error occurred."
        android.app.AlertDialog.Builder(this)
            .setTitle("Update check failed")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun informAboutRestrictedSettings() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.restricted_settings_title))
        builder.setMessage(getString(R.string.restricted_settings_text))
        val imageView = ImageView(this).apply {
            setImageResource(R.drawable.restricted_settings)
            setPadding(32, 16, 32, 16)
        }
        builder.setView(imageView)
        builder.setNeutralButton(getString(R.string.restricted_settings_open_settings)) { _, _ ->
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            }
        }
        builder.setPositiveButton(getString(R.string.restricted_settings_open_accessibility)) { _, _ ->
            ScreenshotAccessibilityService.openAccessibilitySettings(this)
        }
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (settingsRepo.enableShortcuts && event.isCtrlPressed && event.isAltPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_R -> {
                    val intent = Intent(this, FloatyService::class.java).apply {
                        action = FloatyService.ACTION_START_RECORDING
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    return true
                }
                KeyEvent.KEYCODE_S -> {
                    val intent = Intent(this, FloatyService::class.java).apply {
                        action = FloatyService.ACTION_STOP_RECORDING
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenAbout: () -> Unit,
    onOpenIssue: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowVersion: () -> Unit,
    onOpenAccessibility: () -> Unit,
    refreshKey: Int
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val settings = remember(refreshKey) { SettingsRepo(context) }

    val screenshotCount by remember(refreshKey) { mutableIntStateOf(settings.screenshotCount) }
    val videoCount by remember(refreshKey) { mutableIntStateOf(settings.videoCount) }
    var showGuide by remember { mutableStateOf(false) }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (canDrawOverlay(context)) {
            FloatyService.startFloaty(context)
            Toast.makeText(context, "Floaty overlay launched", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Overlay permission is required to display controls", Toast.LENGTH_LONG).show()
        }
    }

    val requestNotifPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Notification permission result handled */ }

    LaunchedEffect(Unit) {
        if (!canDrawOverlay(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                overlayPermissionLauncher.launch(intent)
            }
        } else {
            FloatyService.startFloaty(context)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF1E242B),
                modifier = Modifier.width(280.dp)
            ) {
                Text(
                    text = "Floaty Menu",
                    color = Color(0xFF26A69A),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(20.dp)
                )
                HorizontalDivider(color = Color(0xFF333D47))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.White) },
                    label = { Text("About", color = Color.White) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenAbout()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_about")
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.ReportProblem, contentDescription = null, tint = Color.White) },
                    label = { Text("Issue", color = Color.White) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenIssue()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_issue")
                )

                NavigationDrawerItem(
                    icon = { Box(modifier = Modifier.size(24.dp)) },
                    label = { Text("Check for updates (v${com.rootdeck.floaty.BuildConfig.VERSION_NAME})", color = Color(0xFF90A4AE), fontFamily = FontFamily.Monospace) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onShowVersion()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_version")
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Floaty",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            modifier = Modifier.testTag("app_title_centered")
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("hamburger_button")
                        ) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("topbar_settings_button")
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF1E242B))
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF12161A))
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Interactive How-to-Use Guide Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF26A69A), RoundedCornerShape(8.dp))
                        .background(Color(0xFF16202A))
                        .padding(14.dp)
                        .testTag("how_to_use_card")
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showGuide = !showGuide },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💡 How to Use Floaty",
                                color = Color(0xFF80CBC4),
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                            Icon(
                                imageVector = if (showGuide) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle Guide",
                                tint = Color(0xFF80CBC4)
                            )
                        }

                        AnimatedVisibility(visible = showGuide) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                GuideStep(number = "1", title = "Floating Controls", desc = "Controls launch automatically and use popup-free native capture.")
                                GuideStep(number = "2", title = "Drag Anywhere", desc = "Drag the pill smoothly across your screen to park it anywhere.")
                                GuideStep(number = "3", title = "Popup-Free Capture", desc = "Uses Accessibility service. Note: in Accessibility settings, turn ON 'Floaty' (keep 'Floaty shortcut' off).")
                                GuideStep(number = "4", title = "Screen Recording", desc = "Tap 🔴 (Record) to start. Use ⏸ to pause and ⏹ to save.")
                                GuideStep(number = "5", title = "Quick Access", desc = "Tap [ Images ] or [ Videos ] below to view your captures in Floaty folders.")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Captured section
                Text(
                    text = "Captured",
                    color = Color(0xFF26A69A),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                HorizontalDivider(
                    color = Color(0xFF333D47),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Screenshots:", color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        "$screenshotCount",
                        color = Color(0xFF80CBC4),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.testTag("count_screenshots")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Videos:", color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        "$videoCount",
                        color = Color(0xFF80CBC4),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.testTag("count_videos")
                    )
                }

                HorizontalDivider(
                    color = Color(0xFF333D47),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Keyboard shortcuts
                Text(
                    text = "Keyboard shortcuts",
                    color = Color(0xFF26A69A),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ctrl + Alt + R", color = Color(0xFF80CBC4), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text("Start recording", color = Color(0xFFCCCCCC), fontSize = 13.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ctrl + Alt + S", color = Color(0xFF80CBC4), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text("Stop recording", color = Color(0xFFCCCCCC), fontSize = 13.sp)
                }

                HorizontalDivider(
                    color = Color(0xFF333D47),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Equal width Images and Videos buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { openMediaFolder(context, FolderStore.KEY_IMAGES) },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("btn_view_images"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B333C))
                    ) {
                        Text("[ Images ]", fontFamily = FontFamily.Monospace, color = Color.White)
                    }

                    Button(
                        onClick = { openMediaFolder(context, FolderStore.KEY_VIDEOS) },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("btn_view_videos"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B333C))
                    ) {
                        Text("[ Videos ]", fontFamily = FontFamily.Monospace, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun GuideStep(number: String, title: String, desc: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "[$number] ",
            color = Color(0xFF26A69A),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Column {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            Text(
                text = desc,
                color = Color(0xFFB0BEC5),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

fun canDrawOverlay(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context)
    else true

fun openMediaFolder(context: Context, key: String) {
    val customUri = FolderStore.uri(context, key)
    if (customUri != null) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(customUri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
    }

    try {
        val targetDir = if (key == FolderStore.KEY_IMAGES) {
            FolderStore.getDefaultImagesDir(context)
        } else {
            FolderStore.getDefaultVideosDir(context)
        }
        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            targetDir
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "Folder ready at: ${FolderStore.getDisplayPath(context, key)}",
            Toast.LENGTH_LONG
        ).show()
    }
}
