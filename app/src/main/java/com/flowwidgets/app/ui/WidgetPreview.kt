package com.flowwidgets.app.ui

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

val Ink = Color(0xFF242925)
val Muted = Color(0xFF777B74)
val Paper = Color(0xFFF7F7F0)
val Coral = Color(0xFFE77C64)
val Sage = Color(0xFFDDE5D4)

@Composable
fun WidgetPreview(
    kind: String,
    style: String = "CLASSIC",
    accent: Color = Coral,
    background: Color = Ink,
    textColor: Color = Color.White,
    note: String = "",
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(30_000)
        }
    }
    val context = LocalContext.current
    val battery = remember {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val max = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && max > 0) level * 100 / max else null
        }
    }
    val bg = background
    val fg = textColor
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(if (style == "ONE") 14.dp else 26.dp))
            .background(bg)
            .padding(20.dp)
    ) {
        val showAnalogPreview = maxWidth >= 215.dp
        when (kind) {
            "CLOCK" ->
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                            color = fg,
                            fontSize = (43 * scale).sp,
                            fontWeight = FontWeight.Light,
                        )
                        Text(
                            SimpleDateFormat("EEE, d MMM", Locale.getDefault())
                                .format(now)
                                .uppercase(),
                            color = fg.copy(alpha = .65f),
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                        )
                    }
                    if (showAnalogPreview)
                        Canvas(Modifier.size(68.dp)) {
                            val center = Offset(size.width / 2, size.height / 2)
                            val radius = size.minDimension / 2
                            drawCircle(fg.copy(alpha = .15f))
                            for (i in 0..11) {
                                val a = Math.PI * i / 6 - Math.PI / 2
                                drawLine(
                                    fg.copy(alpha = .5f),
                                    center +
                                        Offset(
                                            (cos(a) * radius * .8).toFloat(),
                                            (sin(a) * radius * .8).toFloat(),
                                        ),
                                    center +
                                        Offset(
                                            (cos(a) * radius * .9).toFloat(),
                                            (sin(a) * radius * .9).toFloat(),
                                        ),
                                    2f,
                                )
                            }
                            val cal = Calendar.getInstance()
                            val minute = cal.get(Calendar.MINUTE) * Math.PI / 30 - Math.PI / 2
                            val hour =
                                (cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60.0) *
                                    Math.PI / 6 - Math.PI / 2
                            drawLine(
                                fg,
                                center,
                                center +
                                    Offset(
                                        (cos(hour) * radius * .45).toFloat(),
                                        (sin(hour) * radius * .45).toFloat(),
                                    ),
                                4f,
                            )
                            drawLine(
                                accent,
                                center,
                                center +
                                    Offset(
                                        (cos(minute) * radius * .7).toFloat(),
                                        (sin(minute) * radius * .7).toFloat(),
                                    ),
                                3f,
                            )
                            drawCircle(accent, 4f, center)
                        }
                }
            "BATTERY" ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "ENERGÍA",
                        color = fg.copy(alpha = .6f),
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            battery?.let { "$it" } ?: "—",
                            fontSize = 48.sp,
                            color = fg,
                            fontWeight = FontWeight.Light,
                        )
                        Text(" %", color = accent, fontSize = 22.sp)
                    }
                    LinearProgressIndicator(
                        progress = { (battery ?: 0) / 100f },
                        modifier =
                            Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(8.dp)),
                        color = accent,
                        trackColor = fg.copy(alpha = .12f),
                    )
                }
            "CALENDAR" ->
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            SimpleDateFormat("MMMM", Locale.getDefault()).format(now).uppercase(),
                            color = accent,
                            fontSize = 12.sp,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            SimpleDateFormat("dd", Locale.getDefault()).format(now),
                            color = fg,
                            fontSize = 57.sp,
                            fontWeight = FontWeight.Light,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            SimpleDateFormat("EEEE", Locale.getDefault()).format(now),
                            color = fg,
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Tu día, a tu ritmo", color = fg.copy(alpha = .55f), fontSize = 11.sp)
                    }
                }
            "WEATHER" ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "EL TIEMPO",
                        color = fg.copy(alpha = .6f),
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                    )
                    Text("☀  —°", color = fg, fontSize = 43.sp)
                    Text(
                        "Añade una ubicación para consultar",
                        color = fg.copy(alpha = .6f),
                        fontSize = 11.sp,
                    )
                }
            "MEDIA" ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row {
                        Box(
                            Modifier.size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accent.copy(alpha = .3f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("♫", fontSize = 27.sp, color = accent)
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("Tu banda sonora", color = fg, fontWeight = FontWeight.Medium)
                            Text(
                                "Sin reproducción activa",
                                color = fg.copy(alpha = .55f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                    Text(
                        "↶             ▶             ↷",
                        color = fg,
                        fontSize = 25.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            "NOTE" ->
                Column {
                    Text(
                        "UNA IDEA PARA GUARDAR",
                        color = accent,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        note.ifBlank { "Tu próxima gran idea\nempieza aquí." },
                        color = fg,
                        fontSize = (18 * scale).sp,
                        lineHeight = 25.sp,
                        maxLines = 4,
                    )
                }
            "PHOTO" ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("▧", fontSize = 38.sp, color = accent)
                        Text(
                            "Un instante, siempre cerca",
                            color = fg.copy(alpha = .7f),
                            fontSize = 12.sp,
                        )
                    }
                }
            else ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "A UN TOQUE",
                        color = fg.copy(alpha = .6f),
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("↗", color = Ink, fontSize = 28.sp)
                        }
                        Text(
                            "Tu app favorita",
                            color = fg,
                            modifier = Modifier.padding(start = 12.dp),
                            fontSize = 15.sp,
                        )
                    }
                }
        }
    }
}

@Composable
fun ConfigPreview(config: com.flowwidgets.app.data.WidgetConfig, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val foreground = Color(config.textColor)
    val background = Color(config.background)
    when (config.kind.name) {
        "SHORTCUT" ->
            Column(
                modifier.clip(RoundedCornerShape(26.dp)).background(background).padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("A UN TOQUE", color = foreground.copy(alpha = .65f), fontSize = 10.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (config.shortcutIcon == "app") "↗" else config.shortcutIcon,
                        color = Color(config.accent),
                        fontSize = 38.sp,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        config.title.ifBlank {
                            config.shortcutPackage?.let {
                                runCatching {
                                        context.packageManager
                                            .getApplicationLabel(
                                                context.packageManager.getApplicationInfo(it, 0)
                                            )
                                            .toString()
                                    }
                                    .getOrNull()
                            }
                                ?: if (config.shortcutUri != null) "Acceso seleccionado"
                                else "Elige un acceso"
                        },
                        color = foreground,
                        fontSize = 17.sp,
                    )
                }
            }
        "CLOCK" -> {
            var date by remember { mutableStateOf(Date()) }
            LaunchedEffect(config.showSeconds) {
                while (true) {
                    date = Date()
                    delay(if (config.showSeconds) 1000 else 30000)
                }
            }
            if (config.analog)
                WidgetPreview(
                    "CLOCK",
                    config.style.name,
                    Color(config.accent),
                    Color(config.background),
                    Color(config.textColor),
                    modifier = modifier,
                    scale = config.textScale,
                )
            else
                Column(
                    modifier.clip(RoundedCornerShape(26.dp)).background(background).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment =
                        when (config.alignment) {
                            "center" -> Alignment.CenterHorizontally
                            "end" -> Alignment.End
                            else -> Alignment.Start
                        },
                ) {
                    Text(
                        SimpleDateFormat(
                                (if (config.clock24) "HH:mm" else "hh:mm") +
                                    (if (config.showSeconds) ":ss" else "") +
                                    (if (!config.clock24) " a" else ""),
                                Locale.getDefault(),
                            )
                            .format(date),
                        color = foreground,
                        fontSize = (48 * config.textScale).sp,
                        fontWeight = FontWeight.Light,
                        fontFamily =
                            when (config.font) {
                                "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                                "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                                else -> androidx.compose.ui.text.font.FontFamily.SansSerif
                            },
                    )
                    if (config.showDate)
                        Text(
                            SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(date),
                            color = foreground.copy(alpha = .65f),
                            fontSize = 13.sp,
                        )
                }
        }

        "MEDIA" -> {
            var media by remember {
                mutableStateOf(com.flowwidgets.app.data.MediaRepository.current(context))
            }
            LaunchedEffect(Unit) {
                while (true) {
                    media = com.flowwidgets.app.data.MediaRepository.current(context)
                    delay(1500)
                }
            }
            Column(
                modifier.clip(RoundedCornerShape(26.dp)).background(background).padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    media?.art?.let {
                        androidx.compose.foundation.Image(
                            it.asImageBitmap(),
                            "Portada",
                            Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)),
                        )
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            media?.title ?: "Sin reproducción activa",
                            color = foreground,
                            fontSize = 17.sp,
                            maxLines = 1,
                        )
                        Text(
                            media?.artist ?: "Abre tu aplicación de música",
                            color = foreground.copy(alpha = .6f),
                            fontSize = 12.sp,
                        )
                    }
                }
                Row(
                    Modifier.align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    TextButton(
                        enabled = media?.canPrevious == true,
                        onClick = {
                            com.flowwidgets.app.data.MediaRepository.action(context, "previous")
                        },
                    ) {
                        Text("↶", color = foreground, fontSize = 25.sp)
                    }
                    TextButton(
                        enabled = media != null,
                        onClick = {
                            com.flowwidgets.app.data.MediaRepository.action(context, "toggle")
                        },
                    ) {
                        Text(
                            if (media?.playing == true) "Ⅱ" else "▶",
                            color = foreground,
                            fontSize = 25.sp,
                        )
                    }
                    TextButton(
                        enabled = media?.canNext == true,
                        onClick = {
                            com.flowwidgets.app.data.MediaRepository.action(context, "next")
                        },
                    ) {
                        Text("↷", color = foreground, fontSize = 25.sp)
                    }
                }
            }
        }
        "PHOTO" -> {
            val bitmap by
                produceState<android.graphics.Bitmap?>(null, config.photoUri) {
                    value =
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching {
                                    config.photoUri?.let {
                                        android.graphics.ImageDecoder.decodeBitmap(
                                            android.graphics.ImageDecoder.createSource(
                                                context.contentResolver,
                                                android.net.Uri.parse(it),
                                            )
                                        ) { decoder, info, _ ->
                                            val ratio =
                                                800f / maxOf(info.size.width, info.size.height)
                                            if (ratio < 1)
                                                decoder.setTargetSize(
                                                    (info.size.width * ratio)
                                                        .toInt()
                                                        .coerceAtLeast(1),
                                                    (info.size.height * ratio)
                                                        .toInt()
                                                        .coerceAtLeast(1),
                                                )
                                            decoder.allocator =
                                                android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                                        }
                                    }
                                }
                                .getOrNull()
                        }
                }
            if (bitmap != null)
                androidx.compose.foundation.Image(
                    bitmap!!.asImageBitmap(),
                    contentDescription = "Fotografía seleccionada",
                    modifier = modifier.clip(RoundedCornerShape(26.dp)),
                    contentScale =
                        if (config.photoScale == "fit") androidx.compose.ui.layout.ContentScale.Fit
                        else androidx.compose.ui.layout.ContentScale.Crop,
                )
            else
                WidgetPreview(
                    config.kind.name,
                    config.style.name,
                    Color(config.accent),
                    Color(config.background),
                    Color(config.textColor),
                    config.note,
                    modifier,
                    config.textScale,
                )
        }
        "WEATHER" -> {
            val weather = com.flowwidgets.app.data.WeatherRepository.cached(context, config)
            if (weather != null)
                Column(
                    modifier.clip(RoundedCornerShape(26.dp)).background(background).padding(22.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(config.city, color = foreground, fontSize = 13.sp, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${weather.temp.toInt()}°",
                            fontSize = 48.sp,
                            color = foreground,
                            fontWeight = FontWeight.Light,
                        )
                        Spacer(Modifier.width(18.dp))
                        Column {
                            Text(weather.description, color = foreground, fontSize = 13.sp)
                            Text(
                                "↑ ${weather.high.toInt()}°  ↓ ${weather.low.toInt()}°",
                                color = foreground.copy(alpha = .65f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                    Text(
                        "Actualizado ${SimpleDateFormat("d MMM HH:mm",Locale.getDefault()).format(Date(weather.updatedAt))}",
                        color = foreground.copy(alpha = .55f),
                        fontSize = 10.sp,
                    )
                }
            else
                WidgetPreview(
                    config.kind.name,
                    config.style.name,
                    Color(config.accent),
                    Color(config.background),
                    Color(config.textColor),
                    config.note,
                    modifier,
                    config.textScale,
                )
        }
        "NOTE" ->
            Column(
                modifier.clip(RoundedCornerShape(26.dp)).background(background).padding(22.dp),
                horizontalAlignment =
                    when (config.alignment) {
                        "center" -> Alignment.CenterHorizontally
                        "end" -> Alignment.End
                        else -> Alignment.Start
                    },
            ) {
                Text(
                    config.title.ifBlank { "UNA IDEA PARA GUARDAR" },
                    color = Color(config.accent),
                    fontSize = 10.sp,
                )
                Spacer(Modifier.height(15.dp))
                Text(
                    config.note.ifBlank { "Escribe algo que quieras tener cerca." },
                    fontSize = (20 * config.textScale).sp,
                    color = foreground,
                    fontFamily =
                        when (config.font) {
                            "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                            "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                            else -> androidx.compose.ui.text.font.FontFamily.SansSerif
                        },
                    textAlign =
                        when (config.alignment) {
                            "center" -> androidx.compose.ui.text.style.TextAlign.Center
                            "end" -> androidx.compose.ui.text.style.TextAlign.End
                            else -> androidx.compose.ui.text.style.TextAlign.Start
                        },
                    maxLines = 5,
                )
            }
        else ->
            WidgetPreview(
                config.kind.name,
                config.style.name,
                Color(config.accent),
                Color(config.background),
                Color(config.textColor),
                config.note,
                modifier,
                config.textScale,
            )
    }
}
