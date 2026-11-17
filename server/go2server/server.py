"""Servidor WebSocket: corre la ronda, mueve el modelo en MuJoCo y habla con la app.

Mensajes (contrato del TP, no el protocolo oficial de Unitree):
  app -> servidor: command
  servidor -> app: telemetry (1 por segundo), alerts (cuando cambian), event, command_response
"""

import asyncio
import json
import random
import time

import websockets

from .robot import Robot
from .world import World

ROBOT_ID = "go2-02"
MAX_QUEUED_EVENTS = 500

HELP = """Comandos de consola:
  obstaculo      pone un obstáculo antes del próximo checkpoint
  temperatura    la próxima medición supera el umbral crítico
  bateria        baja la batería al 31 % y la descarga más rápido
  corte [s]      corta el enlace con la app (60 s por defecto)
  velocidad N    multiplica el tiempo simulado (1 a 10)
  estado         muestra el estado del robot
  ayuda          muestra esta ayuda"""


class Go2Server:
    def __init__(self, world: World, speed: int):
        self.robot = Robot(ROBOT_ID)
        self.world = world
        self.speed = speed
        self.clients = set()
        self.queued_events = []
        self.handled_commands = set()
        self.link_down_until = 0.0
        self.last_alerts = None
        self.marks = {}

    def link_up(self):
        return time.time() >= self.link_down_until

    # ------------------------------------------------------------ conexión con la app

    async def handle_client(self, websocket):
        self.clients.add(websocket)
        print(f"App conectada desde {websocket.remote_address}")
        self.last_alerts = None  # la app nueva recibe la lista completa
        try:
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
        while True:
            await asyncio.sleep(1.0)
            self.robot.set_link_available(self.link_up())
            self.robot.tick(float(self.speed))
            self.publish()

    def publish(self):
        new_events = self.robot.drain_events()
        self.track_checkpoints(new_events)
        x, y = self.robot.plan_position()
        self.world.place(x, y, self.robot.heading())
        self.queued_events = (self.queued_events + new_events)[-MAX_QUEUED_EVENTS:]
        if not self.clients or not self.link_up():
            return
        messages = [json.dumps(event) for event in self.queued_events]
        self.queued_events = []
        alerts = json.dumps({"type": "alerts", "alerts": self.robot.alerts})
        if alerts != self.last_alerts:
            messages.append(alerts)
            self.last_alerts = alerts
        level_basement = y >= 14.0
        link = {"latency_ms": random.randint(180, 260) if level_basement else random.randint(70, 110),
                "rssi_dbm": -74 if level_basement else -61}
        messages.append(json.dumps(self.robot.telemetry(self.world.plan_position(), link)))
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
        if self.robot.state == "ON_MISSION" and m is not None and m.current and self.robot.speed == 0:
            marks.setdefault(m.current[0], ("INSPECTING", None))
        self.world.show_checkpoints(marks)

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


async def serve(host, port, model_dir, speed, with_viewer, interactive):
    world = World(model_dir, with_viewer)
    server = Go2Server(world, speed)
    async with websockets.serve(server.handle_client, host, port):
        print(f"Servidor del Go2 escuchando en ws://{host}:{port} (velocidad x{speed})")
        tasks = [server.run()]
        if interactive:
            tasks.append(server.console())
        await asyncio.gather(*tasks)
