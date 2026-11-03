package com.nucleusflow.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatMessage(
    val id: Long,
    val text: String,
    val from: String,
    val timestamp: Long
)

/**
 * ChatManager — v1.11.1: el chat como PLANO DE CONTROL.
 *
 * Antes: `send()` escribia CHAT_MSG en las 4 particiones y "disparaba" un
 * comando `CHAT agent=X` que no existia (respuesta carton). Ahora:
 *
 *  - Conectado al plano (RouterLink con el PC router): cada mensaje viaja como
 *    `ROUTER agent=<a> text=<t>`; el router lo entrega al agente
 *    (hermes/web/windows/phone/*) y la respuesta vuelve como PUSH y se
 *    muestra aqui. Cada respuesta se espeja en las particiones (bus Supabase)
 *    como CHAT_MSG, igual que antes: la memoria compartida sigue siendo la
 *    fuente de verdad entre agentes.
 *  - Sin PC: el mensaje se encola localmente (persistido) y se re-envia como
 *    `SYNC|CHAT_MSG|...` en cuanto el enlace se (re)conecta: nada se pierde.
 *
 * El enrutado real en el PC (quien consume inbox/hermes, inbox/web, ...)
 * vive en `router/nucleus_router.py` + los consumidores de cada agente.
 */
object ChatManager {
    private const val TAG = "ChatManager"

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var seq: Long = 0L

    // Cola de mensajes sin enrutar (PC desconectado). Persistida en un prefs
    // simple (no son secretos: son el chat del usuario en su propio equipo).
    private var prefs: android.content.SharedPreferences? = null
    private val pending = ArrayDeque<Pair<String, String>>() // (agent, text)

    fun attach(context: Context) {
        val p = prefs ?: context.getSharedPreferences("chat_router", Context.MODE_PRIVATE).also {
            prefs = it
        }
        // restaura la cola
        try {
            val raw = p.getString("pending", "") ?: ""
            if (raw.isNotEmpty()) {
                for (entry in raw.split("|||")) {
                    val i = entry.indexOf("::")
                    if (i > 0) pending.add(entry.substring(0, i) to entry.substring(i + 2))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "cola de chat: ${e.message}")
        }
    }

    private fun persistPending() {
        prefs?.edit()?.putString(
            "pending",
            pending.joinToString("|||") { (a, t) -> "$a::$t" }
        )?.apply()
    }

    fun isConnected(): Boolean = RouterLink.state.value.connected

    fun send(text: String, agent: String? = null) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val userMsg = ChatMessage(++seq, clean, "Tú", System.currentTimeMillis())
        _messages.value = _messages.value + userMsg

        val agentRef = agent?.takeIf { it.isNotBlank() } ?: "*"
        val escaped = clean.replace("\n", "\\n")

        // Espejo en la memoria compartida (bus Supabase) — igual que antes.
        if (SharedPartitionManager.isReady()) {
            SharedPartitionManager.writeAll(0, "CHAT_MSG|from=Tú|agent=$agentRef|text=$escaped")
        }

        if (isConnected()) {
            val ok = RouterLink.sendLine("ROUTER agent=$agentRef text=$escaped")
            if (ok) {
                val confirm = if (agentRef == "phone") {
                    "El móvil lo resuelve localmente."
                } else if (agentRef == "*") {
                    "Enviado a todos los agentes (el PC confirma a cada uno)."
                } else {
                    "Enrutado a [$agentRef] en el PC; la respuesta llegara por enlace."
                }
                addMessage(confirm, "plano")
            }
        } else {
            pending.add(agentRef to escaped)
            persistPending()
            addMessage("En cola: el PC no esta conectado al plano. Se enrutara al reconectar (ningun mensaje se pierde).", "plano")
        }
    }

    /** (RouterLink onConnected) Re-envia la cola pendiente al plano. */
    fun flushPending() {
        if (pending.isEmpty() || !isConnected()) return
        for ((agent, text) in pending) {
            RouterLink.sendLine("SYNC|CHAT_MSG|from=Tú|agent=$agent|text=$text")
        }
        pending.clear()
        persistPending()
        addMessage("Cola pendiente enrutada al plano (${_messages.value.size} mensajes en vista).", "plano")
    }

    /** (RouterLink onPush) Respuesta de un agente viaja del PC al chat. */
    fun handlePush(from: String, text: String) {
        val clean = text.replace("\\n", "\n")
        addMessage(clean, from)
        // Espejo en la memoria compartida: el agente ya "hablo"; la bus lo guarda.
        if (SharedPartitionManager.isReady()) {
            SharedPartitionManager.writeAll(0, "CHAT_MSG|from=$from|agent=$from|text=${clean.replace("\n", "\\n")}")
        }
    }

    private fun addMessage(text: String, from: String) {
        val msg = ChatMessage(++seq, text, from, System.currentTimeMillis())
        _messages.value = _messages.value + msg
    }
}
