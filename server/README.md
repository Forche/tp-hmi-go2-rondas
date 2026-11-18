# Servidor del Go2 con MuJoCo

Servidor de misiones para el robot **Go2-02** de la app. Corre la ronda sobre un Go2 simulado en MuJoCo y habla con la app por WebSocket usando el contrato JSON del TP.

## Qué es real y qué es simulado

El servidor tiene dos partes, separadas por un contrato:

| Parte | Archivos | Con un Go2 real |
| --- | --- | --- |
| **Lógica de la ronda:** misiones, estados, reglas (R1 a R5), alertas, validación de órdenes, contrato JSON con la app | `mission_control.py`, `server.py`, `site.py` | Igual, sin cambios |
| **Contrato con el robot:** moverse por el recorrido, frenar, dónde está, LiDAR, cámara, sensores del ambiente, batería | `robot_interface.py` | Igual |
| **El robot simulado**, que cumple ese contrato | `sim_robot.py`, `sim_body.py`, `sim_scene.py` | Se reemplaza por `unitree_robot.py`, que usaría el SDK de Unitree (hoy es sólo un esbozo) |

`mission_control.py` no importa nada de la simulación; un test lo verifica.

El robot simulado:

- **Modelo:** el Go2 de [MuJoCo Menagerie](https://github.com/google-deepmind/mujoco_menagerie/tree/main/unitree_go2) (Apache 2.0), en la escena del sitio cargado (salas, racks y equipos como volúmenes, escalera al subsuelo). La escena y la cámara están en `sim_scene.py`.
- **Física:** MuJoCo corre `mj_step` 50 veces por segundo (`sim_body.py`). El robot se apoya con contactos y gravedad sobre los pisos, los bloques y los equipos de la escena, y las patas trotan con un control PD de cada articulación. No hay una política de locomoción entrenada: una fuerza horizontal sobre el tronco lo guía por el recorrido (gira en el lugar antes de una curva cerrada). En la escalera el tronco va guiado sobre los escalones, sin física. Este es el papel que en el Go2 real cumple el controlador de caminata de Unitree.
- **Posición medida:** la posición, la velocidad y el rumbo que recibe la app salen de la simulación. La lógica de la ronda decide hasta dónde ir y avanza según lo que informa el robot. En la simulación la posición es exacta; un robot real la estima con error.
- **LiDAR simulado:** rayos (`mj_ray`) a lo largo del camino que el robot tiene por delante. Si algo lo bloquea, el robot frena a 0,9 m y pide una decisión; los racks y equipos al costado del camino no cuentan.
- **Cámara frontal:** en cada checkpoint inspeccionado y ante un obstáculo, se genera la imagen de la cámara del robot y se le manda la foto (JPEG) a la app.
- **Batería y sensores del ambiente** (`sim_robot.py`): un modelo de carga y descarga, y lecturas inventadas a partir de los valores típicos del archivo de sitio.
- **Ronda:** la lógica (`mission_control.py`) tiene el mismo comportamiento que el simulador de la app (`RobotSimulator.kt`).

### Por qué no `unitree_mujoco`

[unitree_mujoco](https://github.com/unitreerobotics/unitree_mujoco) también corre el Go2 en MuJoCo y expone la misma interfaz que el robot real, pero sólo a bajo nivel (comandos a cada uno de los 12 motores, por DDS con `unitree_sdk2`). No camina solo: no trae el controlador de caminata de Unitree, y el único entrenado que publica Unitree (`unitree_rl_gym`) no incluye al Go2. Un servidor de rondas para el robot real usaría el nivel alto (pedirle que camine), así que con `unitree_mujoco` habría que escribir un controlador que en el robot real no se usaría.

## Instalación

Requiere Python 3.10 o superior.

```bash
cd server
python -m venv .venv
.venv/Scripts/python -m pip install -r requirements.txt
```

Modelo del Go2 (sólo esa carpeta de Menagerie, ~30 MB):

```bash
mkdir third_party && cd third_party
git clone --depth 1 --filter=blob:none --sparse https://github.com/google-deepmind/mujoco_menagerie.git
cd mujoco_menagerie && git sparse-checkout set unitree_go2
```

## Uso

```bash
.venv/Scripts/python -m go2server --viewer
```

| Opción | Para qué |
| --- | --- |
| `--sitio ARCHIVO` | Sitio que se le carga al robot (por defecto `../sitios/datacenter-edificio-a.json`; formato en [sitios/README.md](../sitios/README.md)) |
| `--viewer` | Abre la ventana de MuJoCo para ver al robot recorrer el data center |
| `--speed N` | Multiplica el tiempo simulado (1 a 10; por defecto 4) |
| `--port N` | Puerto del WebSocket (por defecto 8765) |
| `--no-console` | Sin comandos por consola, para correrlo en segundo plano |

En la consola se pueden provocar los escenarios: `obstaculo` (una caja sobre el próximo checkpoint: tapa el punto y el robot pide una decisión, E4), `caja` (una caja en el camino: el robot la rodea solo y avisa), `despejar` (saca la caja), `agua` (un charco sobre el camino), `humo` (un foco de fuego con humo al costado del camino), `temperatura`, `humedad`, `ruido` (la próxima medición sale de rango), `bateria`, `corte [s]`, `aleatorio` (activa o desactiva las situaciones al azar durante la ronda, `sim_random.py`), `velocidad N`, `estado`.

Con la caja en el pasillo, *Reintentar* desde la app vuelve rechazada porque el LiDAR la sigue viendo; después de `despejar`, *Reintentar* se acepta y el robot sigue. Con *Saltear*, el robot rodea la caja por un costado. Al volver a la base la caja, el agua y el fuego se retiran, y lo que se pidió y no llegó a aparecer se descarta. Lo que se pide con el robot en la base aparece en la próxima ronda.

**Criterio:** si el robot puede resolver el problema solo, saca una foto, avisa y sigue: la caja en el camino y el charco los rodea, una medición fuera de rango la informa y ante humo vuelve a la base. Sólo pide una decisión cuando no puede medir: una caja sobre el checkpoint (E4).

El charco no lo ve el LiDAR (es plano): lo ve la cámara a 3,5 m. El humo lo detecta el módulo de sensores a 4,5 m. Las dos detecciones se simulan sabiendo dónde está cada cosa; un robot real las haría por imagen y con un sensor de humo.

### Conectar la app

- **Emulador:** la app ya apunta a `10.0.2.2:8765`, que es la PC desde el emulador. Elegí **Go2-02** en la pantalla de selección.
- **Celular en la misma red Wi-Fi:** compilá la app con la IP de la PC:

```bash
./gradlew assembleDebug -Pgo2.mujocoAddress=192.168.0.20:8765
```

## Tests

```bash
.venv/Scripts/python -m unittest discover -s tests -t .
```

## Mensajes

Contrato propio del TP, no el protocolo oficial de Unitree.

| Dirección | Tipo | Contenido |
| --- | --- | --- |
| servidor → app | `site` | Al conectarse: el mapa que tiene cargado el robot (el archivo de sitio sin `simulation` ni `scene_3d`) |
| app → servidor | `command` | `command_id`, `name` (`START_MISSION`, `PAUSE`, `RESUME`, `CANCEL`, `RETURN_TO_BASE`, `OPERATIONAL_STOP`, `ACK_ALERT`, `INCIDENT_DECISION`) y `params` |
| servidor → app | `command_response` | `command_id`, `status` (`ACCEPTED` o `REJECTED`) y `reason` |
| servidor → app | `telemetry` | Una vez por segundo: batería, estado, misión, ubicación, movimiento, enlace y última lectura ambiental |
| servidor → app | `alerts` | La lista de alertas de la sesión, cada vez que cambia |
| servidor → app | `event` | `MISSION_STARTED`, `CHECKPOINT_RESULT` (con `photo_id`), `ALERT`, `MISSION_FINISHED` |
| servidor → app | `photo` | `photo_id`, `timestamp` y `jpeg_base64`: una foto de la cámara frontal (320 × 240) que la app guarda. La de un checkpoint se llama `<ronda>_<checkpoint>`; la de un incidente (obstáculo, agua, humo) la nombra la alerta en `photo_id` |
| app → servidor | `camera` | `enabled`: pide o corta la cámara en vivo |
| servidor → app | `camera_frame` | `timestamp` y `jpeg_base64`: cámara en vivo, unas 4 imágenes por segundo mientras la app la pida. No se guarda |

Con el enlace cortado (`corte`), el servidor no envía nada y descarta las órdenes. Los eventos se guardan y se mandan al volver la conexión.

## Limitaciones

- No hay controlador de locomoción entrenado: las patas trotan, pero lo que lleva al robot por el recorrido es una fuerza de guía sobre el tronco. Un Go2 real camina con el controlador de Unitree.
- La escalera no se simula físicamente: el tronco va guiado sobre los escalones (bajar escaleras necesita una política entrenada, por ejemplo con MuJoCo Playground, fuera del alcance del TP).
- El desvío alrededor de un obstáculo es un corrimiento lateral fijo, no un planificador de caminos.
- Las lecturas de temperatura, humedad y ruido son simuladas, igual que en la app.
