package com.nucleusflow.ui.screens.diag

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nucleusflow.engine.DiagManager

@Composable
fun DiagScreen() {
    val results by DiagManager.results.collectAsState()

    LaunchedEffect(Unit) {
        DiagManager.runDiagnostics()
    }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Diagnóstico del Sistema", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { DiagManager.runDiagnostics() }) {
                Text("Ejecutar Test")
            }
        }

        if (results.isEmpty()) {
            Card { Column(Modifier.padding(16.dp)) { Text("Ejecutando pruebas de diagnóstico...") } }
        }

        results.forEach { res ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (res.success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(res.testName, style = MaterialTheme.typography.titleMedium)
                    Text(res.details, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
