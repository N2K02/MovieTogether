package com.movietogether.app.watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.movietogether.app.MainActivity

/** Explicit user-started foreground hosting; no boot receiver or hidden persistence. */
class HostService : Service() {
    companion object {
        private const val CHANNEL = "watch_party"
        private const val NOTIFICATION_ID = 2208
        private const val ACTION_START = "com.movietogether.app.START_HOST"
        private const val ACTION_STOP = "com.movietogether.app.STOP_HOST"

        fun start(context: Context, uri: Uri, title: String) {
            val intent = Intent(context, HostService::class.java)
                .setAction(ACTION_START)
                .putExtra("movieUri", uri.toString())
                .putExtra("movieTitle", title)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }
        fun stop(context: Context) {
            context.startService(Intent(context, HostService::class.java).setAction(ACTION_STOP))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        val uri = intent.getStringExtra("movieUri")?.let(Uri::parse)
        if (uri == null) { stopSelf(); return START_NOT_STICKY }
        try {
            RoomRuntime.startServer(this, uri, intent.getStringExtra("movieTitle").orEmpty())
        } catch (e: Exception) {
            Log.e("MovieTogether", "Cannot start host", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL, "MovieTogether watch party", NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, HostService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("MovieTogether room is running")
            .setContentText("Selected movie is available to paired viewers")
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_delete, "Stop sharing", stop)
            .setOngoing(true)
            .build()
    }

    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }

    override fun onDestroy() {
        if (RoomRuntime.host.value.running) RoomRuntime.stopServer()
        super.onDestroy()
    }
}
