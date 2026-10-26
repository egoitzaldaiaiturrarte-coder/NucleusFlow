package com.nucleusflow.ui.screens.processes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nucleusflow.core.Actions
import com.nucleusflow.core.AppState
import com.nucleusflow.core.ProcInfo
import com.nucleusflow.ui.components.ResultBanner
import kotlinx.coroutines.launch

@Composable
fun ProcessesScreen() {
    val processes by AppState.processes.collectAsState()
    val cores by AppState.cores.collectAsState()
    val busy by AppState.busy.collectAsState()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ProcInfo?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var resultOk by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { AppState.refreshAll(includeProcesses = true) }

    val filtered = remember(processes, query) {
        if (query.isBlank()) processes
        else processes.filter { it.name.contains(query, ignoreCase = true) || it.pid.toString() == query }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Procesos & Hilos", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = { AppState.refreshAll(includeProcesses = true) }) {
                Text(if (busy) "…" else "Refrescar")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Filtrar por nombre o PID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        ResultBanner(result, resultOk)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.pid }) { p ->
                Card {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name.take(48), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "pid ${p.pid} · oom ${p.oomAdj} · CPUs ${p.cpus} · ${p.state}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { selected = p }) { Text("Afinidad") }
                            TextButton(onClick = {
                                scope.launch {
                                    val r = Actions.killProcess(p.pid)
                                    result = r.message
                                    resultOk = r.ok
                                    AppState.refreshAll(includeProcesses = true)
                                }
                            }) { Text("Matar") }
                        }
                    }
                }
            }
        }
    }

    selected?.let { p ->
        AffinityDialog(
            proc = p,
            coreCount = cores.size.coerceAtLeast(1),
            onDismiss = { selected = null },
            onApply = { mask ->
                selected = null
                scope.launch {
                    val r = Actions.setAffinity(p.pid, mask)
                    result = r.message
                    resultOk = r.ok
                    AppState.refreshAll(includeProcesses = true)
                }
            },
            onOom = { value ->
                selected = null
                scope.launch {
                    val r = Actions.setOomAdj(p.pid, value)
                    result = r.message
                    resultOk = r.ok
                    AppState.refreshAll(includeProcesses = true)
                }
            }
        )
    }
}

@Composable
private fun AffinityDialog(
    proc: ProcInfo,
    coreCount: Int,
    onDismiss: () -> Unit,
    onApply: (Long) -> Unit,
    onOom: (Int) -> Unit
) {
    var mask by remember(proc.pid) {
        var initial = 0L
        for (i in 0 until coreCount) initial = initial or (1L shl i)
        mutableStateOf(initial)
    }
    var oom by remember(proc.pid) { mutableStateOf(proc.oomAdj) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Afinidad · ${proc.name.take(28)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Selecciona los núcleos permitidos:", style = MaterialTheme.typography.bodySmall)
                val chunks = (0 until coreCount).chunked(4)
                chunks.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { i ->
                            val checked = (mask and (1L shl i)) != 0L
                            FilterChip(
                                selected = checked,
                                onClick = {
                                    mask = if (checked) mask and (1L shl i).inv() else mask or (1L shl i)
                                },
                                label = { Text("CPU $i") }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("oom_score_adj: $oom", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = oom.toFloat(),
                    onValueChange = { oom = it.toInt() },
                    valueRange = -1000f..1000f,
                    steps = 19
                )
                Text(
                    "Negativo = más prioritario (requiere privilegios), positivo = más sacrificable.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(mask) }) { Text("Aplicar CPUs") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onOom(oom) }) { Text("Aplicar OOM") }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        }
    )
}
