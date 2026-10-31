package com.nucleusflow.ui.screens.chat

import android.content.Intent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nucleusflow.engine.ChatManager
import com.nucleusflow.engine.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val messages by ChatManager.messages.collectAsState()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var autoImprove by remember { mutableStateOf(false) }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val matches = data?.getStringArrayListExtra(SpeechRecognizer.RESULTS_RECOGNITION)
        val transcript = matches?.firstOrNull()?.toList()?.firstOrNull()?.toString()
        if (!transcript.isNullOrBlank()) {
            ChatManager.send(transcript)
        }
    }

    fun send() {
        val t = input.trim()
        if (t.isNotBlank()) {
            ChatManager.send(t)
            input = ""
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(messages, key = { it.id }) { m: ChatMessage ->
                Text(text = "${m.from}: ${m.text}", modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                singleLine = true,
                label = { Text("Mensaje") }
            )
            IconButton(onClick = {
                val intent = Intent(SpeechRecognizer.ACTION_RECOGNIZE_SPEECH)
                voiceLauncher.launch(intent)
            }) {
                Icon(imageVector = Icons.Default.Mic, contentDescription = "Dictar")
            }
            Button(onClick = { send() }) { Text("Enviar") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Auto-mejora")
            Switch(checked = autoImprove, onCheckedChange = { autoImprove = it })
        }
    }
}
