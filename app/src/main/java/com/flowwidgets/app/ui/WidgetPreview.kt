package com.flowwidgets.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flowwidgets.app.data.*
import com.flowwidgets.app.widgets.VisualWidgetRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val Ink = Color(0xFF242925)
val Muted = Color(0xFF777B74)
val Paper = Color(0xFFF7F7F0)
val Coral = Color(0xFFE77C64)
val Sage = Color(0xFFDDE5D4)

@Composable
fun WidgetPreview(
    kind: String,
    style: String = "CLASSIC",
    accent: Color = Color(0xFFFF725E),
    background: Color = Color(0xFF20272D),
    textColor: Color = Color.White,
    note: String = "",
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    ConfigPreview(
        WidgetConfig(
            kind = WidgetKind.valueOf(kind),
            style = WidgetStyle.valueOf(style),
            accent = accent.toArgbLong(),
            background = background.toArgbLong(),
            textColor = textColor.toArgbLong(),
            note = note,
            textScale = scale,
            analog = kind == "CLOCK",
        ),
        modifier,
    )
}

private fun Color.toArgbLong(): Long = (toArgb().toLong() and 0xFFFFFFFFL)

private data class PreviewFrame(val bitmap: Bitmap, val state: VisualWidgetRenderer.State)

/** These are the actual launcher drawings, not an unrelated mock of the widget. */
@Composable
fun ConfigPreview(config: WidgetConfig, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier) {
        val width = maxWidth.value.toInt().coerceIn(40, 600)
        val height = maxHeight.value.toInt().coerceIn(40, 600)
        val frame by
            produceState<PreviewFrame?>(null, config, width, height) {
                while (true) {
                    value =
                        withContext(Dispatchers.IO) {
                            val state = VisualWidgetRenderer.snapshot(context, config)
                            PreviewFrame(
                                VisualWidgetRenderer.render(config, width, height, state),
                                state,
                            )
                        }
                    delay(
                        if (config.kind == WidgetKind.CLOCK && config.showSeconds) 1_000
                        else if (config.kind == WidgetKind.MEDIA) 3_000 else 30_000
                    )
                }
            }
        frame?.let { rendered ->
            Image(
                rendered.bitmap.asImageBitmap(),
                contentDescription = "Vista previa de ${config.kind.name.lowercase()}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            if (config.kind == WidgetKind.MEDIA && rendered.state.media != null) {
                val media = rendered.state.media
                val color = VisualWidgetRenderer.palette(config).foreground
                Row(
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    for ((action, enabled) in
                        listOf(
                            "previous" to media.canPrevious,
                            "toggle" to true,
                            "next" to media.canNext,
                        )) {
                        IconButton(
                            enabled = enabled,
                            onClick = { MediaRepository.action(context, action) },
                        ) {
                            val icon =
                                remember(action, media.playing, color) {
                                    VisualWidgetRenderer.transportIcon(
                                        if (action == "toggle")
                                            (if (media.playing) "pause" else "play")
                                        else action,
                                        color,
                                    )
                                }
                            Image(
                                icon.asImageBitmap(),
                                contentDescription =
                                    when (action) {
                                        "previous" -> "Anterior"
                                        "next" -> "Siguiente"
                                        else -> "Reproducir o pausar"
                                    },
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
