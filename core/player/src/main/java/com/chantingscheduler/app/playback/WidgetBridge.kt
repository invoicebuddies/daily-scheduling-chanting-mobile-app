package com.chantingscheduler.app.playback

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Nudges the home-screen widget to refresh. Lives in core:player (the lowest
 * module that knows when sessions start/stop); the actual widget receiver is
 * declared in the app module with an APPWIDGET_UPDATE intent filter, so a
 * package-targeted broadcast reaches it without a compile-time dependency.
 */
object WidgetBridge {

    fun requestUpdate(context: Context) {
        runCatching {
            val manager = AppWidgetManager.getInstance(context) ?: return
            // The widget provider class lives in the app module; referencing it
            // by name keeps this module decoupled.
            val provider = ComponentName(
                context.packageName,
                "com.chantingscheduler.app.ui.widget.NextSessionWidgetReceiver",
            )
            val ids = manager.getAppWidgetIds(provider)
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .setPackage(context.packageName)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
    }
}
