package com.nucleusflow.engine

import com.nucleusflow.shizuku.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppPackage(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean
)

object PackageManager {
    private val _installedPackages = MutableStateFlow<List<AppPackage>>(emptyList())
    val installedPackages: StateFlow<List<AppPackage>> = _installedPackages.asStateFlow()

    fun scanPackages() {
        val output = ShizukuManager.exec("pm list packages -f")
        val list = mutableListOf<AppPackage>()
        output.lineSequence().forEach { line ->
            if (line.startsWith("package:")) {
                val parts = line.removePrefix("package:").split("=")
                if (parts.size == 2) {
                    val apkPath = parts[0]
                    val pkgName = parts[1]
                    val isSys = apkPath.contains("/system/") || apkPath.contains("/product/")
                    list.add(AppPackage(packageName = pkgName, appName = pkgName.substringAfterLast('.'), isSystem = isSys))
                }
            }
        }
        _installedPackages.value = list.sortedBy { it.appName }
    }

    fun uninstallPackage(packageName: String): String {
        val cmd = "pm uninstall $packageName"
        val resp = ShizukuManager.exec(cmd)
        scanPackages()
        return resp
    }

    fun installApk(apkPath: String): String {
        val cmd = "pm install -r \"$apkPath\""
        val resp = ShizukuManager.exec(cmd)
        scanPackages()
        return resp
    }
}
