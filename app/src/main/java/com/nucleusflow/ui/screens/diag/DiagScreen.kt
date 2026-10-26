package com.nucleusflow.ui.screens.diag

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nucleusflow.core.Actions
import com.nucleusflow.core.AppState
import com.nucleusflow.core.Shell
import com.nucleusflow.shizuku.ShizukuBridge
import com.nucleusflow.ui.components.ResultBanner
import com.nucleusflow.ui.components.StatusPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiagScreen() {
    val shizuku by ShizukuBridge.state.collectAsState()
    var output by remember { mutableStateOf("Pulsa una prueba para ejecutarla.") }
    var result by remember { mutableStateOf<String?>(null) }
    var resultOk by remember { mutableStateOf(true) }
    var root by remember { mutableStateOf(Shell.rootAvailable) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { root = withContext(Dispatchers.IO) { Shell.detectRoot() } }

    fun run(title: String, cmd: String) {
        scope.launch {
            output = "» $title\n\n${Shell.exec(cmd)}"
        }
    }

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Diagnóstico", style = MaterialTheme.typography.titleLarge)
            StatusPill(if (shizuku.serviceBound) "SHIZUKU ${shizuku.uid}" else "SIN SERVICIO", shizuku.serviceBound)
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pruebas de integración", style = MaterialTheme.typography.titleMedium)
                Text("Modo: ${Shell.lastMode} · Root: ${if (root) "sí" else "no"}", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { run("Identidad", "id; echo; getprop ro.product.model; getprop ro.build.version.release") }) { Text("id") }
                    OutlinedButton(onClick = { run("Kernel", "uname -a; echo; cat /proc/version") }) { Text("Kernel") }
                    OutlinedButton(onClick = { run("Memoria", "head -20 /proc/meminfo; echo; cat /proc/swaps") }) { Text("Memoria") }
                    OutlinedButton(onClick = { run("CPU", "cat /sys/devices/system/cpu/present; echo; ls /sys/devices/system/cpu/cpufreq 2>/dev/null") }) { Text("CPU") }
                    OutlinedButton(onClick = { run("Batería", "dumpsys battery 2>&1 | head -25") }) { Text("Batería") }
                    OutlinedButton(onClick = { run("SELinux", "getenforce; echo; getprop ro.boot.selinux") }) { Text("SELinux") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            val path = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
                            val cur = Shell.exec("cat $path 2>/dev/null").trim()
                            if (cur.isBlank()) {
                                result = "No se pudo leer $path"
                                resultOk = false
                            } else {
                                val r = Actions.runRaw("echo '$cur' > $path 2>&1 && echo OK || echo FAIL")
                                result = "Prueba de escritura en $path: ${r.message}"
                                resultOk = r.ok && !r.message.contains("FAIL")
                            }
                        }
                    }) { Text("Escritura sysfs") }
                    OutlinedButton(onClick = { run("Paquetes", "pm list packages -3 2>&1 | head -40") }) { Text("Apps") }
                }
                ResultBanner(result, resultOk)
            }
        }

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Salida", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    output,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = horizontalArrangement,
        content = content
    )
}
