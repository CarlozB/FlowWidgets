# Verificación de la entrega

## Resultados ejecutados

| Comprobación | Resultado |
| --- | --- |
| `./gradlew assembleDebug` | Correcta; APK generado |
| `./gradlew testDebugUnitTest` | **41 pasadas, 0 fallidas, 0 omitidas** |
| `./gradlew lintDebug` | **0 errores, 93 advertencias** |
| `./gradlew assembleDebugAndroidTest` | APK de pruebas instrumentadas compilado |
| `apksigner verify --verbose` | Firma v2 válida, un firmante |
| `zipalign -c 4` | Correcta |
| Inspección de APK/manifiesto | Paquete `com.flowwidgets.app`, minSdk 33, target/compile 36; ocho proveedores registrados |
| Open-Meteo por HTTPS desde el entorno | Comprobación anterior de geocodificación/pronóstico válida; la nueva consulta de cinco días recibió HTTP 403 en esta sesión. El esquema ampliado y su caché se verificaron con fixtures; falta consulta desde el APK |

La última ejecución completa de verificación terminó con código 0. Los informes originales están en [`reports/`](reports/): seis XML JUnit, informe Lint y registro de compilación/verificación. Las advertencias Lint son recomendaciones de versiones, orden de parámetros Compose, extensiones KTX, posiciones físicas de RemoteViews (izquierda/derecha) y recursos antiguos conservados para compatibilidad de actualización; no se deshabilitaron comprobaciones ni se creó un baseline para ocultar errores.

## Qué cubren las 41 pruebas locales

- Nueve pruebas de repositorios: serialización de todos los ajustes, valores por defecto, persistencia DataStore, aislamiento entre widgets, actualización/borrado, tema, listas dañadas, calendario sin permiso, caché meteorológica por coordenadas, timestamp y fallback offline sin peticiones de red, códigos meteorológicos y pronóstico ampliado con campos opcionales.
- Cinco pruebas de widgets: registro y metadatos de los ocho proveedores; aplicación de RemoteViews de cada tipo; reloj analógico y tres estilos; conservación al redimensionar y limpieza al borrar; notas compactas y layouts de fuentes de reloj.
- Nueve pruebas gráficas nativas: ocho tipos en siete proporciones (56 renders), límites de memoria/transparencia, recorte frente a ajuste real, iconos meteorológicos distintos, reloj sin agujas congeladas, colores y exportación de ejemplos desde el renderer de producción.
- Seis pruebas adaptativas: tamaños reportados y fallback de orientación, límite de 16 variantes, presupuesto global, grids, reaplicación al encoger, transacciones entre instancias, caché Glass, once proporciones y mapa RemoteViews de los ocho tipos.
- Diez regresiones de colocación y tipografía: primera actualización sin callback en los tres estilos actuales, callbacks previos/tardíos/duplicados y concurrencia, paleta explícita, pesos reales de Inter en Canvas y TextClock, cifras tabulares, ajuste de números y esfera cuadrada/centrada tras medir vistas Android.
- Las pruebas de host se ejecutan con Robolectric API 35. No equivalen a una prueba del launcher Xiaomi.

## Pruebas instrumentadas no ejecutadas

Se instalaron Android Emulator y una imagen AOSP API 36 y se creó un dispositivo virtual de tablet. Sin `/dev/kvm`, el arranque por software fue demasiado lento; el watchdog de Android interrumpió el inicio y el sistema rechazó instalar el APK con `device is still booting`. Se cerró el intento a petición del usuario para terminar la entrega. No se modificaron ni deshabilitaron las aserciones de las pruebas para darlo por válido.

El usuario compartió una captura de la versión 1.2 ejecutándose en su tablet y reportó errores de estilo, colores y encaje del reloj, atendidos en 1.2.1. **Esta nueva APK no se ejecutó en Android real desde el entorno, ni se ejecutaron las cuatro pruebas instrumentadas.** El APK de pruebas sí compila. Las pruebas cubren el recorrido Compose (bienvenida/catálogo/editor/ajustes), las ocho RemoteViews en el framework real y la decodificación/fotografía con ajuste. La decodificación se mantiene en la suite de Android porque Robolectric no reproduce la ruta nativa de ImageDecoder y sus descriptores de archivo. Se añadió una cuarta comprobación de Inter y cifras tabulares en TextClock nativos con los cinco pesos.

## Pendiente en Xiaomi Pad 7 / HyperOS

1. Instalar y abrir la app; comprobar que no hay cierres, incluido tras rotar.
2. Añadir los ocho tipos y probar redimensionamientos en la cuadrícula real. La selección de tamaño de la app no fuerza las celdas del launcher.
3. Guardar dos instancias distintas, cerrar la app y reiniciar la tablet; comprobar persistencia y actualizaciones.
4. Conceder/revocar calendario y acceso multimedia; probar eventos y controles con una sesión real.
5. Seleccionar fotos con Photo Picker, comprobar permisos persistentes, ajuste/recorte y rotación.
6. Seleccionar ciudad desde la app y verificar consulta, datos guardados y timestamp sin conexión. Las peticiones HTTPS del entorno pasaron, pero no se ha probado el recorrido de red dentro del APK.
7. Comprobar accesos a apps/contactos/ajustes, colores dinámicos, tema oscuro, reloj y batería contra el sistema.

Ver [`INSTALL.md`](INSTALL.md) y la lista manual del README. No se garantiza una ocupación exacta 1×1 ni comportamiento uniforme en launchers no probados. No hay errores de compilación conocidos, pero la ausencia de validación en dispositivo impide declarar la aplicación completamente probada.

## Identificación del APK

- Archivo final: `dist/FlowWidgets-debug.apk`
- Certificado de depuración: coincide con el APK 1.2 ya publicado, por lo que la actualización conserva datos si se instala sobre él.
- Tamaño: 30,664,455 bytes
- SHA-256: `ffb1f9fbc872f6dfc49b78550c4e69a81f018555b9cabff4d2afcac372345a4b`
- Versión: `1.3.0` (6), compilación de depuración, no release de Play Store.

## Cambios visuales y límites de esta validación

Se revisó la lámina generada con el mismo motor que usa el escritorio: [`images/FlowWidgets-design.png`](images/FlowWidgets-design.png). Es una exportación de pruebas, no una captura de la app ejecutada en HyperOS; sus cifras, fecha y portada abstracta son fixtures exclusivos del test. No se introducen estos datos en el APK. Los previews del selector muestran estados vacíos sin inventar batería, clima ni canciones.

Las notas, el clima, la batería, el mes y los accesos se dibujan con Canvas. Fotos y portadas son imágenes reales seleccionadas/publicadas por sus fuentes. El reloj mantiene componentes nativos con esfera y agujas propias; el reproductor conserva controles clicables separados del dibujo. Las vistas previas Compose usan el mismo motor, por lo que no son maquetas independientes del widget real.

Se mantuvieron los IDs de layouts antiguos y se añadieron layouts de escritorio nuevos, para que el launcher pueda reinflar la composición durante una actualización; también se atiende MY_PACKAGE_REPLACED. El resultado de esa migración en Xiaomi se debe comprobar en el dispositivo. Si aparece una vista antigua, quitar y añadir de nuevo el widget. Las instancias digitales anteriores conservan su preferencia: activar Reloj analógico para ver la esfera.

No se volvió a iniciar el emulador, conforme a la indicación del usuario. Las tres pruebas instrumentadas compilan pero siguen sin ejecutarse; la apertura real y los permisos/sesiones de terceros requieren la tablet.

## Validación adaptativa 1.2

Se revisó también [la lámina adaptativa](images/FlowWidgets-adaptive.png), exportada por el motor de producción con fixtures exclusivos de test. Compara tarjetas, filas, composiciones medianas y paneles. Los controles multimedia se superponen con los mismos símbolos y regiones de los controles nativos. Las pruebas gráficas recorren además las once proporciones del editor para los ocho tipos. No es una captura del launcher ni una medición de consumo en hardware.

Consulta [ADAPTIVE_WIDGETS.md](ADAPTIVE_WIDGETS.md) para las estrategias por tipo, las APIs oficiales contrastadas y los límites de reproducción, red y animaciones. La apertura en Android real, persistencia tras reinicio y compatibilidad exacta con HyperOS siguen pendientes de la prueba física del usuario.

## Colocación, colores e Inter · 1.2.1

Se verificó que la nueva APK contiene exactamente el TTF oficial y el texto de licencia conservado en el repositorio. El certificado coincide con 1.2.0. La lámina [FlowWidgets-Inter.png](images/FlowWidgets-Inter.png) compara los cinco pesos y la esfera compacta centrada con colores personalizados. La selección explícita de colores conserva los demás colores visibles al abandonar una paleta automática.

[TYPOGRAPHY.md](TYPOGRAPHY.md) registra la procedencia, SHA del archivo oficial, uso en Canvas/TextClock, APIs públicas y correcciones del flujo de colocación. Los errores antiguos que dejaron una instancia guardada con Classic requieren editarla de nuevo; no se puede recuperar una selección que no quedó vinculada. Falta repetir en HyperOS la colocación y reducción usando la nueva APK.

## Centrado y retirada de One UI · 1.2.2

Se añadieron comprobaciones de los relojes compactos en 80, 132, 180, 240 y 280 dp: esfera grande centrada en ambos ejes, sin fecha, calendario, zonas ni agenda, incluso al partir de una configuración digital. El calendario pequeño se verifica sobre los píxeles dibujados del número para comprobar su centrado visible. Las pruebas de tipografía digital usan ahora un perfil amplio, donde se mantiene esa opción.

Se comprobó la migración de ONE a Classic sin perder colores, contenido ni peso de fuente, y que el selector solo ofrece Classic, Glass y Material. Las láminas y el preview del reloj se regeneraron con el motor actualizado. El APK conserva la firma de la versión anterior. Falta validar esta versión en el launcher HyperOS del usuario; no se volvió a iniciar el emulador.

Se comprobó además que el AnalogClock compacto se centra en el tamaño final del host aunque sus dimensiones difieran de los dp anunciados: usa el espacio real del padre con márgenes simétricos. La esfera nativa mantiene su proporción circular.

## LiquidGlass y superficies reflectantes · 1.3.0

La suite incluye dos pruebas adicionales de reflejos en manecillas/controles y fracción/límites de la barra, y dos pruebas del adaptador de fondo: máscara, perfiles, óptica oficial, caché, opacidad/iluminación y lámina exportada. El test de centrado del calendario ahora identifica los glifos por un color exclusivo, manteniendo la misma tolerancia y evitando confundir reflejos del fondo con texto.

Se verificó que los cinco archivos del núcleo coinciden byte por byte con el commit oficial fijado. La función que dibuja la esfera y los números es idéntica a la de 1.2.2. La lámina [FlowWidgets-LiquidGlass.png](images/FlowWidgets-LiquidGlass.png) usa el motor real con fixtures de test y no es una captura del launcher.

La integración es un adaptador CPU de las funciones SDF/lente del núcleo, con fondo de luz difusa generado. No ejecuta el renderer AGSL ni desenfoca el fondo del launcher. El APK conserva acciones multimedia y relojes nativos. Más detalles y licencia en [LIQUID_GLASS.md](LIQUID_GLASS.md). No se probó la nueva APK ni el consumo en hardware físico; se mantienen cuatro pruebas instrumentadas compiladas y no ejecutadas.
