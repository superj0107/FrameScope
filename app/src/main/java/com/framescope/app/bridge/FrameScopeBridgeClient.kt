package com.framescope.app.bridge

import android.content.Context
import android.util.Base64
import com.framescope.app.shizuku.CommandResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for the small shell-UID bridge started by the one-time ADB command.
 *
 * The bridge uses a request/response mailbox under shared external storage.
 * Android 8.1 vendor SELinux policies may block both Unix sockets and TCP
 * connections between an app UID and a shell UID, while this shared-storage
 * path is available to both sides. Each request is one file and one response;
 * command output is base64 encoded because dumpsys output contains newlines.
 */
@Singleton
class FrameScopeBridgeClient @Inject constructor(
    @ApplicationContext context: Context
) {

    private val mailboxDirectory = (
        context.getExternalFilesDir("bridge")
            ?: File("/sdcard/Android/data/com.framescope.app/files/bridge")
    ).apply { mkdirs() }
    private val requestLock = Any()

    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val response = request("PING")
        if (response != "PONG") {
            com.framescope.app.utils.FrameScopeLog.w("Bridge ping response: ${response ?: "<null>"}", tag = "FrameScopeBridge")
        }
        response == "PONG"
    }

    suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        val response = request("EXEC\t${encode(command)}") ?: return@withContext ""
        if (!response.startsWith("OK\t")) return@withContext ""
        decode(response.substringAfter('\t'))
    }

    suspend fun executeCommandWithResult(command: String): CommandResult? = withContext(Dispatchers.IO) {
        val response = request("EXEC_RESULT\t${encode(command)}") ?: return@withContext null
        if (!response.startsWith("RESULT\t")) return@withContext null
        val fields = response.split('\t', limit = 3)
        if (fields.size != 3) return@withContext null
        CommandResult().apply {
            exitCode = fields[1].toIntOrNull() ?: -1
            output = decode(fields[2])
        }
    }

    suspend fun readProcStat(): String = executeCommand("cat /proc/stat")

    suspend fun getThermalTemperatures(): String = executeCommand("dumpsys thermalservice")

    private fun request(request: String): String? {
        synchronized(requestLock) {
            val requestId = "${System.currentTimeMillis()}_${android.os.Process.myPid()}_${request.hashCode().toUInt()}"
            val requestFile = File(mailboxDirectory, "request_$requestId")
            val temporaryRequestFile = File(mailboxDirectory, "${requestFile.name}.tmp")
            val responseFile = File(mailboxDirectory, "response_$requestId")
            return try {
                // Write to a temporary file first. The shell bridge scans the
                // directory continuously, so it must never observe a partial
                // request while the app is still writing the command.
                FileOutputStream(temporaryRequestFile).use { stream ->
                    stream.write(request.toByteArray(StandardCharsets.UTF_8))
                    stream.flush()
                }
                if (!temporaryRequestFile.renameTo(requestFile)) return null
                val deadline = System.currentTimeMillis() + REQUEST_TIMEOUT_MS
                while (System.currentTimeMillis() < deadline) {
                    if (responseFile.exists()) {
                        return responseFile.readText(StandardCharsets.UTF_8).trimEnd()
                    }
                    Thread.sleep(MAILBOX_POLL_INTERVAL_MS)
                }
                null
            } catch (e: Exception) {
                com.framescope.app.utils.FrameScopeLog.w(
                    "Bridge mailbox request failed: ${e.javaClass.simpleName}: ${e.message.orEmpty()}",
                    tag = "FrameScopeBridge"
                )
                null
            } finally {
                requestFile.delete()
                temporaryRequestFile.delete()
                responseFile.delete()
            }
        }
    }

    private fun encode(value: String): String =
        Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)

    private fun decode(value: String): String = try {
        String(Base64.decode(value, Base64.DEFAULT), StandardCharsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        ""
    }

    companion object {
        const val MAILBOX_PATH = "/sdcard/Android/data/com.framescope.app/files/bridge"
        private const val REQUEST_TIMEOUT_MS = 3000L
        private const val MAILBOX_POLL_INTERVAL_MS = 10L

        /** Command used by the Windows helper and shown in the in-app tutorial. */
        const val START_COMMAND =
            "CLASSPATH=\$(pm path com.framescope.app | cut -d: -f2); export CLASSPATH; app_process /system/bin com.framescope.app.bridge.ShellBridgeMain >/dev/null 2>&1 &"
    }
}
