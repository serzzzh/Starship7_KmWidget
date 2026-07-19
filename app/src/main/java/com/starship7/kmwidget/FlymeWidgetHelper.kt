package com.starship7.kmwidget

import android.content.Context
import android.content.Intent

object FlymeWidgetHelper {

    private const val ACTION_TOGGLE_AICY_WIDGET = "com.flyme.auto.launcher.action.TOGGLE_AICY_WIDGET"
    private const val FLYME_LAUNCHER_PACKAGE = "com.flyme.auto.launcher"

    fun openWidgetPicker(context: Context): Boolean {
        return try {
            val intent = Intent(ACTION_TOGGLE_AICY_WIDGET).apply {
                setPackage(FLYME_LAUNCHER_PACKAGE)
            }
            context.sendBroadcast(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun installHomeShortcut(context: Context): Boolean {
        return try {
            val shortcutIntent = Intent(context, WidgetActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val installIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
                putExtra(Intent.EXTRA_SHORTCUT_NAME, context.getString(R.string.widget_shortcut_name))
                putExtra(
                    Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(context, R.mipmap.ic_launcher)
                )
                putExtra("duplicate", false)
            }
            context.sendBroadcast(installIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
