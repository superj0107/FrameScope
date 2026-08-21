package com.framescope.app.gaming

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Notification Listener Service that intercepts and cancels ALL incoming
 * notifications while Gaming Mode is active.
 *
 * Rationale: OriginOS sometimes bypasses DND for internal "System warnings"
 * and "Battery alerts".  This listener provides a second layer of suppression
 * that operates independently of the NotificationManager DND API.
 *
 * The service must be enabled by the user via
 * Settings → Apps → Special App Access → Notification Access.
 */
@AndroidEntryPoint
class GamingNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Observe Gaming Mode state — when it flips to active, immediately purge
        // every existing notification in the tray (except our own FGS / recovery ones).
        serviceScope.launch {
            GamingModeEngine.isActive.collectLatest { active ->
                if (active) {
                    purgeExistingNotifications()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /**
     * Walk the current notification tray and cancel everything that isn't ours.
     */
    private fun purgeExistingNotifications() {
        try {
            val current = activeNotifications ?: return
            for (sbn in current) {
                if (sbn.packageName == packageName) {
                    if (sbn.id == GamingModeService.NOTIFICATION_ID ||
                        sbn.id == GamingModeEngine.RECOVERY_NOTIFICATION_ID) continue
                }
                try {
                    cancelNotification(sbn.key)
                } catch (_: Exception) { /* non-fatal */ }
            }
        } catch (_: Exception) { /* service might be disconnected */ }
    }

    /**
     * Called whenever a new notification is posted.
     * If Gaming Mode is active, cancel it immediately.
     */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // Do not touch our own notifications — that would cause a loop or hide recovery alerts.
        if (sbn.packageName == packageName) {
            if (sbn.id == GamingModeService.NOTIFICATION_ID || sbn.id == GamingModeEngine.RECOVERY_NOTIFICATION_ID) return
        }

        if (GamingModeEngine.isActive.value) {
            try {
                cancelNotification(sbn.key)
            } catch (e: Exception) {
                // Swallow — failing to cancel a notification is non-fatal.
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No action needed on removal.
    }
}
