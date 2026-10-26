package com.nucleusflow.ui.screens.cores

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nucleusflow.core.Actions
import com.nucleusflow.core.AppState
import com.nucleusflow.core.CoreInfo
import com.nucleusflow.core.SysProbe
import com.nucleusflow.shizuku.ShizukuBridge
import com.nucleusflow.ui.components.ResultBanner
import com.nucleusflow.ui.components.StatusPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CoresScreen() {
    val cores by AppState.cores.collectAsState()
    val shizuku by ShizukuBridge.state.collectAsState()
    var result by remember { mutableStateOf<String?>(null) }
    var resultOk by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        AppState.refreshAll(includeProcesses = false)
        while (true) {
            delay(4000)
            AppState.refreshAll(includeProcesses = false)
        }
    }

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Núcleos & Frecuencia", style = MaterialTheme.typography.titleLarge)
            StatusPill(if (shizuku.serviceBound) "ESCRITURA" else "SOLO LECTURA", shizuku.serviceBound)
        }
        if (cores.isEmpty()) {
            Card { Column(Modifier.padding(16.dp)) {
                Text("Leyendo topología de CPU…")
                Text("Si persiste, conecta Shizuku en Inicio.", style = MaterialTheme.typography.bodySmall)
            } }
        }
        cores.forEach { core ->
            CoreCard(core) { msg, ok ->
                result = msg
                resultOk = ok
                AppState.refreshAll(includeProcesses = false)
            }
        }
        ResultBanner(result, resultOk)
    }
}

@Composable
private fun CoreCard(core: CoreInfo, onResult: (String, Boolean) -> Unit) {
    val freqList = remember(core.frequencies) {
        val f = core.frequencies.sorted()
        if (f.isEmpty()) listOf(core.minFreq, core.maxFreq).filter { it > 0 }.distinct().sorted() else f
    }
    var targetIndex by remember(core.maxFreq, freqList) {
        val idx = freqList.indexOfFirst { it >= core.maxFreq }
        mutableStateOf(if (idx >= 0) idx else freqList.lastIndex.coerceAtLeast(0))
    }
    var governor by remember(core.governor) { mutableStateOf(core.governor) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CPU ${core.index}", style = MaterialTheme.typography.titleMedium)
                Text(SysProbe.formatHz(core.curFreq), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "min ${SysProbe.formatHz(core.minFreq)} · max ${SysProbe.formatHz(core.maxFreq)} · hw ${SysProbe.formatHz(core.hwMax)}",
                style = MaterialTheme.typography.bodySmall
            )
            if (freqList.size > 1) {
                Text(
                    "Máx objetivo: ${SysProbe.formatHz(freqList[targetIndex])}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = targetIndex.toFloat(),
                    onValueChange = { targetIndex = it.toInt().coerceIn(0, freqList.lastIndex) },
                    valueRange = 0f..freqList.lastIndex.toFloat(),
                    steps = (freqList.size - 2).coerceAtLeast(0)
                )
            }
            if (core.governors.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    core.governors.forEach { g ->
                        FilterChip(
                            selected = governor == g,
                            onClick = { governor = g },
                            label = { Text(g) }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            var ok = true
                            val msgs = StringBuilder()
                            if (freqList.isNotEmpty()) {
                                val r = Actions.setFreq(core.index, null, freqList[targetIndex])
                                ok = ok && r.ok
                                msgs.append(r.message).append('\n')
                            }
                            if (governor.isNotBlank() && governor != core.governor) {
                                val r = Actions.setGovernor(core.index, governor)
                                ok = ok && r.ok
                                msgs.append(r.message).append('\n')
                            }
                            onResult(if (msgs.isBlank()) "Sin cambios" else msgs.toString().trim(), ok)
                            busy = false
                        }
                    }
                ) { Text(if (busy) "…" else "Aplicar") }
                OutlinedButton(
                    enabled = !busy && freqList.isNotEmpty(),
                    onClick = {
                        busy = true
                        scope.launch {
                            val r = Actions.setFreq(core.index, freqList.first(), freqList.last())
                            onResult(r.message, r.ok)
                            busy = false
                        }
                    }
                ) { Text("Restaurar") }
            }
        }
    }
}
