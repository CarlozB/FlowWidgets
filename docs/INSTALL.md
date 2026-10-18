# Instalar y probar en Xiaomi Pad 7

## Instalación directa

1. Copia **`dist/FlowWidgets-debug.apk`** a la tablet.
2. Abre el archivo y permite temporalmente instalar aplicaciones desde la aplicación de archivos que estés utilizando, si Android lo solicita.
3. Instala **Flow Widgets** y ábrela. No necesita root ni cambiar de launcher.
4. Puedes retirar después el permiso de instalar aplicaciones desconocidas de la aplicación de archivos.

El APK está firmado para depuración. Si existe otra instalación con el mismo paquete y distinta firma, Android rechazará actualizarla. Desinstalar esa versión eliminará sus datos; conserva antes cualquier nota o configuración que necesites.

## Actualizar a la versión 1.2.2

Instala el APK nuevo sobre el anterior; tiene el mismo paquete y mantiene tus configuraciones. El sistema debe aceptar la misma firma de depuración. En un reloj existente, activa **Reloj analógico** para mostrar la esfera; el ajuste digital anterior se conserva intencionadamente. Puedes personalizar los ocho tipos desde su editor.

Si HyperOS mantiene una tarjeta antigua, quítala del escritorio y vuelve a añadirla. Para ver fotografías debes seleccionarlas; para ver portadas, habilita el acceso multimedia y reproduce contenido en una app compatible. No se incluyen fotos ni canciones simuladas.

Después de actualizar, edita desde **Mis widgets** las instancias que quedaron con Classic o colores incorrectos y guarda el estilo deseado. No se pueden recuperar colores que la versión anterior no vinculó a esa instancia. Las nuevas colocaciones guardan el diseño antes de solicitarlo al launcher.

En el editor, elige **Inter** y **Automático** o uno de sus cinco pesos. Amplía/reduce y compara cifras como 11:11 y 08:08; deben mantener un ancho estable. Comprueba el centrado del reloj compacto y la separación de la fecha. La licencia se puede consultar en Ajustes.

## Añadir widgets

En la app, abre el catálogo, elige un tipo y personalízalo. Pulsa **Añadir a la pantalla de inicio** y confirma el diálogo del launcher.

Si HyperOS no permite el fijado directo, mantén pulsado el escritorio, entra en **Widgets** y busca los proveedores **Flow · Reloj**, **Flow · Batería**, **Flow · Calendario**, **Flow · Clima**, **Flow · Música**, **Flow · Notas**, **Flow · Fotos** y **Flow · Accesos**. Arrastra uno al escritorio y configúralo. El launcher decide la cuadrícula y las dimensiones finales.

Para cambiar una instancia, usa **Mis widgets → Editar**, o la opción de reconfiguración del launcher si está disponible. Para quitarla del escritorio, utiliza **Eliminar** del launcher; **Restablecer** en la app recupera sus ajustes iniciales.

## Comprobar el redimensionamiento

Mantén pulsado el widget en el escritorio y arrastra sus bordes. Prueba una tarjeta pequeña, una fila, una columna y un panel grande en ambas orientaciones. El editor simula once proporciones; el launcher decide cuáles admite realmente.

Al ampliar un reloj, configura zonas horarias y eventos; en Notas añade tareas, que podrás marcar desde el panel. En Accesos añade varios destinos; Fotos muestra collages si elegiste varias imágenes. Música solo muestra progreso/cola si el reproductor los publica. Los controles deben seguir funcionando después de ampliar y reducir.

Comprueba dos instancias con ajustes distintos, cierra la app y reinicia. Las dimensiones exactas 1×1 y las animaciones de HyperOS todavía requieren prueba física. Detalles en [ADAPTIVE_WIDGETS.md](ADAPTIVE_WIDGETS.md).

## Datos y permisos

- **Clima:** busca una ciudad y selecciona un resultado con conexión. No requiere permiso GPS ni clave de API.
- **Calendario:** activa próximos eventos y acepta el permiso únicamente si quieres verlos. El mes funciona sin ese permiso.
- **Música:** abre la explicación del editor y habilita Flow Widgets en **Acceso a notificaciones**. Reproduce contenido en una app que publique sesiones multimedia; los controles dependen de lo que esa sesión admita.
- **Fotos:** selecciona imágenes desde el selector del sistema. No se necesita acceso completo a la galería.
- **Contactos:** elige un contacto en el selector del sistema; no se lee toda la agenda.

## Diagnóstico mediante ADB

Con opciones de desarrollador y depuración USB activadas, conecta la tablet y acepta su autorización:

```bash
adb devices
adb install -r dist/FlowWidgets-debug.apk
adb shell am start -n com.flowwidgets.app/.MainActivity
```

Para ejecutar las tres pruebas instrumentadas desde el proyecto, con Java y Android SDK configurados:

```bash
./gradlew connectedDebugAndroidTest
```

Si la app se cierra, reproduce el fallo y consulta:

```bash
adb logcat -d -s AndroidRuntime
```

Comparte el mensaje de error, la acción que lo produjo y la versión de Android/HyperOS. Evita compartir un volcado completo de notificaciones o datos personales.

## Orden recomendado de prueba

Primero verifica apertura y una nota sin permisos. Después prueba reloj, batería y calendario mensual. Continúa con ciudad, fotos, accesos y multimedia. Por último, prueba dos instancias con ajustes distintos, giro de pantalla y reinicio. Los resultados de la compilación están en [`VALIDATION.md`](VALIDATION.md).

## Cambios del reloj compacto en 1.2.2

Tras actualizar, los relojes 1×1/2×2 muestran solo la esfera grande. Fecha, reloj digital e información adicional se reservan para tamaños mayores. No hace falta reconfigurar la instancia para activar este comportamiento. Si el launcher mantiene una vista antigua, abre Mis widgets y guarda esa instancia; después prueba reducir y ampliar. One UI desaparece del selector; sus instancias conservan colores y contenido bajo Classic.

## Material LiquidGlass · 1.3.0

Instala sobre 1.2.2 para conservar los ajustes. El editor permite ajustar transparencia, iluminación y esquinas del fondo en las tres familias. Compara las manecillas, play/pausa y la línea de tiempo en tamaños pequeños y grandes; las acciones musicales deben seguir funcionando con una sesión real.

El fondo utiliza una adaptación del núcleo óptico de LiquidGlass para bitmaps de widgets. Es un material difuso generado con reflejos; no captura ni desenfoca el fondo de pantalla. Consulta la licencia en Ajustes y los límites en [LIQUID_GLASS.md](LIQUID_GLASS.md).
