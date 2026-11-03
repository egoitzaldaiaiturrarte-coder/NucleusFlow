package com.nucleusflow.engine

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * RouterLink — v1.11.1: el móvil CONECTADO al plano de control del PC.
 *
 * Cliente TCP de `router/nucleus_router.py` (corre en el PC, puerto 41050):
 * el chat de NucleusFlow deja de ser un cartón — cada mensaje se enruta al
 * agente indicado (hermes = Hermes en el PC, web = bus Supabase,
 * windows = agentes locales del PC, phone = el propio móvil, * = todos)
 * y las respuestas vuelven por este canal como lineas PUSH.
 *
 * Protocolo (lineas \n, mismo estilo que AndroidComputeBridge):
 *   AUTH:<token>  -> AUTH:OK | ERR:AUTH:n
 *   PING          -> PONG
 *   STATUS        -> STATUS:agents=...|inbox=...
 *   ROUTER agent=<a> text=<t> -> ROUTED:<a>|<estado>
 *   POLL           -> [PUSH from=<a> text=<t>]* + POLL:END | POLL:EMPTY
 *   SYNC|<linea CHAT_MSG>      -> re-enrutado (cola del telefono tras reinicio)
 *
 * Token: el MISMO del nodo de computo (EncryptedSharedPreferences
 * "nucleus_compute_auth" — lo gestiona AndroidComputeBridge.getToken).
 * Una sola clave para todo el plano; nunca viaja por codigo, solo entre los
 * dispositivos del usuario (mismo criterio que el adb forward de hoy).
 *
 * Un solo hilo (un socket, un lector, una cola de salida): no hay escrituras
 * concurrentes al socket. Reconexion con backoff 2s..30s; al reconectar
 * re-autentica y hace POLL para recoger respuestas pendientes (store &
 * forward: nada se pierde si el PC se duerme en el medio).
 */
object RouterLink {
    private const val TAG = "RouterLink"
    const val DEFAULT_PORT = 41050
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val INITIAL_BACKOFF_MS = 2_000L
    private const val MAX_BACKOFF_MS = 30_000L
    private const val MAX_AUTH_FAILURES = 5

    data class State(
        val running: Boolean = false,
        val connected: Boolean = false,
        val host: String = "",
        val port: Int = DEFAULT_PORT,
        val hasToken: Boolean = false,
        val lastEvent: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** (UI) Callback para lineas PUSH del router: el chat muestra la respuesta. */
    @Volatile var onPush: ((from: String, text: String) -> Unit)? = null
    /** (UI) Callback de estado de enrutado (ROUTED:..., ERR:...). */
    @Volatile var onRouteEvent: ((line: String) -> Unit)? = null
    /** (UI) Callback al (re)conectar: el chat envia su cola de mensajes pendientes. */
    @Volatile var onConnected: (() -> Unit)? = null
    /** (UI) Callback al perder conexion. */
    @Volatile var onDisconnected: (() -> Unit)? = null

    @Volatile private var host: String = ""
    @Volatile private var port: Int = DEFAULT_PORT
    @Volatile private var stopRequested = true
    @Volatile private var token: String? = null

    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null

    private fun emit(s: State) {
        _state.value = s
    }

    private fun current(): State = _state.value

    /** (UI) Lee el token del nodo Android (mismo almacén que el bridge). */
    fun readToken(context: Context): String? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                "nucleus_compute_auth",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            prefs.getString("auth_token", null)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "sin token del nodo: ${e.message}")
            null
        }
    }

    /** (UI) Arranca el enlace hacia el PC router. */
    fun start(context: Context, host: String, port: Int) {
        this.host = host.trim().takeIf { it.isNotEmpty() } ?: "127.0.0.1"
        this.port = port.coerceIn(1, 65535)
        this.token = readToken(context)
        stopRequested = false
        if (running.compareAndSet(false, true)) {
            worker = Thread({ loop() }, "nucleus-router-link").apply {
                isDaemon = true
                start()
            }
        }
        emit(current().copy(running = true, host = this.host, port = this.port,
            hasToken = token != null,
            lastEvent = if (token == null) "Sin token: arranca el nodo de computo primero"
            else "Conectando a $this.host:$this.port..."))
    }

    /** (UI) Detiene el enlace. */
    fun stop() {
        stopRequested = true
        running.set(false)
        try {
            currentSocket?.close()
        } catch (ignored: Exception) {
        }
        emit(current().copy(running = false, connected = false, lastEvent = "Enlace detenido"))
    }

    /** (UI) Envio a la cola de salida (no bloquea; lo escribe el hilo del socket). */
    fun sendLine(line: String): Boolean {
        val s = current()
        if (!s.connected) return false
        outQueue.add(line)
        outSignal.set(true)
        return true
    }

    // ================= hilo del socket =================

    @Volatile private var currentSocket: Socket? = null
    private val outQueue = ArrayDeque<String>()
    private val outSignal = AtomicBoolean(false)

    private fun loop() {
        var backoff = INITIAL_BACKOFF_MS
        while (!stopRequested) {
            val ok = runSocket()
            if (stopRequested) break
            if (ok) {
                backoff = INITIAL_BACKOFF_MS
                continue
            }
            emit(current().copy(connected = false,
                lastEvent = "PC no alcanzable en $host:$port; reintento en ${backoff / 1000}s"))
            try {
                Thread.sleep(backoff)
            } catch (e: InterruptedException) {
                break
            }
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
        running.set(false)
        emit(current().copy(connected = false, running = stopRequested.not()))
    }

    /** Un intento de conexion completo. Devuelve true si duró lo suficiente (AUTH ok). */
    private fun runSocket(): Boolean {
        var socket: Socket? = null
        var writer: OutputStream? = null
        var reader: BufferedReader? = null
        var authenticated = false
        try {
            socket = Socket().apply {
                soTimeout = 2_000 // el lector usa polling; los envios usan la cola
            }
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            currentSocket = socket
            writer = socket.getOutputStream()
            reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            authenticated = doAuth(writer)
            if (!authenticated) {
                emit(current().copy(connected = false,
                    lastEvent = "El PC rechazo el token (ERR:AUTH)"))
                return false
            }
            emit(current().copy(connected = true, lastEvent = "Conectado al plano de control ($host:$port)"))
            onConnected?.invoke()
            // Recoge respuestas pendientes (store & forward) y luego monitorea.
            drainInbox(reader, writer, initial = true)
            while (!stopRequested) {
                val line = readLineWithTimeout(reader) ?: break
                handleServerLine(line)
            }
        } catch (e: Exception) {
            Log.w(TAG, "router link: ${e.message}")
        } finally {
            try {
                reader?.close()
            } catch (ignored: Exception) {
            }
            try {
                writer?.close()
            } catch (ignored: Exception) {
            }
            try {
                socket?.close()
            } catch (ignored: Exception) {
            }
            if (currentSocket === socket) currentSocket = null
            if (authenticated) {
                emit(current().copy(connected = false, lastEvent = "Desconectado"))
                onDisconnected?.invoke()
            }
        }
        return authenticated
    }

    private fun doAuth(writer: OutputStream): Boolean {
        val t = token
        if (t == null) return false
        writeLine(writer, "AUTH:$t")
        // Espera (con timeout) la respuesta de auth.
        val deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            // el lector principal se arranca despues; aqui leemos directos
            val line = currentSocket?.getInputStream()?.buffered()?.readLine()
                ?: return false
            when {
                line == "AUTH:OK" -> return true
                line.startsWith("ERR:AUTH") -> return false
                else -> continue
            }
        }
        return false
    }

    /**
     * Lector principal: lineas del router. Tambien vacia la cola de salida
     * entre lecturas (mismo hilo = sin escrituras concurrentes).
     */
    private fun drainInbox(reader: BufferedReader, writer: OutputStream, initial: Boolean) {
        while (!stopRequested) {
            drainOutQueue(writer)
            val line = readLineWithTimeout(reader) ?: return
            handleServerLine(line)
        }
    }

    private fun readLineWithTimeout(reader: BufferedReader): String? {
        // soTimeout 2s: un timeout es "seguir esperando", no error.
        try {
            while (true) {
                val line = reader.readLine()
                if (line != null) return line
                // timeout (linea null): sigue, salvo que pidan detener
                if (stopRequested) return null
            }
        } catch (e: java.net.SocketTimeoutException) {
            // el while interno lo convierte en bucle; defensivo:
            return if (stopRequested) null else ""
        }
    }

    private fun handleServerLine(line: String) {
        when {
            line.startsWith("PUSH from=") -> {
                val m = Regex("PUSH from=(\\S+) text=(.*)").find(line)
                if (m != null) onPush?.invoke(m.groupValues[1], m.groupValues[2])
            }
            line.startsWith("ROUTED:") || line.startsWith("ERR:") || line.startsWith("POLL:") -> {
                emit(current().copy(lastEvent = line))
                onRouteEvent?.invoke(line)
            }
            line == "PONG" -> Unit
            line.isBlank() -> Unit
            else -> Log.d(TAG, "router line: $line")
        }
    }

    private fun drainOutQueue(writer: OutputStream) {
        while (outSignal.getAndSet(false)) {
            val line = outQueue.removeFirstOrNull() ?: return
            writeLine(writer, line)
        }
    }

    private fun writeLine(writer: OutputStream, line: String) {
        try {
            writer.write(line.toByteArray(Charsets.UTF_8))
            writer.write('\n'.code.toByte())
            writer.flush()
        } catch (e: Exception) {
            Log.w(TAG, "envio fallido: ${e.message}")
        }
    }
}
