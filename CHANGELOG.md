# Cambios

## 1.3.0 · Núcleo LiquidGlass y superficies reflectantes

- Núcleo oficial LiquidGlass fijado por commit, sin modificar, con licencia Apache 2.0 y procedencia verificable.
- Adaptador de óptica SDF/lente para fondos difusos, refracción de borde y brillo, compartido entre previews y RemoteViews. Sin captura ni blur en vivo del launcher.
- Reflejos en manecillas y controles musicales, conservando geometrías y acciones; esfera y números sin cambios.
- Barra de progreso translúcida con reflejo y fracción real de reproducción.
- Ajustes de material disponibles en las tres familias, caché acotada y sin bucles de refresco adicionales.


## 1.2.2 · Relojes compactos y retirada de One UI

- Reloj compacto: solo esfera analógica grande y centrada, sin fecha ni contenido adicional, también en instancias antes digitales.
- Calendario pequeño: número del día centrado a partir de sus límites de glifos, sin desplazamientos fijos de línea base.
- Eliminado One UI/One Style del modelo y selector; migración de preferencias ONE a Classic sin descartar colores ni contenido.
- Regresiones para la ausencia de información adicional en relojes compactos y centrado visual del calendario; pruebas digitales trasladadas a perfiles amplios donde siguen disponibles.


## 1.2.1 · Colocación, colores e Inter

- Corrección de estilos/colores perdidos al crear widgets antes del callback del launcher; diseño preferido por tipo, vinculación transaccional y preview personalizado.
- Confirmaciones tardías/duplicadas no sobrescriben ediciones; se distinguen solicitudes concurrentes.
- Paletas explícitas respetadas, incluidos colores coincidentes con los valores predefinidos; esfera y marcas personalizables.
- Inter oficial incluida con licencia SIL OFL y atribución en Ajustes. Light, Regular, Medium, Semibold y Bold, números tabulares, tamaño óptico y ajuste de relojes al espacio.
- Inter en Canvas y TextClock reales, además de Compose. Migración de la opción sans anterior y peso configurable por instancia.
- Esfera analógica cuadrada y centrada, sin invadir la fecha en formatos compactos.


## 1.2.0 · Widgets adaptativos

- Motor de perfiles dp compartido por los ocho proveedores, variantes RemoteViews y reconstrucción al redimensionar.
- Contenido progresivo: zonas horarias, calendario/agenda, detalles reales de batería, pronóstico diario, progreso y cola compatible, collages, listas marcables y hasta dieciséis accesos.
- Editor con once proporciones simuladas y personalización Soft Light Glass con caché acotada.
- Persistencia compatible con versiones anteriores y actualización transaccional de tareas por instancia.
- Pruebas de perfiles, presupuesto, reaplicación al reducir y aislamiento de datos; documentación de APIs y límites físicos.


## 1.1.0

- Composiciones de escritorio propias por tipo, inspiradas en la claridad visual de iOS.
- Esfera y agujas originales del reloj, operadas por AnalogClock nativo; modo digital conservado.
- Sol, nubes, lluvia, nieve, niebla y tormentas dibujados desde código; caché con código meteorológico actual.
- Indicadores de batería, notas con cabecera, calendario adaptable y accesos con iconos prominentes.
- Portada multimedia dominante y controles vectoriales con acciones reales.
- Vistas previas Compose y del selector generadas por el motor de escritorio.
- Redibujado tras actualizar el APK; layouts nuevos que preservan los IDs antiguos para evitar reutilizar la jerarquía anterior.
- 20 pruebas locales: persistencia, RemoteViews y renderización nativa en siete proporciones.

## 1.0.0

- Proyecto Android Kotlin/Compose con catálogo adaptable y editor por instancia.
- Ocho proveedores de widgets, preferencias DataStore y cuatro familias visuales.
- Integraciones oficiales de batería, calendario, fotos, accesos y sesiones multimedia.
- Clima Open-Meteo, caché offline y actualizaciones periódicas de bajo consumo.
- Pruebas de almacenamiento y RemoteViews, análisis estático y recorridos Compose.

Los resultados ejecutados y las limitaciones de dispositivo se registran en `docs/VALIDATION.md`.
