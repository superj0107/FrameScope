package com.framescope.app.overlay

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.framescope.app.MainActivity
import com.framescope.app.R

/**
 * Builds the single, persistent FrameScope overlay notification.
 *
 * Stateless and side-effect free: given the current overlay visibility, it always returns
 * the same [Notification] content, so callers can rebuild-and-renotify on every state change
 * without duplicating notifications or leaking state into this class.
 *
 * Action wiring:
 *  - Body tap            -> always opens MainActivity.
 *  - Primary action       -> Start Overlay (when hidden) / Stop Overlay (when visible).
 *                            Never tears down the foreground service or cancels the notification.
 *  - Secondary action     -> Open App (when hidden) / Exit App (when visible).
 *                            Exit App is the only action that fully removes this notification.
 */
object OverlayNotificationBuilder {

    fun build(context: Context, isOverlayVisible: Boolean): Notification {
        val contentIntent = pendingActivityIntent(context)

        val primaryAction = if (isOverlayVisible) {
            action(
                context = context,
                icon = R.drawable.ic_action_stop,
                label = context.getString(R.string.overlay_notif_action_stop),
                broadcastAction = OverlayNotificationActionReceiver.ACTION_STOP_OVERLAY,
                requestCode = REQUEST_CODE_PRIMARY
            )
        } else {
            action(
                context = context,
                icon = R.drawable.ic_action_play,
                label = context.getString(R.string.overlay_notif_action_start),
                broadcastAction = OverlayNotificationActionReceiver.ACTION_START_OVERLAY,
                requestCode = REQUEST_CODE_PRIMARY
            )
        }

        val secondaryAction = if (isOverlayVisible) {
            action(
                context = context,
                icon = R.drawable.ic_action_close,
                label = context.getString(R.string.overlay_notif_action_exit),
                broadcastAction = OverlayNotificationActionReceiver.ACTION_EXIT_APP,
                requestCode = REQUEST_CODE_SECONDARY
            )
        } else {
            action(
                context = context,
                icon = R.drawable.ic_action_dashboard,
                label = context.getString(R.string.overlay_notif_action_open),
                broadcastAction = OverlayNotificationActionReceiver.ACTION_OPEN_APP,
                requestCode = REQUEST_CODE_SECONDARY
            )
        }

        val title = if (isOverlayVisible) {
            context.getString(R.string.overlay_notif_title_active)
        } else {
            context.getString(R.string.overlay_notif_title_stopped)
        }

        return NotificationCompat.Builder(context, OverlayService.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.overlay_notif_content_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.overlay_notif_content_text)))
            .addAction(primaryAction)
            .addAction(secondaryAction)
            .build()
    }

    private fun action(
        context: Context,
        icon: Int,
        label: String,
        broadcastAction: String,
        requestCode: Int
    ): NotificationCompat.Action {
        val intent = Intent(context, OverlayNotificationActionReceiver::class.java).apply {
            action = broadcastAction
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(icon, label, pendingIntent).build()
    }

    private fun pendingActivityIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_CONTENT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val REQUEST_CODE_CONTENT = 100
    private const val REQUEST_CODE_PRIMARY = 101
    private const val REQUEST_CODE_SECONDARY = 102
}
