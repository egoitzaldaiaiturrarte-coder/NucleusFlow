package com.nucleusflow.ui.screens.integration

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nucleusflow.core.AppState
import com.nucleusflow.core.Shell
import com.nucleusflow.shizuku.ShizukuBridge
import com.nucleusflow.ui.components.StatusPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun IntegrationScreen() {
    val context = LocalContext.current
    val shizuku by ShizukuBridge.state.collectAsState()
    val environment by AppState.environment.collectAsState()
    var droidBridge by remember { mutableStateOf(false) }
    var root by remember { mutableStateOf(Shell.rootAvailable) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        droidBridge = isInstalled(context, "com.droidbridge.standalone")
        root = withContext(Dispatchers.IO) { Shell.detectRoot() }
        ShizukuBridge.refresh()
        AppState.refreshEnvironment()
    }

    val envLines = remember(environment) {
        environment.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    }
    val writePaths = remember(environment) {
        envLines.filter { it.startsWith("W ") || it.startsWith("R ") || it.startsWith("- ") }
    }

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Integración con Android", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = {
                ShizukuBridge.refresh()
                scope.launch {
                    root = withContext(Dispatchers.IO) { Shell.detectRoot() }
                    AppState.refreshEnvironment()
                }
            }) { Text("Reescanear") }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Puentes disponibles", style = MaterialTheme.typography.titleMedium)
                EnvRow("Shizuku instalado", shizuku.installed)
                EnvRow("Shizuku en ejecución", shizuku.running)
                EnvRow("Permiso Shizuku", shizuku.permission)
                EnvRow("Shell privilegiado (${shizuku.uid})", shizuku.privileged)
                EnvRow("DroidBridge instalado", droidBridge, note = "detectado como puente alternativo")
                EnvRow("Root (su)", root)
                Text("Modo de ejecución actual: ${Shell.lastMode}", style = MaterialTheme.typography.bodySmall)
                Text(shizuku.message, style = MaterialTheme.typography.bodySmall)
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Dispositivo y kernel", style = MaterialTheme.typography.titleMedium)
                val idLine = envLines.firstOrNull { it.startsWith("uid=") }
                val kernel = envLines.firstOrNull { it.startsWith("Linux ") }
                val androidIdx = envLines.indexOf("== ANDROID ==")
                val android = if (androidIdx >= 0) envLines.getOrNull(androidIdx + 1) else null
                val model = if (androidIdx >= 0) envLines.getOrNull(androidIdx + 2) else null
                Text(idLine ?: "uid desconocido", style = MaterialTheme.typography.bodySmall)
                Text(kernel ?: "kernel desconocido", style = MaterialTheme.typography.bodySmall)
                Text("Android ${android.orEmpty()} · ${model.orEmpty()}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Rutas del sistema detectadas", style = MaterialTheme.typography.titleMedium)
                if (writePaths.isEmpty()) {
                    Text("Escaneando rutas…", style = MaterialTheme.typography.bodySmall)
                }
                writePaths.forEach { line ->
                    val writable = line.startsWith("W ")
                    val exists = !line.startsWith("- ")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(line.substring(2), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            when {
                                !exists -> "ausente"
                                writable -> "escritura"
                                else -> "solo lectura"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                !exists -> MaterialTheme.colorScheme.outline
                                writable -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Comunicación bidireccional", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Cada acción muestra el comando enviado y la respuesta del sistema en las pantallas de Núcleos, Procesos y Memoria.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Si una ruta aparece en solo lectura, Shizuku debe reiniciarse con root o vía ADB con permisos suficientes.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun EnvRow(label: String, active: Boolean, note: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (note != null) Text(note, style = MaterialTheme.typography.labelSmall)
        }
        StatusPill(if (active) "SÍ" else "NO", active)
    }
}

private fun isInstalled(context: Context, pkg: String): Boolean = runCatching {
    context.packageManager.getPackageInfo(pkg, 0)
    true
}.getOrDefault(false)
