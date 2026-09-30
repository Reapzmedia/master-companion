package com.mastercompanion.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import android.widget.Toast
import com.mastercompanion.R
import com.mastercompanion.data.remote.RemoteVaultRepository
import com.mastercompanion.data.remote.model.CommandProgressStatus
import com.mastercompanion.data.remote.model.PcPowerStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class QuickWakeWidget : AppWidgetProvider() {

    @Inject
    lateinit var remoteVaultRepository: RemoteVaultRepository

    companion object {
        const val ACTION_WAKE_PC = "com.mastercompanion.action.WIDGET_WAKE_PC"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WAKE_PC) {
            Toast.makeText(context, "Dispatched Wake signal to PC...", Toast.LENGTH_SHORT).show()

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope.launch {
                try {
                    remoteVaultRepository.dispatchWakeCommand()
                    Timber.i("Widget dispatched wake command")
                } catch (e: Exception) {
                    Timber.e(e, "Error dispatching wake from widget")
                }
            }

            // Immediately reflect Waking in widget views
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, QuickWakeWidget::class.java)
            val allIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (id in allIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_quick_wake)
                views.setTextViewText(R.id.widget_label, "Waking...")
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_wake)

        // Set click intent
        val intent = Intent(context, QuickWakeWidget::class.java).apply {
            action = ACTION_WAKE_PC
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val state = remoteVaultRepository.vaultState.value
            val isOnline = state.targetPc?.status == PcPowerStatus.ONLINE
            val label = if (isOnline) "PC Online" else "Wake PC"

            views.setTextViewText(R.id.widget_label, label)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
