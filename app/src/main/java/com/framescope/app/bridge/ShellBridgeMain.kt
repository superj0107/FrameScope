package com.framescope.app.bridge

import android.os.Process
import android.util.Base64
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.io.File
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

        while (true) {
            val requests = mailbox.listFiles { file ->
                file.isFile && file.name.startsWith("request_")
            }
            requests.orEmpty().forEach { requestFile ->
                val workingFile = File(mailbox, requestFile.name.replaceFirst("request_", "working_"))
                // Rename is the claim operation. Only one worker can own a request.
                if (requestFile.renameTo(workingFile)) {
                    workers.submit { handle(mailbox, workingFile) }
                }
            }
            Thread.sleep(MAILBOX_POLL_INTERVAL_MS)
        }
    }

    private fun handle(mailbox: File, requestFile: File) {
        try {
            val request = requestFile.readText(StandardCharsets.UTF_8)
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
            val responseFile = File(mailbox, requestFile.name.replaceFirst("working_", "response_"))
            val temporaryFile = File(mailbox, "${responseFile.name}.tmp")
            temporaryFile.writeText(response, StandardCharsets.UTF_8)
            temporaryFile.renameTo(responseFile)
            requestFile.delete()
        } catch (_: Exception) {
            // The app may delete a timed-out request while it is being handled.
        }
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
