package com.nucleusflow.shizuku

import android.os.IBinder
import java.io.BufferedReader
import java.io.InputStreamReader

class NucleusUserService : IUserService.Stub() {
    override fun destroy() {}
    override fun exit() {}

    override fun exec(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
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
            out.toString()
        } catch (e: Exception) {
            "ERR:${e.message}"
        }
    }

    override fun getUid(): Int = android.os.Process.myUid()
}
