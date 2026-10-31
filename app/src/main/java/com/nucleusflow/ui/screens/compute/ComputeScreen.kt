package com.nucleusflow.ui.screens.compute

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nucleusflow.engine.AndroidComputeBridge

@Composable
fun ComputeScreen() {
    val context = LocalContext.current
    val isRunning by AndroidComputeBridge.isComputeServerRunning.collectAsState()
    val logs by AndroidComputeBridge.computeLogs.collectAsState()
    val authToken by AndroidComputeBridge.authToken.collectAsState()
    val listenOnAllInterfaces by AndroidComputeBridge.listenOnAllInterfaces.collectAsState()

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nodo de Procesamiento Android", style = MaterialTheme.typography.titleLarge)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estado del Nodo: ${if (isRunning) "Activo (Puerto 8899)" else "Detenido"}", style = MaterialTheme.typography.titleMedium)
                Text("Bind: ${if (listenOnAllInterfaces) "0.0.0.0 (LAN)" else "127.0.0.1 (localhost)"}", style = MaterialTheme.typography.bodySmall)
                Text("Permite offloading de tareas de procesamiento desde Windows/host usando los núcleos del móvil Android.")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { AndroidComputeBridge.startComputeNode(context) }, enabled = !isRunning) {
                        Text("Iniciar Nodo")
                    }
                    Button(onClick = { AndroidComputeBridge.stopComputeNode() }, enabled = isRunning, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Detener")
                    }
                }

                Switch(
                    value = listenOnAllInterfaces,
                    onValueChange = { AndroidComputeBridge.setListenOnAllInterfaces(it) },
                    enabled = !isRunning
                )
                Text(
                    "Solo LAN, requiere token",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Token de Autenticacion", style = MaterialTheme.typography.titleMedium)
                if (authToken == null) {
                    Text("No generado. Inicia el nodo para generar el token.")
                } else {
                    Text(authToken!!)
                    Button(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("nucleusflow_auth_token", authToken))
                    }) {
                        Text("Copiar token")
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Logs de Procesamiento Remoto", style = MaterialTheme.typography.titleMedium)
                if (logs.isEmpty()) {
                    Text("Sin tareas recibidas.")
                } else {
                    logs.forEach { log ->
                        Text("• $log", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
