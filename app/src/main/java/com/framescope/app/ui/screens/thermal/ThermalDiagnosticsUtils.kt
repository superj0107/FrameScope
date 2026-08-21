package com.framescope.app.ui.screens.thermal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.framescope.app.metrics.MetricReadStatus
import com.framescope.app.metrics.MetricsEngine
import com.framescope.app.metrics.MetricsState
import com.framescope.app.i18n.trStatic
import com.framescope.app.ui.theme.Amber
import com.framescope.app.ui.theme.MutedBlue
import com.framescope.app.ui.theme.MutedGreen
import com.framescope.app.ui.theme.PrimaryRed
import com.framescope.app.ui.theme.Teal
import java.util.Locale

/** Single source of truth for human-readable thermal status text across UI and ViewModels. */
fun getThermalStatusLabel(status: Int): String {
    return when (status) {
        0 -> trStatic("Normal")
        1 -> trStatic("Light Throttling")
        2 -> trStatic("Moderate Throttling")
        3 -> trStatic("Severe Throttling")
        4 -> trStatic("Critical Throttling")
        5 -> trStatic("Emergency Mitigation")
        6 -> trStatic("System Shutdown")
        else -> "${trStatic("Level")} $status"
    }
}

/** Pure helper to evaluate thermal pressure level based on status code and delta thresholds. */
fun computeThermalPressure(status: Int, cpuDelta: Float, skinDelta: Float): String {
    return when (status) {
        0 -> if (cpuDelta > 1.5f || skinDelta > 1.0f) trStatic("Rising") else trStatic("Stable")
        1, 2 -> trStatic("Elevated")
        else -> trStatic("Critical")
    }
}

fun getThermalDisplayValue(value: Float, present: Boolean, readStatus: MetricReadStatus): String {
    return when (readStatus) {
        MetricReadStatus.NoShizuku -> trStatic("Needs Shizuku")
        MetricReadStatus.EmptyOutput -> trStatic("Not Supported")
        MetricReadStatus.ParseFailed -> trStatic("Parse Failed")
        MetricReadStatus.Loading -> trStatic("Waiting...")
        else -> if (present) String.format(Locale.US, "%.1f°C", value) else trStatic("Not Supported")
    }
}

fun getBatteryDisplayValue(tempC: Float): String {
    return if (tempC <= 0f) trStatic("Unavailable") else String.format(Locale.US, "%.1f°C", tempC)
}

fun getTopProcessDisplayValue(name: String?, percent: Float, readStatus: MetricReadStatus): String {
    return when (readStatus) {
        MetricReadStatus.NoShizuku -> trStatic("Needs Shizuku")
        MetricReadStatus.EmptyOutput -> trStatic("Unavailable")
        MetricReadStatus.ParseFailed -> trStatic("Parse Failed")
        MetricReadStatus.Loading -> trStatic("Waiting...")
        MetricReadStatus.NoData -> trStatic("No process data")
        MetricReadStatus.Ok -> {
            if (name != null) {
                "$name (${String.format(Locale.US, "%.0f", percent)}%)"
            } else {
                "—"
            }
        }
        else -> "—"
    }
}

fun evaluateLikelyCause(
    snapshots: List<MetricsEngine.MetricsSnapshot>,
    currentState: MetricsState
): DiagnosticCause {
    if (snapshots.size < 5) {
        return DiagnosticCause(
            title = trStatic("Monitoring Performance"),
            description = trStatic("Gathering telemetry samples to evaluate frame drop root cause..."),
            color = Color(0xFF60A5FA)
        )
    }

    val avgFps = snapshots.map { it.state.fps }.average()
    val minFps = snapshots.minOf { it.state.fps }
    val isFpsDropped = minFps < (avgFps * 0.85) || currentState.fps < 45

    val maxCpuTemp = snapshots.maxOf { it.state.thermalCpuC }
    val maxSkinTemp = snapshots.maxOf { it.state.thermalSkinC }
    val isHot = maxCpuTemp >= 65f || maxSkinTemp >= 42f || currentState.thermalStatus > 0

    val topCpu = currentState.topProcessCpuPercent
    val isProcessBusy = topCpu >= 20f && currentState.topProcessName != null

    return when {
        isFpsDropped && isHot -> DiagnosticCause(
            title = trStatic("Likely Thermal Pressure"),
            description = String.format(Locale.US, trStatic("FPS dropped while CPU/Skin temperature elevated to %s."), String.format(Locale.US, "%.1f°C", maxCpuTemp)),
            color = Color(0xFFEF4444)
        )
        isFpsDropped && isProcessBusy -> DiagnosticCause(
            title = trStatic("Likely Background CPU Contention"),
            description = String.format(Locale.US, trStatic("FPS dropped while '%s' consumed %s CPU."), currentState.topProcessName, String.format(Locale.US, "%.0f%%", topCpu)),
            color = Color(0xFFF59E0B)
        )
        currentState.jankyFrames >= 6 -> DiagnosticCause(
            title = trStatic("Likely Frame Pacing Issue"),
            description = trStatic("Average FPS is steady, but display compositor detected sudden jank frame spikes."),
            color = Color(0xFFFB7185)
        )
        else -> DiagnosticCause(
            title = trStatic("No Thermal Correlation Detected"),
            description = trStatic("Thermals and background CPU usage are within normal limits."),
            color = Color(0xFF34D399)
        )
    }
}

fun seriesForMode(
    mode: GraphMetricMode,
    maxFps: Float,
    maxTemp: Float,
    maxJank: Float = 10f,
    hasGpu: Boolean = true
): List<GraphSeries> {
    val fpsSeries = GraphSeries("FPS", Color(0xFF38BDF8), "", maxFps, fillArea = true) { it.state.fps.toFloat() }
    val cpuSeries = GraphSeries("CPU", Color(0xFFFF2E4D), "°", maxTemp, fillArea = false) { it.state.thermalCpuC }
    val gpuSeries = GraphSeries("GPU", Color(0xFF3B82F6), "°", maxTemp, fillArea = false) { it.state.thermalGpuC }
    val skinSeries = GraphSeries("Skin", Color(0xFFFFB703), "°", maxTemp, fillArea = false) { it.state.thermalSkinC }
    val batterySeries = GraphSeries("Battery", Color(0xFF06D6A0), "°", maxTemp, fillArea = false) { it.state.batteryTempC }
    val jankSeries = GraphSeries("Jank", Color(0xFFEF476F), "", maxJank, fillArea = false) { it.state.jankyFrames.toFloat() }
    val topProcessSeries = GraphSeries("Top Process", Color(0xFFFF7300), "%", 100f, fillArea = false) { it.state.topProcessCpuPercent }

    return when (mode) {
        GraphMetricMode.FPS_THERMAL -> listOf(cpuSeries, skinSeries, fpsSeries)
        GraphMetricMode.FPS_JANK -> listOf(jankSeries, fpsSeries)
        GraphMetricMode.FPS_ONLY -> listOf(fpsSeries)
        GraphMetricMode.THERMAL_ONLY -> {
            val list = mutableListOf(cpuSeries.copy(fillArea = true))
            if (hasGpu) list.add(gpuSeries)
            list.add(skinSeries)
            list.add(batterySeries)
            list
        }
        GraphMetricMode.FPS_TOP_PROCESS -> listOf(topProcessSeries, fpsSeries)
    }
}

fun formatSecondsAgo(secondsAgo: Int): String {
    if (secondsAgo <= 0) return trStatic("now")
    val hours = secondsAgo / 3600
    val minutes = (secondsAgo % 3600) / 60
    val seconds = secondsAgo % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}${trStatic("h")} ${minutes}${trStatic("m")}"
        hours > 0 -> "${hours}${trStatic("h")}"
        minutes > 0 -> "${minutes}${trStatic("m")}"
        else -> "${seconds}${trStatic("s")}"
    }
}

fun plateBgForLabel(label: String): Color = when (label) {
    "CPU" -> Color(0xFFEF4444).copy(alpha = 0.14f)
    "GPU" -> Color(0xFF3B82F6).copy(alpha = 0.14f)
    "SKIN" -> Color(0xFFF59E0B).copy(alpha = 0.14f)
    "NPU" -> Color(0xFF8B5CF6).copy(alpha = 0.14f)
    "BATTERY" -> Color(0xFF10B981).copy(alpha = 0.14f)
    "JANK RATE" -> Color(0xFFF43F5E).copy(alpha = 0.14f)
    "TOP PROCESS" -> Color(0xFF06B6D4).copy(alpha = 0.14f)
    else -> Color(0xFFEC4899).copy(alpha = 0.14f)
}

fun plateBorderForLabel(label: String): Color = when (label) {
    "CPU" -> Color(0xFFEF4444).copy(alpha = 0.28f)
    "GPU" -> Color(0xFF3B82F6).copy(alpha = 0.28f)
    "SKIN" -> Color(0xFFF59E0B).copy(alpha = 0.28f)
    "NPU" -> Color(0xFF8B5CF6).copy(alpha = 0.28f)
    "BATTERY" -> Color(0xFF10B981).copy(alpha = 0.28f)
    "JANK RATE" -> Color(0xFFF43F5E).copy(alpha = 0.28f)
    "TOP PROCESS" -> Color(0xFF06B6D4).copy(alpha = 0.28f)
    else -> Color(0xFFEC4899).copy(alpha = 0.28f)
}

fun iconColorForLabel(label: String): Color = when (label) {
    "CPU" -> Color(0xFFF87171)
    "GPU" -> Color(0xFF60A5FA)
    "SKIN" -> Color(0xFFFBBF24)
    "NPU" -> Color(0xFFA78BFA)
    "BATTERY" -> Color(0xFF34D399)
    "JANK RATE" -> Color(0xFFFB7185)
    "TOP PROCESS" -> Color(0xFF22D3EE)
    else -> Color(0xFFF472B6)
}

fun iconForLabel(label: String): ImageVector = when (label) {
    "CPU" -> Icons.Default.Memory
    "GPU" -> Icons.Default.Speed
    "SKIN" -> Icons.Default.Thermostat
    "NPU" -> Icons.Default.DeveloperBoard
    "BATTERY" -> Icons.Default.BatteryChargingFull
    "JANK RATE" -> Icons.Default.Warning
    "TOP PROCESS" -> Icons.Default.Layers
    else -> Icons.Default.LocalFireDepartment
}
