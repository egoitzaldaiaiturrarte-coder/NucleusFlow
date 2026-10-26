package com.nucleusflow.ui.screens.perapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PerAppScreen() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Gestión por App", style = MaterialTheme.typography.titleLarge)
        Card { Column(Modifier.padding(16.dp)) { Text("Perfiles por paquete con aplicación al primer plano + restaura al salir."); } }
    }
}
