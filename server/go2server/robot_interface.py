"""Lo que la lógica de la ronda le pide y le pregunta al robot.

El servidor tiene dos partes:

- La lógica de la ronda (mission_control.py y server.py): misiones, reglas, alertas, validación de órdenes y el
  contrato JSON con la app. Es software real: con un Go2 de verdad seguiría siendo el mismo.
- El robot (esta interfaz): moverse, saber dónde está, el LiDAR, la cámara, los sensores del ambiente y
  la batería. Hoy lo da SimulatedRobot (sim_robot.py), con MuJoCo. Con un Go2 real lo daría
  UnitreeRobot (unitree_robot.py), con el SDK de Unitree.

La lógica de la ronda sólo usa lo que está acá. Pasar al robot real es cambiar la implementación.
La posición se expresa como distancia recorrida sobre el recorrido del archivo de sitio.
"""


class RobotInterface:
    """Contrato con el robot. Cada implementación completa todos los métodos."""

    # ------------------------------------------------------------ movimiento

    def go_to(self, distance):
        """Caminar por el recorrido hasta esa distancia (hacia adelante o hacia atrás)."""
        raise NotImplementedError

    def hold(self):
        """Frenar y quedarse quieto donde está."""
        raise NotImplementedError

    def bypass_obstacle(self):
        """Rodear el obstáculo que tiene adelante y seguir por el recorrido."""
        raise NotImplementedError

    def dock(self):
        """Conectarse a la base de carga (al llegar a la base)."""
        raise NotImplementedError

    def update(self, dt):
        """Pasaron `dt` segundos. Un robot real no necesita hacer nada; la simulación avanza su estado."""
        raise NotImplementedError

    # ------------------------------------------------------------ estado

    @property
    def distance(self):
        """Dónde está, como distancia sobre el recorrido (m)."""
        raise NotImplementedError

    @property
    def speed(self):
        """Velocidad medida (m/s)."""
        raise NotImplementedError

    @property
    def forward(self):
        """True si avanza en el sentido del recorrido; False si vuelve hacia la base."""
        raise NotImplementedError

    def position(self):
        """(x, y) en el plano del sitio, en metros."""
        raise NotImplementedError

    def heading(self):
        """Rumbo en grados del plano (0 = este, 90 = sur)."""
        raise NotImplementedError

    def location_confidence(self):
        """Confianza de la ubicación, de 0 a 1."""
        raise NotImplementedError

    # ------------------------------------------------------------ sensores

    def obstacle_ahead(self, lookahead):
        """LiDAR: distancia (m, sobre el camino) al primer objeto que bloquea el camino por delante,
        o None si está libre en esos metros."""
        raise NotImplementedError

    def read_environment(self, checkpoint_id):
        """Módulo de sensores: temperatura, humedad y ruido medidos ahora, con el formato del contrato."""
        raise NotImplementedError

    def take_photo(self, photo_id):
        """Saca una foto con la cámara frontal y la guarda con ese id. Devuelve el id, o None si no hay cámara."""
        raise NotImplementedError

    def live_frame(self):
        """Cámara en vivo: un JPEG de lo que ve la cámara frontal ahora (no se guarda), o None si no hay cámara."""
        raise NotImplementedError

    def drain_photos(self):
        """Las fotos sacadas desde la última vez: lista de (id, JPEG)."""
        raise NotImplementedError

    # ------------------------------------------------------------ energía

    @property
    def battery(self):
        """Carga de la batería (%)."""
        raise NotImplementedError

    @property
    def charging(self):
        """True si está cargando en la base."""
        raise NotImplementedError

    def battery_drain_rate(self):
        """Consumo actual (% por segundo). La regla R5 lo usa para estimar si le alcanza la batería."""
        raise NotImplementedError
