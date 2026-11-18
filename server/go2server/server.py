"""Servidor WebSocket: corre la ronda sobre la física de MuJoCo y habla con la app.

La física avanza 50 veces por segundo (body.py); el motor de la ronda (robot.py), una vez por segundo.

Mensajes (contrato del TP, no el protocolo oficial de Unitree):
  app -> servidor: command
  servidor -> app: site (al conectarse), telemetry (1 por segundo), alerts (cuando cambian), event,
                   photo (la foto de la cámara frontal en un checkpoint o ante un obstáculo),
                   command_response
"""

import asyncio
import base64
import json
import random
import time

import websockets

from .body import PhysicsBody
from .robot import Robot
from .world import World

ROBOT_ID = "go2-02"
MAX_QUEUED_EVENTS = 500
FRAME_SECONDS = 0.02  # 50 cuadros por segundo de física y de ventana

HELP = """Comandos de consola:
  obstaculo      pone una caja en el pasillo, antes del próximo checkpoint
  despejar       saca la caja (después, "Reintentar" desde la app funciona)
  temperatura    la próxima medición supera el umbral crítico
  bateria        baja la batería al 31 % y la descarga más rápido
  corte [s]      corta el enlace con la app (60 s por defecto)
  velocidad N    multiplica el tiempo simulado (1 a 10)
  estado         muestra el estado del robot
  ayuda          muestra esta ayuda"""


class Go2Server:
    def __init__(self, world: World, site, speed: int):
        self.site = site
        self.world = world
        self.body = PhysicsBody(world)
        self.robot = Robot(ROBOT_ID, site, body=self.body)
        self.speed = speed
        self.clients = set()
        self.queued_events = []
        self.handled_commands = set()
        self.link_down_until = 0.0
        self.last_alerts = None
        self.marks = {}
        self.photos_taken = set()

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
        except websockets.ConnectionClosed:
            pass
        finally:
            self.clients.discard(websocket)
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
            accepted, reason = self.robot.handle(message.get("name"), message.get("params"))
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
        while True:
            await asyncio.sleep(FRAME_SECONDS)
            with self.world.lock():
                self.body.frame(FRAME_SECONDS * self.speed)
            self.world.sync()
            if time.monotonic() >= next_tick:
                next_tick += 1.0
                self.robot.set_link_available(self.link_up())
                self.robot.tick(float(self.speed))
                self.publish()

    def publish(self):
        new_events = self.robot.drain_events()
        self.track_checkpoints(new_events)
        new_events += self.take_photos(new_events)
        x, y = self.robot.plan_position()
        self.queued_events = (self.queued_events + new_events)[-MAX_QUEUED_EVENTS:]
        if not self.clients or not self.link_up():
            return
        messages = [json.dumps(event) for event in self.queued_events]
        self.queued_events = []
        alerts = json.dumps({"type": "alerts", "alerts": self.robot.alerts})
        if alerts != self.last_alerts:
            messages.append(alerts)
            self.last_alerts = alerts
        level_basement = self.site.level_at(y) == "BASEMENT"
        link = {"latency_ms": random.randint(180, 260) if level_basement else random.randint(70, 110),
                "rssi_dbm": -74 if level_basement else -61}
        messages.append(json.dumps(self.robot.telemetry((x, y), link)))
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
        m = self.robot.mission
        if self.robot.state == "ON_MISSION" and m is not None and m.current and self.robot.inspection_left > 0:
            marks.setdefault(m.current[0], ("INSPECTING", None))
        self.world.show_checkpoints(marks)

    def take_photos(self, events):
        """Saca con la cámara frontal las fotos que nombran los eventos nuevos (una vez cada una).
        El robot está quieto: en el checkpoint, o frenado frente al obstáculo."""
        photos = []
        for event in events:
            photo_id = event.get("photo_id") or (event.get("alert") or {}).get("photo_id")
            if not photo_id or photo_id in self.photos_taken:
                continue
            self.photos_taken.add(photo_id)
            jpeg = self.world.photo(*self.body.pose())
            photos.append({"type": "photo", "photo_id": photo_id, "timestamp": int(time.time() * 1000),
                           "jpeg_base64": base64.b64encode(jpeg).decode("ascii")})
        return photos

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
            if command == "obstaculo":
                self.robot.trigger("OBSTACLE")
            elif command == "despejar":
                self.robot.trigger("CLEAR_OBSTACLE")
            elif command == "temperatura":
                self.robot.trigger("HIGH_TEMPERATURE")
            elif command == "bateria":
                self.robot.trigger("LOW_BATTERY")
            elif command == "corte":
                seconds = int(args[0]) if args else 60
                self.link_down_until = time.time() + seconds
                print(f"Enlace cortado durante {seconds} s")
                continue
            elif command == "velocidad" and args:
                self.speed = max(1, min(10, int(args[0])))
            elif command == "estado":
                print(f"{self.robot.state} · batería {self.robot.battery:.0f} % · "
                      f"posición {self.robot.plan_position()} · enlace {'arriba' if self.link_up() else 'cortado'}")
                continue
            else:
                print(HELP)
                continue
            print("Listo")


async def serve(host, port, model_dir, site, speed, with_viewer, interactive):
    world = World(model_dir, site, with_viewer)
    server = Go2Server(world, site, speed)
    async with websockets.serve(server.handle_client, host, port):
        print(f"Sitio cargado: {site.name} ({len(site.checkpoints)} checkpoints)")
        print(f"Servidor del Go2 escuchando en ws://{host}:{port} (velocidad x{speed})")
        tasks = [server.run()]
        if interactive:
            tasks.append(server.console())
        await asyncio.gather(*tasks)
