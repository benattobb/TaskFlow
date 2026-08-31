package com.example.taskflow

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import com.example.taskflow.google.GoogleTaskSync
import com.example.taskflow.google.SyncResult
import com.example.taskflow.nlp.LocalTaskParser
import com.example.taskflow.widget.TaskWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Short-lived, user-initiated voice capture launched from a widget. */
class VoiceCaptureActivity : Activity() {
    private var recognizer: SpeechRecognizer? = null
    private var isAdding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), microphoneRequest)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == microphoneRequest && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
        else finish()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Voice input is unavailable on this device.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) { finish() }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) addSpokenTask(spoken)
                    else finish()
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your task")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            })
        }
    }

    private fun addSpokenTask(spoken: String) {
        if (isAdding) return
        isAdding = true
        recognizer?.destroy()
        val task = LocalTaskParser().parse(spoken)
        CoroutineScope(Dispatchers.Main).launch {
            runCatching { GoogleTaskSync(this@VoiceCaptureActivity).sync(task) }
                .onSuccess { result ->
                    when (result) {
                        SyncResult.NotConnected -> {
                            TaskAddedNotification.showNeedsSetup(this@VoiceCaptureActivity, task)
                            Toast.makeText(this@VoiceCaptureActivity, "Connect Google to add this task.", Toast.LENGTH_SHORT).show()
                        }
                        is SyncResult.Success -> {
                            TaskWidgetProvider.refresh(this@VoiceCaptureActivity)
                            TaskAddedNotification.showAdded(this@VoiceCaptureActivity, task)
                            Toast.makeText(this@VoiceCaptureActivity, "Task added.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .onFailure {
                    TaskAddedNotification.showFailed(this@VoiceCaptureActivity, task)
                    Toast.makeText(this@VoiceCaptureActivity, "Couldn't add that task. Tap the notification to edit.", Toast.LENGTH_LONG).show()
                }
            finishAndRemoveTask()
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        super.onDestroy()
    }

    companion object {
        const val extraCapturedText = "com.example.taskflow.CAPTURED_TEXT"
        private const val microphoneRequest = 17
    }
}
