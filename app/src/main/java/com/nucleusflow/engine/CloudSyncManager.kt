package com.nucleusflow.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object CloudSyncManager {
    private const val TAG = "CloudSyncManager"

    private val _supabaseUrl = MutableStateFlow("")
    val supabaseUrl: StateFlow<String> = _supabaseUrl.asStateFlow()

    private val _supabaseKey = MutableStateFlow("")
    val supabaseKey: StateFlow<String> = _supabaseKey.asStateFlow()

    private val _githubToken = MutableStateFlow("")
    val githubToken: StateFlow<String> = _githubToken.asStateFlow()

    private val _githubUsername = MutableStateFlow("No conectado")
    val githubUsername: StateFlow<String> = _githubUsername.asStateFlow()

    private val _syncStatus = MutableStateFlow("Sin sincronizar")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    fun configureSupabase(url: String, key: String) {
        _supabaseUrl.value = url
        _supabaseKey.value = key
        _syncStatus.value = "Supabase configurado"
    }

    suspend fun loginGitHub(token: String): Boolean = withContext(Dispatchers.IO) {
        _githubToken.value = token
        try {
            val url = URL("https://api.github.com/user")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 5000
            val code = conn.responseCode
            if (code == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val username = response.substringAfter("\"login\":\"").substringBefore("\"")
                _githubUsername.value = username
                _syncStatus.value = "GitHub Conectado: $username"
                true
            } else {
                _githubUsername.value = "Error de autenticación"
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "GitHub login failed", e)
            _githubUsername.value = "Error de red"
            false
        }
    }

    suspend fun syncToSupabase(payloadJson: String): Boolean = withContext(Dispatchers.IO) {
        val urlStr = _supabaseUrl.value
        val key = _supabaseKey.value
        if (urlStr.isBlank() || key.isBlank()) {
            _syncStatus.value = "Supabase no configurado"
            return@withContext false
        }

        try {
            val url = URL("$urlStr/rest/v1/nucleus_profiles")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", key)
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates")
            conn.doOutput = true
            conn.outputStream.write(payloadJson.toByteArray())
            val code = conn.responseCode
            val success = code in 200..299
            _syncStatus.value = if (success) "Sincronizado con Supabase exitosamente" else "Error Supabase code: $code"
            success
        } catch (e: Exception) {
            Log.e(TAG, "Supabase sync failed", e)
            _syncStatus.value = "Error de sincronización Supabase"
            false
        }
    }
}
