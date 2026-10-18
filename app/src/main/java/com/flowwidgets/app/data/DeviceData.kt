package com.flowwidgets.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.CalendarContract

object DeviceData {
    fun battery(context: Context): BatteryInfo {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val source =
            when {
                plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "Corriente alterna"
                plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
                plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Inalámbrica"
                else -> null
            }
        val temperature =
            if (i?.hasExtra(BatteryManager.EXTRA_TEMPERATURE) == true)
                (i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) / 10.0).takeIf {
                    it in -40.0..100.0
                }
            else null
        val voltage =
            if (i?.hasExtra(BatteryManager.EXTRA_VOLTAGE) == true)
                i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0).takeIf { it > 0 }
            else null
        val health =
            when (
                i?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Normal"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Sobrecalentamiento"
                BatteryManager.BATTERY_HEALTH_COLD -> "Fría"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Deteriorada"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobretensión"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Fallo detectado"
                else -> null
            }
        return BatteryInfo(
            if (level >= 0 && scale > 0) level * 100 / scale else -1,
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
            source,
            temperature,
            voltage,
            health,
            i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() },
        )
    }

    fun events(context: Context): List<CalendarEvent> {
        if (
            context.checkSelfPermission(Manifest.permission.READ_CALENDAR) !=
                PackageManager.PERMISSION_GRANTED
        )
            return emptyList()
        return runCatching {
                val start = System.currentTimeMillis()
                val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(uri, start)
                ContentUris.appendId(uri, start + 7 * 24 * 60 * 60 * 1000L)
                val result = mutableListOf<CalendarEvent>()
                context.contentResolver
                    .query(
                        uri.build(),
                        arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
                        null,
                        null,
                        "${CalendarContract.Instances.BEGIN} ASC",
                    )
                    ?.use { c ->
                        while (c.moveToNext() && result.size < 12) result.add(
                            CalendarEvent(c.getString(0) ?: "Evento", c.getLong(1))
                        )
                    }
                result
            }
            .getOrDefault(emptyList())
    }

    @Suppress("DEPRECATION")
    fun apps(context: Context): List<AppShortcut> =
        context.packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                0,
            )
            .map {
                AppShortcut(
                    it.loadLabel(context.packageManager).toString(),
                    it.activityInfo.packageName,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
}
