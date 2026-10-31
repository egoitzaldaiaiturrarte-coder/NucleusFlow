package com.nucleusflow.ui.screens.cores

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nucleusflow.engine.KernelManager
import com.nucleusflow.ui.components.ProExplainModal

@Composable
fun CoresScreen() {
    val clusters by KernelManager.clusters.collectAsState()
    val cores by KernelManager.cores.collectAsState()
    var showExplanation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        KernelManager.scanCores()
    }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Núcleos & Frecuencia", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { showExplanation = true }) {
                Text("Explicación Pro")
            }
        }

        if (clusters.isEmpty()) {
            Card { Column(Modifier.padding(16.dp)) { Text("Escaneando topología de núcleos...") } }
        }

        clusters.forEach { cluster ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Clúster ${cluster.clusterId} (Cores: ${cluster.coreIds.joinToString()})", style = MaterialTheme.typography.titleMedium)
                    Text("Governor actual: ${cluster.governor}")
                    Text("Frecuencia Máxima: ${cluster.maxFreq / 1000} MHz")

                    Text("Cambiar Governor:", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        cluster.availableGovernors.take(4).forEach { gov ->
                            Button(
                                onClick = { KernelManager.setClusterGovernor(cluster.clusterId, gov) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (cluster.governor == gov) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text(gov)
                            }
                        }
                    }
                }
            }
        }

        if (showExplanation) {
            ProExplainModal(
                title = "Control de Núcleos y Frecuencias",
                description = "Permite gestionar el gobernador de CPU y los límites de frecuencia por clúster.",
                howManaged = "Modifica archivos en /sys/devices/system/cpu/cpu*/cpufreq/",
                path = "/sys/devices/system/cpu/cpu*/cpufreq/scaling_governor",
                method = "Escritura directa vía Root/Shizuku o NativeCore",
                impact = "Optimiza rendimiento o consumo energético según el perfil elegido.",
                reversible = "Sí, totalmente reversible mediante restauración de snapshot o selección manual.",
                support = "Dependiente del kernel y soporte de drivers cpufreq.",
                notes = "Valores no compatibles son rechazados por el kernel automáticamente.",
                onDismiss = { showExplanation = false }
            )
        }
    }
}
