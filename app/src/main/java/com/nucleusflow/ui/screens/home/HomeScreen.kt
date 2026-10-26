package com.nucleusflow.ui.screens.home

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nucleusflow.core.Actions
import com.nucleusflow.core.AppState
import com.nucleusflow.core.Shell
import com.nucleusflow.core.SysProbe
import com.nucleusflow.shizuku.ShizukuBridge
import com.nucleusflow.ui.components.ResultBanner
import com.nucleusflow.ui.components.StatusPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val shizuku by ShizukuBridge.state.collectAsState()
    val cores by AppState.cores.collectAsState()
    val memory by AppState.memory.collectAsState()
    val busy by AppState.busy.collectAsState()
    var result by remember { mutableStateOf<String?>(null) }
    var resultOk by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        AppState.refreshAll(includeProcesses = false)
        while (true) {
            delay(3000)
            if (ShizukuBridge.state.value.privileged) {
                AppState.refreshAll(includeProcesses = false)
            }
        }
    }

    val active = shizuku.privileged || Shell.rootAvailable
    val mode = when {
        shizuku.privileged -> "Activo · shell Shizuku (uid ${shizuku.uid})"
        Shell.rootAvailable -> "Activo · Root"
        else -> "Inactivo · sin privilegios"
    }

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("NucleusFlow", style = MaterialTheme.typography.titleLarge)
        Text(
            "Control de CPU, afinidad y memoria con privilegios reales",
            style = MaterialTheme.typography.bodyMedium
        )

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Estado del puente", style = MaterialTheme.typography.titleMedium)
                    StatusPill(if (active) "ACTIVO" else "INACTIVO", active)
                }
                Text(mode, style = MaterialTheme.typography.bodyMedium)
                Text(shizuku.message, style = MaterialTheme.typography.bodySmall)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            activity?.let { ShizukuBridge.requestPermission(it) }
                                ?: run { result = "No se pudo obtener la actividad"; resultOk = false }
                        },
                        enabled = true
                    ) {
                        Text(if (active) "Refrescar estado" else "Conectar con Shizuku")
                    }
                    OutlinedButton(
                        onClick = {
                            ShizukuBridge.refresh()
                            AppState.refreshAll()
                        }
                    ) { Text(if (busy) "…" else "Refrescar") }
                }
                if (!shizuku.running) {
                    Text(
                        "Shizuku debe estar en ejecución. Ábrelo, actívalo y vuelve aquí.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (shizuku.privileged) {
                    Text(
                        "Shell de Shizuku operativo. CPU/gobernador y memoria se leen en vivo; " +
                            "algunas escrituras sysfs solo funcionan con root.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Núcleos", if (cores.isEmpty()) "—" else cores.size.toString(), Modifier.weight(1f))
            StatCard(
                "Frec. media",
                if (cores.isEmpty()) "—" else SysProbe.formatHz(cores.map { it.curFreq }.filter { it > 0 }.average().toLong()),
                Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("RAM usada", if (memory.totalKb == 0L) "—" else "${memory.usedPct}%", Modifier.weight(1f))
            StatCard("Carga", memory.load.substringBefore(' ').ifEmpty { "—" }, Modifier.weight(1f))
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Memoria", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${SysProbe.formatKb(memory.usedKb)} usados de ${SysProbe.formatKb(memory.totalKb)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                LinearProgressIndicator(
                    progress = if (memory.totalKb > 0) memory.usedKb.toFloat() / memory.totalKb else 0f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Swap: ${SysProbe.formatKb(memory.swapTotalKb - memory.swapFreeKb)} / ${SysProbe.formatKb(memory.swapTotalKb)}" +
                        if (memory.zram.isNotEmpty()) " · ZRAM ${memory.zram.size}" else "",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Acciones rápidas", style = MaterialTheme.typography.titleMedium)
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
                if (!active) {
                    Text(
                        "Estas acciones requieren Shizuku activo (o root).",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Light
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
