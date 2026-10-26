package com.nucleusflow.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProExplainModal(
    title: String,
    description: String,
    howManaged: String,
    path: String,
    method: String,
    impact: String,
    reversible: String,
    support: String,
    notes: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Section("Descripción", description)
                Section("¿Cómo lo gestiona Android/Kernel?", howManaged)
                Section("Ruta / Origen", path)
                Section("Método en esta app", method)
                Section("Impacto esperado", impact)
                Section("Reversible", reversible)
                Section("Soporte en este dispositivo", support)
                if (notes.isNotBlank()) Section("Notas / Advertencias", notes)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun Section(h: String, b: String) {
    Column {
        Text(h, style = MaterialTheme.typography.labelLarge)
        Text(b, style = MaterialTheme.typography.bodySmall)
    }
}
