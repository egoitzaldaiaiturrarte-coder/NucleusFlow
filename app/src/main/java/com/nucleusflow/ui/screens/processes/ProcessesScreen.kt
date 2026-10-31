package com.nucleusflow.ui.screens.processes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nucleusflow.engine.ProcessManager

@Composable
fun ProcessesScreen() {
    val processes by ProcessManager.processes.collectAsState()
    var filterText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        ProcessManager.scanProcesses()
    }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Procesos & Hilos", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = filterText,
            onValueChange = { filterText = it },
            label = { Text("Filtrar por proceso o paquete") },
            modifier = Modifier.fillMaxWidth()
        )

        val filtered = processes.filter { it.name.contains(filterText, true) || it.packageName.contains(filterText, true) }

        if (filtered.isEmpty()) {
            Card { Column(Modifier.padding(16.dp)) { Text("Cargando procesos desde /proc...") } }
        }

        filtered.take(50).forEach { proc ->
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("PID: ${proc.pid} • ${proc.name}", style = MaterialTheme.typography.titleMedium)
                    Text("Paquete: ${proc.packageName}", style = MaterialTheme.typography.bodySmall)
                    Text("OOM Score Adj: ${proc.oomScoreAdj} • Hilos: ${proc.threads.size}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { ProcessManager.setOomScoreAdj(proc.pid, -1000) }) {
                            Text("Priorizar (-1000)")
                        }
                    }
                }
            }
        }
    }
}
