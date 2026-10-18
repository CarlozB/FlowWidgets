# Verificación de la entrega

## Resultados ejecutados

| Comprobación | Resultado |
| --- | --- |
| `./gradlew assembleDebug` | Correcta; APK generado |
| `./gradlew testDebugUnitTest` | **13 pasadas, 0 fallidas, 0 omitidas** |
| `./gradlew lintDebug` | **0 errores, 16 advertencias** |
| `./gradlew assembleDebugAndroidTest` | APK de pruebas instrumentadas compilado |
| `apksigner verify --verbose` | Firma v2 válida, un firmante |
| `zipalign -c 4` | Correcta |
| Inspección de APK/manifiesto | Paquete `com.flowwidgets.app`, minSdk 33, target/compile 36; ocho proveedores registrados |
| Open-Meteo por HTTPS desde el entorno | Geocodificación de Lima y esquema de pronóstico correctos; 48 muestras horarias y dos días de máximas/mínimas |

La última ejecución completa de verificación terminó con código 0. Los informes originales están en [`reports/`](reports/): dos XML JUnit, informe Lint y registro de compilación/verificación. Las advertencias Lint son recomendaciones de versiones, orden de parámetros Compose y extensiones KTX; no se deshabilitaron comprobaciones ni se creó un baseline para ocultar errores.

## Qué cubren las 13 pruebas locales

- Ocho pruebas de repositorios: serialización de todos los ajustes, valores por defecto, persistencia DataStore, aislamiento entre widgets, actualización/borrado, tema, listas dañadas, calendario sin permiso, caché meteorológica por coordenadas, timestamp y fallback offline sin peticiones de red, códigos meteorológicos.
- Cinco pruebas de widgets: registro y metadatos de los ocho proveedores; aplicación de RemoteViews de cada tipo; reloj analógico y cuatro estilos; conservación al redimensionar y limpieza al borrar; notas compactas y layouts de fuentes de reloj.
- Las pruebas de host se ejecutan con Robolectric API 35. No equivalen a una prueba del launcher Xiaomi.

## Pruebas instrumentadas no ejecutadas

Se instalaron Android Emulator y una imagen AOSP API 36 y se creó un dispositivo virtual de tablet. Sin `/dev/kvm`, el arranque por software fue demasiado lento; el watchdog de Android interrumpió el inicio y el sistema rechazó instalar el APK con `device is still booting`. Se cerró el intento a petición del usuario para terminar la entrega. No se modificaron ni deshabilitaron las aserciones de las pruebas para darlo por válido.

**No se verificó todavía que la aplicación abra en Android real, ni se ejecutaron las tres pruebas instrumentadas.** El APK de pruebas sí compila. Las tres pruebas cubren el recorrido Compose (bienvenida/catálogo/editor/ajustes), las ocho RemoteViews en el framework real y la decodificación/fotografía con ajuste. La última se mantiene en la suite de Android porque Robolectric no reproduce la ruta nativa de ImageDecoder y sus descriptores de archivo.

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
- Tamaño: 29,621,751 bytes
- SHA-256: `9bcdec96e1237b14faf6a1e8ebee31af8c92a7968272c911caf3e42b29547b5a`
- Versión: `1.0.0` (1), compilación de depuración, no release de Play Store.
