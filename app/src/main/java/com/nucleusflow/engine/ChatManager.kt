package com.nucleusflow.engine

data class ChatMessage(
    val id: Long,
    val text: String,
    val from: String,
    val timestamp: Long
)

object ChatManager {
    private val _messages = kotlinx.coroutines.flow.MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: kotlinx.coroutines.flow.StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var seq: Long = 0L

    fun send(text: String, agent: String? = null) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val userMsg = ChatMessage(++seq, clean, "Tú", System.currentTimeMillis())
        _messages.value = _messages.value + userMsg

        val agentRef = agent?.takeIf { it.isNotBlank() } ?: "*"
        val cmd = "CHAT agent=$agentRef text=${clean.replace("\n", "\\n")}"
        val resp = SystemExecutor.executeCommand(cmd, "Chat con agente")

        val agentMsg = ChatMessage(++seq, "Agente: ${resp.take(200)}", agentRef, System.currentTimeMillis())
        _messages.value = _messages.value + agentMsg
    }
}
