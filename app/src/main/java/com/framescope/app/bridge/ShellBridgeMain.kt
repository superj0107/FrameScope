package com.framescope.app.bridge

import android.os.Process
import android.util.Base64
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.io.File
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Entry point for app_process. This class is loaded from the FrameScope APK by
 * `adb shell`, therefore the process keeps the shell UID instead of the normal
 * application UID. It is intentionally tiny: the app remains the UI and this
 * process only executes the existing diagnostic commands and returns output.
 */
object ShellBridgeMain {

    @JvmStatic
    fun main(args: Array<String>) {
        if (Process.myUid() != Process.SHELL_UID) {
            return
        }

        val mailbox = File(FrameScopeBridgeClient.MAILBOX_PATH)
        if (!mailbox.exists() && !mailbox.mkdirs()) return
        val workers = Executors.newFixedThreadPool(MAX_WORKERS)
        // File.renameTo() is not a reliable cross-UID claim operation on
        // several Android external-storage providers. Keep the claim state in
        // this shell process instead and read the already-complete request
        // file directly. A request ID is unique, so a process restart can
        // safely retry any request that was left behind.
        val inFlight = Collections.synchronizedSet(mutableSetOf<String>())

        while (true) {
            val requests = mailbox.listFiles { file ->
                file.isFile &&
                    file.name.startsWith("request_") &&
                    !file.name.endsWith(".tmp")
            }
            requests.orEmpty().forEach { requestFile ->
                if (inFlight.add(requestFile.name)) {
                    workers.submit {
                        try {
                            handle(mailbox, requestFile)
                        } finally {
                            inFlight.remove(requestFile.name)
                        }
                    }
                }
            }
            Thread.sleep(MAILBOX_POLL_INTERVAL_MS)
        }
    }

    private fun handle(mailbox: File, requestFile: File) {
        try {
            val request = requestFile.readText(StandardCharsets.UTF_8)
            // The app may have had to publish the final request directly
            // because renameTo() failed. Do not consume a partially visible
            // file; it will be retried on the next mailbox scan.
            if (!isCompleteRequest(request)) return
            val response = when {
                request == "PING" -> "PONG"
                request.startsWith("EXEC\t") -> {
                    val output = execute(decode(request.substringAfter('\t')))
                    "OK\t${encode(output.output)}"
                }
                request.startsWith("EXEC_RESULT\t") -> {
                    val output = execute(decode(request.substringAfter('\t')))
                    "RESULT\t${output.exitCode}\t${encode(output.output)}"
                }
                else -> "ERROR\tbad_request"
            }
            val responseFile = File(mailbox, requestFile.name.replaceFirst("request_", "response_"))
            val temporaryFile = File(mailbox, "${responseFile.name}.tmp")
            temporaryFile.writeText(response, StandardCharsets.UTF_8)
            // Android 8.1 vendor external-storage providers may return false
            // from File.renameTo() even within the same directory. If rename
            // fails, leave the complete .tmp file in place. The app
            // explicitly polls and validates that compatibility response, so
            // it never has to read a response file while it is being written.
            temporaryFile.renameTo(responseFile)
            requestFile.delete()
        } catch (_: Exception) {
            // The app may delete a timed-out request while it is being handled.
        }
    }

    private fun isCompleteRequest(request: String): Boolean = when {
        request == "PING" -> true
        request.startsWith("EXEC\t") -> isCompleteBase64(request.substringAfter('\t'))
        request.startsWith("EXEC_RESULT\t") -> isCompleteBase64(request.substringAfter('\t'))
        else -> false
    }

    private fun isCompleteBase64(value: String): Boolean {
        if (value.isEmpty()) return false
        val decoded = decode(value)
        return decoded.isNotEmpty() && encode(decoded) == value
    }

    private const val MAILBOX_POLL_INTERVAL_MS = 10L
    private const val MAX_WORKERS = 4
    private const val COMMAND_TIMEOUT_MS = 3000L

    private fun execute(command: String): ShellResult {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val outputHolder = StringBuilder()
            val reader = Thread {
                runCatching {
                    process.inputStream.bufferedReader().use { outputHolder.append(it.readText()) }
                }
            }
            reader.start()
            val finished = process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) process.destroyForcibly()
            reader.join(500)
            ShellResult(outputHolder.toString().trim(), if (finished) process.exitValue() else 124)
        } catch (e: Exception) {
            ShellResult("FrameScope Bridge error: ${e.message.orEmpty()}", -1)
        }
    }

    private data class ShellResult(val output: String, val exitCode: Int)

    private fun encode(value: String): String =
        Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)

    private fun decode(value: String): String = try {
        String(Base64.decode(value, Base64.DEFAULT), StandardCharsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        ""
    }
}
