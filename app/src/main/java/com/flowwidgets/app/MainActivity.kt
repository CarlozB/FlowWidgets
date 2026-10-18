package com.flowwidgets.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.flowwidgets.app.data.*
import com.flowwidgets.app.ui.FlowApp
import com.flowwidgets.app.widgets.WidgetActions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId =
            intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        val configuring = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        if (configuring) setResult(Activity.RESULT_CANCELED)
        lifecycleScope.launch {
            val kind =
                runCatching {
                        WidgetKind.valueOf(
                            intent.getStringExtra("widget_kind")?.uppercase() ?: "CLOCK"
                        )
                    }
                    .getOrDefault(WidgetKind.CLOCK)
            val initial =
                if (configuring) FlowRepository.ensureWidget(this@MainActivity, widgetId, kind)
                else null
            setContent {
                FlowApp(
                    initial,
                    configuring,
                    onSave = { config ->
                        lifecycleScope.launch {
                            FlowRepository.save(this@MainActivity, config)
                            WidgetActions.update(this@MainActivity, config.id).join()
                            if (configuring) {
                                setResult(
                                    Activity.RESULT_OK,
                                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                                )
                                finish()
                            } else
                                Toast.makeText(
                                        this@MainActivity,
                                        "Cambios guardados",
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                        }
                    },
                    onPin = { config ->
                        lifecycleScope.launch {
                            val pinned = WidgetActions.pin(this@MainActivity, config)
                            if (!pinned)
                                Toast.makeText(
                                        this@MainActivity,
                                        "Tu launcher no permite añadir directamente. Mantén pulsado el escritorio, abre Widgets y elige Flow Widgets.",
                                        Toast.LENGTH_LONG,
                                    )
                                    .show()
                        }
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}
