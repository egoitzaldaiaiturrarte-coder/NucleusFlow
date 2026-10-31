package com.nucleusflow.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

object ShizukuManager {
    private const val TAG = "ShizukuManager"

    private val _isShizukuAvailable = MutableStateFlow(false)
    val isShizukuAvailable: StateFlow<Boolean> = _isShizukuAvailable.asStateFlow()

    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    private val _connectionMode = MutableStateFlow("Desconectado")
    val connectionMode: StateFlow<String> = _connectionMode.asStateFlow()

    private var userService: IUserService? = null

    private val userServiceArgs: Shizuku.UserServiceArgs
        get() = Shizuku.UserServiceArgs(
            ComponentName("com.nucleusflow", NucleusUserService::class.java.name)
        )
        .daemon(false)
        .processNameSuffix("nucleus_service")
        .version(1)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                userService = IUserService.Stub.asInterface(binder)
                _isServiceConnected.value = true
                _connectionMode.value = "Shizuku UserService"
                Log.d(TAG, "Shizuku UserService connected successfully")
                try {
                    binder.linkToDeath({
                        Log.d(TAG, "UserService died")
                        userService = null
                        _isServiceConnected.value = false
                        _connectionMode.value = "Desconectado"
                    }, 0)
                } catch (e: RemoteException) {
                    Log.e(TAG, "Failed to link to death", e)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
            _isServiceConnected.value = false
            _connectionMode.value = "Desconectado"
            Log.d(TAG, "Shizuku UserService disconnected")
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateStatus()
        bindUserService()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _isShizukuAvailable.value = false
        _isServiceConnected.value = false
        userService = null
        _connectionMode.value = "Desconectado"
    }

    private val permissionGrantedListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 1001) {
            val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
            _isPermissionGranted.value = granted
            if (granted) {
                bindUserService()
            } else {
                checkRootFallback()
            }
        }
    }

    fun init(context: Context) {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionGrantedListener)
            updateStatus()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize Shizuku", e)
            checkRootFallback()
        }
    }

    fun updateStatus() {
        try {
            val available = Shizuku.pingBinder()
            _isShizukuAvailable.value = available
            if (available) {
                val granted = Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
                _isPermissionGranted.value = granted
                if (granted) {
                    if (userService == null) {
                        bindUserService()
                    } else {
                        _isServiceConnected.value = true
                        _connectionMode.value = "Shizuku UserService"
                    }
                } else {
                    _connectionMode.value = "Shizuku sin permiso"
                }
            } else {
                checkRootFallback()
            }
        } catch (e: Throwable) {
            _isShizukuAvailable.value = false
            checkRootFallback()
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(1001)
                } else {
                    _isPermissionGranted.value = true
                    bindUserService()
                }
            } else {
                checkRootFallback()
            }
        } catch (e: Throwable) {
            checkRootFallback()
        }
    }

    private fun bindUserService() {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to bind Shizuku UserService", e)
            checkRootFallback()
        }
    }

    private fun checkRootFallback() {
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exitCode = p.waitFor()
            if (exitCode == 0) {
                _connectionMode.value = "Root (su)"
                return
            }
        } catch (ignored: Throwable) {}
        _connectionMode.value = "Modo Estándar (Local)"
    }

    fun exec(cmd: String): String {
        // Try Shizuku UserService first
        try {
            val service = userService
            if (service != null) {
                return service.exec(cmd)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Shizuku exec failed, falling back", e)
        }

        // Try Root su
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val out = StringBuilder()
            BufferedReader(InputStreamReader(p.inputStream)).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) out.append(line).append('\n')
            }
            BufferedReader(InputStreamReader(p.errorStream)).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) out.append("ERR:").append(line).append('\n')
            }
            p.waitFor()
            return out.toString()
        } catch (e: Throwable) {
            Log.w(TAG, "Root exec failed, falling back to local sh", e)
        }

        // Fallback local sh
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = StringBuilder()
            BufferedReader(InputStreamReader(p.inputStream)).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) out.append(line).append('\n')
            }
            p.waitFor()
            out.toString()
        } catch (e: Throwable) {
            "ERR:${e.message}"
        }
    }

    fun isRootOrPrivileged(): Boolean {
        val mode = _connectionMode.value
        return mode.contains("Shizuku") || mode.contains("Root")
    }
}
