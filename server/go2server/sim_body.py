"""Cuerpo del robot: dónde está, cómo se mueve y qué tiene adelante.

Es parte del robot simulado (sim_robot.py). La lógica de la ronda decide a qué distancia del
recorrido ir; el cuerpo lo lleva hasta ahí y le informa la distancia en la que realmente está.

- KinematicBody: sin MuJoCo. Avanza a velocidad constante; lo usan los tests de la lógica de la ronda.
- PhysicsBody: el Go2 en MuJoCo con la física corriendo (mj_step). El robot se apoya con contactos y
  gravedad y las patas trotan con un control PD de cada articulación. No hay una política de locomoción
  entrenada: una fuerza horizontal sobre el tronco lo guía por el recorrido. En la escalera el tronco
  se ubica sobre los escalones sin física (bajar escaleras necesita un controlador que no tenemos).
  La posición, la velocidad y el rumbo que se informan salen de la simulación.
"""

import math

import mujoco
import numpy as np

SPEED_MPS = 0.5
ARRIVAL_TOLERANCE = 0.12      # m: a esta distancia del objetivo se considera que llegó
SAFETY_GAP = 0.75             # m desde el centro del robot (la nariz queda a ~0.45 m): frena solo
LIDAR_HEIGHT = 0.18           # m sobre el piso
LIDAR_SPREAD = (-0.16, 0.0, 0.16)  # m: rayos a los costados para cubrir el ancho del robot
BYPASS_OFFSET = 0.8           # m: cuánto se corre hacia un costado para rodear un obstáculo
BYPASS_FULL = 0.9             # m antes y después del obstáculo con el corrimiento completo
BYPASS_RAMP = 1.2             # m para correrse y para volver al recorrido
STAND_HEIGHT = 0.25             # m: altura del tronco parado
STAIRS_MARGIN = 0.7            # m alrededor de la escalera donde el tronco va guiado
OBSTACLE_SIZE = (0.25, 0.25, 0.25)  # media caja (m)


def _bypass(distance, obstacle_at):
    """Corrimiento lateral para rodear el obstáculo: completo al lado del obstáculo (el robot frena
    a menos de un metro, así que el desvío arranca de costado) y suave al volver al recorrido."""
    gap = abs(distance - obstacle_at)
    if gap <= BYPASS_FULL:
        return BYPASS_OFFSET
    u = (gap - BYPASS_FULL) / BYPASS_RAMP
    return BYPASS_OFFSET * math.cos(u * math.pi / 2) ** 2 if u < 1 else 0.0


class KinematicBody:
    """Cuerpo sin física para los tests: avanza a velocidad constante y "ve" el obstáculo por cálculo."""


    def __init__(self, site):
        self.site = site
        self.route = site.path
        self.distance = 0.0
        self.goal = 0.0
        self.speed = 0.0
        self.forward = True
        self.obstacle_at = None
        self.bypassed = set()  # distancias del recorrido que el robot rodea (la caja, un charco)

    def set_goal(self, goal):
        self.goal = goal

    def hold(self):
        self.goal = self.distance
        self.speed = 0.0

    def advance(self, dt):
        delta = self.goal - self.distance
        if abs(delta) < 1e-6:
            self.speed = 0.0
            return
        self.forward = delta > 0
        step = SPEED_MPS * dt
        ahead = self.obstacle_ahead(abs(delta))
        if ahead is not None:
            step = min(step, max(0.0, ahead - SAFETY_GAP))
        self.distance = self.goal if abs(delta) <= step else self.distance + (step if self.forward else -step)
        self.speed = SPEED_MPS if step > 0 else 0.0

    def reset_at_base(self):
        self.distance = self.goal = self.speed = 0.0
        self.clear_obstacle()
        self.bypassed.clear()

    def place_obstacle(self, distance):
        self.obstacle_at = distance

    def clear_obstacle(self):
        self.bypassed.discard(self.obstacle_at)
        self.obstacle_at = None

    def bypass_obstacle(self):
        if self.obstacle_at is not None:
            self.bypass_point(self.obstacle_at)

    def bypass_point(self, distance):
        """Desde ahora, el camino rodea por un costado lo que hay en esa distancia del recorrido."""
        self.bypassed.add(distance)

    def obstacle_ahead(self, lookahead):
        """Distancia al obstáculo sobre el camino, en el sentido de avance, o None si no hay nada."""
        if self.obstacle_at is None or self.obstacle_at in self.bypassed:
            return None
        gap = (self.obstacle_at - self.distance) * (1 if self.forward else -1)
        return gap if 0 <= gap <= lookahead else None

    def position(self):
        return self.route.point_at(self.distance)

    def heading(self):
        return self.route.heading_at(self.distance, self.forward)


class PhysicsBody(KinematicBody):
    """El Go2 de MuJoCo con la física corriendo. Ver la explicación al principio del archivo."""


    # Control PD de las patas (los actuadores del modelo son de torque).
    KP, KD = 45.0, 1.6
    STAND = np.array([0.0, 0.9, -1.8] * 4)
    # Trote: patas diagonales en fase (FL+RR y FR+RL). Orden del modelo: FL, FR, RL, RR.
    PHASE = np.array([0.0, math.pi, math.pi, 0.0])
    GAIT_HZ = 2.2
    # Guía del tronco por el recorrido.
    KP_XY, KD_XY = 260.0, 90.0
    KP_YAW, KD_YAW = 40.0, 6.0
    MAX_FORCE = 140.0
    MAX_TORQUE = 15.0
    LOOKAHEAD = 0.35

    def __init__(self, scene):
        super().__init__(scene.site)
        self.scene = scene
        self.model, self.data = scene.model, scene.data
        self.base_id = self.model.body("base").id
        self.obstacle_body = self.model.body("obstaculo").id
        self.obstacle_mocap = self.model.body_mocapid[self.obstacle_body]
        self.reference = 0.0
        self.phase = 0.0
        self.guided = False
        self.measured_speed = 0.0
        self.yaw_goal = 0.0
        self._hide_obstacle()
        x, y = self.route.point_at(0.0)
        self.yaw_goal = self._route_yaw(0.0, True)
        self._set_pose(x, y, self.yaw_goal)

    # ------------------------------------------------------------ órdenes del motor de la ronda

    def hold(self):
        self.goal = self.distance
        self.reference = self.distance
        # Quieto, mira en el sentido del recorrido (después de un desvío pudo quedar girado):
        # así la cámara apunta a lo que tiene que inspeccionar.
        self.yaw_goal = self._route_yaw(self.distance, self.forward)

    def advance(self, dt):
        """La física avanza en frame(); acá sólo se informa lo medido."""
        self.speed = self.measured_speed if self.measured_speed > 0.05 else 0.0

    def reset_at_base(self):
        super().reset_at_base()
        self.reference = 0.0

    def place_obstacle(self, distance):
        super().place_obstacle(distance)
        x, y = self.route.point_at(distance)
        self.data.mocap_pos[self.obstacle_mocap] = [x, -y, self.site.floor_z(x, y) + OBSTACLE_SIZE[2]]

    def clear_obstacle(self):
        super().clear_obstacle()
        self._hide_obstacle()

    def bypass_point(self, distance):
        super().bypass_point(distance)
        # El desvío arranca desde donde frenó, no desde la referencia, que pudo quedar más cerca del obstáculo.
        self.reference = self.distance

    def _hide_obstacle(self):
        self.data.mocap_pos[self.obstacle_mocap] = [0.0, 0.0, -50.0]

    # ------------------------------------------------------------ simulación

    def frame(self, seconds):
        """Avanza `seconds` de tiempo simulado."""
        moving = abs(self.goal - self.distance) > ARRIVAL_TOLERANCE
        if moving:
            self.forward = self.goal > self.distance
        sign = 1.0 if self.forward else -1.0
        # La referencia avanza a la velocidad de marcha, pero espera al cuerpo y frena ante un obstáculo.
        room = self.obstacle_ahead(abs(self.goal - self.distance) + 0.5) if moving else None
        limit = None if room is None else self.distance + sign * max(0.0, room - SAFETY_GAP)
        if moving and abs(self.reference - self.distance) < 0.5:
            step = SPEED_MPS * seconds
            if limit is not None:
                step = min(step, max(0.0, (limit - self.reference) * sign))
            remaining = abs(self.goal - self.reference)
            self.reference += sign * min(step, remaining)
        target = self.reference + (sign * self.LOOKAHEAD if moving else 0.0)
        if limit is not None:
            target = min(target, limit) if sign > 0 else max(target, limit)
        target = min(max(target, 0.0), self.route.length)
        tx, ty = self._path_point(target)
        if moving:
            self.yaw_goal = self._path_yaw(self.reference, sign)
        # Con el margen negativo la zona se agranda: la física vuelve recién con las cuatro patas fuera de los escalones.
        on_stairs = self.site.on_stairs(tx, ty, margin=-STAIRS_MARGIN) or self.site.on_stairs(*self._path_point(self.reference), margin=-STAIRS_MARGIN)
        if on_stairs:
            self._guided_frame(seconds, moving)
        else:
            self._physics_frame(seconds, moving, tx, ty)
        self._measure()

    def _physics_frame(self, seconds, moving, tx, ty):
        m, d = self.model, self.data
        if self.guided:
            d.qvel[:] = 0.0
            self.guided = False
        steps = max(1, int(round(seconds / m.opt.timestep)))
        for _ in range(steps):
            self.phase = (self.phase + 2 * math.pi * self.GAIT_HZ * m.opt.timestep) % (2 * math.pi) if moving else 0.0
            self._legs(moving)
            pos, vel = d.qpos[0:2], d.qvel[0:2]
            goal = np.array([tx, -ty])
            to_goal = goal - pos
            dist = float(np.linalg.norm(to_goal))
            # Mira hacia el punto al que va; si tiene que girar mucho, primero gira en el lugar.
            heading_goal = math.atan2(to_goal[1], to_goal[0]) if moving and dist > 0.25 else self.yaw_goal
            yaw = self._yaw()
            error = (heading_goal - yaw + math.pi) % (2 * math.pi) - math.pi
            walk = min(1.0, max(0.0, 1.0 - (abs(error) - 0.35) / 0.6))
            desired_vel = to_goal / dist * SPEED_MPS * walk if moving and dist > 1e-6 else np.zeros(2)
            force = self.KP_XY * to_goal * (0.25 * walk if moving else 1.0) + self.KD_XY * (desired_vel - vel)
            norm = np.linalg.norm(force)
            if norm > self.MAX_FORCE:
                force *= self.MAX_FORCE / norm
            torque = float(np.clip(self.KP_YAW * error - self.KD_YAW * d.qvel[5], -self.MAX_TORQUE, self.MAX_TORQUE))
            d.xfrc_applied[self.base_id] = [force[0], force[1], 0.0, 0.0, 0.0, torque]
            mujoco.mj_step(m, d)
        d.xfrc_applied[self.base_id] = 0.0

    def _guided_frame(self, seconds, moving):
        """Escalera: el tronco se ubica sobre los escalones, sin física, mientras las patas siguen el trote."""
        self.guided = True
        steps = max(1, int(round(seconds / self.model.opt.timestep)))
        if moving:
            self.phase = (self.phase + 2 * math.pi * self.GAIT_HZ * self.model.opt.timestep * steps) % (2 * math.pi)
        x, y = self._path_point(self.reference)
        self._set_pose(x, y, self.yaw_goal, legs=self._gait_targets(moving))
        self.data.time += seconds

    def _legs(self, moving):
        d = self.data
        q, qd = d.qpos[7:19], d.qvel[6:18]
        torque = self.KP * (self._gait_targets(moving) - q) - self.KD * qd
        d.ctrl[:] = np.clip(torque, self.model.actuator_ctrlrange[:, 0], self.model.actuator_ctrlrange[:, 1])

    def _gait_targets(self, moving):
        if not moving:
            return self.STAND.copy()
        targets = self.STAND.copy()
        for leg in range(4):
            s = math.sin(self.phase + self.PHASE[leg])
            targets[leg * 3 + 1] += 0.18 * math.cos(self.phase + self.PHASE[leg])
            targets[leg * 3 + 2] -= 0.55 * max(0.0, s)  # en el vuelo dobla la rodilla y levanta el pie
        return targets

    def _set_pose(self, x, y, yaw, legs=None):
        d = self.data
        d.qpos[0:3] = [x, -y, self.site.floor_z(x, y) + STAND_HEIGHT]
        d.qpos[3:7] = [math.cos(yaw / 2), 0.0, 0.0, math.sin(yaw / 2)]
        d.qpos[7:19] = self.STAND if legs is None else legs
        d.qvel[:] = 0.0
        mujoco.mj_forward(self.model, d)

    def _measure(self):
        x, y = float(self.data.qpos[0]), -float(self.data.qpos[1])
        if self.guided:
            self.distance = self.reference
            self.measured_speed = SPEED_MPS if abs(self.goal - self.reference) > ARRIVAL_TOLERANCE else 0.0
        else:
            measured = self._project(x, y)
            self.distance = self.goal if abs(self.goal - measured) <= ARRIVAL_TOLERANCE else measured
            self.measured_speed = 0.8 * self.measured_speed + 0.2 * float(np.linalg.norm(self.data.qvel[0:2]))

    def _project(self, x, y):
        """Distancia sobre el recorrido del punto más cercano, buscando cerca de la referencia
        (el recorrido pasa dos veces por zonas parecidas: una proyección global podría saltar)."""
        best, best_gap = self.reference, float("inf")
        lo, hi = max(0.0, self.reference - 2.0), min(self.route.length, self.reference + 2.0)
        s = lo
        while s <= hi:
            px, py = self._path_point(s)
            gap = math.hypot(px - x, py - y)
            if gap < best_gap:
                best, best_gap = s, gap
            s += 0.05
        return best

    # ------------------------------------------------------------ sensores

    def obstacle_ahead(self, lookahead):
        """LiDAR: rayos a lo largo del camino que el robot tiene por delante. Devuelve la distancia
        (sobre el camino) al primer objeto que lo bloquea, o None. Lo que esté al costado del camino
        (racks, equipos) no cuenta."""
        if self.guided:
            return None
        sign = 1.0 if self.forward else -1.0
        start = self.distance
        travelled, s, step = 0.0, start, 0.25
        while travelled < lookahead:
            nxt = min(max(s + sign * step, 0.0), self.route.length)
            if nxt == s:
                return None
            a, b = self._path_point(s), self._path_point(nxt)
            if self.site.on_stairs(*b, margin=-STAIRS_MARGIN):  # los escalones no son un obstáculo
                return None
            hit = self._ray_between(a, b)
            if hit is not None:
                return travelled + hit
            travelled += abs(nxt - s)
            s = nxt
        return None

    def _ray_between(self, a, b):
        (ax, ay), (bx, by) = a, b
        length = math.hypot(bx - ax, by - ay)
        if length < 1e-6:
            return None
        ux, uy = (bx - ax) / length, (by - ay) / length
        z = self.site.floor_z(ax, ay) + LIDAR_HEIGHT
        geomgroup = np.array([1, 1, 0, 0, 0, 0], dtype=np.uint8)  # escena y obstáculo; el robot está en 2 y 3
        geom = np.array([-1], dtype=np.int32)
        best = None
        for side in LIDAR_SPREAD:
            px, py = ax - uy * side, ay + ux * side
            hit = mujoco.mj_ray(self.model, self.data, np.array([px, -py, z]), np.array([ux, -uy, 0.0]),
                                geomgroup, 1, -1, geom)
            if 0 <= hit <= length and (best is None or hit < best):
                best = hit
        return best

    # ------------------------------------------------------------ geometría

    def _path_point(self, distance):
        x, y = self.route.point_at(distance)
        if self.bypassed:
            offset = max(_bypass(distance, at) for at in self.bypassed)
            if offset:
                heading = math.radians(self.route.heading_at(distance, True))
                # Hacia la izquierda del sentido del recorrido (en el plano, y crece hacia abajo).
                x, y = x + math.sin(heading) * offset, y - math.cos(heading) * offset
        return x, y

    def _route_yaw(self, distance, forward):
        return -math.radians(self.route.heading_at(distance, forward))

    def _path_yaw(self, distance, sign):
        """Rumbo (yaw de MuJoCo) del camino que sigue el robot, desvíos incluidos: mira hacia donde camina."""
        ax, ay = self._path_point(distance)
        bx, by = self._path_point(min(max(distance + sign * 0.4, 0.0), self.route.length))
        if math.hypot(bx - ax, by - ay) < 1e-3:
            return self.yaw_goal
        return math.atan2(-(by - ay), bx - ax)

    def _yaw(self):
        w, x, y, z = self.data.qpos[3:7]
        return math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z))

    def position(self):
        return float(self.data.qpos[0]), -float(self.data.qpos[1])

    def heading(self):
        return round(math.degrees(-self._yaw())) % 360

    def pose(self):
        """Posición del tronco (MuJoCo) y matriz de orientación, para la cámara."""
        return self.data.xpos[self.base_id].copy(), self.data.xmat[self.base_id].reshape(3, 3).copy()
