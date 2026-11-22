package com.example.integrations

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

data class TermuxCommandLog(
    val command: String,
    val status: String,
    val timestamp: Long = System.currentTimeMillis()
)

class TermuxBridge(private val context: Context) {
    private val appContext = context.applicationContext

    private val _logs = MutableStateFlow<List<TermuxCommandLog>>(emptyList())
    val logs: StateFlow<List<TermuxCommandLog>> = _logs.asStateFlow()

    fun isTermuxInstalled(): Boolean {
        return try {
            appContext.packageManager.getPackageInfo("com.termux", 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun hasRunCommandPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            "com.termux.permission.RUN_COMMAND"
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isSafeCommand(command: String): Pair<Boolean, String> {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return false to "Command cannot be empty."
        val blocked = listOf("rm -rf /", ":(){", "mkfs", "dd if=", "su ", "sudo ")
        if (blocked.any { trimmed.contains(it, ignoreCase = true) }) {
            return false to "Blocked potentially destructive or root-only command."
        }
        return true to "Validated"
    }

    fun executeCommand(
        command: String,
        workDir: String = "/data/data/com.termux/files/home",
        background: Boolean = false
    ): Result<String> {
        val (safe, reason) = isSafeCommand(command)
        if (!safe) return Result.failure(SecurityException(reason))
        if (!isTermuxInstalled()) {
            return Result.failure(IllegalStateException("Termux (com.termux) is not installed on this device."))
        }
        if (!hasRunCommandPermission()) {
            return Result.failure(SecurityException("Grant com.termux.permission.RUN_COMMAND in Android Settings > App Permissions."))
        }

        return try {
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", workDir)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
            }
            appContext.startService(intent)
            val msg = "Dispatched to Termux (${if (background) "background" else "foreground"}): $command"
            _logs.value = listOf(TermuxCommandLog(command, msg)) + _logs.value.take(29)
            Result.success(msg)
        } catch (e: Exception) {
            val err = "Termux RUN_COMMAND failed: ${e.localizedMessage}. Ensure 'allow-external-apps=true' in ~/.termux/termux.properties."
            _logs.value = listOf(TermuxCommandLog(command, err)) + _logs.value.take(29)
            Result.failure(IllegalStateException(err, e))
        }
    }

    suspend fun checkLocalPort(port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 600)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
