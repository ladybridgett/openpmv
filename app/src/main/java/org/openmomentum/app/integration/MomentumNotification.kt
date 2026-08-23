package org.openmomentum.app.integration

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.openmomentum.app.R
import org.openmomentum.app.model.HeadphoneState
import org.openmomentum.app.ui.MainActivity

object MomentumNotification {
    private const val CHANNEL_ID = "momentum_connection"
    private const val NOTIFICATION_ID = 4104

    fun publish(context: Context, state: HeadphoneState) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!state.reachable) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!canNotify(context)) return

        createChannel(manager)
        val details = listOfNotNull(
            state.batteryPercent?.let { "$it% battery" },
            state.noiseMode.displayName,
        ).joinToString(" · ")

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_headphones)
            .setContentTitle("MOMENTUM 4")
            .setContentText(details)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openAppIntent(context))
            .addAction(action(context, R.drawable.ic_noise_control, "ANC", MomentumActionReceiver.ACTION_ANC, 21))
            .addAction(action(context, R.drawable.ic_noise_control, "Hear", MomentumActionReceiver.ACTION_TRANSPARENCY, 22))
            .addAction(action(context, R.drawable.ic_noise_control, "Off", MomentumActionReceiver.ACTION_OFF, 23))
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun createChannel(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Headphone connection",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Battery and noise-control shortcuts while MOMENTUM 4 is connected"
                setShowBadge(false)
            },
        )
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        20,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(
        context: Context,
        icon: Int,
        title: String,
        action: String,
        requestCode: Int,
    ): Notification.Action {
        val intent = Intent(context, MomentumActionReceiver::class.java).setAction(action)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(icon, title, pending).build()
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
