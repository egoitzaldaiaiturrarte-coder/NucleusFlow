package com.nucleusflow.ui.screens.compute

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nucleusflow.engine.AndroidComputeBridge

@Composable
fun ComputeScreen() {
    val isRunning by AndroidComputeBridge.isComputeServerRunning.collectAsState()
    val logs by AndroidComputeBridge.computeLogs.collectAsState()

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nodo de Procesamiento Android", style = MaterialTheme.typography.titleLarge)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estado del Nodo: ${if (isRunning) "Activo (Puerto 8899)" else "Detenido"}", style = MaterialTheme.typography.titleMedium)
                Text("Permite offloading de tareas de procesamiento desde Windows/host usando los núcleos del móvil Android.")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { AndroidComputeBridge.startComputeNode() }, enabled = !isRunning) {
                        Text("Iniciar Nodo")
                    }
                    Button(onClick = { AndroidComputeBridge.stopComputeNode() }, enabled = isRunning, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Detener")
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
