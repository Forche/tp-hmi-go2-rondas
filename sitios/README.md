# Archivos de sitio

Un archivo de sitio describe el lugar donde trabaja el robot:

- el plano y los checkpoints;
- el recorrido y las misiones;
- los umbrales de alerta.

En un Go2 real esto sale de la puesta en marcha: el mapa del LiDAR y los puntos que marca el técnico. Acá es un JSON que se le **carga al robot**. La app no trae el mapa fijo: lo recibe del robot que elige el operador. Así, cambiar de sitio no requiere tocar ni recompilar la app.

| Robot | Cómo se le carga el sitio | Cómo lo recibe la app |
| --- | --- | --- |
| Go2-01 (simulador local) | Modo demo → *Cargar archivo…* (con el robot en la base) | Directo del simulador, que corre dentro del APK |
| Go2-02 (servidor MuJoCo) | `python -m go2server --sitio ruta/al/sitio.json` | Mensaje `site` por WebSocket al conectarse; la app guarda una copia para mostrar el plano si se corta el enlace |

Los dos simuladores aplican las mismas reglas para validar el archivo. Si algo falla, avisan qué es y siguen con el sitio anterior; el servidor no arranca.

Ejemplos de esta carpeta, que van dentro del APK:

- `datacenter-edificio-a.json`: el caso del TP. Tiene planta baja y subsuelo, 8 checkpoints, escalera y dos misiones. Es el sitio por defecto.
- `datacenter-edificio-b.json`: una sola planta, 5 checkpoints y sólo la ronda completa.

## Formato `go2-sitio/1`

Las coordenadas van en metros sobre el plano: `x` crece hacia la derecha e `y` hacia abajo. Si hay subsuelo, se dibuja debajo de la planta baja en el mismo plano.

| Campo | Qué es |
| --- | --- |
| `format` | Siempre `"go2-sitio/1"` |
| `site_id`, `name` | Identificador y nombre que ve el operador |
| `width`, `height` | Tamaño del plano |
| `base` | Dónde carga el robot (`x`, `y`) |
| `basement_start_y` | Desde qué `y` empieza el subsuelo; `null` si hay una sola planta |
| `stairs` | Tramo de escalera (`left`, `right`, `top`, `bottom`); `null` si no hay. Ahí el robot pierde precisión de ubicación |
| `areas` | Lo que se dibuja en el plano. Cada área lleva `kind` (`ROOM`, `RACK_ROW`, `COLD_AISLE`, `EQUIPMENT`, `STAIRS`, `LEVEL_LABEL`), `label`, `left`, `top`, `width` y `height` |
| `checkpoints` | Cada punto lleva `id`, `number`, `name`, `zone` (`COLD_AISLE`, `HOT_AISLE`, `COOLING`, `NETWORK`, `ELECTRICAL`), `x` e `y`. Tiene que estar sobre el recorrido (a menos de 0,5 m) |
| `route` | Recorrido desde la base, como lista de puntos. El robot va y vuelve por él |
| `missions` | `FULL_ROUND` (obligatoria) y `GROUND_FLOOR_ROUND` (opcional), cada una con su lista de checkpoints. El chequeo puntual existe siempre |
| `thresholds` | Batería (`min_to_start`, `warning`, `critical`), temperatura por zona, humedad baja y alta, y ruido sobre la línea base. Cada uno con `warning` y `critical` |
| `simulation` | Sólo para los simuladores: humedad típica y, por zona, temperatura y ruido típicos. Un robot real no lo tiene, y el servidor no se lo manda a la app |
| `scene_3d` | Sólo para la ventana de MuJoCo: profundidad del subsuelo y carteles. Cada área puede traer además `height_m` (altura del volumen) y `color` (`#RRGGBB`) |

Cada zona que usen los checkpoints necesita sus umbrales de temperatura y sus lecturas típicas.

Para probar un sitio nuevo:

1. Copiar uno de los ejemplos y editarlo.
2. Cargarlo en el servidor con `--sitio`, o en el emulador desde el modo demo.

Para subir el archivo al emulador:

```bash
adb push mi-sitio.json /sdcard/Download/
```
