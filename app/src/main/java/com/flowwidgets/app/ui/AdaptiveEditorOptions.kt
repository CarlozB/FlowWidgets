package com.flowwidgets.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.flowwidgets.app.data.*
import java.util.TimeZone
import java.util.UUID

@Composable
fun AdaptiveEditorOptions(config: WidgetConfig, change: (WidgetConfig) -> Unit) {
    val context = LocalContext.current
    Text("Diseño adaptativo", style = MaterialTheme.typography.titleMedium)
    Text("Al ampliar aparecen más detalles y controles. El tamaño final depende del launcher.")
    Text("Opacidad")
    Slider(config.opacity, { change(config.copy(opacity = it)) }, valueRange = .25f..1f)
    run {
        Text("LiquidGlass · Transparencia del fondo")
        Slider(
            config.glassOpacity,
            { change(config.copy(glassOpacity = it)) },
            valueRange = .25f..1f,
        )
        Text("Iluminación")
        Slider(config.glassLighting, { change(config.copy(glassLighting = it)) })
        Text("Esquinas")
        Slider(config.glassCorner, { change(config.copy(glassCorner = it)) }, valueRange = 8f..40f)
    }
    if (config.kind == WidgetKind.CLOCK) {
        var query by remember { mutableStateOf("") }
        var picking by remember { mutableStateOf(false) }
        val permission =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                change(config.copy(showEvents = it))
            }
        Row {
            Text("Eventos al ampliar", Modifier.weight(1f))
            Switch(
                config.showEvents,
                {
                    if (
                        !it ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_CALENDAR,
                            ) == PackageManager.PERMISSION_GRANTED
                    )
                        change(config.copy(showEvents = it))
                    else permission.launch(Manifest.permission.READ_CALENDAR)
                },
            )
        }
        Text("Zonas horarias · hasta tres")
        config.timeZones.forEach { zone ->
            TextButton({ change(config.copy(timeZones = config.timeZones - zone)) }) {
                Text("$zone  ×")
            }
        }
        OutlinedButton({ picking = true }, enabled = config.timeZones.size < 3) {
            Text("Añadir zona horaria")
        }
        if (picking)
            AlertDialog(
                onDismissRequest = { picking = false },
                title = { Text("Zona horaria") },
                text = {
                    Column {
                        OutlinedTextField(
                            query,
                            { query = it },
                            label = { Text("Buscar ciudad o zona") },
                        )
                        Column(
                            Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())
                        ) {
                            TimeZone.getAvailableIDs()
                                .filter { it.contains(query, true) && it !in config.timeZones }
                                .take(100)
                                .forEach { zone ->
                                    TextButton({
                                        change(
                                            config.copy(
                                                timeZones = (config.timeZones + zone).take(3)
                                            )
                                        )
                                        picking = false
                                    }) {
                                        Text(zone)
                                    }
                                }
                        }
                    }
                },
                confirmButton = { TextButton({ picking = false }) { Text("Cerrar") } },
            )
    }
    if (config.kind == WidgetKind.NOTE) {
        var text by remember { mutableStateOf("") }
        Text("Lista al ampliar · toca cada tarea en el widget para marcarla")
        config.noteItems.forEach { entry ->
            Row {
                Checkbox(
                    entry.checked,
                    { checked ->
                        change(
                            config.copy(
                                noteItems =
                                    config.noteItems.map {
                                        if (it.id == entry.id) it.copy(checked = checked) else it
                                    }
                            )
                        )
                    },
                )
                Text(entry.text, Modifier.weight(1f))
                TextButton({
                    change(
                        config.copy(noteItems = config.noteItems.filterNot { it.id == entry.id })
                    )
                }) {
                    Text("×")
                }
            }
        }
        OutlinedTextField(
            text,
            { text = it },
            label = { Text("Nueva tarea") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            {
                change(
                    config.copy(
                        noteItems =
                            config.noteItems + NoteEntry(UUID.randomUUID().toString(), text.trim())
                    )
                )
                text = ""
            },
            enabled = text.isNotBlank() && config.noteItems.size < 20,
        ) {
            Text("Añadir tarea")
        }
    }
    if (config.kind == WidgetKind.SHORTCUT) {
        var picking by remember { mutableStateOf(false) }
        val apps = remember { DeviceData.apps(context) }
        fun add(entry: ShortcutEntry) {
            if (config.shortcuts.size < 15)
                change(config.copy(shortcuts = config.shortcuts + entry))
        }
        val contact =
            rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
                if (uri != null)
                    add(
                        ShortcutEntry(
                            UUID.randomUUID().toString(),
                            "Contacto",
                            uri = uri.toString(),
                        )
                    )
            }
        Text("Más accesos al ampliar · hasta 16 en total")
        config.shortcuts.forEach { entry ->
            Row {
                Text(entry.label.ifBlank { entry.packageName ?: "Acceso" }, Modifier.weight(1f))
                TextButton({
                    change(
                        config.copy(shortcuts = config.shortcuts.filterNot { it.id == entry.id })
                    )
                }) {
                    Text("×")
                }
            }
        }
        OutlinedButton({ picking = true }, enabled = config.shortcuts.size < 15) {
            Text("Añadir aplicación")
        }
        OutlinedButton({ contact.launch(null) }, enabled = config.shortcuts.size < 15) {
            Text("Añadir contacto")
        }
        if (config.shortcuts.size < 15)
            Row {
                listOf(
                        "Ajustes" to "android.settings.SETTINGS",
                        "Wi-Fi" to "android.settings.WIFI_SETTINGS",
                        "Bluetooth" to "android.settings.BLUETOOTH_SETTINGS",
                    )
                    .forEach { (label, uri) ->
                        TextButton({
                            add(ShortcutEntry(UUID.randomUUID().toString(), label, uri = uri))
                        }) {
                            Text(label)
                        }
                    }
            }
        if (picking)
            AlertDialog(
                onDismissRequest = { picking = false },
                title = { Text("Aplicaciones") },
                text = {
                    Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                        apps.forEach { app ->
                            TextButton({
                                add(
                                    ShortcutEntry(
                                        UUID.randomUUID().toString(),
                                        app.label,
                                        packageName = app.packageName,
                                    )
                                )
                                picking = false
                            }) {
                                Text(app.label)
                            }
                        }
                    }
                },
                confirmButton = { TextButton({ picking = false }) { Text("Cerrar") } },
            )
    }
}
