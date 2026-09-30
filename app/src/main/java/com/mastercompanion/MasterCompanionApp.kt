package com.mastercompanion

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class MasterCompanionApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Plant Timber tree for full observability
        Timber.plant(Timber.DebugTree())

        Timber.i("MasterCompanion application starting...")
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channels = listOf(
                NotificationChannel(
                    CHANNEL_BATTERY_GUARD,
                    getString(R.string.battery_guard_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Persistent foreground notification for battery monitoring and charge protection"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_COMMAND_BRIDGE,
                    getString(R.string.command_bridge_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Persistent notification for local Ktor HTTP command server"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_AUDIO_RECEIVER,
                    getString(R.string.audio_receiver_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Foreground playback notification for low-latency PC audio streaming"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_PC_READY,
                    "PC Boot Status",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "High-priority notification when your PC completes boot verification"
                    setShowBadge(true)
                    enableVibration(true)
                }
            )

            channels.forEach { notificationManager.createNotificationChannel(it) }
        }
    }

    companion object {
        const val CHANNEL_BATTERY_GUARD = "channel_battery_guard"
        const val CHANNEL_COMMAND_BRIDGE = "channel_command_bridge"
        const val CHANNEL_AUDIO_RECEIVER = "channel_audio_receiver"
        const val CHANNEL_PC_READY = "channel_pc_ready"
    }
}
