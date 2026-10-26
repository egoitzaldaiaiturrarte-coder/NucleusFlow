package com.nucleusflow.ui.screens.cores

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CoresScreen() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Núcleos & Frecuencia", style = MaterialTheme.typography.titleLarge)
        Card { Column(Modifier.padding(16.dp)) { Text("Topología detectada en tiempo real desde /sys/devices/system/cpu/"); Text("Sliders de min/max + governor solo si escribibles", style = MaterialTheme.typography.bodySmall) } }
    }
}
