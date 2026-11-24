package io.openvault.app.core.security

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.openvault.app.MainActivity
import io.openvault.app.OpenVaultApplication
import io.openvault.app.R

/**
 * Manages live clipboard countdown warning notifications when copying sensitive secrets
 * (TOTP codes, passwords, SafeKey / Vault+ Recovery Keys, cards, etc.) and handles
 * instant "Clear Now" actions.
 */
object ClipboardNotificationHelper {
    const val CHANNEL_ID = "openvault_clipboard_v1"
    const val NOTIFICATION_ID = 4102
    const val ACTION_CLEAR_CLIPBOARD_NOW = "io.openvault.app.action.CLEAR_CLIPBOARD_NOW"
    private const val ACCENT_COLOR = 0xFF0C74F2.toInt()

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val appCtx = context.applicationContext
        val isVi = (appCtx as? OpenVaultApplication)?.container?.preferences?.settings?.value
            ?.language?.isVietnameseActive() == true
        val manager = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            if (isVi) "Cảnh báo bộ nhớ tạm OpenVault" else "OpenVault Clipboard Guard",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = if (isVi) {
                "Hiển thị đếm ngược xóa tự động sau khi sao chép mật khẩu, TOTP hoặc Recovery Key."
            } else {
                "Shows auto-clear countdown warnings after copying passwords, TOTP codes, or Recovery Keys."
            }
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        val osGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return osGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun showCountdownNotification(
        context: Context,
        label: String,
        expiresAtMs: Long,
        totalSeconds: Int,
        isVi: Boolean,
    ) {
        val appCtx = context.applicationContext
        if (!hasNotificationPermission(appCtx)) return
        ensureChannel(appCtx)

        val remainingMs = (expiresAtMs - System.currentTimeMillis()).coerceAtLeast(1_000L)
        val title = if (isVi) "Đã sao chép $label" else "Copied $label"
        val body = if (isVi) {
            "Tự xóa khỏi bộ nhớ tạm khi hết giờ"
        } else {
            "Auto-clears from clipboard when timer ends"
        }
        val subText = if (isVi) "Bộ nhớ tạm · ${totalSeconds}s" else "Clipboard · ${totalSeconds}s"

        val publicVersion = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_openvault)
            .setSubText(subText)
            .setContentTitle(if (isVi) "Bảo vệ bộ nhớ tạm OpenVault" else "OpenVault Clipboard Guard")
            .setContentText(
                if (isVi) "Tự động xóa dữ liệu nhạy cảm khi hết giờ"
                else "Auto-clearing copied secret when timer ends",
            )
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(expiresAtMs)
            .setShowWhen(true)
            .setColor(ACCENT_COLOR)
            .build()

        val notification = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_openvault)
            .setSubText(subText)
            .setContentTitle(title)
            .setContentText(body)
            .setColor(ACCENT_COLOR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(expiresAtMs)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(remainingMs + 1_500L)
            .setContentIntent(openAppPendingIntent(appCtx))
            .addAction(
                0,
                if (isVi) "Xóa ngay" else "Clear Now",
                clearNowPendingIntent(appCtx),
            )
            .build()

        runCatching {
            NotificationManagerCompat.from(appCtx).notify(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    fun showStaticCopiedNotification(
        context: Context,
        label: String,
        isSensitive: Boolean,
        isVi: Boolean,
    ) {
        val appCtx = context.applicationContext
        if (!hasNotificationPermission(appCtx)) return
        ensureChannel(appCtx)

        val title = if (isVi) "Đã sao chép $label" else "Copied $label"
        val body = if (isVi) {
            if (isSensitive) "Đang lưu trong bộ nhớ tạm (chưa bật tự xóa). Bấm để xóa ngay."
            else "Đã sao chép vào bộ nhớ tạm."
        } else {
            if (isSensitive) "Stored in clipboard (auto-clear off). Tap to clear now."
            else "Copied to clipboard."
        }

        val builder = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_openvault)
            .setContentTitle(title)
            .setContentText(body)
            .setColor(ACCENT_COLOR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(if (isSensitive) 15_000L else 8_000L)
            .setContentIntent(openAppPendingIntent(appCtx))
            .addAction(
                0,
                if (isVi) "Xóa ngay" else "Clear Now",
                clearNowPendingIntent(appCtx),
            )

        runCatching {
            NotificationManagerCompat.from(appCtx).notify(NOTIFICATION_ID, builder.build())
        }
    }

    @SuppressLint("MissingPermission")
    fun showClearedConfirmation(
        context: Context,
        label: String?,
        isVi: Boolean,
    ) {
        val appCtx = context.applicationContext
        if (!hasNotificationPermission(appCtx)) return
        ensureChannel(appCtx)

        val target = label?.takeIf { it.isNotBlank() }
            ?: if (isVi) "dữ liệu nhạy cảm" else "sensitive data"
        val notification = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_openvault)
            .setSubText("OpenVault")
            .setContentTitle(if (isVi) "Đã xóa bộ nhớ tạm" else "Clipboard Cleared")
            .setContentText(
                if (isVi) "Đã xóa $target một cách an toàn."
                else "Safely cleared $target from clipboard.",
            )
            .setColor(ACCENT_COLOR)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setShowWhen(false)
            .setUsesChronometer(false)
            .setOngoing(false)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(3_500L)
            .build()

        runCatching {
            NotificationManagerCompat.from(appCtx).notify(NOTIFICATION_ID, notification)
        }
    }

    fun cancelNotification(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
        }
    }

    private fun clearNowPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ClipboardClearReceiver::class.java).apply {
            action = ACTION_CLEAR_CLIPBOARD_NOW
        }
        return PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class ClipboardClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ClipboardNotificationHelper.ACTION_CLEAR_CLIPBOARD_NOW) return
        val app = context.applicationContext as? OpenVaultApplication ?: return
        app.container.clipboard.clearNow(force = true)
    }
}
