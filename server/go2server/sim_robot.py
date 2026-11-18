"""El robot simulado: todo lo que en un Go2 real sería hardware.

- Movimiento, posición y LiDAR: el cuerpo (sim_body.py). PhysicsBody es el Go2 en MuJoCo con la física
  corriendo; KinematicBody avanza por cálculo y lo usan los tests de la lógica de la ronda.
- Cámara frontal: imágenes generadas por MuJoCo (sim_scene.py). Sin escena no hay cámara.
- Batería: un modelo de carga y descarga.
- Sensores del ambiente: lecturas inventadas a partir de los valores típicos del archivo de sitio.
- Escenarios de prueba (consola del servidor): batería baja, una caja en el pasillo, temperatura alta.
  Son cosas que le pasan al robot o al lugar, por eso viven acá y no en la lógica de la ronda.
"""

import random

from .robot_interface import RobotInterface

CHARGE_RATE = 0.25        # %/s en la base
MOVING_DRAIN = 0.012      # %/s caminando
IDLE_DRAIN = 0.004        # %/s quieto fuera de la base
LOW_BATTERY_DRAIN_MULTIPLIER = 15.0
INITIAL_BATTERY = 92.0


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
        self.obstacle_armed = False
        self.high_temperature_armed = False
        self.hot_checkpoint = None

    # ------------------------------------------------------------ movimiento

    def go_to(self, distance):
        self.docked = False
        self.body.set_goal(distance)

    def hold(self):
        self.body.hold()

    def bypass_obstacle(self):
        self.body.bypass_obstacle()

    def dock(self):
        # Entre rondas el personal retira lo que haya quedado en el pasillo.
        self.body.reset_at_base()
        self.docked = True

    def update(self, dt):
        self._place_armed_obstacle()
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

    def read_environment(self, checkpoint_id):
        zone = self.site.checkpoints[checkpoint_id][1]
        temperature, noise_baseline = self.site.typical_readings[zone]
        if self.high_temperature_armed:
            # Escenario E5: este punto está caliente; se mantiene así mientras el robot siga midiéndolo.
            self.high_temperature_armed = False
            self.hot_checkpoint = checkpoint_id
        if self.hot_checkpoint == checkpoint_id:
            temperature = self.site.temperature_limits[zone][1] + 1.1 + self.rng.uniform(-0.3, 0.3)
        else:
            self.hot_checkpoint = None
            temperature += self.rng.uniform(-0.6, 0.6)
        return {
            "checkpoint_id": checkpoint_id,
            "temperature_c": round(temperature, 1),
            "humidity_pct": round(self.site.humidity_pct + self.rng.uniform(-4, 4), 1),
            "noise_dba": round(noise_baseline + self.rng.uniform(-1.5, 1.5), 1),
            "noise_baseline_dba": noise_baseline,
        }

    def take_photo(self, photo_id):
        if self.scene is None:
            return None
        if not any(pid == photo_id for pid, _ in self.photos):
            self.photos.append((photo_id, self.scene.photo(*self.body.pose())))
        return photo_id

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
        elif scenario == "OBSTACLE":
            self.obstacle_armed = True
        elif scenario == "CLEAR_OBSTACLE":
            self.obstacle_armed = False
            self.body.clear_obstacle()
        elif scenario == "HIGH_TEMPERATURE":
            self.high_temperature_armed = True

    def _place_armed_obstacle(self):
        """Escenario E4: aparece una caja en el pasillo, a mitad de camino del próximo destino."""
        if not self.obstacle_armed or not self.body.forward:
            return
        gap = self.body.goal - self.body.distance
        at = self.body.distance + min(3.0, gap / 2)
        if gap > 2.5 and self._fits_obstacle(at):
            self.body.place_obstacle(at)
            self.obstacle_armed = False

    def _fits_obstacle(self, at):
        """La caja va en un tramo recto y lejos de la escalera, donde se la puede rodear."""
        route, margin = self.site.path, 2.2
        if route.segment_index_at(at - margin) != route.segment_index_at(at + margin):
            return False
        return not self.site.on_stairs(*route.point_at(at), margin=-1.5)
