package com.rootdeck.floaty.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.rootdeck.floaty.app.capture.ScreenshotAccessibilityService
import com.rootdeck.floaty.app.prefs.SettingsRepo
import com.rootdeck.floaty.app.storage.FolderStore
import com.rootdeck.floaty.theme.FloatyTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FloatyTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepo(context) }
    val config = LocalConfiguration.current

    var opacity by remember { mutableFloatStateOf(settings.opacity) }
    var iconSize by remember { mutableStateOf(settings.iconSize) }
    var haptics by remember { mutableStateOf(settings.haptics) }
    var autoHide by remember { mutableStateOf(settings.autoHideDuringRecording) }

    var autoCopy by remember { mutableStateOf(settings.autoCopyClipboard) }
    var imageFormat by remember { mutableStateOf(settings.imageFormat) }
    var captureSound by remember { mutableStateOf(settings.captureSound) }

    var enableShortcuts by remember { mutableStateOf(settings.enableShortcuts) }

    var imagesFolderUri by remember { mutableStateOf(FolderStore.uri(context, FolderStore.KEY_IMAGES)) }
    var videosFolderUri by remember { mutableStateOf(FolderStore.uri(context, FolderStore.KEY_VIDEOS)) }

    var captureMethod by remember { mutableStateOf(settings.captureMethod) }
    var preventIfLocked by remember { mutableStateOf(settings.preventIfLocked) }
    var autoCropEnabled by remember { mutableStateOf(settings.autoCropEnabled) }
    var autoCropLeft by remember { mutableStateOf(settings.autoCropLeft.toString()) }
    var autoCropTop by remember { mutableStateOf(settings.autoCropTop.toString()) }
    var autoCropRight by remember { mutableStateOf(settings.autoCropRight.toString()) }
    var autoCropBottom by remember { mutableStateOf(settings.autoCropBottom.toString()) }
    var fileFormat by remember { mutableStateOf(settings.fileFormat) }
    var formatQuality by remember { mutableFloatStateOf(settings.formatQuality.toFloat()) }
    var fileNamePattern by remember { mutableStateOf(settings.fileNamePattern) }

    var resolution by remember { mutableStateOf(settings.recordResolution) }
    var recordAudio by remember { mutableStateOf(settings.recordAudio) }
    var recordFps by remember { mutableIntStateOf(settings.recordFps) }

    var startOnBoot by remember { mutableStateOf(settings.startOnBoot) }
    var persistentNotif by remember { mutableStateOf(settings.persistentNotification) }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        recordAudio = granted
        settings.recordAudio = granted
        if (!granted) {
            Toast.makeText(context, "Microphone permission was not granted", Toast.LENGTH_SHORT).show()
        }
    }

    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            FolderStore.save(context, FolderStore.KEY_IMAGES, uri)
            imagesFolderUri = uri
        }
    }

    val pickVideosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            FolderStore.save(context, FolderStore.KEY_VIDEOS, uri)
            videosFolderUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 1. Floating Icon
            SectionHeader("1. Floating Icon")
            Text(
                "Opacity: ${(opacity * 100).toInt()}%",
                color = Color(0xFFCCCCCC),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            Slider(
                value = opacity,
                onValueChange = {
                    opacity = it
                    settings.opacity = it
                },
                valueRange = 0.3f..1.0f,
                steps = 6,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF26A69A),
                    activeTrackColor = Color(0xFF26A69A)
                ),
                modifier = Modifier.testTag("slider_opacity")
            )

            Text("Icon Size:", color = Color(0xFFCCCCCC), fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf("small", "medium", "large").forEach { size ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        RadioButton(
                            selected = iconSize.equals(size, ignoreCase = true),
                            onClick = {
                                iconSize = size
                                settings.iconSize = size
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF26A69A))
                        )
                        Text(size.replaceFirstChar { it.uppercase() }, color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            SettingToggle(
                label = "Haptic feedback on tap",
                checked = haptics,
                onCheckedChange = {
                    haptics = it
                    settings.haptics = it
                },
                tag = "toggle_haptics"
            )

            SettingToggle(
                label = "Auto-hide icon during recording",
                checked = autoHide,
                onCheckedChange = {
                    autoHide = it
                    settings.autoHideDuringRecording = it
                },
                tag = "toggle_auto_hide"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Screenshot & Clipboard
            SectionHeader("2. Screenshot & Clipboard")
            SettingToggle(
                label = "Auto-copy image reference to clipboard (paste-able in most apps on Android 5–9; only foreground apps on Android 10+).",
                checked = autoCopy,
                onCheckedChange = {
                    autoCopy = it
                    settings.autoCopyClipboard = it
                },
                tag = "toggle_auto_copy"
            )

            Text("Image Format:", color = Color(0xFFCCCCCC), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf("png", "jpg", "webp").forEach { fmt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        RadioButton(
                            selected = fileFormat.equals(fmt, ignoreCase = true) || (fmt == "jpg" && imageFormat.equals("jpeg", ignoreCase = true)),
                            onClick = {
                                imageFormat = fmt
                                fileFormat = fmt
                                settings.fileFormat = fmt
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF26A69A))
                        )
                        Text(fmt.uppercase(), color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            SettingToggle(
                label = "Play capture sound effect",
                checked = captureSound,
                onCheckedChange = {
                    captureSound = it
                    settings.captureSound = it
                },
                tag = "toggle_capture_sound"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Keyboard Shortcuts
            SectionHeader("3. Keyboard Shortcuts")
            Text("Ctrl + Alt + R  : Start recording", color = Color(0xFF80CBC4), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Text("Ctrl + Alt + S  : Stop recording", color = Color(0xFF80CBC4), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            val hasKeyboard = config.keyboard != Configuration.KEYBOARD_NOKEYS
            Text(
                text = if (hasKeyboard) "Status: External keyboard connected" else "Status: No external keyboard detected",
                color = if (hasKeyboard) Color(0xFF81C784) else Color(0xFFB0BEC5),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            SettingToggle(
                label = "Enable hardware keyboard shortcuts listener",
                checked = enableShortcuts,
                onCheckedChange = {
                    enableShortcuts = it
                    settings.enableShortcuts = it
                },
                tag = "toggle_shortcuts"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Storage & Folders
            SectionHeader("4. Storage & Folders (SAF)")
            FolderRow(
                title = "Images Directory",
                currentPath = imagesFolderUri?.path ?: FolderStore.getDisplayPath(context, FolderStore.KEY_IMAGES),
                onChange = { pickImagesLauncher.launch(null) },
                buttonTag = "btn_change_images_folder"
            )

            FolderRow(
                title = "Videos Directory",
                currentPath = videosFolderUri?.path ?: FolderStore.getDisplayPath(context, FolderStore.KEY_VIDEOS),
                onChange = { pickVideosLauncher.launch(null) },
                buttonTag = "btn_change_videos_folder"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Capture Method
            SectionHeader("5. Capture Method")
            Text("Method:", color = Color(0xFFCCCCCC), fontSize = 13.sp)
            Column(modifier = Modifier.padding(top = 4.dp)) {
                listOf(
                    Triple(
                        "native",
                        "🎯 Native (popup-free)  [Recommended]",
                        "Uses AccessibilityService to trigger system screenshot. No popup after first enable."
                    ),
                    Triple(
                        "legacy",
                        "📱 Legacy (MediaProjection)",
                        "Shows 'Start recording or casting?' popup every screenshot."
                    ),
                    Triple(
                        "auto",
                        "🔄 Auto",
                        "Native first, falls back to Legacy if accessibility is off."
                    )
                ).forEach { (mode, title, desc) ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = captureMethod.equals(mode, ignoreCase = true),
                            onClick = {
                                captureMethod = mode
                                settings.captureMethod = mode
                                settings.useNative = (mode == "native" || mode == "auto")
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF26A69A))
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(desc, color = Color(0xFF90A4AE), fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                }
            }

            val isAccessibilityActive = ScreenshotAccessibilityService.instance != null
            if (!isAccessibilityActive) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Accessibility settings unavailable", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_accessibility_settings"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B4D46))
                ) {
                    Text("Open Accessibility Settings", color = Color.White, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = {
                    try {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Notification settings unavailable", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_notification_settings"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3440))
            ) {
                Text("Open Notification Settings", color = Color.White, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingToggle(
                label = "Prevent screenshots while locked",
                checked = preventIfLocked,
                onCheckedChange = {
                    preventIfLocked = it
                    settings.preventIfLocked = it
                },
                tag = "toggle_prevent_locked"
            )

            SettingToggle(
                label = "Auto-crop screenshots",
                checked = autoCropEnabled,
                onCheckedChange = {
                    autoCropEnabled = it
                    settings.autoCropEnabled = it
                },
                tag = "toggle_auto_crop"
            )

            if (autoCropEnabled) {
                Text(
                    "Crop insets (px):",
                    color = Color(0xFFCCCCCC),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Left" to (autoCropLeft to { s: String -> autoCropLeft = s; settings.autoCropLeft = s.toIntOrNull() ?: 0 }),
                        "Top" to (autoCropTop to { s: String -> autoCropTop = s; settings.autoCropTop = s.toIntOrNull() ?: 0 }),
                        "Right" to (autoCropRight to { s: String -> autoCropRight = s; settings.autoCropRight = s.toIntOrNull() ?: 0 }),
                        "Bottom" to (autoCropBottom to { s: String -> autoCropBottom = s; settings.autoCropBottom = s.toIntOrNull() ?: 0 })
                    ).forEach { (label, pair) ->
                        val (v, setter) = pair
                        OutlinedTextField(
                            value = v,
                            onValueChange = { str ->
                                val filtered = str.filter { it.isDigit() }.take(4)
                                setter(filtered)
                            },
                            label = { Text(label, fontSize = 10.sp, color = Color(0xFF80CBC4)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_crop_${label.lowercase()}"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF26A69A),
                                unfocusedBorderColor = Color(0xFF333D47)
                            )
                        )
                    }
                }
            }

            Text("File Format:", color = Color(0xFFCCCCCC), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf("png", "jpg", "webp").forEach { fmt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        RadioButton(
                            selected = fileFormat.equals(fmt, ignoreCase = true),
                            onClick = {
                                fileFormat = fmt
                                imageFormat = fmt
                                settings.fileFormat = fmt
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF26A69A))
                        )
                        Text(fmt.uppercase(), color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            Text(
                "Format Quality: ${formatQuality.toInt()}%",
                color = Color(0xFFCCCCCC),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 8.dp)
            )
            Slider(
                value = formatQuality,
                onValueChange = {
                    formatQuality = it
                    settings.formatQuality = it.toInt()
                },
                valueRange = 10f..100f,
                steps = 17,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF26A69A),
                    activeTrackColor = Color(0xFF26A69A)
                ),
                modifier = Modifier.testTag("slider_format_quality")
            )

            Text("File Name Pattern:", color = Color(0xFFCCCCCC), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            OutlinedTextField(
                value = fileNamePattern,
                onValueChange = {
                    fileNamePattern = it
                    settings.fileNamePattern = it
                },
                placeholder = { Text("floaty_%timestamp%", color = Color(0xFF607D8B), fontSize = 12.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("input_filename_pattern"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF26A69A),
                    unfocusedBorderColor = Color(0xFF333D47)
                )
            )
            Text(
                "Variables: %timestamp%, %date%, %time%, %counter%, %app%",
                color = Color(0xFF90A4AE),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Screen Recording
            SectionHeader("6. Screen Recording")
            Text("Resolution:", color = Color(0xFFCCCCCC), fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf("sd" to "SD (480p)", "hd" to "HD (720p)", "device" to "Device").forEach { (key, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        RadioButton(
                            selected = resolution.equals(key, ignoreCase = true),
                            onClick = {
                                resolution = key
                                settings.recordResolution = key
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF26A69A))
                        )
                        Text(label, color = Color.White, fontSize = 11.sp)
                    }
                }
            }

            SettingToggle(
                label = "Record microphone audio",
                checked = recordAudio,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        recordAudio = false
                        settings.recordAudio = false
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                            PackageManager.PERMISSION_GRANTED
                    ) {
                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        recordAudio = true
                        settings.recordAudio = true
                    }
                },
                tag = "toggle_record_audio"
            )

            Text("Frame Rate:", color = Color(0xFFCCCCCC), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(24, 30).forEach { fps ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        RadioButton(
                            selected = recordFps == fps,
                            onClick = {
                                recordFps = fps
                                settings.recordFps = fps
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF26A69A))
                        )
                        Text("$fps FPS", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            Text(
                "Notice: Screenshots taken during recording may lower frame rate on older devices.",
                color = Color(0xFFFFB74D),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 7. General & System
            SectionHeader("7. General & System")
            SettingToggle(
                label = "Start on boot (overlay only on API 34+)",
                checked = startOnBoot,
                onCheckedChange = {
                    startOnBoot = it
                    settings.startOnBoot = it
                },
                tag = "toggle_start_on_boot"
            )

            SettingToggle(
                label = "Persistent notification",
                checked = persistentNotif,
                onCheckedChange = {
                    persistentNotif = it
                    settings.persistentNotification = it
                },
                tag = "toggle_persistent_notif"
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isIgnoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Battery settings unavailable", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_battery_optimization"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3440))
                ) {
                    Text(
                        if (isIgnoring) "Battery Optimization Disabled (Active)" else "Disable Battery Optimization",
                        color = if (isIgnoring) Color(0xFF81C784) else Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF26A69A),
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
    HorizontalDivider(color = Color(0xFF333D47), thickness = 1.dp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
fun SettingToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF26A69A)
            )
        )
    }
}

@Composable
fun FolderRow(
    title: String,
    currentPath: String,
    onChange: () -> Unit,
    buttonTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, Color(0xFF2B333C))
            .background(Color(0xFF191F26))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(currentPath, color = Color(0xFF90A4AE), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Button(
            onClick = onChange,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26A69A)),
            modifier = Modifier.testTag(buttonTag)
        ) {
            Text("Change", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}
