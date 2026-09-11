package com.mastercompanion.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.i("Boot completed received, starting background services...")

            val batteryGuardIntent = Intent(context, BatteryGuardService::class.java)
            val commandBridgeIntent = Intent(context, CommandBridgeService::class.java)
            val audioReceiverIntent = Intent(context, AudioReceiverService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(batteryGuardIntent)
                context.startForegroundService(commandBridgeIntent)
                context.startForegroundService(audioReceiverIntent)
            } else {
                context.startService(batteryGuardIntent)
                context.startService(commandBridgeIntent)
                context.startService(audioReceiverIntent)
            }
        }
    }
}
