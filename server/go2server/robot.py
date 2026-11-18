"""Motor de la ronda del robot: mismo comportamiento que RobotSimulator.kt en la app.

Es sincrónico: tick() avanza el tiempo simulado y handle() decide si acepta una orden.
Las alertas y los eventos usan directamente el formato del contrato JSON.

El motor decide a qué distancia del recorrido tiene que ir el robot; el cuerpo (body.py) lo lleva y le
informa dónde está y qué tiene adelante. Con el cuerpo de MuJoCo, la posición, la velocidad, el
obstáculo y las fotos salen de la simulación.
"""

import random
import time

from .body import SPEED_MPS, KinematicBody


INSPECTION_SECONDS = 4.0
OBSTACLE_GAP = 0.9          # m entre el centro del robot y el obstáculo al detenerse
LIDAR_RANGE = 4.0           # m de camino que revisa por delante
CHARGE_RATE = 0.25
MOVING_DRAIN = 0.012
IDLE_DRAIN = 0.004
LOW_BATTERY_DRAIN_MULTIPLIER = 15.0
WATCH_READINGS = 3
WATCH_INTERVAL_SECONDS = 10.0
MAX_SECONDS_WITHOUT_LINK = 300.0
MAX_ALERTS = 30
EPSILON = 1e-6


def now_ms():
    return int(time.time() * 1000)


class Mission:
    def __init__(self, mission_id, mission_type, targets):
        self.id = mission_id
        self.type = mission_type
        self.targets = targets  # lista de (checkpoint_id, distancia en el recorrido)
        self.next_index = 0
        self.inspected = 0
        self.skipped = 0
        self.elapsed = 0.0
        self.cancelled = False
        self.low_battery = False
        self.last_checkpoint = None

    @property
    def current(self):
        return self.targets[self.next_index] if self.next_index < len(self.targets) else None

    @property
    def all_done(self):
        return self.next_index >= len(self.targets)


class Robot:
    def __init__(self, robot_id, site, rng=None, body=None):
        self.robot_id = robot_id
        self.site = site
        self.rng = rng or random.Random()
        self.route = site.path
        self.body = body or KinematicBody(site)
        self.state = "AT_BASE"
        self.battery = 92.0
        self.speed = 0.0
        self.mission = None
        self.alerts = []
        self.last_reading = None
        self.sequence = 0
        self.next_alert = 1
        self.events = []
        self.inspection_left = 0.0
        self.obstacle_armed = False
        self.high_temperature_armed = False
        self.drain_multiplier = 1.0
        self.low_battery_alerted = False
        self.critical_battery_alerted = False
        self.watch_left = 0
        self.watch_timer = 0.0
        self.stopped_while_returning = False
        self.link_available = True
        self.seconds_without_link = 0.0

    @property
    def distance(self):
        """Dónde está el robot sobre el recorrido, según su cuerpo."""
        return self.body.distance

    @property
    def forward(self):
        return self.body.forward

    # ------------------------------------------------------------ tiempo

    def tick(self, dt):
        if self.mission and self.state != "AT_BASE":
            self.mission.elapsed += dt
        self._update_link(dt)
        if self.state == "AT_BASE":
            self._charge(dt)
        elif self.state == "ON_MISSION":
            self._advance_mission(dt)
        elif self.state == "RETURNING":
            self._move_towards(0.0, dt)
            if self.distance <= 0.0:
                self._arrive_at_base()
        else:
            self._halt()
            if self.state == "AWAITING_DECISION":
                self._tick_watch(dt)
        self._drain_battery(dt)
        self._check_battery()

    def set_link_available(self, available):
        self.link_available = available
        if available:
            self.seconds_without_link = 0.0

    def _update_link(self, dt):
        if self.link_available:
            return
        self.seconds_without_link += dt
        if self.seconds_without_link >= MAX_SECONDS_WITHOUT_LINK and self.mission and self.state not in ("RETURNING", "AT_BASE"):
            self._resolve_decisions()
            self.state = "RETURNING"

    def _charge(self, dt):
        self._halt()
        self.drain_multiplier = 1.0
        self.battery = min(100.0, self.battery + CHARGE_RATE * dt)
        if self.battery > self.site.battery_warning + 5:
            self.low_battery_alerted = False
        if self.battery > self.site.battery_critical + 5:
            self.critical_battery_alerted = False

    def _advance_mission(self, dt):
        m = self.mission
        if m is None:
            return
        if self.inspection_left > 0:
            self._halt()
            self.inspection_left -= dt
            if self.inspection_left <= 0:
                self._complete_inspection(m)
            return
        target = m.current
        if target is None:
            self.state = "RETURNING"
            return
        checkpoint_id, target_distance = target
        if self._lacks_battery_for_basement(m, checkpoint_id):
            return
        if self.obstacle_armed:
            # Escenario E4: aparece una caja en el pasillo, entre el robot y el próximo checkpoint.
            at = self.distance + min(3.0, (target_distance - self.distance) / 2)
            if target_distance - self.distance > 2.5 and self._fits_obstacle(at):
                self.body.place_obstacle(at)
                self.obstacle_armed = False
        # LiDAR: si algo bloquea el camino antes del checkpoint, frena antes y pide una decisión.
        ahead = self.body.obstacle_ahead(LIDAR_RANGE)
        blocked = ahead is not None and self.distance + ahead - OBSTACLE_GAP < target_distance
        if blocked and ahead - OBSTACLE_GAP <= 0.15:
            self._halt()
            self.state = "AWAITING_DECISION"
            self._raise_alert("WARNING", "OBSTACLE", checkpoint_id, None, None, ["SKIP", "RETRY"],
                              photo=f"{m.id}_{checkpoint_id}_obstaculo")
            return
        stop_at = self.distance + ahead - OBSTACLE_GAP if blocked else target_distance
        self._move_towards(stop_at, dt)
        if self.distance >= target_distance - EPSILON:
            self._halt()
            self.inspection_left = INSPECTION_SECONDS

    def _move_towards(self, target, dt):
        """Le pide al cuerpo que vaya hasta `target`; la velocidad es la que mide el cuerpo."""
        self.body.set_goal(target)
        self.body.advance(dt)
        self.speed = self.body.speed

    def _halt(self):
        self.speed = 0.0
        self.body.hold()

    def _arrive_at_base(self):
        self.speed = 0.0
        self.inspection_left = 0.0
        # Entre rondas el personal retira lo que haya quedado en el pasillo.
        self.body.reset_at_base()
        self.state = "AT_BASE"
        self._resolve_decisions()
        m = self.mission
        if m is not None:
            if m.cancelled:
                result = "CANCELLED"
            elif m.all_done:
                result = "COMPLETED"
            elif m.low_battery:
                result = "INTERRUPTED_LOW_BATTERY"
            else:
                result = "CANCELLED"
            self._emit({"kind": "MISSION_FINISHED", "mission_id": m.id, "result": result,
                        "completed": m.inspected, "total": len(m.targets)})
        self.mission = None

    # ------------------------------------------------------------ inspección

    def _complete_inspection(self, m):
        target = m.current
        if target is None:
            return
        checkpoint_id = target[0]
        reading = self._measure(checkpoint_id)
        self.last_reading = reading
        issues = self._evaluate(checkpoint_id, reading)
        status = "OK" if not issues else "ANOMALY"
        m.inspected += 1
        m.last_checkpoint = checkpoint_id
        photo = f"{m.id}_{checkpoint_id}"
        self._emit({"kind": "CHECKPOINT_RESULT", "mission_id": m.id, "checkpoint_id": checkpoint_id,
                    "status": status, "reading": reading, "photo_id": self._photo(photo)})
        waits = False
        for code, severity, value, threshold in issues:
            needs_decision = code == "TEMPERATURE_HIGH" and severity == "CRITICAL"
            waits = waits or needs_decision
            self._raise_alert(severity, code, checkpoint_id, value, threshold,
                              ["CONTINUE", "WATCH"] if needs_decision else [], photo=photo)
        m.next_index += 1
        if waits:
            self.state = "AWAITING_DECISION"
        elif not self.link_available:
            self.state = "PAUSED"  # regla R3: sin enlace, espera en un lugar seguro
        elif m.all_done:
            self.state = "RETURNING"
        else:
            self.state = "ON_MISSION"

    def _measure(self, checkpoint_id):
        zone = self.site.checkpoints[checkpoint_id][1]
        temperature, noise_baseline = self.site.typical_readings[zone]
        if self.high_temperature_armed:
            self.high_temperature_armed = False
            temperature = self.site.temperature_limits[zone][1] + 1.1
        else:
            temperature += self.rng.uniform(-0.6, 0.6)
        return {
            "checkpoint_id": checkpoint_id,
            "temperature_c": round(temperature, 1),
            "humidity_pct": round(self.site.humidity_pct + self.rng.uniform(-4, 4), 1),
            "noise_dba": round(noise_baseline + self.rng.uniform(-1.5, 1.5), 1),
            "noise_baseline_dba": noise_baseline,
        }

    def _evaluate(self, checkpoint_id, reading):
        zone = self.site.checkpoints[checkpoint_id][1]
        warning, critical = self.site.temperature_limits[zone]
        t = reading["temperature_c"]
        if t >= critical:
            return [("TEMPERATURE_HIGH", "CRITICAL", t, critical)]
        if t >= warning:
            return [("TEMPERATURE_HIGH", "WARNING", t, warning)]
        return []

    def _tick_watch(self, dt):
        if self.watch_left <= 0 or self.last_reading is None:
            return
        self.watch_timer -= dt
        if self.watch_timer > 0:
            return
        reading = dict(self.last_reading)
        reading["temperature_c"] = round(reading["temperature_c"] + self.rng.uniform(-0.3, 0.3), 1)
        self.last_reading = reading
        self.watch_left -= 1
        self.watch_timer = WATCH_INTERVAL_SECONDS

    # ------------------------------------------------------------ batería

    def _lacks_battery_for_basement(self, m, checkpoint_id):
        """Regla R5 (igual que en la app): no baja al subsuelo si la batería no alcanza para volver."""
        site = self.site
        if site.level_at(site.checkpoints[checkpoint_id][0][1]) != "BASEMENT":
            return False
        x, y = self.route.point_at(self.distance)
        if site.level_at(y) != "GROUND_FLOOR" or site.on_stairs(x, y, margin=0.0):
            return False
        below = [t for t in m.targets[m.next_index:] if site.level_at(site.checkpoints[t[0]][0][1]) == "BASEMENT"]
        farthest = max(t[1] for t in below)
        meters = (farthest - self.distance) + farthest
        drain = (meters / SPEED_MPS * MOVING_DRAIN + len(below) * INSPECTION_SECONDS * IDLE_DRAIN) * self.drain_multiplier
        needed = site.battery_critical + drain
        if self.battery >= needed:
            return False
        m.low_battery = True
        self._raise_alert("WARNING", "BATTERY_INSUFFICIENT_FOR_BASEMENT", checkpoint_id, self.battery, round(needed, 1))
        self._halt()
        self.state = "RETURNING"
        return True

    def _drain_battery(self, dt):
        if self.state == "AT_BASE":
            return
        rate = MOVING_DRAIN if self.speed > 0 else IDLE_DRAIN
        self.battery = max(0.0, self.battery - rate * dt * self.drain_multiplier)

    def _check_battery(self):
        if self.state == "AT_BASE":
            return
        if self.battery < self.site.battery_warning and not self.low_battery_alerted:
            self.low_battery_alerted = True
            if self.mission:
                self.mission.low_battery = True
            self._raise_alert("WARNING", "BATTERY_LOW", None, self.battery, float(self.site.battery_warning))
        if self.battery < self.site.battery_critical and not self.critical_battery_alerted:
            self.critical_battery_alerted = True
            if self.mission:
                self.mission.low_battery = True
            self._raise_alert("CRITICAL", "BATTERY_CRITICAL", None, self.battery, float(self.site.battery_critical))
            if self.state != "RETURNING":
                self._resolve_decisions()
                self.inspection_left = 0.0
                self.state = "RETURNING"

    # ------------------------------------------------------------ órdenes

    def handle(self, name, params):
        """Devuelve (aceptada, motivo de rechazo)."""
        params = params or {}
        if name == "START_MISSION":
            return self._start(params.get("mission"), params.get("checkpoint_id"))
        if name == "PAUSE":
            if self.state != "ON_MISSION":
                return False, "INVALID_STATE"
            if self._on_stairs():
                return False, "ON_STAIRS"
            self.state = "PAUSED"
            self._halt()
            return True, None
        if name == "RESUME":
            if self.state not in ("PAUSED", "STOPPED"):
                return False, "INVALID_STATE"
            self.state = self._state_after_resume()
            return True, None
        if name == "CANCEL":
            if self.mission is None:
                return False, "NO_ACTIVE_MISSION"
            if self.state == "RETURNING":
                return False, "INVALID_STATE"
            self.mission.cancelled = True
            self._go_back()
            return True, None
        if name == "RETURN_TO_BASE":
            if self.state in ("AT_BASE", "RETURNING"):
                return False, "INVALID_STATE"
            self._go_back()
            return True, None
        if name == "OPERATIONAL_STOP":
            if self.state in ("AT_BASE", "STOPPED"):
                return False, "INVALID_STATE"
            self.stopped_while_returning = self.state == "RETURNING"
            self.state = "STOPPED"
            self._halt()
            return True, None
        if name == "ACK_ALERT":
            alert = self._alert(params.get("alert_id"))
            if alert is None:
                return False, "ALERT_NOT_FOUND"
            alert["acknowledged"] = True
            return True, None
        if name == "INCIDENT_DECISION":
            return self._decide(params.get("alert_id"), params.get("decision"))
        return False, "INVALID_STATE"

    def _start(self, mission_type, checkpoint_id):
        if self.state != "AT_BASE":
            return False, "NOT_AT_BASE"
        if self.battery < self.site.battery_min_to_start:
            return False, "LOW_BATTERY"
        ids = [checkpoint_id] if mission_type == "SPOT_CHECK" and checkpoint_id else self.site.missions.get(mission_type, [])
        targets = sorted(
            ((cp, self.route.distance_of(self.site.checkpoints[cp][0])) for cp in ids if cp in self.site.checkpoints),
            key=lambda t: t[1],
        )
        if not targets:
            return False, "INVALID_STATE"
        self.mission = Mission(f"m-{now_ms()}", mission_type, targets)
        self.inspection_left = 0.0
        self.state = "ON_MISSION"
        self._emit({"kind": "MISSION_STARTED", "mission_id": self.mission.id,
                    "mission_type": mission_type, "total": len(targets)})
        return True, None

    def _decide(self, alert_id, decision):
        m = self.mission
        if m is None:
            return False, "NO_ACTIVE_MISSION"
        alert = self._alert(alert_id)
        if alert is None or not alert["requires_decision"]:
            return False, "ALERT_NOT_FOUND"
        if self.state != "AWAITING_DECISION" or decision not in alert["options"]:
            return False, "INVALID_STATE"
        if decision == "RETRY":
            # Vuelve a mirar con el LiDAR: si el camino sigue bloqueado, no puede seguir.
            if self.body.obstacle_ahead(LIDAR_RANGE) is not None:
                return False, "OBSTACLE_PERSISTS"
            self._resolve(alert)
            self.state = "ON_MISSION"
            return True, None
        if decision == "SKIP":
            target = m.current
            if target is not None:
                m.skipped += 1
                m.last_checkpoint = target[0]
                self._emit({"kind": "CHECKPOINT_RESULT", "mission_id": m.id, "checkpoint_id": target[0],
                            "status": "SKIPPED", "reading": None, "photo_id": None})
                # Rodea el obstáculo (el resto de la ronda lo esquiva) y sigue con el próximo checkpoint.
                self.body.bypass_obstacle()
                m.next_index += 1
            self._resolve(alert)
            self.state = "RETURNING" if m.all_done else "ON_MISSION"
            return True, None
        if decision == "CONTINUE":
            self.watch_left = 0
            self._resolve(alert)
            self.state = "RETURNING" if m.all_done else "ON_MISSION"
            return True, None
        if decision == "WATCH":
            self.watch_left = WATCH_READINGS
            self.watch_timer = WATCH_INTERVAL_SECONDS
            return True, None
        return False, "INVALID_STATE"

    def _go_back(self):
        self._resolve_decisions()
        self.inspection_left = 0.0
        self.stopped_while_returning = False
        self.state = "RETURNING"

    def _state_after_resume(self):
        m = self.mission
        if self.stopped_while_returning or m is None:
            state = "RETURNING"
        elif any(a["requires_decision"] for a in self.alerts):
            state = "AWAITING_DECISION"
        elif m.all_done:
            state = "RETURNING"
        else:
            state = "ON_MISSION"
        self.stopped_while_returning = False
        return state

    def _fits_obstacle(self, at):
        """El obstáculo del escenario va en un tramo recto y lejos de la escalera, donde se puede rodear."""
        route, margin = self.route, 2.2
        if route.segment_index_at(at - margin) != route.segment_index_at(at + margin):
            return False
        return not self.site.on_stairs(*route.point_at(at), margin=-1.5)

    def _on_stairs(self):
        return self.site.on_stairs(*self.route.point_at(self.distance))

    # ------------------------------------------------------------ escenarios (consola del servidor)

    def trigger(self, scenario):
        if scenario == "LOW_BATTERY":
            self.battery = min(self.battery, self.site.battery_warning + 1.0)
            self.drain_multiplier = LOW_BATTERY_DRAIN_MULTIPLIER
        elif scenario == "OBSTACLE":
            self.obstacle_armed = True
        elif scenario == "CLEAR_OBSTACLE":
            self.obstacle_armed = False
            self.body.clear_obstacle()
        elif scenario == "HIGH_TEMPERATURE":
            self.high_temperature_armed = True

    # ------------------------------------------------------------ salida

    def plan_position(self):
        if self.state == "AT_BASE" and not self.body.has_camera:
            return self.site.base
        return self.body.position()

    def heading(self):
        return self.body.heading()

    def drain_events(self):
        events, self.events = self.events, []
        return events

    def telemetry(self, position, link):
        """Arma el mensaje de telemetría. La posición la pasa quien la lee del cuerpo."""
        self.sequence += 1
        m = self.mission
        decision = next((a for a in self.alerts if a["requires_decision"]), None)
        current = (decision or {}).get("checkpoint_id") or (m.current[0] if m and m.current else None) or (m.last_checkpoint if m else None)
        mission = None
        if m is not None:
            ids = [t[0] for t in m.targets]
            mission = {
                "mission_id": m.id, "type": m.type, "current_cp": current,
                "current_position": ids.index(current) + 1 if current in ids else len(ids),
                "completed": m.inspected + m.skipped, "total": len(ids), "elapsed_s": int(m.elapsed),
            }
        x, y = position
        return {
            "type": "telemetry",
            "robot_id": self.robot_id,
            "seq": self.sequence,
            "timestamp": now_ms(),
            "connection": link,
            "battery": {"percent": round(self.battery), "charging": self.state == "AT_BASE" and self.battery < 100,
                        "est_minutes": round(self.battery / (MOVING_DRAIN * 60))},
            "state": self.state,
            "mission": mission,
            "location": {
                "level": self.site.level_at(y),
                "from_cp": m.last_checkpoint if m else None,
                "to_cp": None if self.state == "RETURNING" or m is None or m.current is None else m.current[0],
                "x": round(x, 2), "y": round(y, 2), "heading_deg": self.heading(),
                "confidence": 0.81 if self._on_stairs() else round(0.91 + self.rng.uniform(0, 0.05), 2),
            },
            "motion": {"speed_mps": round(self.speed, 2), "moving": self.speed > 0},
            "active_alerts": sum(1 for a in self.alerts if not a["acknowledged"]),
            "environment": self.last_reading,
        }

    # ------------------------------------------------------------ auxiliares

    def _photo(self, photo_id):
        """Id de la foto de la cámara frontal, o None si el cuerpo no tiene cámara (los tests)."""
        return photo_id if self.body.has_camera else None

    def _raise_alert(self, severity, code, checkpoint_id, measured, threshold, options=None, photo=None):
        options = options or []
        alert = {
            "id": f"a-{self.next_alert}", "timestamp": now_ms(), "severity": severity, "code": code,
            "checkpoint_id": checkpoint_id,
            "measured": round(measured, 1) if measured is not None else None,
            "threshold": threshold, "requires_decision": bool(options), "options": options,
            "acknowledged": False, "photo_id": self._photo(photo) if photo else None,
        }
        self.next_alert += 1
        self.alerts.insert(0, alert)
        del self.alerts[MAX_ALERTS:]
        self._emit({"kind": "ALERT", "mission_id": self.mission.id if self.mission else None, "alert": dict(alert)})

    def _alert(self, alert_id):
        return next((a for a in self.alerts if a["id"] == alert_id), None)

    def _resolve(self, alert):
        alert["requires_decision"] = False
        alert["acknowledged"] = True

    def _resolve_decisions(self):
        for alert in self.alerts:
            if alert["requires_decision"]:
                self._resolve(alert)
        self.watch_left = 0

    def _emit(self, event):
        event["type"] = "event"
        event["timestamp"] = now_ms()
        self.events.append(event)
