package com.nucleusflow.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nucleusflow.engine.*
import com.nucleusflow.shizuku.ShizukuManager

@Composable
fun HomeScreen() {
    val shizukuAvailable by ShizukuManager.isShizukuAvailable.collectAsState()
    val permissionGranted by ShizukuManager.isPermissionGranted.collectAsState()
    val connectionMode by ShizukuManager.connectionMode.collectAsState()
    val cores by KernelManager.cores.collectAsState()
    val memory by MemoryManager.status.collectAsState()

    LaunchedEffect(Unit) {
        ShizukuManager.init(androidx.compose.ui.platform.LocalContext.current)
        KernelManager.scanCores()
        MemoryManager.scanMemory()
    }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NucleusFlow - Control Adaptativo", style = MaterialTheme.typography.titleLarge)
                Text("Modo de Conexión: $connectionMode", style = MaterialTheme.typography.bodyMedium)
                if (!permissionGranted) {
                    Button(onClick = { ShizukuManager.requestPermission() }) {
                        Text("Solicitar Permiso Shizuku")
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Resumen del Sistema", style = MaterialTheme.typography.titleMedium)
                Text("• Núcleos CPU detectados: ${cores.size}")
                Text("• ZRAM Habilitado: ${if (memory.zramEnabled) "Sí (${memory.zramDisksize})" else "No"}")
                Text("• Swappiness: ${memory.swappiness}")
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { SnapshotManager.createSnapshot() }
            ) {
                Text("Crear Snapshot")
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = { SnapshotManager.restoreSnapshot() }
            ) {
                Text("Restaurar")
            }
        }
    }
}
