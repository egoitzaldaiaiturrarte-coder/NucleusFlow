package com.nucleusflow.ui.screens.memory

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MemoryScreen() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Memoria & Swap", style = MaterialTheme.typography.titleLarge)
        Card { Column(Modifier.padding(16.dp)) { Text("ZRAM • SWAP • VM (/proc/sys/vm) • Compactación • Empuje a ZRAM/SWAP • Priorización a primer plano (oom_score_adj + cgroups)"); Text("Solo se muestran rutas escribibles. comp_algorithm leído desde kernel.", style = MaterialTheme.typography.bodySmall) } }
    }
}
