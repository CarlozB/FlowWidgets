package com.flowwidgets.app.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle

/** The same editor handles in-app editing and launcher configuration. */
class WidgetConfigureActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setResult(RESULT_CANCELED)
        widgetId =
            intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        if (state == null) {
            val provider =
                AppWidgetManager.getInstance(this)
                    .getAppWidgetInfo(widgetId)
                    ?.provider
                    ?.shortClassName
                    ?.substringAfterLast('.')
                    ?.removeSuffix("Widget")
                    ?.lowercase() ?: "clock"
            val editor =
                Intent()
                    .setClassName(packageName, "$packageName.MainActivity")
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .putExtra("widget_kind", provider)
            startActivityForResult(editor, 104)
        }
    }

    @Deprecated("Activity callback retained for launcher configuration")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 104) {
            if (resultCode == RESULT_OK) {
                AdaptiveWidgetProvider.updateAll(this)
                setResult(
                    RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                )
            }
            finish()
        }
    }
}
