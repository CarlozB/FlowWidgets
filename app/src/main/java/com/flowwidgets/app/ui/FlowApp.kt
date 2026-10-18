package com.flowwidgets.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowwidgets.app.data.*
import com.flowwidgets.app.widgets.WidgetActions
import kotlinx.coroutines.launch

private val labels =
    mapOf(
        "CLOCK" to "Reloj",
        "BATTERY" to "Batería",
        "CALENDAR" to "Calendario",
        "WEATHER" to "Clima",
        "MEDIA" to "Música",
        "NOTE" to "Notas",
        "PHOTO" to "Fotografías",
        "SHORTCUT" to "Accesos",
    )
private val descriptions =
    mapOf(
        "CLOCK" to "Cada minuto, a tu manera",
        "BATTERY" to "Energía de un vistazo",
        "CALENDAR" to "Espacio para tus días",
        "WEATHER" to "Prepárate para salir",
        "MEDIA" to "Dale ritmo a tu pantalla",
        "NOTE" to "Ideas que te acompañan",
        "PHOTO" to "Tus momentos favoritos",
        "SHORTCUT" to "Más cerca de lo que usas",
    )

@Composable
fun FlowApp(
    initial: WidgetConfig? = null,
    configuring: Boolean = false,
    onSave: (WidgetConfig) -> Unit,
    onPin: (WidgetConfig) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val configs by FlowRepository.configs(context).collectAsState(initial = emptyList())
    val theme by FlowRepository.theme(context).collectAsState(initial = "system")
    val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    var page by remember { mutableStateOf("Catálogo") }
    var category by remember { mutableStateOf("Todos") }
    var editing by remember(initial) { mutableStateOf(initial) }
    val scope = rememberCoroutineScope()
    var welcomed by remember {
        mutableStateOf(context.getSharedPreferences("onboarding", 0).getBoolean("seen", false))
    }
    MaterialTheme(
        typography = interTypography(),
        colorScheme =
            if (dark)
                darkColorScheme(
                    primary = Coral,
                    background = Color(0xFF171C19),
                    surface = Color(0xFF252C27),
                    onSurface = Color(0xFFF3F4EC),
                )
            else
                lightColorScheme(
                    primary = Ink,
                    secondary = Coral,
                    background = Paper,
                    surface = Paper,
                    onSurface = Ink,
                ),
    ) {
        Surface(
            Modifier.fillMaxSize().systemBarsPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (!welcomed && !configuring) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Brand()
                    Spacer(Modifier.height(56.dp))
                    Text(
                        "Tu pantalla.\nTu pequeño mundo.",
                        fontSize = 43.sp,
                        lineHeight = 49.sp,
                        letterSpacing = (-1.5).sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Widgets útiles y personales para darle espacio a lo que importa.",
                        fontSize = 17.sp,
                        lineHeight = 26.sp,
                        color = Muted,
                    )
                    Spacer(Modifier.height(32.dp))
                    WidgetPreview("CLOCK", modifier = Modifier.fillMaxWidth().height(165.dp))
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        WidgetPreview(
                            "BATTERY",
                            accent = Color(0xFFBFD99A),
                            modifier = Modifier.weight(1f).height(165.dp),
                        )
                        WidgetPreview("NOTE", modifier = Modifier.weight(1f).height(165.dp))
                    }
                    Spacer(Modifier.height(36.dp))
                    Button(
                        onClick = {
                            context
                                .getSharedPreferences("onboarding", 0)
                                .edit()
                                .putBoolean("seen", true)
                                .apply()
                            welcomed = true
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors =
                            ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Ink),
                    ) {
                        Text("Crear mi espacio  ↗")
                    }
                    Spacer(Modifier.height(15.dp))
                    Text(
                        "Sin cuentas. Sin anuncios. Simplemente tuyo.",
                        fontSize = 12.sp,
                        color = Muted,
                    )
                }
                return@Surface
            }

            if (editing != null) {
                Editor(
                    editing!!,
                    configuring || editing!!.id > 0,
                    onBack = { if (configuring) onCancel() else editing = null },
                    onDone = { config ->
                        if (configuring || config.id > 0) onSave(config) else onPin(config)
                        if (!configuring) editing = null
                    },
                )
                return@Surface
            }
            BoxWithConstraints {
                val wide = maxWidth >= 840.dp
                Row(Modifier.fillMaxSize()) {
                    if (wide)
                        Column(Modifier.width(218.dp).fillMaxHeight().padding(26.dp, 38.dp)) {
                            Brand()
                            Spacer(Modifier.height(52.dp))
                            listOf("Catálogo", "Mis widgets", "Ajustes").forEach { item ->
                                NavigationItem(item, page == item) { page = item }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                "HECHO PARA TU\nPANTALLA DE INICIO",
                                fontSize = 10.sp,
                                lineHeight = 17.sp,
                                letterSpacing = 1.6.sp,
                                color = Muted,
                            )
                            Spacer(Modifier.height(18.dp))
                            Text(
                                "Flow Widgets · ${com.flowwidgets.app.BuildConfig.VERSION_NAME}",
                                fontSize = 11.sp,
                                color = Muted,
                            )
                        }
                    Column(Modifier.weight(1f)) {
                        if (!wide) {
                            Row(
                                Modifier.fillMaxWidth().padding(22.dp, 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Brand()
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { page = "Ajustes" }) { Text("Ajustes") }
                            }
                            Row(Modifier.padding(horizontal = 16.dp)) {
                                listOf("Catálogo", "Mis widgets").forEach {
                                    FilterChip(
                                        selected = page == it,
                                        onClick = { page = it },
                                        label = { Text(it) },
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                            }
                        }
                        when (page) {
                            "Catálogo" ->
                                LazyColumn(
                                    Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(if (wide) 36.dp else 22.dp),
                                    verticalArrangement = Arrangement.spacedBy(26.dp),
                                ) {
                                    item {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                "TU PANTALLA, TU ESTILO",
                                                fontSize = 11.sp,
                                                letterSpacing = 2.sp,
                                                color = Muted,
                                            )
                                            Surface(shape = CircleShape, color = Sage) {
                                                Text(
                                                    "✦  SIN LÍMITES",
                                                    modifier = Modifier.padding(11.dp, 7.dp),
                                                    fontSize = 9.sp,
                                                    color = Ink,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(18.dp))
                                        Text(
                                            "Pequeños widgets.\nGrandes posibilidades.",
                                            fontSize = if (wide) 43.sp else 34.sp,
                                            lineHeight = if (wide) 49.sp else 40.sp,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = (-1.7).sp,
                                        )
                                        Spacer(Modifier.height(13.dp))
                                        Text(
                                            "Diseña un espacio que se sienta tuyo.\nInformación útil. Menos ruido. Más tú.",
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp,
                                            color = Muted,
                                        )
                                    }
                                    item {
                                        Hero { editing = WidgetConfig(kind = WidgetKind.CLOCK) }
                                    }
                                    item {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                "Explora la colección",
                                                fontSize = 23.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text("08 widgets", fontSize = 11.sp, color = Muted)
                                        }
                                        Spacer(Modifier.height(15.dp))
                                        Row(
                                            Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            listOf("Todos", "Esenciales", "Personal", "Conectados")
                                                .forEach {
                                                    FilterChip(
                                                        selected = category == it,
                                                        onClick = { category = it },
                                                        label = { Text(it, fontSize = 12.sp) },
                                                    )
                                                }
                                        }
                                    }
                                    val kinds =
                                        WidgetKind.entries.filter {
                                            category == "Todos" ||
                                                when (category) {
                                                    "Esenciales" ->
                                                        it.name in
                                                            listOf("CLOCK", "BATTERY", "CALENDAR")
                                                    "Personal" ->
                                                        it.name in
                                                            listOf("NOTE", "PHOTO", "SHORTCUT")
                                                    else -> it.name in listOf("WEATHER", "MEDIA")
                                                }
                                        }
                                    kinds.chunked(if (wide) 2 else 1).forEach { row ->
                                        item {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                                            ) {
                                                row.forEach { kind ->
                                                    Column(
                                                        Modifier.weight(1f).clickable {
                                                            editing = WidgetConfig(kind = kind)
                                                        }
                                                    ) {
                                                        WidgetPreview(
                                                            kind.name,
                                                            accent =
                                                                if (kind.ordinal % 2 == 0) Coral
                                                                else Color(0xFFBFD99A),
                                                            modifier =
                                                                Modifier.fillMaxWidth()
                                                                    .height(166.dp),
                                                        )
                                                        Row(
                                                            Modifier.fillMaxWidth()
                                                                .padding(top = 14.dp),
                                                            verticalAlignment =
                                                                Alignment.CenterVertically,
                                                        ) {
                                                            Column(Modifier.weight(1f)) {
                                                                Text(
                                                                    labels[kind.name]!!,
                                                                    fontSize = 17.sp,
                                                                    fontWeight = FontWeight.Medium,
                                                                )
                                                                Text(
                                                                    descriptions[kind.name]!!,
                                                                    color = Muted,
                                                                    fontSize = 12.sp,
                                                                    modifier =
                                                                        Modifier.padding(top = 4.dp),
                                                                )
                                                            }
                                                            Text(
                                                                "↗",
                                                                fontSize = 25.sp,
                                                                color = Muted,
                                                            )
                                                        }
                                                    }
                                                }
                                                if (row.size == 1 && wide)
                                                    Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                    item {
                                        Text(
                                            "Diseñados para Android 13–16 · Sin anuncios ni rastreadores",
                                            fontSize = 11.sp,
                                            color = Muted,
                                            modifier = Modifier.padding(vertical = 16.dp),
                                        )
                                    }
                                }
                            "Mis widgets" ->
                                LazyColumn(
                                    contentPadding = PaddingValues(28.dp),
                                    verticalArrangement = Arrangement.spacedBy(18.dp),
                                ) {
                                    item {
                                        Text(
                                            "Tu colección",
                                            fontSize = 34.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            "Un espacio para cada parte de tu día.",
                                            color = Muted,
                                            modifier = Modifier.padding(top = 10.dp),
                                        )
                                    }
                                    if (configs.none { it.id > 0 })
                                        item {
                                            Surface(
                                                shape = RoundedCornerShape(24.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                            ) {
                                                Column(Modifier.padding(30.dp)) {
                                                    Text(
                                                        "Todavía hay espacio para crear.",
                                                        fontSize = 22.sp,
                                                    )
                                                    Text(
                                                        "Elige un widget del catálogo y añádelo a tu pantalla de inicio.",
                                                        modifier =
                                                            Modifier.padding(vertical = 15.dp),
                                                        color = Muted,
                                                    )
                                                    Button(onClick = { page = "Catálogo" }) {
                                                        Text("Explorar widgets")
                                                    }
                                                }
                                            }
                                        }
                                    configs
                                        .filter { it.id > 0 }
                                        .forEach { config ->
                                            item {
                                                Column {
                                                    ConfigPreview(
                                                        config,
                                                        Modifier.fillMaxWidth().height(160.dp),
                                                    )
                                                    Row(
                                                        verticalAlignment =
                                                            Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            config.title.ifBlank {
                                                                labels[config.kind.name]!!
                                                            },
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                        TextButton(onClick = { editing = config }) {
                                                            Text("Editar")
                                                        }
                                                        TextButton(
                                                            onClick = {
                                                                scope.launch {
                                                                    FlowRepository.save(
                                                                        context,
                                                                        WidgetConfig(
                                                                            id = config.id,
                                                                            kind = config.kind,
                                                                        ),
                                                                    )
                                                                    WidgetActions.update(context)
                                                                }
                                                            }
                                                        ) {
                                                            Text("Restablecer")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                }
                            else ->
                                SettingsScreen(
                                    theme,
                                    onTheme = {
                                        scope.launch { FlowRepository.setTheme(context, it) }
                                    },
                                )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(31.dp).background(Coral, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("≈", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text("flow", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
        Text(" widgets", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
private fun NavigationItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) Sage else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when (label) {
                "Catálogo" -> "▦"
                "Mis widgets" -> "◫"
                else -> "⚙"
            },
            fontSize = 20.sp,
            color = if (selected) Ink else Muted,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontSize = 13.sp,
            color = if (selected) Ink else Muted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun Hero(onClick: () -> Unit) {
    Surface(
        color = Color(0xFFE9EDDF),
        shape = RoundedCornerShape(27.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MENOS ES MÁS", fontSize = 10.sp, letterSpacing = 1.7.sp, color = Muted)
                Text(
                    "El tiempo,\na tu ritmo.",
                    fontSize = 27.sp,
                    lineHeight = 32.sp,
                    color = Ink,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
                    Text("Personalizar reloj  ↗", color = Ink, fontSize = 12.sp)
                }
            }
            WidgetPreview("CLOCK", modifier = Modifier.weight(1.2f).height(155.dp))
        }
    }
}

@Composable
private fun SettingsScreen(theme: String, onTheme: (String) -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text("A tu manera", fontSize = 34.sp, fontWeight = FontWeight.Medium)
        Text("Apariencia", fontSize = 20.sp)
        Choices(
            listOf("system", "light", "dark"),
            theme,
            {
                when (it) {
                    "system" -> "Sistema"
                    "light" -> "Claro"
                    else -> "Oscuro"
                }
            },
            onTheme,
        )
        HorizontalDivider()
        Text("Tú decides qué compartir", fontSize = 20.sp)
        Text(
            "Flow funciona sin cuenta, anuncios ni rastreadores. Tus notas, fotos y preferencias permanecen en este dispositivo.",
            color = Muted,
            lineHeight = 22.sp,
        )
        Text("Calendario", fontWeight = FontWeight.SemiBold)
        Text(
            "El permiso se solicita solo al activar eventos en un widget de calendario.",
            color = Muted,
        )
        Text("Reproducción multimedia", fontWeight = FontWeight.SemiBold)
        Text(
            "El acceso a notificaciones permite consultar la sesión de música y usar sus controles. No guardamos tus notificaciones.",
            color = Muted,
        )
        OutlinedButton(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        ) {
            Text("Gestionar acceso multimedia")
        }
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            }
        ) {
            Text("Permisos de la aplicación")
        }
        HorizontalDivider()
        var showInterLicense by remember { mutableStateOf(false) }
        TextButton(onClick = { showInterLicense = true }) {
            Text("Inter · licencia SIL Open Font License")
        }
        if (showInterLicense)
            AlertDialog(
                onDismissRequest = { showInterLicense = false },
                title = { Text("Inter · The Inter Project Authors") },
                text = {
                    Text(
                        remember {
                            context.assets.open("licenses/Inter-OFL.txt").bufferedReader().use {
                                it.readText()
                            }
                        },
                        modifier =
                            Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showInterLicense = false }) { Text("Cerrar") }
                },
            )
        var showGlassLicense by remember { mutableStateOf(false) }
        TextButton(onClick = { showGlassLicense = true }) {
            Text("LiquidGlass · Apache License 2.0")
        }
        if (showGlassLicense)
            AlertDialog(
                onDismissRequest = { showGlassLicense = false },
                title = { Text("LiquidGlass · Copyright 2026 LiquidGlass contributors") },
                text = {
                    Text(
                        remember {
                            context.assets
                                .open("licenses/LiquidGlass-Apache-2.0.txt")
                                .bufferedReader()
                                .use { it.readText() }
                        },
                        modifier =
                            Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showGlassLicense = false }) { Text("Cerrar") }
                },
            )
        Text("Sobre los widgets", fontSize = 20.sp)
        Text(
            "Los tamaños son orientativos: el launcher decide la cuadrícula final. Glass usa transparencias compatibles; no desenfoca el fondo de pantalla. El segundero depende del soporte del launcher. El clima usa Open-Meteo y guarda la última consulta disponible.",
            color = Muted,
            lineHeight = 22.sp,
        )
        Text(
            "Flow Widgets ${com.flowwidgets.app.BuildConfig.VERSION_NAME}\nDiseñado para que tu pantalla respire.",
            fontSize = 12.sp,
            color = Muted,
        )
    }
}

@Composable
internal fun Choices(
    values: List<String>,
    selected: String,
    label: (String) -> String = { it },
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach {
            FilterChip(
                selected = selected == it,
                onClick = { onSelect(it) },
                label = { Text(label(it)) },
            )
        }
    }
}
