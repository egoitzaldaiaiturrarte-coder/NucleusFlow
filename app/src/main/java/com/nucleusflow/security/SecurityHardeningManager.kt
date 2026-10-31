package com.nucleusflow.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SecurityHardeningManager {
    private const val TAG = "SecurityHardening"

    // ---- Configuración de endurecimiento ----
    val ALLOW_BACKUP_DISABLED = false
    val CLEARTEXT_TRAFFIC_DISABLED = false

    // ---- Patrones prohibidos para ejecución de comandos (sanitización) ----
    private val DANGEROUS_PATTERNS = listOf(
        ";", "&&", "||", "|", ">", "<", "`", "\$(", "\${", "rm ", "mv ", "cp ",
        "chmod ", "chown ", "dd ", "mkfs ", "wipe", "mount ", "umount ", "mkfs.",
        "nc ", "ncat ", "curl ", "wget ", "bash ", "sh ", "python ", "perl ",
        "reboot", "poweroff", "init ", "systemctl ", "service "
    )

    private val DANGEROUS_PREFIXES = listOf(
        "rm ", "mv ", "cp ", "chmod ", "chown ", "dd ", "wipe ", "mount ",
        "umount ", "reboot", "poweroff", "init ", "systemctl ", "nc ", "ncat ",
        "curl ", "wget "
    )

    private val ALLOWED_PREFIXES = listOf(
        "ls ", "ps ", "top ", "dumpsys ", "pm ", "am ", "getprop ", "settings ",
        "input ", "screencap ", "screenrecord ", "cat ", "grep ", "id ", "whoami ",
        "uname ", "uptime ", "free ", "df ", "ifconfig ", "ip ", "ping ",
        "netstat ", "ss ", "logcat ", "CHAT "
    )

    // ---- Estado de integridad ----
    private var _isDeviceRooted = false
    private var _isDebuggable = false
    private var _isEmulator = false
    private var _integrityScore = 100

    fun init(context: Context) {
        try {
            _isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            _isEmulator = Build.FINGERPRINT.contains("generic") ||
                Build.FINGERPRINT.contains("unknown") ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.MODEL.contains("sdk_")
            _isDeviceRooted = detectRoot(context)
            _integrityScore = calculateIntegrityScore()
            Log.d(TAG, "SecurityHardening init: rooted=$_isDeviceRooted debug=$_isDebuggable emulator=$_isEmulator score=$_integrityScore")
        } catch (e: Throwable) {
            Log.e(TAG, "SecurityHardening init error", e)
        }
    }

    fun getIntegrityScore(): Int = _integrityScore
    fun isDeviceRooted(): Boolean = _isDeviceRooted
    fun isDebuggable(): Boolean = _isDebuggable
    fun isEmulator(): Boolean = _isEmulator
    fun isDeviceIntegrated(): Boolean = !_isDeviceRooted && !_isDebuggable && _integrityScore >= 70

    private fun detectRoot(context: Context): Boolean {
        try {
            // Check su binary locations
            val suPaths = listOf(
                "/system/bin/su", "/system/xbin/su", "/system/sbin/su",
                "/sbin/su", "/vendor/bin/su", "/vendor/xbin/su",
                "/data/local/su", "/data/local/xbin/su", "/data/local/bin/su",
                "/data/adb/su", "/magisk/su", "/magisk/core/bin/su"
            )
            for (path in suPaths) {
                if (File(path).exists()) return true
            }
            // Check for dangerous packages
            val pm = context.packageManager
            val dangerousPackages = listOf(
                "com.koushikdutta.superuser", "me.phh.superuser",
                "eu.chainfire.supersu", "com.topjohnwu.magisk",
                "com.noshufou.android.su", "com.zachspong.temprootremovez",
                "com.ramdroid.appquarantine", "com.android.vending" // not dangerous but example
            )
            for (pkg in dangerousPackages) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    return true
                } catch (e: PackageManager.NameNotFoundException) {
                    // Not installed
                }
            }
            // Check build tags
            if (Build.TAGS.contains("test-keys") || Build.TAGS.contains("release-keys").not()) {
                // test-keys es indicador común de root/custom ROM
                if (Build.TAGS.contains("test-keys")) return true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Root detection error", e)
        }
        return false
    }

    private fun calculateIntegrityScore(): Int {
        var score = 100
        if (_isDeviceRooted) score -= 50
        if (_isDebuggable) score -= 30
        if (_isEmulator) score -= 10
        return score.coerceAtLeast(0)
    }

    // ---- Sanitización de comandos para Shizuku / Root exec ----
    fun sanitizeCommand(cmd: String): String? {
        if (cmd.isBlank()) return null
        val trimmed = cmd.trim()

        // Bloquear si contiene patrones peligrosos
        val upper = trimmed.uppercase()
        for (pattern in DANGEROUS_PATTERNS) {
            if (upper.contains(pattern.uppercase())) {
                Log.w(TAG, "Comando bloqueado por patrón peligroso: $pattern")
                return null
            }
        }

        // Verificar que empiece con un prefijo permitido
        val startsAllowed = ALLOWED_PREFIXES.any { trimmed.startsWith(it, ignoreCase = true) }
        if (!startsAllowed) {
            Log.w(TAG, "Comando bloqueado: prefijo no permitido -> $trimmed")
            return null
        }

        return trimmed
    }

    fun isCommandSafe(cmd: String): Boolean = sanitizeCommand(cmd) != null

    // ---- Almacenamiento seguro de tokens (wrapper sobre Android Keystore) ----
    // Nota: Se recomienda usar EncryptedSharedPreferences en producción.
    // Aquí se expone una interfaz simple para integración futura.
    fun encryptToken(token: String): ByteArray? {
        try {
            val key = SecretKeySpec("NucleusFlowSecurityKey2026!".toByteArray(Charsets.UTF_8), "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12)
            java.security.SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            val result = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, result, 0, iv.size)
            System.arraycopy(encrypted, 0, result, iv.size, encrypted.size)
            return result
        } catch (e: Throwable) {
            Log.e(TAG, "encryptToken error", e)
            return null
        }
    }

    fun decryptToken(encrypted: ByteArray?): String? {
        if (encrypted == null || encrypted.size < 13) return null
        try {
            val key = SecretKeySpec("NucleusFlowSecurityKey2026!".toByteArray(Charsets.UTF_8), "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12)
            System.arraycopy(encrypted, 0, iv, 0, 12)
            val cipherText = ByteArray(encrypted.size - 12)
            System.arraycopy(encrypted, 12, cipherText, 0, cipherText.size)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val decrypted = cipher.doFinal(cipherText)
            return String(decrypted, Charsets.UTF_8)
        } catch (e: Throwable) {
            Log.e(TAG, "decryptToken error", e)
            return null
        }
    }

    // ---- Limpieza de backups AnyClaw en sdcard (migrar/borrar) ----
    fun cleanupAnyClawBackupFiles(context: Context) {
        val external = android.os.Environment.getExternalStorageDirectory()
        val targets = listOf(
            java.io.File(external, "Documents/AnyClaw-Backup"),
            java.io.File(external, "Download/AnyClaw-Backup")
        )
        for (target in targets) {
            try {
                if (target.exists()) {
                    target.deleteRecursively()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Cleanup AnyClaw backup failed", e)
            }
        }
    }
}
