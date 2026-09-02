package com.example.taskflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.taskflow.data.CapturedTask
import com.example.taskflow.google.GoogleTaskSync
import com.example.taskflow.google.SyncResult
import com.example.taskflow.nlp.LocalTaskParser
import com.example.taskflow.widget.TaskWidgetProvider
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val QuickCanvas = Color(0xFFFCFBF7)
private val QuickSurface = Color.White
private val QuickInk = Color(0xFF20201E)
private val QuickMuted = Color(0xFF8A8983)
private val QuickRule = Color(0xFFEAE5D9)
private val QuickSage = Color(0xFF658362)
private val QuickSerif = FontFamily(Font(R.font.cormorant_garamond, FontWeight.Normal))

/** A focused, lightweight typing surface launched from the compact widget. */
class QuickTypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = QuickSage, background = QuickCanvas, surface = QuickSurface, onSurface = QuickInk)) {
                QuickTypeCapture()
            }
        }
    }

    @Composable
    private fun QuickTypeCapture() {
        val parser = remember { LocalTaskParser() }
        val focusRequester = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        var input by remember { mutableStateOf("") }
        var preview by remember { mutableStateOf<CapturedTask?>(null) }
        var message by remember { mutableStateOf("Type it naturally — date, time and repeat are understood.") }
        var isSyncing by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboard?.show()
        }

        Scaffold(containerColor = QuickCanvas) { inset ->
            Column(
                modifier = Modifier.fillMaxSize().padding(inset).padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Quick task", fontFamily = QuickSerif, fontSize = 36.sp, color = QuickInk)
                        Text("Type it as you would say it.", color = QuickMuted, fontSize = 14.sp)
                    }
                    OutlinedButton(
                        onClick = { finish() },
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, QuickRule),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = QuickInk)
                    ) { Text("Close") }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value
                        preview = value.takeIf { it.isNotBlank() }?.let(parser::parse)
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    label = { Text("What needs doing?", color = QuickMuted) },
                    placeholder = { Text("Buy milk before cereal becomes soup tomorrow at 8am", color = QuickMuted) },
                    minLines = 3,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = QuickSage, unfocusedBorderColor = QuickRule,
                        focusedContainerColor = QuickSurface, unfocusedContainerColor = QuickSurface,
                        focusedTextColor = QuickInk, unfocusedTextColor = QuickInk, cursorColor = QuickSage
                    )
                )
                preview?.let { task ->
                    QuickPreview(task, onTitleChange = { title ->
                        preview = task.copy(title = title)
                    })
                }
                Button(
                    onClick = {
                        val task = preview ?: return@Button
                        isSyncing = true
                        message = "Adding your task…"
                        lifecycleScope.launch {
                            try {
                                when (GoogleTaskSync(this@QuickTypeActivity).sync(task)) {
                                    SyncResult.NotConnected -> message = "Connect Google in TaskFlow before adding."
                                    is SyncResult.Success -> {
                                        TaskWidgetProvider.refresh(this@QuickTypeActivity)
                                        TaskAddedNotification.showAdded(this@QuickTypeActivity, task)
                                        message = "Added — a confirmation is ready in notifications."
                                        input = ""
                                        preview = null
                                    }
                                }
                            } catch (error: Exception) {
                                message = error.message ?: "Could not add that task."
                            } finally {
                                isSyncing = false
                            }
                        }
                    },
                    enabled = preview != null && !isSyncing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = QuickSage, contentColor = Color.White)
                ) { Text(if (isSyncing) "Adding…" else "Add task", fontWeight = FontWeight.SemiBold) }
                Card(
                    colors = CardDefaults.cardColors(containerColor = QuickSurface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, QuickRule, RoundedCornerShape(18.dp))
                ) {
                    Text(message, modifier = Modifier.padding(16.dp), color = QuickMuted, fontSize = 14.sp)
                }
            }
        }
    }

    @Composable
    private fun QuickPreview(task: CapturedTask, onTitleChange: (String) -> Unit) {
        var editingTitle by remember(task.sourceText) { mutableStateOf(false) }
        val date = task.dueDate?.format(DateTimeFormatter.ofPattern("EEE, d MMM")) ?: "No date"
        val time = task.dueTime?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "All day"
        Card(
            colors = CardDefaults.cardColors(containerColor = QuickSurface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, QuickRule, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (editingTitle) {
                        OutlinedTextField(
                            value = task.title,
                            onValueChange = onTitleChange,
                            modifier = Modifier.weight(1f),
                            label = { Text("Task title") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = QuickSage, unfocusedBorderColor = QuickRule,
                                focusedContainerColor = QuickSurface, unfocusedContainerColor = QuickSurface,
                                focusedTextColor = QuickInk, unfocusedTextColor = QuickInk, cursorColor = QuickSage
                            )
                        )
                    } else {
                        Text(task.title, modifier = Modifier.weight(1f), color = QuickInk, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { editingTitle = !editingTitle },
                        modifier = Modifier.padding(start = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, QuickRule),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = QuickSage)
                    ) { Text(if (editingTitle) "Done" else "Edit", fontSize = 10.sp) }
                }
                Text("$date  ·  $time", color = QuickMuted, fontSize = 13.sp)
                task.recurrence?.let { Text(it.label.uppercase(), color = QuickSage, fontSize = 10.sp) }
            }
        }
    }
}
