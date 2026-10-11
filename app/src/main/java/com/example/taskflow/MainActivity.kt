package com.example.taskflow

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.taskflow.data.CapturedTask
import com.example.taskflow.data.LinkedTask
import com.example.taskflow.data.LinkedTaskStore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.example.taskflow.google.GoogleSignInManager
import com.example.taskflow.google.GoogleTaskSync
import com.example.taskflow.google.SyncResult
import com.example.taskflow.nlp.LocalTaskParser
import com.example.taskflow.widget.FocusWidgetProvider
import com.example.taskflow.widget.TaskWidgetProvider
import kotlinx.coroutines.launch
import java.io.File
import java.time.format.DateTimeFormatter

private val TaskFlowSerif = FontFamily(Font(R.font.cormorant_garamond, FontWeight.Normal))
private val Canvas = Color(0xFFFCFBF7)
private val Surface = Color.White
private val Ink = Color(0xFF20201E)
private val Muted = Color(0xFF8A8983)
private val Rule = Color(0xFFEAE5D9)
private val Sage = Color(0xFF658362)
private val CapturePrompts = listOf(
    "Take the bins out before they gain sentience tomorrow at 7pm",
    "Buy milk before cereal becomes soup tomorrow at 8am",
    "Call Mum before she sends a search party Sunday at 6pm",
    "Water the plants before they unionise Friday at 6pm",
    "Reply to that email before it becomes a historical document today at 4pm"
)
private const val CapturePromptPreferences = "taskflow_capture_prompts"
private const val CapturePromptIndex = "capture_prompt_index"

class MainActivity : ComponentActivity() {
    private var voiceRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        UpdateCheckWorker.schedule(this)
        // Capture widgets never retain a previously submitted task.
        TaskWidgetProvider.refresh(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 8)
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Sage, background = Canvas, surface = Surface, onSurface = Ink)) {
                TaskFlowApp()
            }
        }
    }

    @Composable
    private fun TaskFlowApp() {
        val parser = remember { LocalTaskParser() }
        val initialVoiceText = remember { intent.getStringExtra(VoiceCaptureActivity.extraCapturedText).orEmpty() }
        var capturePromptIndex by remember { mutableIntStateOf(nextCapturePromptIndex()) }
        var input by remember { mutableStateOf(initialVoiceText) }
        var preview by remember { mutableStateOf(initialVoiceText.takeIf { it.isNotBlank() }?.let(parser::parse)) }
        var message by remember { mutableStateOf("Ready for your next task.") }
        var focusTasks by remember { mutableStateOf(FocusTaskStore.load(this@MainActivity)) }
        var googleConnected by remember { mutableStateOf(GoogleSignInManager.isConnected(this@MainActivity)) }
        var isConnectingGoogle by remember { mutableStateOf(false) }
        var isSyncing by remember { mutableStateOf(false) }
        var createCalendar by remember { mutableStateOf(LinkedTaskStore.createsCalendarEvents(this@MainActivity)) }
        var linkedTasks by remember { mutableStateOf(loadLinkedTasks()) }
        var eventToRemove by remember { mutableStateOf<LinkedTask?>(null) }
        var showSyncOptions by remember { mutableStateOf(false) }
        var showSchedule by remember { mutableStateOf(false) }
        var selectedTab by rememberSaveable { mutableIntStateOf(0) }
        val tabScrollStates = listOf(rememberScrollState(), rememberScrollState(), rememberScrollState())
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        BackHandler(enabled = selectedTab != 0) { selectedTab = 0 }
        DisposableEffect(Unit) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    googleConnected = GoogleSignInManager.isConnected(this@MainActivity)
                    linkedTasks = loadLinkedTasks()
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
        var downloadingUpdate by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableIntStateOf(0) }
        var pendingInstallFile by remember { mutableStateOf<File?>(null) }
        var showInstallPermissionDialog by remember { mutableStateOf(false) }
        var updateMessage by remember { mutableStateOf<String?>(null) }
        val installSourceSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (packageManager.canRequestPackageInstalls()) {
                pendingInstallFile?.let(::promptInstall)
                pendingInstallFile = null
            } else {
                updateMessage = "Allow TaskFlow to install this update, then tap Update again."
                pendingInstallFile = null
            }
        }
        val installUpdate = { update: AppUpdate ->
            if (!downloadingUpdate) {
                downloadingUpdate = true
                downloadProgress = 0
                updateMessage = "Downloading and checking the official release…"
                lifecycleScope.launch {
                    try {
                        val apk = UpdateInstaller.downloadAndVerify(this@MainActivity, update) { downloadProgress = it }
                        downloadingUpdate = false
                        pendingInstallFile = apk
                        if (packageManager.canRequestPackageInstalls()) {
                            promptInstall(apk)
                            pendingInstallFile = null
                        } else showInstallPermissionDialog = true
                    } catch (error: Exception) {
                        downloadingUpdate = false
                        updateMessage = error.message ?: "The update could not be downloaded or verified."
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            availableUpdate = UpdateChecker.findAvailable(this@MainActivity)
        }
        val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            isConnectingGoogle = false
            val signInFailure = runCatching {
                GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            }.exceptionOrNull()
            googleConnected = GoogleSignInManager.isConnected(this@MainActivity)
            linkedTasks = loadLinkedTasks()
            message = when {
                signInFailure is ApiException -> when (signInFailure.statusCode) {
                    12501 -> "Google connection was cancelled. Tap Connect Google to try again."
                    10, 12500 -> "Google sign-in is not configured for this app build. Please install the supported TaskFlow update. (Code ${signInFailure.statusCode})"
                    7 -> "Google connection needs internet access. Check your connection and try again."
                    else -> "Google connection failed (code ${signInFailure.statusCode}). Please try again."
                }
                signInFailure != null -> "Google connection could not finish. Please try again."
                googleConnected -> "Google connected. Matching Calendar events follow your choice below."
                else -> "Google connection needs to be completed before syncing."
            }
        }

        if (showSyncOptions) AlertDialog(
            onDismissRequest = { showSyncOptions = false },
            title = { Text("Tasks & Calendar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = createCalendar, enabled = !isSyncing, onCheckedChange = {
                            createCalendar = it
                            LinkedTaskStore.setCreatesCalendarEvents(this@MainActivity, it)
                        })
                        Text("Add Calendar events")
                    }
                    Text("Dated tasks can also reserve time in Calendar. This choice applies to widgets too.")
                    Text("Completing a task keeps its event. Changes made in Google aren’t automatically copied between the two.")
                    Text("Recent tasks show this device’s saved history, not live updates from Google.")
                }
            },
            confirmButton = { TextButton(onClick = { showSyncOptions = false }) { Text("Done") } }
        )
        eventToRemove?.let { task ->
            AlertDialog(
                onDismissRequest = { eventToRemove = null },
                title = { Text("Remove Calendar event?") },
                text = { Text("This removes the Calendar event for ${task.title}. If it repeats, the entire event series is removed. Your Google Task stays in Tasks.") },
                confirmButton = { TextButton(onClick = {
                    eventToRemove = null
                    isSyncing = true
                    lifecycleScope.launch {
                        try {
                            GoogleTaskSync(this@MainActivity).removeCalendarEvent(task)
                            linkedTasks = loadLinkedTasks()
                            message = "Calendar event removed. Google Task kept."
                        } catch (error: Exception) { message = error.message ?: "Could not remove the event." }
                        finally { isSyncing = false }
                    }
                }) { Text("Remove event") } },
                dismissButton = { TextButton(onClick = { eventToRemove = null }) { Text("Keep event") } }
            )
        }
        val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startVoiceInput(
                onListening = { message = "Listening…" },
                onText = { spoken ->
                    input = spoken
                    preview = parser.parse(spoken)
                    message = "Voice task captured. Check the details, then sync."
                },
                onError = { message = "I didn't catch that. Please try again." }
            ) else message = "Microphone access is needed for voice input."
        }

        if (showInstallPermissionDialog) AlertDialog(
            onDismissRequest = { showInstallPermissionDialog = false },
            title = { Text("One Android permission needed") },
            text = { Text("Android requires you to allow TaskFlow to install updates downloaded from outside the Play Store. The APK has already been checked against this installation. You’ll return to the install prompt after allowing it.") },
            confirmButton = { TextButton(onClick = {
                showInstallPermissionDialog = false
                installSourceSettings.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { showInstallPermissionDialog = false }) { Text("Not now") } }
        )

        Scaffold(containerColor = Canvas, bottomBar = {
            TaskFlowNavigation(selectedTab, onSelect = {
                focusManager.clearFocus()
                keyboard?.hide()
                selectedTab = it
            })
        }) { inset ->
            Column(
                modifier = Modifier.fillMaxSize().padding(inset).consumeWindowInsets(inset).verticalScroll(tabScrollStates[selectedTab]).padding(horizontal = 24.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Header()
                if (selectedTab == 0) {
                availableUpdate?.let { update ->
                    UpdateCard(update = update, downloading = downloadingUpdate, progress = downloadProgress,
                        message = updateMessage, onUpdate = { installUpdate(update) })
                }
                SectionLabel("CAPTURE")
                OutlinedTextField(
                    value = input,
                    enabled = !isSyncing,
                    onValueChange = { value ->
                        input = value
                        preview = if (value.isBlank()) null else parser.parse(value)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("What needs doing?", color = Muted) },
                    placeholder = { Text(CapturePrompts[capturePromptIndex], color = Muted) },
                    minLines = 3,
                    trailingIcon = {
                        IconButton(enabled = !isSyncing, onClick = {
                            if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                startVoiceInput(
                                    onListening = { message = "Listening…" },
                                    onText = { spoken ->
                                        input = spoken
                                        preview = parser.parse(spoken)
                                        message = "Voice task captured. Check the details, then sync."
                                    },
                                    onError = { message = "I didn't catch that. Please try again." }
                                )
                            } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }) {
                            Icon(painter = painterResource(R.drawable.ic_mic), contentDescription = "Speak a task", tint = Color.Unspecified)
                        }
                    },
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Sage, unfocusedBorderColor = Rule,
                        focusedContainerColor = Surface, unfocusedContainerColor = Surface,
                        focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Sage
                    )
                )
                preview?.let { task ->
                    ParsePreview(task, onTitleChange = { title -> preview = task.copy(title = title) })
                    if (!isSyncing) {
                        TextButton(onClick = { showSchedule = !showSchedule }) { Text(if (showSchedule) "Hide schedule" else "Adjust schedule") }
                        if (showSchedule) TaskScheduleControls(task, onChange = { preview = it })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            if (!isConnectingGoogle) {
                                isConnectingGoogle = true
                                signIn.launch(GoogleSignInManager.client(this@MainActivity).signInIntent)
                            }
                        },
                        enabled = !isConnectingGoogle && !isSyncing,
                        shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Ink,
                            disabledContentColor = Sage
                        ), modifier = if (preview != null) Modifier.weight(1f) else Modifier
                    ) {
                        if (googleConnected) {
                            Spacer(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Sage))
                            Text("Connected", modifier = Modifier.padding(start = 7.dp))
                        } else {
                            Text(if (isConnectingGoogle) "Connecting…" else "Connect Google")
                        }
                    }
                    if (preview != null) OutlinedButton(
                        onClick = {
                            preview?.let { task ->
                                focusTasks = FocusTaskStore.add(this@MainActivity, task.title)
                                FocusLiveUpdate.show(this@MainActivity, focusTasks)
                                FocusWidgetProvider.refresh(this@MainActivity)
                                message = "Added ${task.title} to focus."
                            }
                        },
                        enabled = preview != null, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Sage), modifier = Modifier.weight(1f)
                    ) { Text("Start focus") }
                    if (preview == null) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showSyncOptions = true }) { Text(if (createCalendar) "Calendar on" else "Tasks only", fontSize = 12.sp) }
                    }
                }
                if (preview != null) TextButton(onClick = { showSyncOptions = true }) {
                    Text(if (createCalendar) "Calendar on · Options" else "Tasks only · Options", fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        val task = preview ?: return@Button
                        if (isSyncing || task.title.isBlank()) return@Button
                        isSyncing = true
                        lifecycleScope.launch {
                            message = "Syncing your task…"
                            try {
                                when (GoogleTaskSync(this@MainActivity).sync(task)) {
                                    SyncResult.NotConnected -> message = "Connect Google before syncing."
                                    is SyncResult.Success -> {
                                        message = "Saved to Google Tasks${if (task.dueDate != null && createCalendar) " and Calendar" else ""}."
                                        TaskWidgetProvider.refresh(this@MainActivity)
                                        input = ""; preview = null
                                        capturePromptIndex = nextCapturePromptIndex()
                                    }
                                }
                            } catch (error: Exception) { message = error.message ?: "Could not sync. Please try again." }
                            finally {
                                linkedTasks = loadLinkedTasks()
                                isSyncing = false
                            }
                        }
                    },
                    enabled = preview?.title?.isNotBlank() == true && !isSyncing, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sage, contentColor = Color.White)
                ) { Text(if (isSyncing) "Saving…" else "Add task", fontWeight = FontWeight.SemiBold) }
                }
                if (message != "Ready for your next task.") Text(message, color = Sage, fontSize = 13.sp)
                if (selectedTab == 2) {
                SectionLabel("FOCUS")
                if (focusTasks.isEmpty()) {
                    Text("A little room to focus.", fontFamily = TaskFlowSerif, fontSize = 28.sp, color = Ink)
                    Text("Capture a task on Home, then tap Start focus.", color = Muted, fontSize = 14.sp)
                    TextButton(onClick = { selectedTab = 0 }) { Text("Capture a task") }
                }
                if (focusTasks.isNotEmpty()) {
                FocusColumn(
                    tasks = focusTasks,
                    onToggle = { focusTask ->
                        val nowSuspended = !focusTask.isSuspended
                        focusTasks = FocusTaskStore.setSuspended(this@MainActivity, focusTask.id, nowSuspended)
                        FocusLiveUpdate.show(this@MainActivity, focusTasks)
                        FocusWidgetProvider.refresh(this@MainActivity)
                        message = if (nowSuspended) "Focus paused for ${focusTask.title}." else "Focus resumed for ${focusTask.title}."
                    },
                    onRemove = { focusTask ->
                        focusTasks = FocusTaskStore.remove(this@MainActivity, focusTask.id)
                        FocusLiveUpdate.show(this@MainActivity, focusTasks)
                        FocusWidgetProvider.refresh(this@MainActivity)
                        message = "Removed ${focusTask.title} from focus."
                    }
                )
                }
                }
                if (selectedTab == 1) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    SectionLabel("YOUR TASKS")
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showSyncOptions = true }) { Text("About", fontSize = 12.sp) }
                }
                if (linkedTasks.isEmpty()) {
                    Text("Start with one small thing.", fontFamily = TaskFlowSerif, fontSize = 28.sp, color = Ink)
                    Text("Tasks saved here will appear in this list.", color = Muted, fontSize = 14.sp)
                    TextButton(onClick = { selectedTab = 0 }) { Text("Add your first task") }
                }
                linkedTasks.forEach { task ->
                    var expanded by remember(task.taskId) { mutableStateOf(false) }
                    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Rule, RoundedCornerShape(18.dp))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(task.title, modifier = Modifier.weight(1f), color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Close" else "Details", fontSize = 12.sp) }
                            }
                            Text(when {
                                task.calendarPending -> "Calendar pending · Retry needed"
                                task.completed -> "Completed"
                                task.calendarEventId != null -> "Tasks + Calendar"
                                else -> "Google Tasks"
                            }, color = Sage, fontSize = 12.sp)
                            if (expanded) {
                            if (task.calendarPending) Text("Retry the original capture to finish syncing.", color = Muted, fontSize = 12.sp)
                            if (task.completed && task.calendarEventId != null) Text("Calendar event kept", color = Muted, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://tasks.google.com/"))) }) { Text("Tasks") }
                                task.calendarUrl?.let { url ->
                                    TextButton(onClick = {
                                        val uri = Uri.parse(url)
                                        if (uri.scheme == "https" && uri.host in listOf("www.google.com", "calendar.google.com")) startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    }) { Text("Calendar") }
                                }
                            }
                            if (!task.completed) OutlinedButton(enabled = !isSyncing, onClick = {
                                isSyncing = true
                                lifecycleScope.launch {
                                    try {
                                        GoogleTaskSync(this@MainActivity).completeTask(task)
                                        linkedTasks = loadLinkedTasks()
                                        message = if (task.calendarEventId != null) "Task completed. Calendar event kept." else "Task completed."
                                    } catch (error: Exception) { message = error.message ?: "Could not complete the task." }
                                    finally { isSyncing = false }
                                }
                            }) { Text("Complete task only") }
                            if (task.calendarEventId != null) TextButton(enabled = !isSyncing, onClick = { eventToRemove = task }) { Text("Remove Calendar event") }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    private fun loadLinkedTasks(): List<LinkedTask> {
        val account = GoogleSignIn.getLastSignedInAccount(this)?.account ?: return emptyList()
        return LinkedTaskStore.load(this, account.name)
    }

    private fun nextCapturePromptIndex(): Int {
        val preferences = getSharedPreferences(CapturePromptPreferences, MODE_PRIVATE)
        val nextIndex = (preferences.getInt(CapturePromptIndex, -1) + 1) % CapturePrompts.size
        preferences.edit().putInt(CapturePromptIndex, nextIndex).apply()
        return nextIndex
    }

    private fun promptInstall(apk: File) {
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(UpdateInstaller.uriFor(this@MainActivity, apk), "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivity(intent)
    }

    @Composable
    private fun Header() = Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(painter = painterResource(R.drawable.taskflow_mark), contentDescription = "TaskFlow", modifier = Modifier.size(36.dp))
        Text("TaskFlow", modifier = Modifier.padding(start = 10.dp), fontFamily = TaskFlowSerif, fontSize = 38.sp, color = Ink)
        Spacer(Modifier.weight(1f))
        Text("TASKS  +  CALENDAR", fontSize = 10.sp, letterSpacing = 1.5.sp, color = Muted)
    }

    @Composable
    private fun SectionLabel(text: String) = Text(text, fontSize = 11.sp, letterSpacing = 2.sp, color = Muted, fontWeight = FontWeight.Medium)

    @Composable
    private fun UpdateCard(
        update: AppUpdate,
        downloading: Boolean,
        progress: Int,
        message: String?,
        onUpdate: () -> Unit
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Rule, RoundedCornerShape(18.dp))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("TaskFlow ${update.versionName} is ready", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text(message ?: "A newer version is ready to install.", color = Muted, fontSize = 13.sp)
                    if (downloading) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(), color = Sage)
                        Text("$progress%", color = Muted, fontSize = 11.sp)
                    }
                }
                OutlinedButton(
                    enabled = !downloading,
                    onClick = onUpdate,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Sage),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Sage)
                ) { Text(if (downloading) "Downloading" else if (message?.startsWith("The update") == true) "Retry" else "Update") }
            }
        }
    }

    @Composable
    private fun ParsePreview(task: CapturedTask, onTitleChange: (String) -> Unit) {
        var editingTitle by remember(task.sourceText) { mutableStateOf(false) }
        val date = task.dueDate?.format(DateTimeFormatter.ofPattern("EEE, d MMM")) ?: "No date"
        val time = task.dueTime?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "All day"
        Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Rule, RoundedCornerShape(18.dp))) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painter = painterResource(R.drawable.taskflow_mark), contentDescription = null, modifier = Modifier.size(24.dp))
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    if (editingTitle) {
                        OutlinedTextField(
                            value = task.title,
                            onValueChange = onTitleChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Task title") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Sage, unfocusedBorderColor = Rule,
                                focusedContainerColor = Surface, unfocusedContainerColor = Surface,
                                focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Sage
                            )
                        )
                    } else {
                        Text(task.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("$date  ·  $time", color = Muted, fontSize = 13.sp)
                    task.recurrence?.let { Text(it.label.uppercase(), color = Sage, fontSize = 10.sp, letterSpacing = 1.sp) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("LOCAL", color = Sage, fontSize = 10.sp, letterSpacing = 1.sp)
                    OutlinedButton(
                        onClick = { editingTitle = !editingTitle },
                        modifier = Modifier.padding(top = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Sage)
                    ) { Text(if (editingTitle) "Done" else "Edit", fontSize = 10.sp) }
                }
            }
        }
    }

    @Composable
    private fun FocusColumn(
        tasks: List<FocusTask>,
        onToggle: (FocusTask) -> Unit,
        onRemove: (FocusTask) -> Unit
    ) {
        if (tasks.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Rule, RoundedCornerShape(18.dp))) {
                Text("Start focus from a captured task to keep it here.", modifier = Modifier.padding(16.dp), color = Muted, fontSize = 14.sp)
            }
            return
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tasks.forEach { task ->
                Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Rule, RoundedCornerShape(18.dp))) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(
                                Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(
                                    if (task.isSuspended) Muted else Sage
                                )
                            )
                            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                                Text(task.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (task.isSuspended) "SUSPENDED" else "ACTIVE", color = if (task.isSuspended) Muted else Sage, fontSize = 10.sp, letterSpacing = 1.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onToggle(task) },
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)
                            ) { Text(if (task.isSuspended) "Resume" else "Suspend") }
                            OutlinedButton(
                                onClick = { onRemove(task) },
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted)
                            ) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun StatusCard(message: String) = Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Rule, RoundedCornerShape(18.dp))) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(Sage))
            Text(message, modifier = Modifier.padding(start = 10.dp), color = Ink, fontSize = 14.sp)
        }
    }

    private fun startVoiceInput(onListening: () -> Unit, onText: (String) -> Unit, onError: () -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            onError()
            return
        }
        voiceRecognizer?.destroy()
        voiceRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onListening()
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) = onError()
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (spoken.isNullOrBlank()) onError() else onText(spoken)
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your task")
            })
        }
    }

    override fun onDestroy() {
        voiceRecognizer?.destroy()
        super.onDestroy()
    }
}
