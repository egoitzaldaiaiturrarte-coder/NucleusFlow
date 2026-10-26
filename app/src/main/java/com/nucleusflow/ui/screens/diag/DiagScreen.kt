package com.nucleusflow.ui.screens.diag

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DiagScreen() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Diagnóstico", style = MaterialTheme.typography.titleLarge)
        Card { Column(Modifier.padding(16.dp)) { Text("Integración Android • Test lectura/escritura • errno/strerror • rutas probadas • snapshot/restaurar"); } }
    }
}
