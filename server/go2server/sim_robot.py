"""El robot simulado: todo lo que en un Go2 real sería hardware.

- Movimiento, posición y LiDAR: el cuerpo (sim_body.py). PhysicsBody es el Go2 en MuJoCo con la física
  corriendo; KinematicBody avanza por cálculo y lo usan los tests de la lógica de la ronda.
- Cámara frontal: imágenes generadas por MuJoCo (sim_scene.py). Sin escena no hay cámara.
- Batería: un modelo de carga y descarga.
- Sensores del ambiente: lecturas inventadas a partir de los valores típicos del archivo de sitio.
- Detección de agua (cámara) y de humo (módulo de sensores): se simulan sabiendo dónde está el charco o
  el fuego; un robot real los detectaría por imagen y con un sensor de humo.
- Escenarios de prueba (consola del servidor): batería baja, una caja en el camino o tapando un
  checkpoint, agua, fuego,
  temperatura alta, humedad o ruido fuera de rango. Son cosas que le pasan al robot o al lugar, por eso
  viven acá y no en la lógica de la ronda.
"""

import math
import random

from .robot_interface import RobotInterface

CHARGE_RATE = 0.25        # %/s en la base
MOVING_DRAIN = 0.012      # %/s caminando
IDLE_DRAIN = 0.004        # %/s quieto fuera de la base
LOW_BATTERY_DRAIN_MULTIPLIER = 15.0
INITIAL_BATTERY = 92.0
FIRE_SIDE_OFFSET = 1.0    # m: el fuego está al costado del camino (en un rack o un equipo)

# Escenarios que se pueden provocar (nombre de la consola -> nombre interno).
SCENARIOS = {
    "bateria": "LOW_BATTERY",
    "obstaculo": "OBSTACLE",        # tapa el próximo checkpoint (E4)
    "caja": "OBSTACLE_PATH",        # en el camino: el robot la rodea solo
    "despejar": "CLEAR_OBSTACLE",
    "agua": "WATER",
    "humo": "SMOKE",
    "temperatura": "HIGH_TEMPERATURE",
    "humedad": "HUMIDITY",
    "ruido": "NOISE",
}


class SimulatedRobot(RobotInterface):

    def __init__(self, site, body, scene=None, rng=None):
        self.site = site
        self.body = body
        self.scene = scene
        self.rng = rng or random.Random()
        self._battery = INITIAL_BATTERY
        self.docked = True
        self.drain_multiplier = 1.0
        self.photos = []
        self.armed = set()           # escenarios que esperan un lugar o una medición para aparecer
        self.hot_checkpoint = None
        self.water_at = None         # distancias del recorrido
        self.smoke_at = None

    # ------------------------------------------------------------ movimiento

    def go_to(self, distance):
        self.docked = False
        self.body.set_goal(distance)

    def hold(self):
        self.body.hold()

    def bypass_obstacle(self):
        self.body.bypass_obstacle()

    def bypass_water(self):
        if self.water_at is not None:
            self.body.bypass_point(self.water_at)

    def dock(self):
        # Entre rondas el personal retira lo que haya quedado en el pasillo, seca el agua y apaga el fuego.
        self.body.reset_at_base()
        self.water_at = self.smoke_at = None
        self._show("charco", None)
        self._show("fuego", None)
        self.docked = True

    def update(self, dt):
        self._place_armed_hazards()
        self.body.advance(dt)
        if self.docked:
            self.drain_multiplier = 1.0
            self._battery = min(100.0, self._battery + CHARGE_RATE * dt)
        else:
            self._battery = max(0.0, self._battery - self.battery_drain_rate() * dt)

    # ------------------------------------------------------------ estado

    @property
    def distance(self):
        return self.body.distance

    @property
    def speed(self):
        return self.body.speed

    @property
    def forward(self):
        return self.body.forward

    def position(self):
        return self.body.position()

    def heading(self):
        return self.body.heading()

    def location_confidence(self):
        x, y = self.position()
        return 0.81 if self.site.on_stairs(x, y) else round(0.91 + self.rng.uniform(0, 0.05), 2)

    # ------------------------------------------------------------ sensores

    def obstacle_ahead(self, lookahead):
        return self.body.obstacle_ahead(lookahead)

    def water_ahead(self, lookahead):
        if self.water_at is None or self.water_at in self.body.bypassed:
            return None
        return self._ahead(self.water_at, lookahead)

    def smoke_ahead(self, lookahead):
        return None if self.smoke_at is None else self._ahead(self.smoke_at, lookahead)

    def _ahead(self, at, lookahead):
        gap = (at - self.body.distance) * (1 if self.body.forward else -1)
        return gap if 0 <= gap <= lookahead else None

    def read_environment(self, checkpoint_id):
        zone = self.site.checkpoints[checkpoint_id][1]
        temperature, noise_baseline = self.site.typical_readings[zone]
        if "HIGH_TEMPERATURE" in self.armed:
            # Escenario E5: este punto está caliente; se mantiene así mientras el robot siga midiéndolo.
            self.armed.discard("HIGH_TEMPERATURE")
            self.hot_checkpoint = checkpoint_id
        if self.hot_checkpoint == checkpoint_id:
            temperature = self.site.temperature_limits[zone][1] + 1.1 + self.rng.uniform(-0.3, 0.3)
        else:
            self.hot_checkpoint = None
            temperature += self.rng.uniform(-0.6, 0.6)
        humidity = self.site.humidity_pct + self.rng.uniform(-4, 4)
        if "HUMIDITY" in self.armed:
            # Humedad alta (por ejemplo, una falla del aire acondicionado): de advertencia o crítica.
            self.armed.discard("HUMIDITY")
            humidity = self.site.humidity_high[0] + self.rng.uniform(1, 14)
        noise = noise_baseline + self.rng.uniform(-1.5, 1.5)
        if "NOISE" in self.armed:
            # Un ventilador o un equipo haciendo ruido de más: de advertencia o crítico.
            self.armed.discard("NOISE")
            noise = noise_baseline + self.site.noise_above[0] + self.rng.uniform(1, 15)
        return {
            "checkpoint_id": checkpoint_id,
            "temperature_c": round(temperature, 1),
            "humidity_pct": round(humidity, 1),
            "noise_dba": round(noise, 1),
            "noise_baseline_dba": noise_baseline,
        }

    def take_photo(self, photo_id):
        if self.scene is None:
            return None
        if not any(pid == photo_id for pid, _ in self.photos):
            self.photos.append((photo_id, self.scene.photo(*self.body.pose())))
        return photo_id

    def live_frame(self):
        return None if self.scene is None else self.scene.photo(*self.body.pose(), quality=60)

    def drain_photos(self):
        photos, self.photos = self.photos, []
        return photos

    # ------------------------------------------------------------ energía

    @property
    def battery(self):
        return self._battery

    @property
    def charging(self):
        return self.docked and self._battery < 100

    def battery_drain_rate(self):
        if self.docked:
            return 0.0
        return (MOVING_DRAIN if self.body.speed > 0 else IDLE_DRAIN) * self.drain_multiplier

    # ------------------------------------------------------------ escenarios (consola del servidor)

    def trigger(self, scenario):
        if scenario == "LOW_BATTERY":
            self._battery = min(self._battery, self.site.battery_warning + 1.0)
            self.drain_multiplier = LOW_BATTERY_DRAIN_MULTIPLIER
        elif scenario == "CLEAR_OBSTACLE":
            self.armed.discard("OBSTACLE")
            self.armed.discard("OBSTACLE_PATH")
            self.body.clear_obstacle()
        else:
            self.armed.add(scenario)

    def _place_armed_hazards(self):
        """Los escenarios del lugar aparecen por delante del robot, antes del próximo destino."""
        if not self.armed or not self.body.forward:
            return
        here, gap = self.body.distance, self.body.goal - self.body.distance
        if "OBSTACLE" in self.armed and gap > 2.5 and self._fits_detour(self.body.goal):
            # Escenario E4: una caja sobre el próximo checkpoint, que tapa el punto de inspección.
            self.body.place_obstacle(self.body.goal)
            self.armed.discard("OBSTACLE")
        if "OBSTACLE_PATH" in self.armed:
            at = here + min(3.0, gap / 2)
            if gap > 4.5 and self._fits_detour(at):
                self.body.place_obstacle(at)
                self.armed.discard("OBSTACLE_PATH")
        if "WATER" in self.armed and self.water_at is None:
            at = here + min(3.5, gap / 2)
            if gap > 3.0 and self._fits_detour(at):
                self.water_at = at
                self._show("charco", self.site.path.point_at(at))
                self.armed.discard("WATER")
        if "SMOKE" in self.armed and self.smoke_at is None:
            at = here + min(6.0, gap * 0.8)
            if gap > 2.0 and not self.site.on_stairs(*self.site.path.point_at(at), margin=-1.5):
                self.smoke_at = at
                x, y = self.site.path.point_at(at)
                heading = math.radians(self.site.path.heading_at(at, True))
                # A la derecha del sentido del recorrido; el robot rodea por la izquierda.
                self._show("fuego", (x - math.sin(heading) * FIRE_SIDE_OFFSET, y + math.cos(heading) * FIRE_SIDE_OFFSET))
                self.armed.discard("SMOKE")

    def _fits_detour(self, at):
        """Lo que hay que rodear va en un tramo recto y lejos de la escalera."""
        route, margin = self.site.path, 2.2
        if route.segment_index_at(at - margin) != route.segment_index_at(at + margin):
            return False
        return not self.site.on_stairs(*route.point_at(at), margin=-1.5)

    def _show(self, name, point):
        if self.scene is not None:
            self.scene.show_hazard(name, *(point or (None, None)))
