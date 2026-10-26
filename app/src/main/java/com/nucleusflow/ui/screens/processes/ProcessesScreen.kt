package com.nucleusflow.ui.screens.processes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProcessesScreen() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Procesos & Hilos", style = MaterialTheme.typography.titleLarge)
        Card { Column(Modifier.padding(16.dp)) { Text("Listado /proc + filtrado por app. Asignación por máscara con sched_setaffinity (JNI). Fallback a método viable."); } }
    }
}
