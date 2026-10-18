# LiquidGlass · Flow Widgets 1.3

Se incorpora el núcleo oficial de [Abdullajon1881/LiquidGlass](https://github.com/Abdullajon1881/LiquidGlass), fijado al commit `72ad05c49628ea2270116b2943629cd81c0cc496`. [Procedencia y hashes](../third_party/LiquidGlass/README.md). Copyright 2026 LiquidGlass contributors, Apache License 2.0; licencia completa en el código y en Ajustes del APK.

## Qué utiliza el escritorio

El widget usa RemoteViews, que permite TextClock, AnalogClock, ImageView y acciones PendingIntent, pero no hospedar LiquidGlassView ni una composición con `Modifier.liquidGlass`. Los renderizadores originales necesitan un proveedor que capture contenido situado detrás dentro de la propia aplicación. El launcher no ofrece ese contenido ni su fondo a nuestros widgets.

Por ello se integra `liquidglass-core` mediante código fuente oficial sin modificar. El adaptador `LiquidGlassWidgetBackground` usa las funciones SDF de caja redondeada y el perfil circular de lente de `GlassMath` para dibujar un campo de luz difuso con borde refractado y brillo dependiente de su normal. El fondo generado no contiene detalle nítido: simula el aspecto esmerilado mediante luces suaves y transparencias. **No se desenfoca en vivo el fondo de pantalla, no se captura el launcher y no se ejecuta su shader AGSL.** Tampoco se anuncian fusión entre widgets ni respuesta de gel al toque.

El mismo bitmap se utiliza en las vistas previas y en el escritorio. El material se aplica al fondo de los widgets con los colores elegidos, sin convertir sus preferencias guardadas a otro estilo. Opacidad, iluminación y esquinas se controlan desde el editor en las tres familias. Fotos y portadas conservan sus imágenes y proporciones; el material de fondo aparece donde la composición deja espacio.

## Piezas del reloj y reproductor

- Manecillas: degradado translúcido con bandas de reflexión y eje centrado. Se usan los mismos bitmaps en AnalogClock y en la vista previa.
- Esfera circular, marcas y números: se mantiene su código y geometría. El reloj compacto continúa mostrando únicamente la esfera grande centrada.
- Play, pausa, anterior y siguiente: se conservan las formas y acciones originales, con una superficie de reflejos. No se sustituyen los PendingIntent por un dibujo sin interacción.
- Línea de tiempo: superficie translúcida y reflejo superior que conserva la fracción real de posición/duración. Sigue omitiéndose cuando el reproductor no comparte datos suficientes.

Los reflejos de estas piezas son un acabado Canvas propio de Flow Widgets. El núcleo de LiquidGlass se usa específicamente para el fondo; no se presenta ese acabado como su renderizador AGSL.

## Recursos y comprobaciones

El campo óptico se calcula a un máximo de 256 px por eje y se amplía suavemente al tamaño del bitmap ya presupuestado por el motor. La caché LRU de fondos sigue limitada a 6 MiB y distingue dimensiones, colores, opacidad, iluminación y radio efectivo. Las esferas y agujas siguen compartidas entre variantes. No se añaden procesos permanentes, red, alarmas ni bucles de animación.

Las pruebas comprueban máscara, dimensiones, lente, caché y controles de opacidad/iluminación, variedad de reflejos con transparencia, fracción y límites de la barra, y los perfiles existentes. El test de centrado del calendario distingue el color de sus glifos del brillo del fondo, manteniendo la misma tolerancia geométrica.

La lámina [FlowWidgets-LiquidGlass.png](images/FlowWidgets-LiquidGlass.png) se genera con el motor de producción y datos exclusivos de pruebas; no es una captura de HyperOS. La aplicación no incluye esos datos. Queda por validar el resultado y consumo en la tablet real; no se volvió a iniciar el emulador.
