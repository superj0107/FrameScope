package com.framescope.app.metrics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.framescope.app.shizuku.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

internal data class CpuTimes(val total: Long, val idle: Long)

internal object CpuStatParser {
    fun parseTotalCpuLine(lines: List<String>): CpuTimes? {
        val parts = lines.firstOrNull()
            ?.trim()
            ?.split("\\s+".toRegex())
            ?: return null

        if (parts.size < 5 || parts[0] != "cpu") return null

        val values = parts.drop(1).map { it.toLongOrNull() ?: return null }
        val user = values.getOrNull(0) ?: return null
        val nice = values.getOrNull(1) ?: return null
        val system = values.getOrNull(2) ?: return null
        val idle = values.getOrNull(3) ?: return null
        val iowait = values.getOrNull(4) ?: 0L
        val irq = values.getOrNull(5) ?: 0L
        val softirq = values.getOrNull(6) ?: 0L
        val steal = values.getOrNull(7) ?: 0L

        return CpuTimes(
            total = user + nice + system + idle + iowait + irq + softirq + steal,
            idle = idle + iowait
        )
    }

    fun calculateUsage(previous: CpuTimes, current: CpuTimes): Int? {
        val diffTotal = current.total - previous.total
        val diffIdle = current.idle - previous.idle
        if (diffTotal <= 0L || diffIdle < 0L) return null

        return (((diffTotal - diffIdle).toFloat() / diffTotal) * 100f)
            .roundToInt()
            .coerceIn(0, 100)
    }
}

@Singleton
class FpsMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager
) {
    data class FpsState(val fps: Int, val jankyFrames: Int)

    val fpsState: Flow<FpsState> = flow {
        var activePackage = ""
        var activeLayer = ""
        var layerReady = false

        while (true) {
            val shizukuReady = shizukuManager.isShizukuAvailable.value &&
                    shizukuManager.hasPermission.value

            if (shizukuReady) {
                try {
                    val windowDump = shizukuManager.executeCommand("dumpsys window windows")
                    val packageMatch = FOREGROUND_PACKAGE_REGEX.find(windowDump)
                    val packageName = packageMatch?.groupValues?.get(1).orEmpty()
                    val layerDump = shizukuManager.executeCommand("dumpsys SurfaceFlinger --list")
                    val layerCandidates = layerDump.lineSequence()
                        .map { it.trim() }
                        .filter { it.contains("$packageName/") }
                        .filterNot { line ->
                            NON_RENDERING_LAYER_MARKERS.any { marker ->
                                line.contains(marker, ignoreCase = true)
                            }
                        }
                        .toList()
                    // SurfaceFlinger also exposes helper layers such as
                    // ActivityRecordInputSink for the foreground window. Those
                    // layers do not contain presented frames, so prefer a real
                    // application surface and never select the helper layer.
                    val layer = layerCandidates.firstOrNull {
                        it.contains("$packageName/$packageName.")
                    } ?: layerCandidates.firstOrNull()

                    if (packageName != activePackage || layer != activeLayer) {
                        activePackage = packageName
                        activeLayer = layer.orEmpty()
                        layerReady = activeLayer.isNotEmpty()
                        if (layerReady) {
                            shizukuManager.executeCommand(
                                "dumpsys SurfaceFlinger --latency-clear '$activeLayer'"
                            )
                        }
                        // The latency buffer was just cleared. Wait for a
                        // complete sampling interval before reading it so the
                        // result cannot come from an old/stale frame history.
                        emit(FpsState(0, 0))
                        delay(SAMPLE_INTERVAL_MS)
                        continue
                    }

                    if (!layerReady) {
                        emit(FpsState(0, 0))
                    } else {
                        val latency = shizukuManager.executeCommand(
                            "dumpsys SurfaceFlinger --latency '$activeLayer'"
                        )
                        val presentTimes = PRESENT_TIME_REGEX.findAll(latency)
                            .mapNotNull { it.groupValues[1].toLongOrNull() }
                            .filter { it > 0L }
                            .toList()
                        if (presentTimes.size < 2) {
                            emit(FpsState(0, 0))
                        } else {
                            val span = presentTimes.last() - presentTimes.first()
                            val fps = if (span > 0L) {
                                (((presentTimes.size - 1) * 1_000_000_000.0) / span).roundToInt()
                            } else 0
                            val janky = presentTimes.zipWithNext().count { (a, b) -> b - a > 25_000_000L }
                            // Do not cap the result at 60. The panel and the
                            // compositor may legitimately report 90/120 FPS.
                            emit(FpsState(fps.coerceAtLeast(0), janky))
                        }
                        // Start a fresh window for the next sample. This makes
                        // a static page report 0 new frames instead of repeating
                        // the last value forever.
                        shizukuManager.executeCommand(
                            "dumpsys SurfaceFlinger --latency-clear '$activeLayer'"
                        )
                    }
                } catch (e: Exception) {
                    emit(FpsState(0, 0))
                }
            } else {
                activePackage = ""
                activeLayer = ""
                layerReady = false
                emit(FpsState(0, 0))
            }
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    companion object {
        private const val SAMPLE_INTERVAL_MS = 500L
        private val FOREGROUND_PACKAGE_REGEX = Regex("(?:mFocusedApp|mCurrentFocus)=.*?\\bu\\d+\\s+([A-Za-z0-9._]+?)/")
        // SurfaceFlinger latency rows are:
        // desired-present-time, actual-present-time, frame-ready-time.
        // FPS must use actual-present-time (the second column).
        private val PRESENT_TIME_REGEX = Regex("^\\s*\\d+\\s+(\\d+)\\s+\\d+\\s*$", RegexOption.MULTILINE)
        private val NON_RENDERING_LAYER_MARKERS = listOf(
            "ActivityRecord",
            "InputSink",
            "Wallpaper",
            "StatusBar",
            "NavigationBar",
            "Insets",
            "animation",
            "Leash",
            "Screenshot",
            "ColorFade"
        )
    }
}

@Singleton
class CpuMonitor @Inject constructor(
    private val shizukuManager: ShizukuManager
) {
    data class CpuClusterState(val effMhz: Int, val perfMhz: Int, val ultraMhz: Int)
    private data class CpuPolicy(val currentMhz: Int, val maxMhz: Int)

    // Expose discovered CPU frequency policy groups, mapped by max clock.
    val cpuClusterUsage: Flow<CpuClusterState> = flow {
        while (true) {
            emit(readClusterState())
            delay(1000)
        }
    }

    // Exact same approach as PerfStats: read cpu0 current clock frequency from sysfs.
    val cpuUsage: Flow<Int> = flow {
        while (true) {
            emit(readFreq(0))
            delay(1000)
        }
    }

    // System-wide CPU utilization percentage (0-100%) parsed from /proc/stat
    val cpuPercentageUsage: Flow<Int?> = flow {
        var previousCpuTimes: CpuTimes? = null
        while (true) {
            try {
                val output = if (shizukuManager.isShizukuAvailable.value && shizukuManager.hasPermission.value) {
                    try {
                        shizukuManager.readProcStat()
                    } catch (e: Exception) {
                        ""
                    }
                } else {
                    ""
                }
                
                val lines = if (output.isNotEmpty()) {
                    output.lines()
                } else {
                    try {
                        java.io.File("/proc/stat").readLines()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }

                val currentCpuTimes = CpuStatParser.parseTotalCpuLine(lines)
                val previous = previousCpuTimes
                previousCpuTimes = currentCpuTimes

                if (currentCpuTimes != null && previous != null) {
                    emit(CpuStatParser.calculateUsage(previous, currentCpuTimes))
                } else {
                    emit(null)
                }
            } catch (e: Exception) {
                emit(null)
            }
            delay(1000)
        }
    }

    private fun readClusterState(): CpuClusterState {
        val policies = readCpuPolicies().sortedBy { it.maxMhz }
        return when (policies.size) {
            0 -> CpuClusterState(0, 0, 0)
            1 -> CpuClusterState(policies[0].currentMhz, 0, 0)
            2 -> CpuClusterState(policies[0].currentMhz, policies[1].currentMhz, 0)
            else -> CpuClusterState(
                effMhz = policies.first().currentMhz,
                perfMhz = policies[policies.lastIndex - 1].currentMhz,
                ultraMhz = policies.last().currentMhz
            )
        }
    }

    private fun readCpuPolicies(): List<CpuPolicy> {
        val policyDir = java.io.File("/sys/devices/system/cpu/cpufreq")
        val policies = policyDir.listFiles { file -> file.isDirectory && file.name.startsWith("policy") }
            ?.mapNotNull { policy ->
                val current = readMhz(policy.resolve("scaling_cur_freq"))
                val max = readMhz(policy.resolve("cpuinfo_max_freq"))
                if (current > 0 || max > 0) CpuPolicy(current, max.coerceAtLeast(current)) else null
            }
            .orEmpty()

        if (policies.isNotEmpty()) return policies

        return java.io.File("/sys/devices/system/cpu")
            .listFiles { file -> file.isDirectory && file.name.matches(Regex("cpu\\d+")) }
            ?.mapNotNull { cpu ->
                val freqDir = cpu.resolve("cpufreq")
                val current = readMhz(freqDir.resolve("scaling_cur_freq"))
                val max = readMhz(freqDir.resolve("cpuinfo_max_freq"))
                if (current > 0 || max > 0) CpuPolicy(current, max.coerceAtLeast(current)) else null
            }
            .orEmpty()
            .distinctBy { it.maxMhz }
    }

    private fun readFreq(coreIndex: Int): Int {
        return readMhz(java.io.File("/sys/devices/system/cpu/cpu$coreIndex/cpufreq/scaling_cur_freq"))
    }

    private fun readMhz(file: java.io.File): Int {
        return try {
            val raw = file.readText().trim()
            (raw.toIntOrNull() ?: 0) / KHZ_TO_MHZ
        } catch (e: Exception) {
            0
        }
    }

    companion object {
        private const val KHZ_TO_MHZ = 1000
    }
}

@Singleton
class RamMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager
) {
    data class RamState(val usedGb: Float, val totalGb: Float)

    val ramUsage: Flow<RamState> = flow {
        while (true) {
            val state = try {
                parseMemInfo()
            } catch (e: Exception) {
                fallbackRam(context)
            }
            emit(state)
            delay(2000)
        }
    }

    private fun parseMemInfo(): RamState {
        val file = java.io.File("/proc/meminfo")
        if (file.exists()) {
            val lines = file.readLines()
            var totalKb = 0f
            var availKb = -1f
            var freeKb = 0f
            var buffersKb = 0f
            var cachedKb = 0f
            for (line in lines) {
                val parts = line.split(":")
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val valStr = parts[1].trim().split(WHITESPACE_REGEX)[0].trim()
                    val value = valStr.toFloatOrNull() ?: 0f
                    when (key) {
                        "MemTotal" -> totalKb = value
                        "MemAvailable" -> availKb = value
                        "MemFree" -> freeKb = value
                        "Buffers" -> buffersKb = value
                        "Cached" -> cachedKb = value
                    }
                }
            }
            val usedKb = if (availKb >= 0f) {
                totalKb - availKb
            } else {
                totalKb - freeKb - buffersKb - cachedKb
            }
            // Return RAM usage in GB
            return RamState(usedKb / (1024f * 1024f), totalKb / (1024f * 1024f))
        } else {
            return fallbackRam(context)
        }
    }

    private fun fallbackRam(context: Context): RamState {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return RamState(
            usedGb = (info.totalMem - info.availMem).toFloat() / (1024 * 1024 * 1024),
            totalGb = info.totalMem.toFloat() / (1024 * 1024 * 1024)
        )
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}

@Singleton
class NetworkMonitor @Inject constructor() {
    data class NetworkState(val rxSpeedKbps: Float, val txSpeedKbps: Float)

    val networkSpeed: Flow<NetworkState> = flow {
        // Guard: on some devices/builds TrafficStats is not supported.
        if (TrafficStats.getTotalRxBytes() == TrafficStats.UNSUPPORTED.toLong()) {
            while (true) { emit(NetworkState(0f, 0f)); delay(2000) }
        }
        var previousRx = TrafficStats.getTotalRxBytes()
        var previousTx = TrafficStats.getTotalTxBytes()
        var previousTime = System.currentTimeMillis()

        while (true) {
            delay(1000)
            val currentRx = TrafficStats.getTotalRxBytes()
            val currentTx = TrafficStats.getTotalTxBytes()
            val currentTime = System.currentTimeMillis()

            val timeDiffSec = (currentTime - previousTime) / 1000.0f
            if (timeDiffSec > 0 && currentRx >= 0 && currentTx >= 0) {
                val rxSpeedKbps = ((currentRx - previousRx) / 1024.0f) / timeDiffSec
                val txSpeedKbps = ((currentTx - previousTx) / 1024.0f) / timeDiffSec
                emit(NetworkState(
                    rxSpeedKbps.coerceAtLeast(0f),
                    txSpeedKbps.coerceAtLeast(0f)
                ))
            } else {
                emit(NetworkState(0f, 0f))
            }

            previousRx = currentRx
            previousTx = currentTx
            previousTime = currentTime
        }
    }
}

@Singleton
class BatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val batteryTemp: Flow<Float> = flow {
        while (true) {
            emit(readBatteryTemp(context))
            delay(BATTERY_POLL_INTERVAL_MS)
        }
    }

    private fun readBatteryTemp(context: Context): Float {
        val intent = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val raw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return raw / 10.0f
    }

    companion object {
        private const val BATTERY_POLL_INTERVAL_MS = 5000L
    }
}

@Singleton
class ThermalMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager
) {
    data class ThermalState(
        val cpuC: Float = 0f,
        val gpuC: Float = 0f,
        val npuC: Float = 0f,
        val skinC: Float = 0f,
        val batteryC: Float = 0f,
        val status: Int = 0,
        val readStatus: MetricReadStatus = MetricReadStatus.Loading,
        val hasCpu: Boolean = false,
        val hasGpu: Boolean = false,
        val hasNpu: Boolean = false,
        val hasSkin: Boolean = false,
        val hasBattery: Boolean = false
    ) {
        val statusLabel: String get() = when (status) {
            0 -> "Normal"
            1 -> "Light Throttling"
            2 -> "Moderate Throttling"
            3 -> "Severe Throttling"
            4 -> "Critical Throttling"
            5 -> "Emergency Mitigation"
            6 -> "System Shutdown"
            else -> "Unknown"
        }

        val pressureLabel: String get() = when (status) {
            0 -> "Stable"
            1, 2 -> "Rising"
            else -> "Elevated"
        }
    }

    val thermalState: Flow<ThermalState> = flow {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        var lastGoodState: ThermalState? = null
        var consecutiveFailures = 0

        while (true) {
            val shizukuReady = shizukuManager.isShizukuAvailable.value &&
                    shizukuManager.hasPermission.value

            val state = if (shizukuReady) {
                try {
                    val output = shizukuManager.getThermalTemperatures()
                    com.framescope.app.utils.FrameScopeLog.d("ThermalMonitor", "Raw output len=${output.length}, startsWith=${output.take(60).replace("\n", "\\n")}")
                    if (output.isBlank()) {
                        consecutiveFailures++
                        if (consecutiveFailures <= 3 && lastGoodState != null) {
                            lastGoodState.copy(readStatus = MetricReadStatus.Stale)
                        } else {
                            if (consecutiveFailures > 3) {
                                lastGoodState = null
                            }
                            val status = powerManager.thermalStatusOrDefault()
                            ThermalState(status = status, readStatus = MetricReadStatus.EmptyOutput)
                        }
                    } else {
                        val parsed = ThermalServiceParser.parse(output)
                        com.framescope.app.utils.FrameScopeLog.d("ThermalMonitor", "Parsed result: entryCount=${parsed?.entryCount}, cpu=${parsed?.cpuC}, gpu=${parsed?.gpuC}, skin=${parsed?.skinC}, battery=${parsed?.batteryC}, halNotReady=${parsed?.halNotReady}")
                        if (parsed == null || parsed.entryCount == 0) {
                            consecutiveFailures++
                            if (consecutiveFailures <= 3 && lastGoodState != null) {
                                lastGoodState.copy(readStatus = MetricReadStatus.Stale)
                            } else {
                                if (consecutiveFailures > 3) {
                                    lastGoodState = null
                                }
                                val status = parsed?.thermalStatus?.takeIf { it > 0 } ?: powerManager.thermalStatusOrDefault()
                                val statusType = if (parsed?.halNotReady == true) MetricReadStatus.EmptyOutput else MetricReadStatus.ParseFailed
                                ThermalState(status = status, readStatus = statusType)
                            }
                        } else {
                            consecutiveFailures = 0
                            val effectiveStatus = parsed.thermalStatus.takeIf { it > 0 } ?: powerManager.thermalStatusOrDefault()
                            val newState = ThermalState(
                                cpuC = parsed.cpuC ?: 0f,
                                gpuC = parsed.gpuC ?: 0f,
                                npuC = parsed.npuC ?: 0f,
                                skinC = parsed.skinC ?: 0f,
                                batteryC = parsed.batteryC ?: 0f,
                                status = effectiveStatus,
                                readStatus = MetricReadStatus.Ok,
                                hasCpu = parsed.cpuC != null,
                                hasGpu = parsed.gpuC != null,
                                hasNpu = parsed.npuC != null,
                                hasSkin = parsed.skinC != null,
                                hasBattery = parsed.batteryC != null
                            )
                            lastGoodState = newState
                            newState
                        }
                    }
                } catch (e: Exception) {
                    consecutiveFailures++
                    if (consecutiveFailures <= 3 && lastGoodState != null) {
                        lastGoodState.copy(readStatus = MetricReadStatus.Stale)
                    } else {
                        if (consecutiveFailures > 3) {
                            lastGoodState = null
                        }
                        val status = powerManager.thermalStatusOrDefault()
                        ThermalState(status = status, readStatus = MetricReadStatus.ParseFailed)
                    }
                }
            } else {
                consecutiveFailures = 0
                lastGoodState = null
                val status = powerManager.thermalStatusOrDefault()
                ThermalState(status = status, readStatus = MetricReadStatus.NoShizuku)
            }
            emit(state)
            delay(2000)
        }
    }
}

private fun android.os.PowerManager.thermalStatusOrDefault(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) currentThermalStatus else 0

data class PingResult(
    val pingMs: Int,
    val readStatus: MetricReadStatus
)

@Singleton
class PingMonitor @Inject constructor(
    private val shizukuManager: ShizukuManager
) {
    companion object {
        // ICMP echo has near-zero cost. 2s polling keeps live overlay ping responsive without battery overhead.
        private const val POLL_INTERVAL_MS = 2_000L
    }

    val ping: Flow<PingResult> = flow {
        while (true) {
            val output = if (shizukuManager.isShizukuAvailable.value && shizukuManager.hasPermission.value) {
                try {
                    shizukuManager.executeCommand("ping -c 1 -w 2 8.8.8.8")
                } catch (e: Exception) {
                    executePing()
                }
            } else {
                executePing()
            }

            val result = parsePingOutput(output)
            emit(result)
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun parsePingOutput(output: String): PingResult {
        if (output.isBlank()) return PingResult(0, MetricReadStatus.EmptyOutput)
        
        return if (output.contains("time=")) {
            val ms = output.split("time=").getOrNull(1)
                ?.split(" ")?.getOrNull(0)
                ?.toFloatOrNull()
                ?.roundToInt() ?: 0
            if (ms > 0) {
                PingResult(ms, MetricReadStatus.Ok)
            } else {
                PingResult(0, MetricReadStatus.ParseFailed)
            }
        } else {
            PingResult(0, MetricReadStatus.NoData)
        }
    }

    private fun executePing(): String {
        return try {
            val process = Runtime.getRuntime().exec("ping -c 1 -w 2 8.8.8.8")
            try {
                process.inputStream.bufferedReader().use { it.readText() }
            } finally {
                process.destroy()
            }
        } catch (e: Exception) {
            com.framescope.app.utils.FrameScopeLog.w("executePing failed", e)
            ""
        }
    }
}

@Singleton
class TopProcessMonitor @Inject constructor(
    private val shizukuManager: ShizukuManager
) {
    data class TopProcess(
        val name: String = "",
        val cpuPercent: Float = 0f,
        val topProcesses: List<CpuInfoTopParser.TopProcess> = emptyList(),
        val readStatus: MetricReadStatus = MetricReadStatus.Loading
    )

    val topProcess: Flow<TopProcess> = flow {
        while (true) {
            val shizukuReady = shizukuManager.isShizukuAvailable.value &&
                    shizukuManager.hasPermission.value

            val result = if (shizukuReady) {
                try {
                    val output = shizukuManager.executeCommand("dumpsys cpuinfo")
                    if (output.isBlank()) {
                        TopProcess(readStatus = MetricReadStatus.EmptyOutput)
                    } else {
                        val parsedList = CpuInfoTopParser.parseTopProcesses(output, limit = 5)
                        val topOne = parsedList.firstOrNull()
                        if (topOne != null) {
                            TopProcess(
                                name = topOne.name,
                                cpuPercent = topOne.cpuPercent,
                                topProcesses = parsedList,
                                readStatus = MetricReadStatus.Ok
                            )
                        } else {
                            TopProcess(readStatus = MetricReadStatus.NoData)
                        }
                    }
                } catch (e: Exception) {
                    TopProcess(readStatus = MetricReadStatus.ParseFailed)
                }
            } else {
                TopProcess(readStatus = MetricReadStatus.NoShizuku)
            }

            emit(result)
            delay(2000)
        }
    }
}
