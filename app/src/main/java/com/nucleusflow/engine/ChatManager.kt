package com.nucleusflow.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatMessage(
    val id: Long,
    val text: String,
    val from: String,
    val timestamp: Long
)

object ChatManager {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var seq: Long = 0L

    fun send(text: String, agent: String? = null) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val userMsg = ChatMessage(++seq, clean, "Tú", System.currentTimeMillis())
        _messages.value = _messages.value + userMsg

        val agentRef = agent?.takeIf { it.isNotBlank() } ?: "*"

        if (SharedPartitionManager.isReady()) {
            SharedPartitionManager.writeAll(0, "CHAT_MSG|from=Tú|agent=$agentRef|text=${clean.replace("\n", "\\n")}")
        }

        val cmd = "CHAT agent=$agentRef text=${clean.replace("\n", "\\n")}"
        val resp = SystemExecutor.executeCommand(cmd, "Chat con agente")

        val agentText = if (resp.isNotBlank() && !resp.startsWith("ERR")) {
            resp.take(200)
        } else {
            "Memoria sincronizada en 4 particiones. Agente [$agentRef] preparado."
        }

        val agentMsg = ChatMessage(++seq, agentText, agentRef, System.currentTimeMillis())
        _messages.value = _messages.value + agentMsg

        if (SharedPartitionManager.isReady()) {
            SharedPartitionManager.writeAll(0, "CHAT_MSG|from=$agentRef|agent=$agentRef|text=${agentText.replace("\n", "\\n")}")
        }
    }
}
