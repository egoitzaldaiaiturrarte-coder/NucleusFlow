package com.nucleusflow.ui.screens.perapp

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nucleusflow.core.AppState
import com.nucleusflow.core.Shell
import com.nucleusflow.ui.components.ResultBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Profile(val mask: Long, val oom: Int)

@Composable
fun PerAppScreen() {
    val context = LocalContext.current
    val packages by AppState.packages.collectAsState()
    val cores by AppState.cores.collectAsState()
    val prefs = remember { context.getSharedPreferences("nucleus_profiles", Context.MODE_PRIVATE) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var resultOk by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        AppState.refreshPackages()
        if (AppState.cores.value.isEmpty()) AppState.refreshAll(includeProcesses = false)
    }

    val filtered = remember(packages, query) {
        if (query.isBlank()) packages else packages.filter { it.contains(query, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Gestión por App", style = MaterialTheme.typography.titleLarge)
        Text(
            "Perfiles guardados: ${prefs.all.size} · se aplican a los procesos en ejecución",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar paquete") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        ResultBanner(result, resultOk)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it }) { pkg ->
                val prof = prefs.all[pkg] as? String
                Card {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(pkg, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                prof ?: "sin perfil",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { selected = pkg }) { Text("Configurar") }
                    }
                }
            }
        }
    }

    selected?.let { pkg ->
        val stored = prefs.getString(pkg, null)
        var mask by remember(pkg) {
            val coreCount = cores.size.coerceAtLeast(1)
            val default = (0 until coreCount).fold(0L) { acc, i -> acc or (1L shl i) }
            mutableStateOf(
                stored?.substringBefore(';')?.toLongOrNull() ?: default
            )
        }
        var oom by remember(pkg) {
            mutableStateOf(stored?.substringAfter(';', "0")?.toIntOrNull() ?: 0)
        }
        val coreCount = cores.size.coerceAtLeast(1)

        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(pkg) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Núcleos permitidos:", style = MaterialTheme.typography.bodySmall)
                    (0 until coreCount).chunked(4).forEach { row ->
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
                    Text("Prioridad OOM: $oom", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = oom.toFloat(),
                        onValueChange = { oom = it.toInt() },
                        valueRange = -1000f..1000f,
                        steps = 19
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putString(pkg, "$mask;$oom").apply()
                    selected = null
                    scope.launch {
                        val r = applyProfile(pkg, mask, oom)
                        result = r.first
                        resultOk = r.second
                    }
                }) { Text("Guardar y aplicar") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancelar") } }
        )
    }
}

private suspend fun applyProfile(pkg: String, mask: Long, oom: Int): Pair<String, Boolean> =
    withContext(Dispatchers.IO) {
        val hex = java.lang.Long.toHexString(mask)
        val cmd = """
pids=${'$'}(pidof $pkg 2>/dev/null || echo "")
if [ -z "${'$'}pids" ]; then echo "NO_PIDS"; exit 0; fi
for pid in ${'$'}pids; do
  taskset -p $hex ${'$'}pid 2>&1
  echo $oom > /proc/${'$'}pid/oom_score_adj 2>&1
  echo "APPLIED ${'$'}pid ${'$'}(grep -m1 '^Cpus_allowed_list:' /proc/${'$'}pid/status 2>/dev/null | cut -f2)"
done
""".trimIndent()
        val out = Shell.execBlocking(cmd)
        if (out.contains("NO_PIDS")) {
            "Perfil guardado. $pkg no está en ejecución ahora." to true
        } else {
            val ok = !out.contains("Permission denied") && !out.contains("Invalid argument")
            out.trim() to ok
        }
    }
