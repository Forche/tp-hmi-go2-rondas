"""Servidor WebSocket: corre la ronda y habla con la app.

Arma las dos partes: la lógica de la ronda (mission_control.py) y el robot, que hoy es SimulatedRobot sobre
MuJoCo (sim_robot.py, sim_body.py, sim_scene.py). La física avanza 50 veces por segundo; la lógica de la
ronda, una vez por segundo. El corte de enlace (`corte`) simula la red, no el robot: vive acá.

Mensajes (contrato del TP, no el protocolo oficial de Unitree):
  app -> servidor: command
  servidor -> app: site (al conectarse), telemetry (1 por segundo), alerts (cuando cambian), event,
                   photo (la foto de la cámara frontal en un checkpoint, o ante un obstáculo, agua o humo),
                   camera_frame (cámara en vivo, mientras la app la pida con un mensaje camera),
                   command_response
"""

import asyncio
import base64
import json
import random
import time

import websockets

from .sim_body import PhysicsBody
from .mission_control import MissionControl
from .sim_random import RandomSituations
from .sim_robot import SCENARIOS, SimulatedRobot
from .sim_scene import Scene

ROBOT_ID = "go2-02"
MAX_QUEUED_EVENTS = 500
FRAME_SECONDS = 0.02  # 50 cuadros por segundo de física y de ventana
LIVE_CAMERA_SECONDS = 0.25  # cámara en vivo: 4 imágenes por segundo

RANDOM_LINK_CUT_SECONDS = 20

HELP = """Comandos de consola:
  obstaculo      pone una caja sobre el próximo checkpoint: tapa el punto y pide una decisión (E4)
  caja           pone una caja en el camino: el robot la rodea solo y avisa
  despejar       saca la caja (después, "Reintentar" desde la app funciona)
  agua           aparece agua en el piso, sobre el camino
  humo           aparece un foco de fuego con humo al costado del camino
  temperatura    la próxima medición supera el umbral crítico
  humedad        la próxima medición tiene la humedad alta
  ruido          la próxima medición tiene ruido de más
  bateria        baja la batería al 31 % y la descarga más rápido
  corte [s]      corta el enlace con la app (60 s por defecto)
  aleatorio      activa o desactiva las situaciones al azar durante la ronda
  velocidad N    multiplica el tiempo simulado (1 a 10)
  estado         muestra el estado del robot
  ayuda          muestra esta ayuda"""


class Go2Server:
    def __init__(self, scene: Scene, site, speed: int):
        self.site = site
        self.scene = scene
        self.body = PhysicsBody(scene)
        # El robot simulado. Con un Go2 real, acá iría UnitreeRobot (unitree_robot.py).
        self.robot = SimulatedRobot(site, self.body, scene=scene)
        self.mission_control = MissionControl(ROBOT_ID, site, self.robot)
        self.speed = speed
        self.clients = set()
        self.queued_events = []
        self.handled_commands = set()
        self.link_down_until = 0.0
        self.last_alerts = None
        self.marks = {}
        self.random_situations = None  # con el modo aleatorio activo, un RandomSituations
        self.camera_viewers = set()     # apps que están mirando la cámara en vivo

    def link_up(self):
        return time.time() >= self.link_down_until

    # ------------------------------------------------------------ conexión con la app

    async def handle_client(self, websocket):
        self.clients.add(websocket)
        print(f"App conectada desde {websocket.remote_address}")
        self.last_alerts = None  # la app nueva recibe la lista completa
        try:
            # Como un robot real, le pasa a la app el mapa que tiene cargado.
            await websocket.send(json.dumps({"type": "site", "site": self.site.app_payload()}))
            async for raw in websocket:
                message = json.loads(raw)
                if message.get("type") == "command":
                    asyncio.create_task(self.respond(websocket, message))
                elif message.get("type") == "camera":
                    # Cámara en vivo: la app la pide al abrir la pantalla y la corta al salir.
                    if message.get("enabled"):
                        self.camera_viewers.add(websocket)
                    else:
                        self.camera_viewers.discard(websocket)
        except websockets.ConnectionClosed:
            pass
        finally:
            self.clients.discard(websocket)
            self.camera_viewers.discard(websocket)
            print("App desconectada")

    async def respond(self, websocket, message):
        # Con el enlace cortado la orden se pierde: la app la marca "sin respuesta" a los 5 s.
        if not self.link_up():
            return
        await asyncio.sleep(random.uniform(0.3, 0.8))
        if not self.link_up():
            return
        command_id = message.get("command_id")
        if command_id in self.handled_commands:
            accepted, reason = True, None  # orden repetida: se confirma sin ejecutarla otra vez
        else:
            self.handled_commands.add(command_id)
            accepted, reason = self.mission_control.handle(message.get("name"), message.get("params"))
        print(f"Orden {message.get('name')}: {'aceptada' if accepted else 'rechazada (' + str(reason) + ')'}")
        await websocket.send(json.dumps({
            "type": "command_response",
            "command_id": command_id,
            "status": "ACCEPTED" if accepted else "REJECTED",
            "reason": reason,
            "responded_at": int(time.time() * 1000),
        }))
        self.publish()

    # ------------------------------------------------------------ ciclo de simulación

    async def run(self):
        next_tick = time.monotonic() + 1.0
        next_frame = time.monotonic()
        while True:
            await asyncio.sleep(FRAME_SECONDS)
            with self.scene.lock():
                self.body.frame(FRAME_SECONDS * self.speed)
            self.scene.sync()
            if self.camera_viewers and time.monotonic() >= next_frame:
                next_frame = time.monotonic() + LIVE_CAMERA_SECONDS
                self.send_live_frame()
            if time.monotonic() >= next_tick:
                next_tick += 1.0
                self.mission_control.set_link_available(self.link_up())
                self.mission_control.tick(float(self.speed))
                self.random_tick(float(self.speed))
                self.publish()

    def random_tick(self, dt):
        control = self.mission_control
        if self.random_situations is None or control.state != "ON_MISSION" or control.mission is None:
            return
        name = self.random_situations.tick(dt, control.mission.id)
        if name is not None:
            print(f"Situación aleatoria: {name}")
            self.apply(name, [str(RANDOM_LINK_CUT_SECONDS)])

    def apply(self, command, args):
        """Provoca un escenario. Devuelve False si el comando no es un escenario."""
        if command == "corte":
            seconds = int(args[0]) if args else 60
            self.link_down_until = time.time() + seconds
            print(f"Enlace cortado durante {seconds} s")
        elif command in SCENARIOS:
            self.robot.trigger(SCENARIOS[command])
        else:
            return False
        return True

    def publish(self):
        new_events = self.mission_control.drain_events()
        self.track_checkpoints(new_events)
        new_events += self.photo_messages()
        x, y = self.mission_control.plan_position()
        self.queued_events = (self.queued_events + new_events)[-MAX_QUEUED_EVENTS:]
        if not self.clients or not self.link_up():
            return
        messages = [json.dumps(event) for event in self.queued_events]
        self.queued_events = []
        alerts = json.dumps({"type": "alerts", "alerts": self.mission_control.alerts})
        if alerts != self.last_alerts:
            messages.append(alerts)
            self.last_alerts = alerts
        level_basement = self.site.level_at(y) == "BASEMENT"
        link = {"latency_ms": random.randint(180, 260) if level_basement else random.randint(70, 110),
                "rssi_dbm": -74 if level_basement else -61}
        messages.append(json.dumps(self.mission_control.telemetry(link)))
        for message in messages:
            websockets.broadcast(self.clients, message)

    def track_checkpoints(self, events):
        """Lleva el resultado de cada checkpoint de la ronda actual para mostrarlo en la escena 3D."""
        for event in events:
            if event.get("kind") == "MISSION_STARTED":
                self.marks = {}
            elif event.get("kind") == "CHECKPOINT_RESULT":
                self.marks[event["checkpoint_id"]] = (event["status"], event.get("reading"))
        marks = dict(self.marks)
        m = self.mission_control.mission
        if self.mission_control.state == "ON_MISSION" and m is not None and m.current and self.mission_control.inspection_left > 0:
            marks.setdefault(m.current[0], ("INSPECTING", None))
        self.scene.show_checkpoints(marks)

    def send_live_frame(self):
        """Cámara en vivo: no se guarda ni se encola; sin enlace, simplemente no llega."""
        if not self.link_up():
            return
        jpeg = self.robot.live_frame()
        if jpeg is None:
            return
        websockets.broadcast(self.camera_viewers, json.dumps({
            "type": "camera_frame", "timestamp": int(time.time() * 1000),
            "jpeg_base64": base64.b64encode(jpeg).decode("ascii")}))

    def photo_messages(self):
        """Las fotos que sacó el robot desde el último envío, como mensajes `photo` del contrato."""
        return [{"type": "photo", "photo_id": photo_id, "timestamp": int(time.time() * 1000),
                 "jpeg_base64": base64.b64encode(jpeg).decode("ascii")}
                for photo_id, jpeg in self.robot.drain_photos()]

    # ------------------------------------------------------------ consola

    async def console(self):
        print(HELP)
        while True:
            try:
                line = await asyncio.to_thread(input, "> ")
            except EOFError:
                return
            parts = line.strip().lower().split()
            if not parts:
                continue
            command, args = parts[0], parts[1:]
            if command == "aleatorio":
                self.random_situations = None if self.random_situations else RandomSituations()
                print("Situaciones aleatorias " + ("activadas" if self.random_situations else "desactivadas"))
                continue
            if command == "corte":
                self.apply(command, args)
                continue
            if self.apply(command, args):
                if command in SCENARIOS and command not in ("bateria", "despejar") and self.mission_control.state == "AT_BASE":
                    print("Listo: el robot está en la base, va a aparecer en la próxima ronda")
                    continue
            elif command == "velocidad" and args:
                self.speed = max(1, min(10, int(args[0])))
            elif command == "estado":
                print(f"{self.mission_control.state} · batería {self.mission_control.battery:.0f} % · "
                      f"posición {self.mission_control.plan_position()} · enlace {'arriba' if self.link_up() else 'cortado'}")
                continue
            else:
                print(HELP)
                continue
            print("Listo")


async def serve(host, port, model_dir, site, speed, with_viewer, interactive):
    scene = Scene(model_dir, site, with_viewer)
    server = Go2Server(scene, site, speed)
    async with websockets.serve(server.handle_client, host, port):
        print(f"Sitio cargado: {site.name} ({len(site.checkpoints)} checkpoints)")
        print(f"Servidor del Go2 escuchando en ws://{host}:{port} (velocidad x{speed})")
        tasks = [server.run()]
        if interactive:
            tasks.append(server.console())
        await asyncio.gather(*tasks)
