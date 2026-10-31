package com.nucleusflow.ui.screens.perapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nucleusflow.engine.PerAppManager

@Composable
fun PerAppScreen() {
    val profiles by PerAppManager.profiles.collectAsState()
    val fgApp by PerAppManager.currentForegroundApp.collectAsState()

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Gestión por App", style = MaterialTheme.typography.titleLarge)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Aplicación en Primer Plano", style = MaterialTheme.typography.titleMedium)
                Text(fgApp, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Perfiles Configurados", style = MaterialTheme.typography.titleMedium)
                if (profiles.isEmpty()) {
                    Text("No hay perfiles activos configurados.")
                } else {
                    profiles.values.forEach { profile ->
                        Text("• ${profile.packageName} (Gov: ${profile.governor})")
                    }
                }
            }
        }
    }
}
