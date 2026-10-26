package com.nucleusflow.ui.screens.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nucleusflow.core.Actions
import com.nucleusflow.core.AppState
import com.nucleusflow.core.SysProbe
import com.nucleusflow.shizuku.ShizukuBridge
import com.nucleusflow.ui.components.ResultBanner
import com.nucleusflow.ui.components.StatusPill
import kotlinx.coroutines.launch

@Composable
fun MemoryScreen() {
    val memory by AppState.memory.collectAsState()
    val shizuku by ShizukuBridge.state.collectAsState()
    var result by remember { mutableStateOf<String?>(null) }
    var resultOk by remember { mutableStateOf(false) }
    var swappiness by remember(memory.swappiness) { mutableStateOf(memory.swappiness.coerceAtLeast(0).toFloat()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { AppState.refreshAll(includeProcesses = false) }

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Memoria & Swap", style = MaterialTheme.typography.titleLarge)
            StatusPill(if (shizuku.serviceBound) "ESCRITURA" else "SOLO LECTURA", shizuku.serviceBound)
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("RAM", style = MaterialTheme.typography.titleMedium)
                Text("Total: ${SysProbe.formatKb(memory.totalKb)}")
                Text("Disponible: ${SysProbe.formatKb(memory.availableKb)}")
                Text("Usada: ${SysProbe.formatKb(memory.usedKb)} (${memory.usedPct}%)")
                Text("Caché: ${SysProbe.formatKb(memory.cachedKb)}")
                LinearProgressIndicator(
                    progress = if (memory.totalKb > 0) memory.usedKb.toFloat() / memory.totalKb else 0f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Carga: ${memory.load.ifBlank { "—" }} · Uptime: ${formatUptime(memory.uptime)}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Swap / ZRAM", style = MaterialTheme.typography.titleMedium)
                Text("Total: ${SysProbe.formatKb(memory.swapTotalKb)} · Libre: ${SysProbe.formatKb(memory.swapFreeKb)}")
                memory.zram.forEach { z ->
                    Text(
                        "${z.name}: ${SysProbe.formatKb(z.disksize / 1024)} · usado ${SysProbe.formatKb(z.used / 1024)} · ${z.algorithm}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (memory.zram.isEmpty() && memory.swapTotalKb == 0L) {
                    Text("No se detectó swap ni ZRAM.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ajustes VM", style = MaterialTheme.typography.titleMedium)
                Text("swappiness: ${swappiness.toInt()}", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = swappiness,
                    onValueChange = { swappiness = it },
                    valueRange = 0f..200f,
                    steps = 19
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val r = Actions.setSwappiness(swappiness.toInt())
                            result = r.message
                            resultOk = r.ok
                            AppState.refreshAll(includeProcesses = false)
                        }
                    }) { Text("Guardar swappiness") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val r = Actions.compactMemory()
                            result = r.message
                            resultOk = r.ok
                            AppState.refreshAll(includeProcesses = false)
                        }
                    }) { Text("Compactar") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            val r = Actions.dropCaches()
                            result = r.message
                            resultOk = r.ok
                            AppState.refreshAll(includeProcesses = false)
                        }
                    }) { Text("Liberar caché") }
                }
                ResultBanner(result, resultOk)
            }
        }
    }
}

private fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "—"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return "${h}h ${m}m"
}
