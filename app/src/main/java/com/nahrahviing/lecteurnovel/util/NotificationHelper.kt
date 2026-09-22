package com.nahrahviing.lecteurnovel.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.nahrahviing.lecteurnovel.MainActivity
import com.nahrahviing.lecteurnovel.R

object NotificationHelper {

    const val CHANNEL_DOWNLOADS_ID = "channel_downloads"
    const val CHANNEL_UPDATES_ID = "channel_updates"

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canal pour les téléchargements (importance LOW pour éviter les bips répétés à chaque % de progression)
            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOADS_ID,
                "Téléchargements",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progression des téléchargements de romans et génération d'EPUB"
                enableVibration(false)
                setShowBadge(false)
            }

            // Canal pour les alertes de nouveaux chapitres
            val updateChannel = NotificationChannel(
                CHANNEL_UPDATES_ID,
                "Nouveaux chapitres",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alertes lorsque de nouveaux chapitres sont disponibles pour vos romans enregistrés"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(downloadChannel)
            notificationManager.createNotificationChannel(updateChannel)
        }
    }

    fun createDownloadNotification(
        context: Context,
        novelTitle: String,
        progress: Int,
        max: Int,
        statusText: String,
        cancelPendingIntent: PendingIntent? = null
    ): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isIndeterminate = max <= 0
        val percentage = if (max > 0) ((progress.toFloat() / max.toFloat()) * 100).toInt() else 0
        val contentSubtext = if (!isIndeterminate) "$percentage%" else ""

        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS_ID)
            .setContentTitle("Téléchargement : $novelTitle")
            .setContentText(statusText)
            .setSubText(contentSubtext)
            .setSmallIcon(R.drawable.ic_download_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .setProgress(if (isIndeterminate) 0 else max, progress, isIndeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (cancelPendingIntent != null) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Annuler",
                cancelPendingIntent
            )
        }

        return builder.build()
    }

    fun showDownloadCompleteNotification(
        context: Context,
        novelTitle: String,
        notificationId: Int
    ) {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS_ID)
            .setContentTitle("Téléchargement terminé")
            .setContentText("$novelTitle a été téléchargé avec succès.")
            .setSmallIcon(R.drawable.ic_download_done)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    fun showChapterUpdateNotification(
        context: Context,
        novelTitle: String,
        newChaptersCount: Int,
        notificationId: Int,
        detailText: String? = null
    ) {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = detailText ?: "$novelTitle : $newChaptersCount nouveau(x) chapitre(s) ajouté(s)."

        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES_ID)
            .setContentTitle("Nouveaux chapitres disponibles !")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_download_notification)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }
}
