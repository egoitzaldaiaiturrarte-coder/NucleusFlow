package com.nucleusflow.ui.screens.chat

import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.nucleusflow.engine.RouterLink

/**
 * v1.11.1: el chat como PLANO DE CONTROL.
 *
 * - Fila de agentes: cada mensaje se dirige a un agente concreto
 *   (hermes = Hermes en el PC, web = bus Supabase, windows = agentes
 *   locales del PC, phone = el propio móvil, * = todos).
 * - Bloque "Plano (PC)": el enlace TCP al router del PC. Conectado: los
 *   mensajes se enrutan de verdad y las respuestas vuelven aqui como PUSH.
 *   Desconectado: el chat sigue funcionando local y encola para el plano.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val messages by ChatManager.messages.collectAsState()
    val linkState by RouterLink.state.collectAsState()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var autoImprove by remember { mutableStateOf(false) }
    var agent by remember { mutableStateOf("hermes") }
    var pcHost by remember { mutableStateOf("") }

    // Enlace de callbacks del plano al chat (una sola vez por composicion).
    LaunchedEffect(Unit) {
        ChatManager.attach(context)
        RouterLink.onPush = { from, text -> ChatManager.handlePush(from, text) }
        RouterLink.onConnected = { ChatManager.flushPending() }
        RouterLink.onRouteEvent = null
        RouterLink.onDisconnected = null
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val matches = data?.getStringArrayListExtra(SpeechRecognizer.RESULTS_RECOGNITION)
        val transcript = matches?.firstOrNull()?.toList()?.firstOrNull()?.toString()
        if (!transcript.isNullOrBlank()) {
            ChatManager.send(transcript, agent)
        }
    }

    fun send() {
        val t = input.trim()
        if (t.isNotBlank()) {
            ChatManager.send(t, agent)
            input = ""
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // === Plano de control: enlace al router del PC ===
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Plano (PC)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(end = 8.dp)
                )
                OutlinedTextField(
                    value = pcHost,
                    onValueChange = { pcHost = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("IP del PC (Tailscale o LAN)") }
                )
                if (linkState.connected) {
                    OutlinedButton(onClick = { RouterLink.stop() }) {
                        Text("Desconectar")
                    }
                } else {
                    OutlinedButton(onClick = {
                        val host = pcHost.trim().ifBlank { "127.0.0.1" }
                        RouterLink.start(context, host, RouterLink.DEFAULT_PORT)
                    }) {
                        Text("Conectar")
                    }
                }
            }
            Text(
                if (!linkState.hasToken && linkState.running) "Arranca el nodo de computo primero (token)."
                else linkState.lastEvent,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // === Selector de agente (el mensaje se dirige a uno concreto) ===
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            listOf("hermes", "web", "windows", "phone", "*").forEach { a ->
                TextButton(
                    onClick = { agent = a },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = when (a) {
                            "*" -> "todos"
                            else -> a
                        },
                        color = if (agent == a) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // === Chat ===
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
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
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
