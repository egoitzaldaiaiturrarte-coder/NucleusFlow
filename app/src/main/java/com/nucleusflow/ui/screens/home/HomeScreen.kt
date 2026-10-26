package com.nucleusflow.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card { Column(Modifier.padding(16.dp)) { Text("NucleusFlow", style = MaterialTheme.typography.titleLarge); Text("Descubrimiento adaptativo • Shizuku/Root • Integración con Android") } }
        Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Estado", style = MaterialTheme.typography.titleMedium); Text("Escaneo dinámico activo. Solo se muestran rutas realmente accesibles.") } }
    }
}
