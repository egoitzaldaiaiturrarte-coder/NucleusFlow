package com.nucleusflow.ui.screens.cloud

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nucleusflow.engine.CloudSyncManager
import com.nucleusflow.engine.PackageManager
import kotlinx.coroutines.launch

@Composable
fun CloudScreen() {
    val coroutineScope = rememberCoroutineScope()
    val githubUser by CloudSyncManager.githubUsername.collectAsState()
    val syncStatus by CloudSyncManager.syncStatus.collectAsState()
    val packages by PackageManager.installedPackages.collectAsState()

    var ghTokenInput by remember { mutableStateOf("") }
    var supabaseUrlInput by remember { mutableStateOf("") }
    var supabaseKeyInput by remember { mutableStateOf("") }
    var packageFilter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        PackageManager.scanPackages()
    }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nube, Cuentas & Paquetes", style = MaterialTheme.typography.titleLarge)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Autenticación GitHub", style = MaterialTheme.typography.titleMedium)
                Text("Estado: $githubUser", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = ghTokenInput,
                    onValueChange = { ghTokenInput = it },
                    label = { Text("GitHub Personal Access Token") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    coroutineScope.launch {
                        CloudSyncManager.loginGitHub(ghTokenInput)
                    }
                }) {
                    Text("Conectar GitHub")
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Secretos Supabase", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = supabaseUrlInput,
                    onValueChange = { supabaseUrlInput = it },
                    label = { Text("Supabase URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = supabaseKeyInput,
                    onValueChange = { supabaseKeyInput = it },
                    label = { Text("Supabase Anon Key") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    CloudSyncManager.configureSupabase(supabaseUrlInput, supabaseKeyInput)
                }) {
                    Text("Configurar Supabase")
                }
                Text("Estado Sync: $syncStatus", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Gestión de Paquetes (Shizuku PM)", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = packageFilter,
                    onValueChange = { packageFilter = it },
                    label = { Text("Buscar paquete (ej. nucleusflow)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { PackageManager.scanPackages() }) {
                    Text("Escanear Paquetes")
                }

                val filtered = packages.filter { it.packageName.contains(packageFilter, true) }
                filtered.take(10).forEach { pkg ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(pkg.appName, modifier = Modifier.weight(1f))
                        Button(onClick = {
                            PackageManager.uninstallPackage(pkg.packageName)
                        }) {
                            Text("Desinstalar")
                        }
                    }
                }
            }
        }
    }
}
