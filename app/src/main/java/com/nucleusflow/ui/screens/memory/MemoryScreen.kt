package com.nucleusflow.ui.screens.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nucleusflow.engine.MemoryManager

@Composable
fun MemoryScreen() {
    val memory by MemoryManager.status.collectAsState()

    LaunchedEffect(Unit) {
        MemoryManager.scanMemory()
    }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Memoria & Swap", style = MaterialTheme.typography.titleLarge)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ZRAM & Swap", style = MaterialTheme.typography.titleMedium)
                Text("ZRAM Habilitado: ${if (memory.zramEnabled) "Sí" else "No"}")
                Text("Tamaño ZRAM: ${memory.zramDisksize}")
                Text("Algoritmo de compresión: ${memory.zramCompAlgorithm}")
                Text("Swappiness: ${memory.swappiness}")
                Text("VFS Cache Pressure: ${memory.vfsCachePressure}")
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { MemoryManager.compactMemory() }
            ) {
                Text("Compactar Memoria")
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = { MemoryManager.pushToZram() }
            ) {
                Text("Liberar Caches")
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Swaps Activos", style = MaterialTheme.typography.titleMedium)
                Text(memory.swapInfo.ifBlank { "Sin swap activo" }, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
