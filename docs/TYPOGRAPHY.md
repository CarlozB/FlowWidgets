# Inter y colocación · 1.2.1

## Fuente oficial y licencia

Se incorpora sin modificar `docs/font-files/InterVariable.ttf` del repositorio oficial [rsms/inter](https://github.com/rsms/inter), commit `353b61b9f4430d5f420d56605a6e7993e0941470`.

- [Archivo original fijado al commit](https://github.com/rsms/inter/blob/353b61b9f4430d5f420d56605a6e7993e0941470/docs/font-files/InterVariable.ttf).
- Archivo Android: `app/src/main/res/font/inter_variable.ttf`.
- SHA-256: `4989b125924991b90d05b2d16e0e388c48f7d5bb8b30539bbf9c755278d0ccaf`.
- Versión interna del archivo oficial: `4.001;git-9221beed3`. El número de desarrollo del repositorio no se usa como versión del binario.
- Copyright © 2016 The Inter Project Authors (https://github.com/rsms/inter).
- Licencia: SIL Open Font License 1.1, conservada íntegra en [`app/src/main/assets/licenses/Inter-OFL.txt`](../app/src/main/assets/licenses/Inter-OFL.txt), incluida en el APK y accesible desde Ajustes.

La fuente variable oficial contiene ejes `wght` (100–900), `opsz` (14–32) y la función OpenType `tnum`, comprobados en sus tablas. Se ofrecen Light 300, Regular 400, Medium 500, Semibold 600 y Bold 700. No se genera ni se renombra una fuente derivada. Inter es la opción predeterminada; no se incorpora SF Pro.

## Widgets reales y vistas previas

`WidgetTypography` comparte las reglas de peso, ajuste de tamaño, espaciado y números tabulares. Canvas utiliza la fuente empaquetada para calendarios, notas, baterías, metadatos, horas de las vistas previas y números de las esferas. Los relojes del escritorio siguen siendo AnalogClock/TextClock nativos: la hora no queda congelada en una imagen.

Los TextClock cargan familias XML `@font/inter_*` del propio APK; RemoteViews ajusta tamaño, espaciado y los ejes variables usando las APIs públicas remotables de TextView. Se contrastaron `setFontVariationSettings`, `setFontFeatureSettings` y `setLetterSpacing` en el [código oficial AOSP de TextView](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/widget/TextView.java), y se aplican en las pruebas de RemoteViews API 35, compiladas contra SDK 36.

El modo Automático usa Light para cifras grandes, Medium para tamaños intermedios, Regular para texto corriente y Semibold/Bold para énfasis. Un peso elegido expresamente tiene prioridad. El tamaño óptico y el espaciado varían según el tamaño final. Los relojes digitales se miden con una muestra tabular que incluye segundos y AM/PM cuando corresponda; se reducen hasta caber. El tamaño del reloj nativo se expresa en dp para coincidir con la geometría del dibujo; el editor conserva su control de escala de texto.

Las instancias antiguas con la opción genérica `sans` migran a Inter. Las elecciones explícitas serif/monospace se conservan y se añade una opción Sistema. Las preferencias de fuente y peso permanecen independientes por instancia. Soft Glass, los tamaños adaptativos y la caché de esferas siguen funcionando; la clave de caché incluye fuente, peso y colores.

## Correcciones de colocación y colores

Antes solo el callback de fijado trasladaba el borrador al `appWidgetId`. Un launcher que actualizaba antes de entregarlo, o no lo entregaba, podía dibujar los valores por defecto. Ahora se guarda el diseño elegido por tipo **antes** de solicitar la colocación. La primera actualización y la configuración del launcher crean la instancia con ese diseño, y el diálogo recibe también una vista previa personalizada.

Si el host omite el callback, la instancia conserva el último diseño solicitado para ese tipo. Si llegan varias solicitudes del mismo tipo, el callback con identificador exacto resuelve su borrador. Las operaciones son transaccionales; las confirmaciones repetidas o tardías no deshacen una edición guardada. Un widget ya configurado conserva sus propios ajustes. La integración física con HyperOS sigue pendiente de prueba.

También se distingue la paleta automática de los colores elegidos expresamente: seleccionar un color que coincide con un valor predeterminado ya no activa otra paleta. La esfera y sus marcas respetan los colores personalizados. La esfera analógica se limita a una región cuadrada, centrada y separada de la fecha, incluidas medidas compactas del host.

Los widgets que ya perdieron su selección en versiones anteriores no tienen esos ajustes recuperables: después de actualizar, edítalos en Mis widgets o vuelve a colocarlos con el diseño deseado.

## Evidencia y límites

Las regresiones prueban primera actualización sin callback, callback previo/tardío/duplicado, solicitudes concurrentes, persistencia de paleta, cinco pesos en Canvas y TextClock, anchura tabular de cifras y medición de relojes compactos en vistas Android. Los ejemplos exportados son dibujos de pruebas del motor real, no capturas de HyperOS. No se volvió a iniciar el emulador; falta confirmar en la tablet la colocación y el aspecto final.
