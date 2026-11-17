# Servidor del Go2 con MuJoCo

Servidor de misiones para el robot **Go2-02** de la app. Corre la ronda, mueve el modelo del Go2 en MuJoCo y habla con la app por WebSocket usando el contrato JSON del TP.

- **Modelo:** el Go2 de [MuJoCo Menagerie](https://github.com/google-deepmind/mujoco_menagerie/tree/main/unitree_go2) (Apache 2.0), en un plano del data center con los racks como volúmenes.
- **Movimiento cinemático:** la base del modelo se ubica sobre el recorrido en cada ciclo y las patas quedan en la pose de pie. No hay controlador de locomoción.
- **Posición:** la que se informa a la app se lee del estado del modelo.
- **Ronda:** el motor (`go2server/robot.py`) tiene el mismo comportamiento que el simulador de la app (`RobotSimulator.kt`).

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
| `--viewer` | Abre la ventana de MuJoCo para ver al robot recorrer el data center |
| `--speed N` | Multiplica el tiempo simulado (1 a 10; por defecto 4) |
| `--port N` | Puerto del WebSocket (por defecto 8765) |
| `--no-console` | Sin comandos por consola, para correrlo en segundo plano |

En la consola se pueden provocar los escenarios: `obstaculo`, `temperatura`, `bateria`, `corte [s]`, `velocidad N`, `estado`.

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
| app → servidor | `command` | `command_id`, `name` (`START_MISSION`, `PAUSE`, `RESUME`, `CANCEL`, `RETURN_TO_BASE`, `OPERATIONAL_STOP`, `ACK_ALERT`, `INCIDENT_DECISION`) y `params` |
| servidor → app | `command_response` | `command_id`, `status` (`ACCEPTED` o `REJECTED`) y `reason` |
| servidor → app | `telemetry` | Una vez por segundo: batería, estado, misión, ubicación, movimiento, enlace y última lectura ambiental |
| servidor → app | `alerts` | La lista de alertas de la sesión, cada vez que cambia |
| servidor → app | `event` | `MISSION_STARTED`, `CHECKPOINT_RESULT`, `ALERT`, `MISSION_FINISHED` |

Con el enlace cortado (`corte`), el servidor no envía nada y descarta las órdenes. Los eventos se guardan y se mandan al volver la conexión.

## Limitaciones

- La escena tiene un solo nivel: el subsuelo está dibujado al lado de la planta baja, como en el plano de la app, y la escalera no se simula físicamente.
- Las lecturas de temperatura, humedad y ruido son simuladas, igual que en la app.
