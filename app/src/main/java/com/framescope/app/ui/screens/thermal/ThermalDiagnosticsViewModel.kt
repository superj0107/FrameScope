package com.framescope.app.ui.screens.thermal

import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framescope.app.metrics.MetricsEngine
import com.framescope.app.metrics.MetricsState
import com.framescope.app.metrics.SessionLogger
import com.framescope.app.repository.SettingsRepository
import com.framescope.app.shizuku.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ThermalDiagnosticsViewModel @Inject constructor(
    private val metricsEngine: MetricsEngine,
    private val sessionLogger: SessionLogger,
    private val shizukuManager: ShizukuManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val metricsState = metricsEngine.metricsState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MetricsState())

    val snapshotHistory = metricsEngine.snapshotHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRecording = sessionLogger.isRecording
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isShizukuAvailable = shizukuManager.isShizukuAvailable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasShizukuPermission = shizukuManager.hasPermission
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Persisted graph controls (issue #55): survive navigating away from and back to
    // this screen, unlike the previous remember{}-scoped selection.
    val thermalTimeWindow = settingsRepository.thermalTimeWindow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settingsRepository.thermalTimeWindow.value)

    val thermalGraphMode = settingsRepository.thermalGraphMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settingsRepository.thermalGraphMode.value)

    fun setThermalTimeWindow(window: TimeWindow) {
        settingsRepository.setThermalTimeWindow(window.name)
    }

    fun setThermalGraphMode(mode: GraphMetricMode) {
        settingsRepository.setThermalGraphMode(mode.name)
    }

    init {
        metricsEngine.setScreenOverrideModules(
            setOf("thermal", "temp", "top_process"),
            requesterKey = "thermal_diagnostics_screen"
        )
    }

    override fun onCleared() {
        super.onCleared()
        metricsEngine.setScreenOverrideModules(emptySet(), requesterKey = "thermal_diagnostics_screen")
    }

    fun toggleRecording() {
        if (sessionLogger.isRecording.value) {
            sessionLogger.stopRecording()
        } else {
            sessionLogger.startRecording()
        }
    }

    fun recordedSampleCount(snapshots: List<MetricsEngine.MetricsSnapshot>): Int {
        val startMs = sessionLogger.recordingStartTimestampMs
        if (startMs == 0L) return 0
        return snapshots.count { it.timestampMs >= startMs }
    }

    fun requestShizukuPermission() {
        shizukuManager.requestPermission()
    }

    fun refreshShizukuState() {
        shizukuManager.refreshState()
    }

    fun exportAndShare(onReady: (Intent) -> Unit, onEmpty: () -> Unit) {
        viewModelScope.launch {
            val file = sessionLogger.exportToFile()
            if (file == null) {
                onEmpty()
            } else {
                onReady(sessionLogger.buildShareIntent(file))
            }
        }
    }

    fun buildDiagnosticSummaryText(snapshots: List<MetricsEngine.MetricsSnapshot>): String {
        val state = metricsState.value
        val recent = snapshots.takeLast(60)
        val maxCpu = recent.maxOfOrNull { it.state.thermalCpuC } ?: state.thermalCpuC
        val maxSkin = recent.maxOfOrNull { it.state.thermalSkinC } ?: state.thermalSkinC
        val avgFps = if (recent.isNotEmpty()) recent.map { it.state.fps }.average().toInt() else state.fps
        val minFps = recent.minOfOrNull { it.state.fps } ?: state.fps
        val maxJank = recent.maxOfOrNull { it.state.jankyFrames } ?: state.jankyFrames
        val topProcess = state.topProcessName ?: "None"

        val statusText = getThermalStatusLabel(state.thermalStatus)

        return """
### FrameScope Thermal Diagnostic Summary
- **Device**: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})
- **System Thermal State**: $statusText
- **CPU Peak (60s)**: ${String.format(Locale.US, "%.1f°C", maxCpu)}
- **Skin Peak (60s)**: ${String.format(Locale.US, "%.1f°C", maxSkin)}
- **FPS Avg / Min**: $avgFps / $minFps FPS
- **Jank Peak**: $maxJank frames
- **Top Process**: $topProcess (${String.format(Locale.US, "%.0f%%", state.topProcessCpuPercent)})
""".trimIndent()
    }
}
