package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.local.AppDatabase

class ArushiApplication : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_ASSISTANT,
                "Arushi Background Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows Arushi Assistant status while running in background"
                setShowBadge(false)
            }

            val taskChannel = NotificationChannel(
                CHANNEL_ID_TASKS,
                "Arushi Task Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when background tasks finish or require attention"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(taskChannel)
        }
    }

    companion object {
        const val CHANNEL_ID_ASSISTANT = "arushi_assistant_channel"
        const val CHANNEL_ID_TASKS = "arushi_task_channel"

        lateinit var instance: ArushiApplication
            private set
    }
}
