package com.nucleusflow.native

object NativeCore {
    init {
        System.loadLibrary("nucleusflow")
    }

    external fun setAffinity(pid: Int, tid: Int, mask: Long): Boolean
    external fun getAffinity(pid: Int, tid: Int): Long
    external fun readFile(path: String): String
    external fun writeFile(path: String, value: String): Boolean
}
