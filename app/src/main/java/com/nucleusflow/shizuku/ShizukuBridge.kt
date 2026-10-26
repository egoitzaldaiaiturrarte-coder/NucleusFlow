package com.nucleusflow.shizuku

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.nucleusflow.BuildConfig
import com.nucleusflow.core.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

object ShizukuBridge {
    private const val TAG = "NucleusFlow"
    const val PERMISSION_REQUEST_CODE = 4242
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val BIND_TIMEOUT_MS = 15_000L

    data class State(
        val installed: Boolean = false,
        val running: Boolean = false,
        val permission: Boolean = false,
        val serviceBound: Boolean = false,
        val privileged: Boolean = false,
        val uid: Int = -1,
        val message: String = "Sin inicializar"
    )

    val state = MutableStateFlow(State())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var bound = false

    @Volatile
    private var binding = false

    @Volatile
    private var listenersRegistered = false

    @Volatile
    private var probeChecked = false

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "shizuku binder received")
        refresh()
    }
    private val binderDead = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "shizuku binder dead")
        Shell.service = null
        bound = false
        binding = false
        refresh()
    }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            Log.i(TAG, "permission result code=$requestCode grant=$grantResult")
            if (requestCode == PERMISSION_REQUEST_CODE) refresh()
        }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "onServiceConnected component=$name binder=$binder")
            val svc = IUserService.Stub.asInterface(binder)
            Shell.service = svc
            binding = false
            bound = svc != null
            state.value = state.value.copy(
                serviceBound = bound,
                message = if (bound) "Servicio conectado, leyendo uid…" else "Binder nulo"
            )
            if (svc != null) {
                scope.launch {
                    val uid = runCatching { svc.uid }.getOrDefault(-1)
                    Log.i(TAG, "user service uid=$uid")
                    state.value = state.value.copy(
                        serviceBound = true,
                        uid = uid,
                        message = if (uid >= 0) "Servicio privilegiado activo (uid $uid)"
                        else "Servicio conectado pero uid no disponible"
                    )
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "onServiceDisconnected component=$name")
            Shell.service = null
            bound = false
            binding = false
            state.value = state.value.copy(
                serviceBound = false,
                uid = -1,
                message = "Servicio desconectado"
            )
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        ensureListeners()
        refresh()
    }

    fun refresh() {
        ensureListeners()
        scope.launch { refreshBlocking() }
    }

    fun requestPermission(activity: Activity) {
        scope.launch {
            val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            if (!running) {
                state.value = state.value.copy(
                    message = "Shizuku no está en ejecución. Ábrelo y actívalo."
                )
                return@launch
            }
            val granted = runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            if (granted) {
                refreshBlocking()
                return@launch
            }
            try {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
                state.value = state.value.copy(message = "Solicitando permiso a Shizuku…")
            } catch (t: Throwable) {
                Log.w(TAG, "requestPermission failed", t)
                state.value = state.value.copy(
                    message = "Error al solicitar permiso: ${t.message}"
                )
            }
        }
    }

    private fun appendDiag(ctx: Context, text: String) {
        runCatching {
            val f = java.io.File(ctx.getExternalFilesDir(null), "diagnostics.txt")
            f.appendText(text + "\n")
        }
    }

    fun probe(): String {
        val ctx = appContext ?: return "sin contexto"
        val sb = StringBuilder()
        sb.append("installed=").append(
            runCatching { ctx.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0); true }
                .getOrDefault(false)
        ).append('\n')
        sb.append("pingBinder=")
            .append(runCatching { Shizuku.pingBinder() }.getOrNull()).append('\n')
        sb.append("version=")
            .append(runCatching { Shizuku.getVersion() }.getOrNull()).append('\n')
        sb.append("checkSelfPermission=")
            .append(runCatching { Shizuku.checkSelfPermission() }.getOrNull()).append('\n')
        sb.append("uid(app)=").append(android.os.Process.myUid()).append('\n')
        sb.append("bound=").append(bound).append(" binding=").append(binding).append('\n')
        sb.append("newProcess=")
            .append(runCatching { execPrivileged("id") }.getOrNull()).append('\n')
        sb.append("markers=")
            .append(
                runCatching {
                    execPrivileged("echo A; cat /proc/loadavg; echo B; cat /proc/uptime; echo C")
                }.getOrNull()
            ).append('\n')
        val a = runCatching { buildArgs(ctx) }.getOrNull()
        if (a != null) {
            sb.append("suffix=service version=").append(BuildConfig.VERSION_CODE).append('\n')
            sb.append("peek=")
                .append(runCatching { Shizuku.peekUserService(a, connection) }.getOrNull())
                .append('\n')
        }
        val result = sb.toString()
        appendDiag(ctx, "=== probe ===\n$result")
        return result
    }

    fun execPrivileged(cmd: String): String? {
        if (!state.value.running) return null
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val out = StringBuilder()
            process.inputStream.bufferedReader().use { it.forEachLine { l -> out.append(l).append('\n') } }
            process.errorStream.bufferedReader().use { it.forEachLine { l -> out.append("ERR:").append(l).append('\n') } }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                "$out\nERR:timeout"
            } else {
                out.toString()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "newProcess failed", t)
            "ERR:newProcess ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    private fun ensureListeners() {
        if (listenersRegistered) return
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
            listenersRegistered = true
            Log.i(TAG, "shizuku listeners registered")
        } catch (t: Throwable) {
            Log.w(TAG, "listener registration failed: ${t.message}")
        }
    }

    private fun refreshBlocking() {
        val ctx = appContext ?: return
        val installed = runCatching {
            ctx.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        }.getOrDefault(false)
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val version = runCatching { Shizuku.getVersion() }.getOrDefault(-1)
        val permission = running && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        Log.i(
            TAG,
            "refresh installed=$installed running=$running version=$version " +
                "permission=$permission bound=$bound binding=$binding"
        )

        var privileged = state.value.privileged
        var uid = state.value.uid
        if (running && permission && !privileged) {
            val id = runCatching { execPrivileged("id") }.getOrNull()
            Log.i(TAG, "privileged check: $id")
            val m = Regex("uid=(\\d+)").find(id.orEmpty())
            if (m != null) {
                privileged = true
                uid = m.groupValues[1].toIntOrNull() ?: -1
                appendDiag(ctx, "privileged ok uid=$uid")
            } else {
                appendDiag(ctx, "privileged check failed: $id")
            }
            probeChecked = true
        }

        val msg = when {
            !installed -> "Shizuku no está instalado"
            !running -> "Shizuku instalado pero no en ejecución"
            !permission -> "Permiso de Shizuku pendiente"
            privileged -> "Shell privilegiado activo (uid $uid). Escrituras sysfs pueden requerir root."
            else -> "Shizuku activo, pero sin shell privilegiado"
        }

        state.value = state.value.copy(
            installed = installed,
            running = running,
            permission = permission,
            privileged = privileged,
            serviceBound = bound && Shell.service != null,
            uid = uid,
            message = msg
        )
    }

    private fun buildArgs(ctx: Context): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(ComponentName(ctx, NucleusUserService::class.java))
            .daemon(false)
            .processNameSuffix("service")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

    private fun startBind() {
        val ctx = appContext ?: return
        val args = try {
            buildArgs(ctx)
        } catch (t: Throwable) {
            state.value = state.value.copy(message = "Error creando args: ${t.message}")
            return
        }
        binding = true
        state.value = state.value.copy(message = "Conectando servicio privilegiado…")
        try {
            Shizuku.bindUserService(args, connection)
            Log.i(TAG, "bindUserService called")
        } catch (t: Throwable) {
            binding = false
            Log.w(TAG, "bindUserService failed", t)
            state.value = state.value.copy(message = "Error al conectar servicio: ${t.message}")
            return
        }
        scope.launch {
            delay(BIND_TIMEOUT_MS)
            if (!bound && binding) {
                val peek = runCatching { Shizuku.peekUserService(args, connection) }.getOrNull()
                Log.w(TAG, "bind timeout, peek=$peek")
                binding = false
                runCatching { Shizuku.unbindUserService(args, connection, false) }
                state.value = state.value.copy(
                    message = "El UserService no arrancó (peek=$peek). Revisa el log de Shizuku."
                )
            }
        }
    }
}
