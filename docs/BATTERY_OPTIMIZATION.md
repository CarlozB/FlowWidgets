# Optimización de consumo · 1.0.1

## Investigación oficial en Internet

Se contrastaron las recomendaciones de actualización selectiva, trabajo periódico diferible y cancelación por ciclo de vida con estas fuentes:

- [Guía Android: widgets y actualizaciones eficientes](https://developer.android.com/develop/ui/views/appwidgets/advanced#update-widgets-efficiently).
- [AOSP AppWidgetProviderInfo](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/appwidget/AppWidgetProviderInfo.java): `updatePeriodMillis` no entrega actualizaciones con una frecuencia mayor a 30 minutos. Se configura en cero cuando la planificación la realiza WorkManager.
- [AndroidX PeriodicWorkRequest](https://github.com/androidx/androidx/blob/androidx-main/work/work-runtime/src/main/java/androidx/work/PeriodicWorkRequest.kt): trabajo periódico inexacto, ventanas flexibles, restricciones y respeto de Doze.
- [AndroidX Constraints](https://github.com/androidx/androidx/blob/androidx-main/work/work-runtime/src/main/java/androidx/work/Constraints.kt): conectividad y batería no baja para trabajo de red.
- [AndroidX ExistingPeriodicWorkPolicy](https://github.com/androidx/androidx/blob/androidx-main/work/work-runtime/src/main/java/androidx/work/ExistingPeriodicWorkPolicy.kt): UPDATE preserva la hora de encolado y no interrumpe una ejecución en curso. No se cancela y recrea el temporizador en cada edición.
- [AndroidX RepeatOnLifecycle](https://github.com/androidx/androidx/blob/androidx-main/lifecycle/lifecycle-runtime/src/commonMain/kotlin/androidx/lifecycle/RepeatOnLifecycle.kt): cancela el bloque en ON_STOP y lo reinicia en ON_START.

La página de developer.android.com devolvió HTTP 403 desde este entorno; se leyeron por Internet las fuentes oficiales AOSP y AndroidX enlazadas. No se usaron artículos sin contrastar para cambiar el comportamiento.

## Cambios aplicados

| Antes | Ahora |
| --- | --- |
| Ocho proveedores con temporizador de 30 minutos, además de WorkManager | `updatePeriodMillis=0`; WorkManager es el único planificador periódico de la app |
| Batería, música o edición repintaban todos los tipos | Eventos dirigidos por tipo o identificador; batería no repinta fotos, notas ni música |
| Ráfagas de callbacks independientes | Ventana de agrupación de 250 ms, con unión de destinos y confirmación al terminar; las solicitudes durante un render pasan al siguiente lote |
| Cada cambio de posición musical podía generar otro render | Si solo avanza la posición, máximo un render cada 15 segundos provocado por esos callbacks; no se añade un temporizador. Cambios de reproducción, acciones, pista o saltos de posición mayores a 5 s no esperan ese plazo |
| Notificaciones musicales repetían actualizaciones de MediaController | Las notificaciones solo inicializan observación cuando faltan sesiones; se conservan callbacks de las sesiones ya observadas |
| Trabajo periódico con cero widgets o borradores | Solo cuentan identificadores registrados por el launcher; se cancelan trabajos sin destinatarios, incluido el trabajo anterior |
| Una consulta meteorológica por instancia cada 30 min | Trabajo de clima de 60 min, flex de 15 min, conexión y batería no baja; una consulta por coordenadas y se omite si la caché tiene menos de una hora |
| Un trabajo general repintaba widgets estáticos | Trabajo local de 30 min, flex de 10 min, solo para batería, calendario, fotos que rotan y relojes con calendario/agenda dibujados |
| El reloj compacto podía motivar consultas de eventos invisibles | TextClock/AnalogClock siguen a cargo de Android; no hay trabajo periódico de la app si son los únicos widgets. Solo se consultan eventos de reloj cuando existe una región visible para ellos |
| Previews seguían consultando mientras su Activity estaba detenida | `repeatOnLifecycle(STARTED)` detiene los bucles al salir de la app. Notas, accesos y fotos fijas se dibujan una vez por configuración/entrada visible |

Las vistas previas de música se consultan cada cinco segundos solo mientras su pantalla está activa; las demás vistas dinámicas cada minuto, salvo segundero explícito. El segundero continúa desactivado por defecto. La batería ignora broadcasts cuyo BatteryInfo no cambió. Los relojes nativos y los controles de reproducción se conservan; no se añade foreground service, alarma exacta ni wakelock propio. El servicio multimedia sigue siendo el NotificationListener habilitado voluntariamente por el usuario, con callbacks del sistema.

Las consultas manuales de clima siguen disponibles. El clima automático puede retrasarse más de una hora por caché, ventanas flexibles, ahorro de batería, falta de red y políticas de HyperOS; se conserva la hora real de los datos. El trabajo local no requiere conexión, por lo que las fotos rotatorias y el calendario pueden actualizarse sin red.

Se mantienen los límites existentes: fondos LRU de 6 MiB, fotos LRU de 8 MiB, óptica de fondo a un máximo de 256 px por eje, bitmaps con presupuesto conjunto y esferas compartidas. El material LiquidGlass y sus reflejos son estáticos.

## Evidencia ejecutada y límites

- Prueba con 100 solicitudes musicales: un único lote, sin incluir fotos u otros tipos no solicitados.
- Solicitudes recibidas durante un render no se pierden; un lote fallido no bloquea el siguiente.
- Se crean trabajos heredados pendientes y se comprueba su cancelación cuando no quedan configuraciones activas.
- Se verifican filtros de borradores/instancias eliminadas, tipos sin trabajo, rotación de fotos, restricciones y ventanas del clima.
- Se comprueba una sola consulta por coordenadas, uso de caché reciente e invalidación al caducar.
- Se verifican callbacks de posición omitidos antes de 15 s y respuesta sin ese retraso a pausa, cambio de pista y búsqueda.
- Los metadatos de los ocho proveedores deben mantener desactivado el temporizador del framework.

Son pruebas de comportamiento y planificación, no una medición de miliamperios ni de autonomía. No se arrancó un emulador ni se midió consumo en la tablet. Para comparar en HyperOS, mantener iguales brillo, música, red, duración y widgets; revisar tanto Flow Widgets como el launcher, que dibuja los relojes nativos. No se atribuye un porcentaje de ahorro sin esa medición.
