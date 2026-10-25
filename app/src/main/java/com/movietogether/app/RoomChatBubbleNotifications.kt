package com.movietogether.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Person
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import com.movietogether.app.watch.ChatEntry
import com.movietogether.app.watch.RoomRuntime

/**
 * Android conversation notification with BubbleMetadata.
 * System settings ultimately decide whether the bubble can float; ordinary notification is fallback.
 * Never requires SYSTEM_ALERT_WINDOW.
 */
object RoomChatBubbleNotifications {
    private const val CHANNEL_ID = "room_chat_bubbles"
    private const val SHORTCUT_ID = "movie_room_chat"
    private const val NOTIFICATION_ID = 2209
    @Volatile private var appContext: Context? = null
    @Volatile private var appForeground = true

    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        val manager = app.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID, "Watch party chat", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Room messages and chat bubble while MovieTogether is minimized"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setAllowBubbles(true)
        }
        manager.createNotificationChannel(channel)
        publishShortcut(app)
    }

    fun setAppForeground(foreground: Boolean) {
        appForeground = foreground
    }

    fun onNewMessage(message: ChatEntry) {
        if (!appForeground) showRoomBubble(message)
    }

    fun showRoomBubble(message: ChatEntry? = null) {
        val app = appContext ?: return
        if (appForeground || (!RoomRuntime.host.value.running && !RoomRuntime.guest.value.connected)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return
        val manager = app.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        val chatIntent = Intent(app, ChatBubbleActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val chatPendingIntent = android.app.PendingIntent.getActivity(
            app, NOTIFICATION_ID, chatIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
        )
        val title = "MovieTogether room"
        val preview = message?.let { "${it.sender}: ${it.text}" } ?: "Open room chat"
        val sender = Person.Builder().setName(message?.sender ?: "MovieTogether").build()
        val currentUser = Person.Builder().setName("You").build()
        val style = Notification.MessagingStyle(currentUser)
            .setConversationTitle(title)
            .addMessage(message?.text ?: "Room chat is available",
                message?.timestampMs ?: System.currentTimeMillis(), sender)
        val builder = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(preview)
            .setContentIntent(chatPendingIntent)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setStyle(style)
            .setShortcutId(SHORTCUT_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setBubbleMetadata(Notification.BubbleMetadata.Builder(SHORTCUT_ID)
                .setDesiredHeight(520)
                .setAutoExpandBubble(false)
                .build())
        }
        // On devices that disable conversation bubbles, this remains a normal message notification.
        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    fun dismiss() {
        appContext?.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    fun openBubbleSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun publishShortcut(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val manager = context.getSystemService(ShortcutManager::class.java)
        val target = Intent(context, ChatBubbleActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val shortcut = ShortcutInfo.Builder(context, SHORTCUT_ID)
            .setShortLabel("Room chat")
            .setLongLabel("MovieTogether watch party chat")
            .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(target)
            .setLongLived(true)
            .setPerson(Person.Builder().setName("MovieTogether room").build())
            .build()
        runCatching { manager.addDynamicShortcuts(listOf(shortcut)) }
    }
}
