# Widgets adaptativos · 1.2

Un proveedor por tipo y una configuración por `appWidgetId`. No se crean proveedores diferentes para cada tamaño. Mantén pulsado el widget en el escritorio y arrastra sus bordes; Android y el launcher deciden los tamaños disponibles.

## Decisiones de arquitectura

Se conserva RemoteViews para usar TextClock, AnalogClock y PendingIntent públicos. `AdaptiveWidgetLayoutEngine` recibe dimensiones **dp** desde `OPTION_APPWIDGET_SIZES`; en su ausencia usa min/max del host para las dos orientaciones. No deduce celdas a partir de píxeles. Los rótulos 1×1…4×4 del editor son simulaciones orientativas, con proporciones distintas, no una instrucción para forzar celdas.

El motor genera distribuciones TILE, ROW, COLUMN, CARD, EXPANDED, WIDE y PANEL. TILE prioriza el dato principal; ROW y COLUMN reorganizan ejes; desde EXPANDED aparecen detalles; PANEL añade más contenido. Los umbrales están en el motor: filas por debajo de 112 dp de alto, columnas estrechas, expansión desde 235 dp de ancho y panel desde 280×280 dp. Cada región se calcula de nuevo; no se estira una captura del diseño compacto.

| Tipo | Estrategia de contenido | Al ampliar |
| --- | --- | --- |
| Reloj | Responsiva por perfiles | Solo esfera en formatos compactos; fecha, calendario, zonas y agenda en perfiles mayores |
| Batería | Responsiva por perfiles | Barra/anillo, conexión, temperatura, voltaje, estado y tecnología publicados por Android |
| Calendario | Responsiva por perfiles | Fecha, semana, mes y agenda opcional |
| Clima | Responsiva por perfiles | Estado actual, hasta seis horas y cuatro días, humedad/sensación/viento si existen |
| Multimedia | Responsiva por perfiles | Portada, metadatos, controles compatibles, progreso, álbum y hasta tres pistas de la cola publicada |
| Fotos | Dimensiones exactas | Recorte/ajuste proporcional, collage hasta nueve fotos seleccionadas |
| Notas | Dimensiones exactas | Más líneas; en panel, hasta ocho tareas visibles y marcables de una lista guardada de veinte |
| Accesos | Responsiva por perfiles | Uno, dos, cuatro, seis, ocho o hasta dieciséis destinos configurados |

Estas estrategias expresan cómo se distribuye el contenido; ambas usan RemoteViews por tamaño real informado por el host. Son equivalentes conceptuales a SizeMode.Exact/Responsive, sin añadir Glance ni afirmar que se esté usando esa biblioteca.

Android 12+ admite `RemoteViews(Map<SizeF, RemoteViews>)`, con un máximo de 16 variantes. Se eliminan duplicados y dimensiones inválidas; si llegan más, se muestrean conservando los extremos. Android selecciona la variante adecuada. La alternativa min/max usa RemoteViews de orientación. `onAppWidgetOptionsChanged` reconstruye desde las opciones recibidas. Todas las vistas condicionales se ocultan antes de activar las pertinentes y se vacían los destinos de toque anteriores: al encoger no quedan controles residuales.

## Datos y acciones

No se inventan autonomía, eventos, canciones, fotos, cola ni pronóstico. Batería consulta el broadcast del sistema; los campos opcionales se omiten si no están publicados. Calendario solo se consulta si el usuario activa eventos y concede READ_CALENDAR. Las zonas elegidas usan TextClock con su identificador real.

La cola y los controles musicales dependen de la MediaSession del reproductor. La barra muestra posición/duración disponibles en la última actualización, proyectando la posición durante reproducción al tomar la instantánea. **No hay un temporizador continuo para animar esa barra**. Si el reproductor no publica duración, cola o acción, se omite. Los controles y la selección de pistas son PendingIntent con acciones verificadas contra la sesión.

Las tareas se actualizan en una transacción DataStore y conservan las demás instancias. Los accesos se resuelven con PackageManager; si desaparece una app se indica que no está disponible y el toque permite editar. No se cambian Wi-Fi/Bluetooth directamente. Las fotos respetan su proporción en cada celda de collage.

El clima conserva caché por coordenadas y fecha de consulta. Solo la búsqueda/actualización meteorológica accede a la red; redimensionar lee la caché. Las respuestas antiguas siguen decodificándose sin los campos nuevos.

## Soft Light Glass y consumo

`SoftLightGlassRenderer` dibuja iluminación, degradados y transparencias. Se ajustan opacidad, intensidad y esquinas desde el editor. La caché LRU de fondos está limitada a 6 MiB y depende de dimensiones, estilo y configuración. Las fotos tienen otra caché acotada; las esferas y agujas se comparten entre variantes.

El presupuesto conjunto de bitmaps se calcula según la pantalla y reserva espacio para los componentes nativos. Los bitmaps se limitan a 720 px por eje; se reduce su resolución si hacen falta muchas variantes. El texto y los controles nativos permanecen independientes. La caché guarda fondos, nunca capturas con eventos o datos de otra instancia.

No se captura ni desenfoca el fondo del launcher. No se añade servicio permanente, sondeo por segundo ni alarma exacta. Se mantienen callbacks multimedia, eventos del sistema y el trabajo periódico existente. Las animaciones de redimensionamiento dependen del launcher; no se prometen animaciones continuas de RemoteViews.

## Fuentes oficiales consultadas

- [Android: tamaños y diseños de widgets](https://developer.android.com/develop/ui/views/appwidgets/layouts).
- [Android: RemoteViews](https://developer.android.com/reference/android/widget/RemoteViews).
- [Android: opciones de AppWidgetManager](https://developer.android.com/reference/android/appwidget/AppWidgetManager).
- [Código AOSP de RemoteViews](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/widget/RemoteViews.java): constructor de mapa, selección de variante y límite de 16.
- [Código AndroidX de SizeMode](https://github.com/androidx/androidx/blob/androidx-main/glance/glance-appwidget/src/main/java/androidx/glance/appwidget/SizeMode.kt): semántica Exact/Responsive y uso de LocalSize.

Las páginas developer.android.com devolvieron HTTP 403 en este entorno. Se verificó el comportamiento leyendo las implementaciones oficiales AOSP/AndroidX enlazadas y compilando las llamadas públicas contra SDK 36.

## Validación física pendiente

Las pruebas Robolectric y gráficas no sustituyen al launcher. Deben probarse Xiaomi Pad 7 Global, HyperOS 3, Android 16, ambas orientaciones, reinicio, ahorro de energía, permisos revocados y sesiones musicales reales. No se afirma compatibilidad exacta 1×1 hasta comprobarla en ese dispositivo. El usuario realizará esta validación; no se volvió a intentar un emulador.

## Cambio de comportamiento · 1.2.2

El perfil de reloj `clockOnly` se aplica hasta 300×300 dp, incluidas las proporciones compactas de 1×1 y 2×2 habituales. Se muestra siempre la esfera analógica, con lado del 94 % de la dimensión menor y centrada en toda la tarjeta. No se reserva altura para fecha ni se crean calendario, zonas o agenda. El ajuste digital se conserva para los perfiles mayores, sin modificarlo silenciosamente en preferencias.

El calendario compacto (hasta 170×190 dp) dibuja solo el día y centra sus límites visibles de glifos. Las cifras dejan de depender de una línea base proporcional fija. Los formatos grandes mantienen semana/mes/agenda.

One UI/One Style ya no forma parte del catálogo. Los registros antiguos `ONE` se decodifican como Classic conservando los demás campos. Los límites dp son orientativos para las celdas del launcher; se mantiene pendiente la comprobación física con la nueva versión en HyperOS.

El AnalogClock compacto ocupa el tamaño final real del padre con márgenes simétricos; centra internamente su esfera sin depender de coordenadas absolutas calculadas desde las opciones dp. Hay una prueba específica con medidas del host distintas de las reportadas.
