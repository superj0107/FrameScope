package com.framescope.app.shizuku

import android.content.pm.PackageManager
import com.framescope.app.bridge.FrameScopeBridgeClient
import com.framescope.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuManager @Inject constructor(
    private val bridgeClient: FrameScopeBridgeClient
) {

    private val _isShizukuAvailable = MutableStateFlow(false)
    val isShizukuAvailable: StateFlow<Boolean> = _isShizukuAvailable.asStateFlow()
    
    // Mutex to serialize commands through the single persistent binder connection.
    private val commandMutex = Mutex()
    // @Volatile ensures cross-thread visibility for the binder reference.
    @Volatile private var commandRunner: ICommandRunner? = null
    // Guard flag prevents duplicate bindUserService calls during async connection setup.
    @Volatile private var isConnecting = false
    private var userServiceConnection: android.content.ServiceConnection? = null
    private var pendingConnection: CompletableDeferred<ICommandRunner?>? = null
    private var firstBindNotBeforeMs = 0L

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    /** True when the embedded shell-UID bridge is reachable. */
    private val _isBridgeAvailable = MutableStateFlow(false)
    val isBridgeAvailable: StateFlow<Boolean> = _isBridgeAvailable.asStateFlow()

    private val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var shizukuBinderAvailable = false
    @Volatile private var shizukuPermissionGranted = false
    @Volatile private var initialized = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        shizukuBinderAvailable = true
        checkPermission()
        publishCombinedState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        shizukuBinderAvailable = false
        shizukuPermissionGranted = false
        disconnectUserService()
        publishCombinedState()
    }

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_PERMISSION) {
            shizukuPermissionGranted = grantResult == PackageManager.PERMISSION_GRANTED
            publishCombinedState()
        }
    }

    fun init() {
        if (initialized) return
        initialized = true
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
            
            shizukuBinderAvailable = Shizuku.pingBinder()
            firstBindNotBeforeMs = SystemClock.elapsedRealtime() + INITIAL_BIND_DELAY_MS
            if (shizukuBinderAvailable) {
                checkPermission()
            }
            refreshState()
            stateScope.launch {
                while (true) {
                    val bridgeReady = bridgeClient.isAvailable()
                    _isBridgeAvailable.value = bridgeReady
                    if (!shizukuBinderAvailable) {
                        shizukuBinderAvailable = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
                        if (shizukuBinderAvailable) checkPermission()
                    }
                    publishCombinedState()
                    delay(BRIDGE_POLL_INTERVAL_MS)
                }
            }
        } catch (e: Exception) {
            com.framescope.app.utils.FrameScopeLog.e("Shizuku init error", e)
            shizukuBinderAvailable = false
            shizukuPermissionGranted = false
            publishCombinedState()
        }
    }
    
    fun refreshState() {
        try {
            shizukuBinderAvailable = Shizuku.pingBinder()
            if (shizukuBinderAvailable) {
                checkPermission()
            } else {
                shizukuPermissionGranted = false
            }
            stateScope.launch {
                _isBridgeAvailable.value = bridgeClient.isAvailable()
                publishCombinedState()
            }
            publishCombinedState()
        } catch (e: Exception) {
            com.framescope.app.utils.FrameScopeLog.e("Shizuku refreshState error", e)
            shizukuBinderAvailable = false
            shizukuPermissionGranted = false
            _isBridgeAvailable.value = false
            publishCombinedState()
        }
    }

    fun destroy() {
        stateScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        disconnectUserService()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }

    fun checkPermission() {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            shizukuPermissionGranted = false
            publishCombinedState()
            return
        }
        shizukuPermissionGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        publishCombinedState()
    }

    fun requestPermission() {
        // The embedded bridge is already shell-authorized; only the optional
        // Shizuku fallback needs an explicit per-app permission request.
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
        }
    }

    private fun publishCombinedState() {
        val bridgeReady = _isBridgeAvailable.value
        _isShizukuAvailable.value = shizukuBinderAvailable || bridgeReady
        _hasPermission.value = shizukuPermissionGranted || bridgeReady
    }

    private fun useShizuku(): Boolean = shizukuBinderAvailable && shizukuPermissionGranted

    private fun useBridge(): Boolean = _isBridgeAvailable.value

    private fun hasPrivilegedBackend(): Boolean = useShizuku() || useBridge()

    private suspend fun awaitCommandRunner(): ICommandRunner? {
        commandRunner?.let { return it }
        val initialDelayMs = firstBindNotBeforeMs - SystemClock.elapsedRealtime()
        if (initialDelayMs > 0L) {
            kotlinx.coroutines.delay(initialDelayMs)
        }
        
        var attempts = 0
        while (attempts < MAX_BIND_RETRIES) {
            attempts++
            connectUserService()
            val deferred = pendingConnection ?: return commandRunner
            val runner = withTimeoutOrNull(BIND_TIMEOUT_MS) { deferred.await() }
            if (runner != null) {
                return runner
            }
            com.framescope.app.utils.FrameScopeLog.w("bindUserService attempt $attempts/$MAX_BIND_RETRIES timed out after ${BIND_TIMEOUT_MS}ms")
            disconnectUserService(remove = true)
            if (attempts < MAX_BIND_RETRIES) {
                kotlinx.coroutines.delay(BIND_RETRY_DELAY_MS)
            }
        }
        com.framescope.app.utils.FrameScopeLog.w("CommandRunner unavailable after $MAX_BIND_RETRIES bind attempts")
        return null
    }

    suspend fun executeCommand(command: String): String {
        if (!hasPrivilegedBackend()) {
            com.framescope.app.utils.FrameScopeLog.w("executeCommand called without a privileged backend")
            return ""
        }
        // Prefer the embedded Bridge whenever it is reachable. Shizuku is
        // only a fallback for devices where the Bridge was not started.
        if (useBridge()) return bridgeClient.executeCommand(command)
        return commandMutex.withLock {
            val runner = awaitCommandRunner() ?: run {
                com.framescope.app.utils.FrameScopeLog.w("CommandRunner unavailable after bind attempt in executeCommand")
                return@withLock ""
            }
            try {
                withContext(Dispatchers.IO) {
                    runner.executeCommand(command)
                }
            } catch (e: Exception) {
                com.framescope.app.utils.FrameScopeLog.e("executeCommand failed: $command", e)
                ""
            }
        }
    }

    suspend fun executeCommandWithExitCode(command: String): Int {
        if (!hasPrivilegedBackend()) {
            com.framescope.app.utils.FrameScopeLog.w("executeCommandWithExitCode called without a privileged backend")
            return COMMAND_EXECUTION_FAILED
        }
        if (useBridge()) {
            return bridgeClient.executeCommandWithResult(command)?.exitCode ?: COMMAND_EXECUTION_FAILED
        }
        return commandMutex.withLock {
            val runner = awaitCommandRunner() ?: run {
                com.framescope.app.utils.FrameScopeLog.w("CommandRunner unavailable after bind attempt in executeCommandWithExitCode")
                return@withLock COMMAND_EXECUTION_FAILED
            }
            try {
                withContext(Dispatchers.IO) {
                    runner.executeCommandWithExitCode(command)
                }
            } catch (e: Exception) {
                com.framescope.app.utils.FrameScopeLog.e("executeCommandWithExitCode failed: $command", e)
                COMMAND_EXECUTION_FAILED
            }
        }
    }

    suspend fun executeCommandWithResult(command: String): CommandResult? {
        if (!hasPrivilegedBackend()) {
            com.framescope.app.utils.FrameScopeLog.w("executeCommandWithResult called without a privileged backend")
            return null
        }
        if (useBridge()) return bridgeClient.executeCommandWithResult(command)
        return commandMutex.withLock {
            val runner = awaitCommandRunner() ?: run {
                com.framescope.app.utils.FrameScopeLog.w("CommandRunner unavailable after bind attempt in executeCommandWithResult")
                return@withLock null
            }
            try {
                withContext(Dispatchers.IO) {
                    runner.executeCommandWithResult(command)
                }
            } catch (e: Exception) {
                com.framescope.app.utils.FrameScopeLog.e("executeCommandWithResult failed: $command", e)
                null
            }
        }
    }

    suspend fun readProcStat(): String {
        if (!hasPrivilegedBackend()) {
            com.framescope.app.utils.FrameScopeLog.w("readProcStat called without a privileged backend")
            return ""
        }
        if (!useShizuku() && useBridge()) return bridgeClient.readProcStat()
        return commandMutex.withLock {
            val runner = awaitCommandRunner() ?: run {
                com.framescope.app.utils.FrameScopeLog.w("CommandRunner unavailable after bind attempt in readProcStat")
                return@withLock ""
            }
            try {
                withContext(Dispatchers.IO) {
                    runner.readProcStat()
                }
            } catch (e: Exception) {
                com.framescope.app.utils.FrameScopeLog.e("readProcStat failed", e)
                ""
            }
        }
    }

    suspend fun getThermalTemperatures(): String {
        if (!hasPrivilegedBackend()) {
            com.framescope.app.utils.FrameScopeLog.w("getThermalTemperatures called without a privileged backend")
            return ""
        }
        if (!useShizuku() && useBridge()) return bridgeClient.getThermalTemperatures()
        return commandMutex.withLock {
            val runner = awaitCommandRunner() ?: run {
                com.framescope.app.utils.FrameScopeLog.w("CommandRunner unavailable after bind attempt in getThermalTemperatures")
                return@withLock ""
            }
            try {
                withContext(Dispatchers.IO) {
                    runner.getThermalTemperatures()
                }
            } catch (e: Exception) {
                com.framescope.app.utils.FrameScopeLog.e("getThermalTemperatures failed", e)
                ""
            }
        }
    }

    suspend fun getSuspendedPackages(packageNames: List<String>): Set<String> {
        if (!hasPrivilegedBackend() || packageNames.isEmpty()) {
            return emptySet()
        }
        return try {
            val userId = android.os.Process.myUid() / 100000
            val res = executeCommandWithResult("cmd package list packages -s --user $userId")
            val rawOutput = res?.output.orEmpty()
            if (rawOutput.isNotBlank() && rawOutput != "null") {
                val globSuspended = rawOutput.lines()
                    .map { line -> line.trim().removePrefix("package:") }
                    .filter { it.isNotBlank() }
                    .toSet()
                packageNames.filter { it in globSuspended }.toSet()
            } else {
                emptySet()
            }
        } catch (e: Exception) {
            com.framescope.app.utils.FrameScopeLog.w("Failed to query suspended packages batch", e)
            emptySet()
        }
    }

    suspend fun suspendPackages(packageNames: List<String>, suspended: Boolean): SuspendResult? {
        if (!hasPrivilegedBackend() || packageNames.isEmpty()) {
            com.framescope.app.utils.FrameScopeLog.w("suspendPackages skipped: no privileged backend or package list empty")
            return null
        }
        if (!useShizuku() && useBridge()) {
            return commandMutex.withLock {
                val failed = mutableListOf<String>()
                val action = if (suspended) "suspend" else "unsuspend"
                var successCount = 0
                for (pkg in packageNames) {
                    val result = bridgeClient.executeCommandWithResult("cmd package $action --user 0 $pkg")
                    if (result?.exitCode == 0) successCount++ else failed += pkg
                }
                SuspendResult().apply {
                    failedPackages = failed.toTypedArray()
                    this.successCount = successCount
                }
            }
        }
        return commandMutex.withLock {
            val runner = awaitCommandRunner() ?: run {
                com.framescope.app.utils.FrameScopeLog.w("CommandRunner unavailable after bind attempt in suspendPackages")
                return@withLock null
            }
            try {
                withContext(Dispatchers.IO) {
                    runner.suspendPackages(packageNames.toTypedArray(), suspended)
                }
            } catch (e: Exception) {
                com.framescope.app.utils.FrameScopeLog.e("suspendPackages failed", e)
                null
            }
        }
    }

    suspend fun setAppOpMode(packageNames: List<String>, opCode: Int, mode: Int): Int {
        if (!hasPrivilegedBackend() || packageNames.isEmpty()) {
            com.framescope.app.utils.FrameScopeLog.w("setAppOpMode skipped: no privileged backend or package list empty")
            return 0
        }
        if (!useShizuku() && useBridge()) {
            return commandMutex.withLock {
                val modeName = when (mode) {
                    0 -> "allow"
                    1 -> "ignore"
                    2 -> "deny"
                    else -> "default"
                }
                var successCount = 0
                for (pkg in packageNames) {
                    if (bridgeClient.executeCommandWithResult("cmd appops set $pkg $opCode $modeName")?.exitCode == 0) {
                        successCount++
                    }
                }
                successCount
            }
        }
        return commandMutex.withLock {
            val runner = awaitCommandRunner() ?: run {
                com.framescope.app.utils.FrameScopeLog.w("CommandRunner unavailable after bind attempt in setAppOpMode")
                return@withLock 0
            }
            try {
                withContext(Dispatchers.IO) {
                    runner.setAppOpMode(packageNames.toTypedArray(), opCode, mode)
                }
            } catch (e: Exception) {
                com.framescope.app.utils.FrameScopeLog.e("setAppOpMode failed", e)
                0
            }
        }
    }

    private fun connectUserService() {
        if (commandRunner != null || isConnecting) return

        // OnBinderDeadListener only fires on a graceful Shizuku shutdown.
        // When the OS kills Shizuku abruptly (e.g. Nothing OS "adj 905: remove task" SIGKILL),
        // the binder death signal never arrives, leaving _isShizukuAvailable=true with a dead binder.
        // pingBinder() is the only reliable way to catch this case before we attempt binding.
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            com.framescope.app.utils.FrameScopeLog.w("Shizuku daemon dead (OS-level kill detected via pingBinder). Halting reconnect.")
            shizukuBinderAvailable = false
            shizukuPermissionGranted = false
            publishCombinedState()
            commandRunner = null
            isConnecting = false
            pendingConnection?.complete(null)
            pendingConnection = null
            return
        }

        isConnecting = true
        val deferred = CompletableDeferred<ICommandRunner?>()
        pendingConnection = deferred

        com.framescope.app.utils.FrameScopeLog.w("connectUserService: attempting bindUserService")

        val args = userServiceArgs()

        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (userServiceConnection !== this) return
                com.framescope.app.utils.FrameScopeLog.w("connectUserService: onServiceConnected")
                val runner = ICommandRunner.Stub.asInterface(service)
                commandRunner = runner
                isConnecting = false
                pendingConnection?.complete(runner)
                pendingConnection = null
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                if (userServiceConnection !== this) return
                com.framescope.app.utils.FrameScopeLog.w("connectUserService: onServiceDisconnected")
                commandRunner = null
                isConnecting = false
                pendingConnection?.complete(null)
                pendingConnection = null
            }
        }
        userServiceConnection = connection
        try {
            Shizuku.bindUserService(args, connection)
        } catch (e: Exception) {
            com.framescope.app.utils.FrameScopeLog.e("bindUserService failed", e)
            isConnecting = false
            shizukuBinderAvailable = false
            shizukuPermissionGranted = false
            publishCombinedState()
            pendingConnection?.complete(null)
            pendingConnection = null
        }
    }

    private fun disconnectUserService(remove: Boolean = false) {
        val conn = userServiceConnection
        if (conn == null) {
            commandRunner = null
            isConnecting = false
            pendingConnection?.complete(null)
            pendingConnection = null
            return
        }
        try {
            Shizuku.unbindUserService(userServiceArgs(), conn, remove)
        } catch (e: Exception) {
            com.framescope.app.utils.FrameScopeLog.e("unbindUserService failed", e)
        }
        userServiceConnection = null
        commandRunner = null
        isConnecting = false
        pendingConnection?.complete(null)
        pendingConnection = null
    }

    private fun userServiceArgs() = Shizuku.UserServiceArgs(
        ComponentName("com.framescope.app", CommandRunnerService::class.java.name)
    ).daemon(true)
        .tag(USER_SERVICE_TAG)
        .version(BuildConfig.VERSION_CODE)
        .processNameSuffix("runner")

    companion object {
        const val REQUEST_CODE_PERMISSION = 1001
        private const val BIND_TIMEOUT_MS = 5000L
        private const val INITIAL_BIND_DELAY_MS = 2000L
        private const val MAX_BIND_RETRIES = 3
        private const val BIND_RETRY_DELAY_MS = 500L
        private const val BRIDGE_POLL_INTERVAL_MS = 1500L
        private const val USER_SERVICE_TAG = "framescope-command-runner"
        private const val COMMAND_EXECUTION_FAILED = -1
    }
}
