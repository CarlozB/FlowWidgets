package com.flowwidgets.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.flowwidgets.app.data.*
import kotlinx.coroutines.launch

@Composable
fun Editor(
    initial: WidgetConfig,
    saving: Boolean,
    onBack: () -> Unit,
    onDone: (WidgetConfig) -> Unit,
) {
    var config by remember(initial) { mutableStateOf(initial) }
    val visiblePalette = com.flowwidgets.app.widgets.VisualWidgetRenderer.palette(config)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()
    var message by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    val calendar =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            config = config.copy(showEvents = granted)
            if (!granted) message = "Puedes usar el calendario sin acceso a eventos."
        }
    val contact =
        rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
            if (uri != null)
                config = config.copy(shortcutUri = uri.toString(), shortcutPackage = null)
        }
    val photos =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) {
            uris ->
            if (uris.isNotEmpty()) {
                uris.forEach {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            it,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
                config =
                    config.copy(
                        photoUri = uris.first().toString(),
                        photoUris = uris.map { it.toString() },
                    )
            }
        }
    val apps = remember {
        if (config.kind == WidgetKind.SHORTCUT) DeviceData.apps(context) else emptyList()
    }
    var showApps by remember { mutableStateOf(false) }
    var locations by remember { mutableStateOf<List<WeatherLocation>>(emptyList()) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Spacer(Modifier.weight(1f))
            Text("TU DISEÑO", fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text("✦", color = Coral, modifier = Modifier.padding(12.dp))
        }
        Column(
            Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .widthIn(max = 760.dp)
                .align(Alignment.CenterHorizontally)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Hazlo tuyo.", fontSize = 36.sp, letterSpacing = (-1).sp)
            Text("La información real, con tu estilo personal.", color = Muted)
            ConfigPreview(config, Modifier.fillMaxWidth().height(260.dp), simulateSize = true)
            Text(
                "Vista previa · El launcher adapta el espacio disponible",
                fontSize = 11.sp,
                color = Muted,
            )
            OutlinedTextField(
                value = config.title,
                onValueChange = { config = config.copy(title = it) },
                label = { Text("Nombre del widget") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Familia visual", fontSize = 18.sp)
            Choices(
                WidgetStyle.entries.map { it.name },
                config.style.name,
                { it.lowercase().replaceFirstChar(Char::uppercaseChar) },
            ) { choice ->
                val style = WidgetStyle.valueOf(choice)
                config =
                    when (style) {
                        WidgetStyle.MATERIAL -> {
                            val palette =
                                if (systemDark) dynamicDarkColorScheme(context)
                                else dynamicLightColorScheme(context)
                            config.copy(
                                style = style,
                                automaticColors = style == WidgetStyle.CLASSIC,
                                background =
                                    palette.surfaceContainer.toArgb().toLong() and 0xFFFFFFFFL,
                                accent = palette.primary.toArgb().toLong() and 0xFFFFFFFFL,
                                textColor = palette.onSurface.toArgb().toLong() and 0xFFFFFFFFL,
                            )
                        }
                        WidgetStyle.GLASS ->
                            config.copy(
                                style = style,
                                automaticColors = style == WidgetStyle.CLASSIC,
                                background = 0xCC27333C,
                                accent = 0xFFA9C5D5,
                                textColor = 0xFFFFFFFF,
                            )
                        WidgetStyle.CLASSIC ->
                            config.copy(
                                style = style,
                                automaticColors = style == WidgetStyle.CLASSIC,
                                background = 0xFF20272D,
                                accent = 0xFFFF725E,
                                textColor = 0xFFFFFFFF,
                            )
                    }
            }
            Text("Color de acento", fontSize = 18.sp)
            Swatches(visiblePalette.accent.toLong() and 0xFFFFFFFFL) {
                config = explicitPalette(config).copy(accent = it)
            }
            Text("Fondo", fontSize = 18.sp)
            Swatches(visiblePalette.background.toLong() and 0xFFFFFFFFL) {
                config = explicitPalette(config).copy(background = it)
            }
            Text("Color del texto", fontSize = 18.sp)
            Swatches(visiblePalette.foreground.toLong() and 0xFFFFFFFFL) {
                config = explicitPalette(config).copy(textColor = it)
            }
            Text("Tipografía", fontSize = 18.sp)
            Choices(
                listOf("inter", "system", "serif", "monospace"),
                config.font,
                {
                    when (it) {
                        "inter" -> "Inter"
                        "system" -> "Sistema"
                        "serif" -> "Editorial"
                        else -> "Monoespaciada"
                    }
                },
            ) {
                config = config.copy(font = it)
            }
            if (config.font == "inter") {
                Text("Peso de Inter", fontSize = 18.sp)
                Choices(
                    listOf("0", "300", "400", "500", "600", "700"),
                    config.fontWeight.toString(),
                    {
                        when (it) {
                            "300" -> "Light"
                            "400" -> "Regular"
                            "500" -> "Medium"
                            "600" -> "Semibold"
                            "700" -> "Bold"
                            else -> "Automático"
                        }
                    },
                ) {
                    config = config.copy(fontWeight = it.toInt())
                }
            }
            Text("Tamaño del texto", fontSize = 18.sp)
            Slider(
                value = config.textScale,
                onValueChange = { config = config.copy(textScale = it) },
                valueRange = .7f..1.5f,
            )
            Text("Tamaño recomendado", fontSize = 18.sp)
            Choices(
                com.flowwidgets.app.widgets.AdaptiveWidgetLayoutEngine.previewSizes.keys.toList(),
                config.size,
            ) {
                config = config.copy(size = it)
            }
            AdaptiveEditorOptions(config) { config = it }
            HorizontalDivider()
            when (config.kind) {
                WidgetKind.CLOCK -> {
                    Text(
                        "En tamaños compactos 1×1 y 2×2 se muestra únicamente la esfera grande. Las opciones de información y reloj digital se aplican a tamaños mayores.",
                        color = Muted,
                    )
                    Toggle("Reloj analógico", config.analog) { config = config.copy(analog = it) }
                    Toggle("Formato de 24 horas", config.clock24) {
                        config = config.copy(clock24 = it)
                    }
                    Toggle("Mostrar fecha", config.showDate) { config = config.copy(showDate = it) }
                    Toggle("Segundero (si el launcher lo permite)", config.showSeconds) {
                        config = config.copy(showSeconds = it)
                    }
                }
                WidgetKind.CALENDAR -> {
                    Toggle("La semana comienza el lunes", config.weekStartsMonday) {
                        config = config.copy(weekStartsMonday = it)
                    }
                    Toggle("Mostrar próximos eventos", config.showEvents) {
                        if (!it) config = config.copy(showEvents = false)
                        else if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_CALENDAR,
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                            config = config.copy(showEvents = true)
                        else calendar.launch(Manifest.permission.READ_CALENDAR)
                    }
                    Text(
                        "Solo se consulta tu calendario si activas los eventos.",
                        fontSize = 12.sp,
                        color = Muted,
                    )
                }
                WidgetKind.NOTE -> {
                    OutlinedTextField(
                        value = config.note,
                        onValueChange = { config = config.copy(note = it) },
                        label = { Text("Tu nota o frase") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Choices(
                        listOf("start", "center", "end"),
                        config.alignment,
                        {
                            when (it) {
                                "start" -> "Izquierda"
                                "center" -> "Centro"
                                else -> "Derecha"
                            }
                        },
                    ) {
                        config = config.copy(alignment = it)
                    }
                }
                WidgetKind.PHOTO -> {
                    Button(
                        onClick = {
                            photos.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    ) {
                        Text(
                            if (config.photoUri == null) "Elegir fotografías"
                            else "Cambiar fotografías"
                        )
                    }
                    Text(
                        "${config.photoUris.size} fotos seleccionadas · Sin acceso completo a la galería",
                        fontSize = 12.sp,
                        color = Muted,
                    )
                    Choices(
                        listOf("crop", "fit"),
                        config.photoScale,
                        { if (it == "crop") "Rellenar" else "Ajustar" },
                    ) {
                        config = config.copy(photoScale = it)
                    }
                    Toggle("Alternar fotos periódicamente", config.rotatePhotos) {
                        config = config.copy(rotatePhotos = it)
                    }
                }
                WidgetKind.MEDIA -> {
                    Text("Conecta tu música", fontSize = 20.sp)
                    Text(
                        "Para consultar la sesión activa y sus controles, habilita Flow Widgets en acceso a notificaciones. No almacenamos tus notificaciones.",
                        color = Muted,
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            )
                        }
                    ) {
                        Text("Habilitar acceso multimedia")
                    }
                }
                WidgetKind.SHORTCUT -> {
                    Text("Icono del acceso", fontSize = 18.sp)
                    Choices(
                        listOf("app", "✦", "↗", "●", "♡"),
                        config.shortcutIcon,
                        { if (it == "app") "Aplicación" else it },
                    ) {
                        config = config.copy(shortcutIcon = it)
                    }
                    OutlinedButton(onClick = { showApps = true }) {
                        Text(
                            apps.find { it.packageName == config.shortcutPackage }?.label
                                ?: "Elegir aplicación"
                        )
                    }
                    OutlinedButton(onClick = { contact.launch(null) }) {
                        Text("Elegir un contacto")
                    }
                    Text(
                        "El selector del sistema comparte solo el contacto que elijas.",
                        fontSize = 12.sp,
                        color = Muted,
                    )
                    Text("O abre una función del sistema", color = Muted)
                    Choices(
                        listOf(
                            "android.settings.SETTINGS",
                            "android.settings.WIFI_SETTINGS",
                            "android.settings.BLUETOOTH_SETTINGS",
                        ),
                        config.shortcutUri ?: "",
                        {
                            when (it) {
                                "android.settings.SETTINGS" -> "Ajustes"
                                "android.settings.WIFI_SETTINGS" -> "Wi-Fi"
                                else -> "Bluetooth"
                            }
                        },
                    ) {
                        config = config.copy(shortcutUri = it, shortcutPackage = null)
                    }
                }
                WidgetKind.WEATHER -> {
                    OutlinedTextField(
                        value = config.city,
                        onValueChange = {
                            config = config.copy(city = it, latitude = null, longitude = null)
                        },
                        label = { Text("Ciudad") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        enabled = config.city.isNotBlank() && !working,
                        onClick = {
                            scope.launch {
                                working = true
                                message = ""
                                runCatching { WeatherRepository.search(config.city) }
                                    .onSuccess {
                                        locations = it
                                        if (it.isEmpty()) message = "No encontramos esa ciudad."
                                    }
                                    .onFailure {
                                        message = "No se pudo buscar. Revisa tu conexión."
                                    }
                                working = false
                            }
                        },
                    ) {
                        Text(if (working) "Buscando…" else "Buscar ubicación")
                    }
                    locations.forEach { location ->
                        TextButton(
                            onClick = {
                                config =
                                    config.copy(
                                        city = location.name,
                                        latitude = location.latitude,
                                        longitude = location.longitude,
                                    )
                                locations = emptyList()
                                scope.launch {
                                    val result = WeatherRepository.refresh(context, config)
                                    message =
                                        if (result == null)
                                            "Ubicación guardada. No hay datos disponibles: revisa la conexión y vuelve a consultar."
                                        else "Pronóstico guardado. Última consulta disponible."
                                    com.flowwidgets.app.widgets.WidgetActions.update(context)
                                }
                            }
                        ) {
                            Text(location.name)
                        }
                    }
                    if (config.latitude != null)
                        Text("✓ Ubicación guardada: ${config.city}", color = Muted)
                    Text(
                        "Datos de Open-Meteo · Sin claves privadas · Caché sin conexión",
                        fontSize = 12.sp,
                        color = Muted,
                    )
                }
                else ->
                    Text(
                        "Usa el nivel real de batería del dispositivo. Se actualiza al cambiar el estado de carga y periódicamente.",
                        color = Muted,
                    )
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(10.dp))
        }
        Surface(shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Button(
                    onClick = { onDone(config) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Ink),
                ) {
                    Text(if (saving) "Guardar cambios" else "Añadir a la pantalla de inicio  ↗")
                }
                if (!saving)
                    Text(
                        "Android te pedirá confirmar dónde colocar el widget.",
                        fontSize = 11.sp,
                        color = Muted,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                    )
            }
        }
    }
    if (showApps)
        AlertDialog(
            onDismissRequest = { showApps = false },
            title = { Text("Elige una aplicación") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    apps.forEach { app ->
                        TextButton(
                            onClick = {
                                config =
                                    config.copy(
                                        shortcutPackage = app.packageName,
                                        shortcutUri = null,
                                    )
                                showApps = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(app.label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showApps = false }) { Text("Cerrar") } },
        )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun Swatches(selected: Long, onSelect: (Long) -> Unit) {
    val colors =
        listOf(
            0xFFFF725EL,
            0xFF20272DL,
            0xFFFFFFFFL,
            0xFFBFD99AL,
            0xFFE2BF7AL,
            0xFF97BCD3L,
            0xFFCEADD5L,
            0xFF56664FL,
        )
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        colors.forEach { color ->
            Box(
                Modifier.size(35.dp)
                    .border(
                        if (selected == color) 3.dp else 1.dp,
                        if (selected == color) Coral else Muted,
                        CircleShape,
                    )
                    .padding(4.dp)
                    .background(Color(color), CircleShape)
                    .clickable { onSelect(color) }
            )
        }
    }
}

private fun explicitPalette(config: WidgetConfig): WidgetConfig {
    val palette = com.flowwidgets.app.widgets.VisualWidgetRenderer.palette(config)
    return config.copy(
        background = palette.background.toLong() and 0xFFFFFFFFL,
        textColor = palette.foreground.toLong() and 0xFFFFFFFFL,
        accent = palette.accent.toLong() and 0xFFFFFFFFL,
        automaticColors = false,
    )
}
