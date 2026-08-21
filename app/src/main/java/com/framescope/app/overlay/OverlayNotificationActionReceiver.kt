package com.framescope.app.overlay

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import com.framescope.app.gaming.GamingModeEngine
import com.framescope.app.repository.SettingsRepository
import com.framescope.app.utils.FrameScopeLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles taps on the FrameScope overlay notification's action buttons.
 *
 * Manifest-declared (not runtime-registered) so delivery does not depend on our process
 * already being alive when the system dispatches the [android.app.PendingIntent]. Not
 * exported: only our own PendingIntents target this receiver.
 *
 * All actions are debounced against rapid double-taps, since a system tray button can be
 * tapped faster than a rebuild-and-renotify cycle completes.
 */
@AndroidEntryPoint
class OverlayNotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var overlayManager: OverlayManager

    @Inject
    lateinit var gamingModeEngine: GamingModeEngine

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastActionElapsedMs < DEBOUNCE_WINDOW_MS) {
            FrameScopeLog.d("Ignoring notification action tap within debounce window")
            return
        }
        lastActionElapsedMs = now

        when (intent.action) {
            ACTION_START_OVERLAY -> {
                overlayManager.showOverlay()
                renotify(context)
            }
            ACTION_STOP_OVERLAY -> {
                overlayManager.hideOverlay()
                renotify(context)
            }
            ACTION_OPEN_APP -> {
                openApp(context)
            }
            ACTION_EXIT_APP -> {
                exitApp(context)
            }
            else -> FrameScopeLog.w("Unknown overlay notification action: ${intent.action}")
        }
    }

    private fun renotify(context: Context) {
        val notification = OverlayNotificationBuilder.build(
            context = context,
            isOverlayVisible = overlayManager.isOverlayVisible.value
        )
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(OverlayService.NOTIFICATION_ID, notification)
    }

    private fun openApp(context: Context) {
        val launchIntent = Intent(context, com.framescope.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(launchIntent)
    }

    /**
     * Fully tears down FrameScope: cancels the notification and stops the overlay service
     * immediately (so the user sees FrameScope gone from the shade and screen right away),
     * then deactivates Gaming Mode gracefully in the background before force-stopping the
     * whole process.
     *
     * Ordering matters here to avoid two race conditions:
     *  1. We must call stopService(), never startService(), to tear down the overlay
     *     service. startService() on an already-stopped (or stopping) instance triggers a
     *     fresh onCreate(), which unconditionally re-shows the overlay -- that was the bug
     *     where the overlay reappeared 2-3s after Exit App.
     *  2. Gaming Mode teardown does Shizuku IPC and must not block onReceive/the main
     *     thread, but it must still complete (restoring suspended apps, disabling DND)
     *     before the process is killed, or the device is left in a degraded state.
     */
    private fun exitApp(context: Context) {
        overlayManager.hideOverlay()
        settingsRepository.setOverlayWasRunning(false)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(OverlayService.NOTIFICATION_ID)

        val stopIntent = Intent(context, OverlayService::class.java)
        context.stopService(stopIntent)

        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        receiverScope.launch {
            try {
                if (GamingModeEngine.isActive.value) {
                    gamingModeEngine.disableGamingMode()
                }
            } catch (e: Exception) {
                FrameScopeLog.e("Failed to deactivate Gaming Mode during Exit App", e)
            } finally {
                Process.killProcess(Process.myPid())
            }
        }
    }

    companion object {
        const val ACTION_START_OVERLAY = "com.framescope.app.ACTION_NOTIF_START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.framescope.app.ACTION_NOTIF_STOP_OVERLAY"
        const val ACTION_OPEN_APP = "com.framescope.app.ACTION_NOTIF_OPEN_APP"
        const val ACTION_EXIT_APP = "com.framescope.app.ACTION_NOTIF_EXIT_APP"

        private const val DEBOUNCE_WINDOW_MS = 500L
        @Volatile private var lastActionElapsedMs = 0L
    }
}
