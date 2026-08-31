package com.example.taskflow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.taskflow.data.CapturedTask
import com.example.taskflow.google.GoogleSignInManager
import com.example.taskflow.google.GoogleTaskSync
import com.example.taskflow.google.SyncResult
import com.example.taskflow.nlp.LocalTaskParser
import com.example.taskflow.widget.FocusWidgetProvider
import com.example.taskflow.widget.TaskWidgetProvider
import kotlinx.coroutines.launch
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
        val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            isConnectingGoogle = false
            googleConnected = GoogleSignInManager.isConnected(this@MainActivity)
            message = when {
                googleConnected -> "Google connected. Tasks will now sync to Calendar."
                result.resultCode == RESULT_CANCELED -> "Google connection was cancelled."
                else -> "Google connection needs to be completed before syncing."
            }
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

        Scaffold(containerColor = Canvas) { inset ->
            Column(
                modifier = Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Header()
                SectionLabel("CAPTURE")
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value
                        preview = if (value.isBlank()) null else parser.parse(value)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("What needs doing?", color = Muted) },
                    placeholder = { Text(CapturePrompts[capturePromptIndex], color = Muted) },
                    minLines = 3,
                    trailingIcon = {
                        IconButton(onClick = {
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
                preview?.let { task -> ParsePreview(task) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            if (!googleConnected && !isConnectingGoogle) {
                                isConnectingGoogle = true
                                signIn.launch(GoogleSignInManager.client(this@MainActivity).signInIntent)
                            }
                        },
                        enabled = !googleConnected && !isConnectingGoogle,
                        shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Ink,
                            disabledContentColor = Sage
                        ), modifier = Modifier.weight(1f)
                    ) {
                        if (googleConnected) {
                            Spacer(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Sage))
                            Text("Google connected", modifier = Modifier.padding(start = 7.dp))
                        } else {
                            Text(if (isConnectingGoogle) "Connecting…" else "Connect Google")
                        }
                    }
                    OutlinedButton(
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
                }
                Button(
                    onClick = {
                        val task = preview ?: return@Button
                        lifecycleScope.launch {
                            message = "Syncing your task…"
                            try {
                                when (GoogleTaskSync(this@MainActivity).sync(task)) {
                                    SyncResult.NotConnected -> message = "Connect Google before syncing."
                                    is SyncResult.Success -> {
                                        message = "Saved to Google Tasks${if (task.dueDate != null) " and Calendar" else ""}."
                                        TaskWidgetProvider.refresh(this@MainActivity)
                                        input = ""; preview = null
                                        capturePromptIndex = nextCapturePromptIndex()
                                    }
                                }
                            } catch (error: Exception) { message = error.message ?: "Could not sync. Please try again." }
                        }
                    },
                    enabled = preview != null, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sage, contentColor = Color.White)
                ) { Text("Add and sync task", fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(4.dp))
                SectionLabel("FOCUS")
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
                SectionLabel("SYNC STATUS")
                StatusCard(message)
            }
        }
    }

    private fun nextCapturePromptIndex(): Int {
        val preferences = getSharedPreferences(CapturePromptPreferences, MODE_PRIVATE)
        val nextIndex = (preferences.getInt(CapturePromptIndex, -1) + 1) % CapturePrompts.size
        preferences.edit().putInt(CapturePromptIndex, nextIndex).apply()
        return nextIndex
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
    private fun ParsePreview(task: CapturedTask) {
        val date = task.dueDate?.format(DateTimeFormatter.ofPattern("EEE, d MMM")) ?: "No date"
        val time = task.dueTime?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "All day"
        Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Rule, RoundedCornerShape(18.dp))) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painter = painterResource(R.drawable.taskflow_mark), contentDescription = null, modifier = Modifier.size(24.dp))
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(task.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$date  ·  $time", color = Muted, fontSize = 13.sp)
                    task.recurrence?.let { Text(it.label.uppercase(), color = Sage, fontSize = 10.sp, letterSpacing = 1.sp) }
                }
                Text("LOCAL", color = Sage, fontSize = 10.sp, letterSpacing = 1.sp)
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
