package com.nucleusflow.shizuku

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.nucleusflow.BuildConfig
import com.nucleusflow.core.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import rikka.shizuku.Shizuku

object ShizukuBridge {
    const val PERMISSION_REQUEST_CODE = 4242
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    data class State(
        val installed: Boolean = false,
        val running: Boolean = false,
        val permission: Boolean = false,
        val serviceBound: Boolean = false,
        val uid: Int = -1,
        val message: String = "Sin inicializar"
    )

    val state = MutableStateFlow(State())

    private var appContext: Context? = null
    private var bound = false
    private var binding = false
    private var registered = false

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        refreshInternal()
    }
    private val binderDead = Shizuku.OnBinderDeadListener {
        Shell.service = null
        bound = false
        refreshInternal()
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                bindService()
            }
            refreshInternal()
        }
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = IUserService.Stub.asInterface(binder)
            Shell.service = svc
            binding = false
            bound = svc != null
            val uid = runCatching { svc?.uid ?: -1 }.getOrDefault(-1)
            state.value = state.value.copy(
                serviceBound = bound,
                uid = uid,
                message = if (bound) "Servicio privilegiado activo (uid $uid)" else "No se pudo obtener el servicio"
            )
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Shell.service = null
            bound = false
            binding = false
            state.value = state.value.copy(serviceBound = false, uid = -1, message = "Servicio desconectado")
        }
    }

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (!registered) {
            registered = true
            runCatching {
                Shizuku.addBinderReceivedListenerSticky(binderReceived)
                Shizuku.addBinderDeadListener(binderDead)
                Shizuku.addRequestPermissionResultListener(permissionResult)
            }
        }
        refreshInternal()
    }

    fun refresh() = refreshInternal()

    fun requestPermission(activity: Activity) {
        if (!state.value.running) {
            state.value = state.value.copy(message = "Shizuku no está en ejecución. Ábrelo y actívalo.")
            return
        }
        if (state.value.permission) {
            bindService()
            return
        }
        try {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            state.value = state.value.copy(message = "Solicitando permiso a Shizuku…")
        } catch (t: Throwable) {
            state.value = state.value.copy(message = "Error al solicitar permiso: ${t.message}")
        }
    }

    private fun refreshInternal() {
        val ctx = appContext ?: return
        val installed = runCatching {
            ctx.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        }.getOrDefault(false)

        val running = runCatching {
            Shizuku.pingBinder()
        }.getOrDefault(false)

        val permission = running && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        if (running && permission && (Shell.service == null || !bound)) {
            bindService()
        }

        val msg = when {
            !installed -> "Shizuku no está instalado"
            !running -> "Shizuku instalado pero no en ejecución"
            !permission -> "Permiso de Shizuku pendiente"
            bound -> "Conectado a Shizuku (uid ${state.value.uid})"
            else -> "Conectando servicio privilegiado…"
        }

        state.value = state.value.copy(
            installed = installed,
            running = running,
            permission = permission,
            serviceBound = bound && Shell.service != null,
            message = msg
        )
    }

    private fun bindService() {
        val ctx = appContext ?: return
        if (bound || binding) return
        binding = true
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(ctx, NucleusUserService::class.java)
            )
                .daemon(false)
                .processNameSuffix("privileged")
                .debuggable(BuildConfig.DEBUG)
                .version(1)
            Shizuku.bindUserService(args, connection)
            state.value = state.value.copy(message = "Conectando servicio privilegiado…")
        } catch (t: Throwable) {
            binding = false
            state.value = state.value.copy(message = "Error al conectar servicio: ${t.message}")
        }
    }
}
