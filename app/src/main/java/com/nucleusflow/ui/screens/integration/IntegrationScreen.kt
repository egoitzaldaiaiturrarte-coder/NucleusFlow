package com.nucleusflow.ui.screens.integration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nucleusflow.engine.SystemExecutor

@Composable
fun IntegrationScreen() {
    val logs by SystemExecutor.logs.collectAsState()

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Integración & Comunicación", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { SystemExecutor.clearLogs() }) {
                Text("Limpiar Logs")
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Mapa de Integración Dinámico", style = MaterialTheme.typography.titleMedium)
                Text("Comunicaciones bidireccionales y llamadas al kernel registradas en tiempo real.")
            }
        }

        if (logs.isEmpty()) {
            Card { Column(Modifier.padding(16.dp)) { Text("No hay registros de ejecución aún.") } }
        }

        logs.forEach { log ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (log.success) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("[${log.timestamp}] ${log.target}", style = MaterialTheme.typography.labelMedium)
                    Text("Ruta: ${log.path}", style = MaterialTheme.typography.bodySmall)
                    Text("Método: ${log.method}", style = MaterialTheme.typography.bodySmall)
                    Text("Comando: ${log.command}", style = MaterialTheme.typography.bodySmall)
                    Text("Respuesta: ${log.response}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
