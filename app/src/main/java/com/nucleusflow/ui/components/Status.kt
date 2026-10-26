package com.nucleusflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun StatusPill(text: String, active: Boolean) {
    val bg = if (active) Color(0xFF1B5E20) else Color(0xFF5D4037)
    val fg = if (active) Color(0xFFA5D6A7) else Color(0xFFFFCCBC)
    Text(
        text = text,
        color = fg,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun ResultBanner(message: String?, ok: Boolean = true) {
    if (message.isNullOrBlank()) return
    val bg = if (ok) Color(0xFF1B5E20) else Color(0xFFB71C1C)
    Text(
        text = message,
        color = Color.White,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
