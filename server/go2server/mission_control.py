"""Lógica de la ronda: mismo comportamiento que RobotSimulator.kt en la app.

Es sincrónico: tick() deja pasar el tiempo y handle() decide si acepta una orden. Las alertas y los
eventos usan directamente el formato del contrato JSON.

No simula nada: todo lo que depende del robot (moverse, dónde está, el LiDAR, la cámara, los sensores
del ambiente y la batería) se lo pide a `robot` (ver robot_interface.py). Con el robot simulado o con un
Go2 real, este archivo es el mismo.
"""

import time

INSPECTION_SECONDS = 4.0
OBSTACLE_GAP = 0.9          # m entre el centro del robot y el obstáculo al detenerse
LIDAR_RANGE = 4.0           # m de camino que revisa por delante
CAMERA_RANGE = 3.5          # m de camino en los que la cámara distingue agua en el piso
WATER_GAP = 1.3             # m entre el centro del robot y el agua al detenerse
SMOKE_RANGE = 4.5           # m a los que el sensor de humo detecta un foco por delante
# Consumo de referencia del robot, para estimar cuánto le dura la batería (regla R5 y autonomía).
NOMINAL_SPEED_MPS = 0.5
NOMINAL_MOVING_DRAIN = 0.012    # %/s caminando
NOMINAL_IDLE_DRAIN = 0.004      # %/s quieto
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
        self.cancelled = False      # el operador la anuló
        self.interrupted = False    # volvió sin terminar: el operador, humo o sin enlace
        self.low_battery = False
        self.last_checkpoint = None

    @property
    def current(self):
        return self.targets[self.next_index] if self.next_index < len(self.targets) else None

    @property
    def all_done(self):
        return self.next_index >= len(self.targets)


class MissionControl:
    def __init__(self, robot_id, site, robot):
        self.robot_id = robot_id
        self.site = site
        self.route = site.path
        self.robot = robot
        self.state = "AT_BASE"
        self.mission = None
        self.alerts = []
        self.last_reading = None
        self.sequence = 0
        self.next_alert = 1
        self.events = []
        self.inspection_left = 0.0
        self.low_battery_alerted = False
        self.critical_battery_alerted = False
        self.watch_left = 0
        self.watch_timer = 0.0
        self.stopped_while_returning = False
        self.link_available = True
        self.seconds_without_link = 0.0

    @property
    def distance(self):
        """Dónde está el robot sobre el recorrido, según el robot."""
        return self.robot.distance

    @property
    def battery(self):
        return self.robot.battery

    # ------------------------------------------------------------ tiempo

    def tick(self, dt):
        if self.mission and self.state != "AT_BASE":
            self.mission.elapsed += dt
        self._update_link(dt)
        if self.state == "AT_BASE":
            self._at_base()
        elif self.state == "ON_MISSION":
            self._advance_mission(dt)
        elif self.state == "RETURNING":
            if self.distance <= 0.0:
                self._arrive_at_base()
            else:
                self.robot.go_to(0.0)
        else:
            self.robot.hold()
            if self.state == "AWAITING_DECISION":
                self._tick_watch(dt)
        self.robot.update(dt)
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
            self.mission.interrupted = True
            self.state = "RETURNING"

    def _at_base(self):
        self.robot.hold()
        if self.battery > self.site.battery_warning + 5:
            self.low_battery_alerted = False
        if self.battery > self.site.battery_critical + 5:
            self.critical_battery_alerted = False

    def _advance_mission(self, dt):
        m = self.mission
        if m is None:
            return
        if self.inspection_left > 0:
            self.robot.hold()
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
        # Humo o fuego por delante: no se acerca, corta la ronda y vuelve a la base.
        if self.robot.smoke_ahead(SMOKE_RANGE) is not None:
            self.robot.hold()
            photo = self.robot.take_photo(f"{m.id}_{checkpoint_id}_humo")
            self._resolve_decisions()
            self.inspection_left = 0.0
            m.interrupted = True
            self.state = "RETURNING"
            self._raise_alert("CRITICAL", "SMOKE_DETECTED", checkpoint_id, None, None, [], photo)
            return
        # Lo que tiene por delante y lo hace frenar: agua en el piso (cámara) o algo que bloquea (LiDAR).
        stops = []
        water = self.robot.water_ahead(CAMERA_RANGE)
        if water is not None:
            stops.append((water - WATER_GAP, "WATER_DETECTED"))
        ahead = self.robot.obstacle_ahead(LIDAR_RANGE)
        if ahead is not None:
            stops.append((ahead - OBSTACLE_GAP, "OBSTACLE"))
        stops = [(gap, code) for gap, code in stops if self.distance + gap < target_distance]
        if stops:
            gap, code = min(stops)
            if gap <= 0.15:
                self.robot.hold()
                self.state = "AWAITING_DECISION"
                if code == "WATER_DETECTED":
                    photo = self.robot.take_photo(f"{m.id}_{checkpoint_id}_agua")
                    self._raise_alert("CRITICAL", code, checkpoint_id, None, None, ["CONTINUE", "WATCH"], photo)
                else:
                    photo = self.robot.take_photo(f"{m.id}_{checkpoint_id}_obstaculo")
                    self._raise_alert("WARNING", code, checkpoint_id, None, None, ["SKIP", "RETRY"], photo)
                return
            self.robot.go_to(self.distance + gap)
            return
        if self.distance >= target_distance - EPSILON:
            self.robot.hold()
            self.inspection_left = INSPECTION_SECONDS
            return
        self.robot.go_to(target_distance)

    def _arrive_at_base(self):
        self.inspection_left = 0.0
        self.robot.dock()
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
                result = "INTERRUPTED"
            self._emit({"kind": "MISSION_FINISHED", "mission_id": m.id, "result": result,
                        "completed": m.inspected, "total": len(m.targets)})
        self.mission = None

    # ------------------------------------------------------------ inspección

    def _complete_inspection(self, m):
        target = m.current
        if target is None:
            return
        checkpoint_id = target[0]
        reading = self.robot.read_environment(checkpoint_id)
        self.last_reading = reading
        issues = self._evaluate(checkpoint_id, reading)
        status = "OK" if not issues else "ANOMALY"
        m.inspected += 1
        m.last_checkpoint = checkpoint_id
        photo = self.robot.take_photo(f"{m.id}_{checkpoint_id}")
        self._emit({"kind": "CHECKPOINT_RESULT", "mission_id": m.id, "checkpoint_id": checkpoint_id,
                    "status": status, "reading": reading, "photo_id": photo})
        waits = False
        for code, severity, value, threshold in issues:
            needs_decision = code == "TEMPERATURE_HIGH" and severity == "CRITICAL"
            waits = waits or needs_decision
            self._raise_alert(severity, code, checkpoint_id, value, threshold,
                              ["CONTINUE", "WATCH"] if needs_decision else [], photo)
        m.next_index += 1
        if waits:
            self.state = "AWAITING_DECISION"
        elif not self.link_available:
            self.state = "PAUSED"  # regla R3: sin enlace, espera en un lugar seguro
        elif m.all_done:
            self.state = "RETURNING"
        else:
            self.state = "ON_MISSION"

    def _evaluate(self, checkpoint_id, reading):
        """Las mismas reglas que la app: temperatura por zona, humedad dentro de un rango y ruido por
        encima del de base. Devuelve (código, severidad, valor, umbral) por cada problema."""
        site, issues = self.site, []
        zone = site.checkpoints[checkpoint_id][1]
        warning, critical = site.temperature_limits[zone]
        t = reading["temperature_c"]
        if t >= critical:
            issues.append(("TEMPERATURE_HIGH", "CRITICAL", t, critical))
        elif t >= warning:
            issues.append(("TEMPERATURE_HIGH", "WARNING", t, warning))
        h = reading["humidity_pct"]
        (low_warning, low_critical), (high_warning, high_critical) = site.humidity_low, site.humidity_high
        if h <= low_critical or h >= high_critical:
            issues.append(("HUMIDITY_OUT_OF_RANGE", "CRITICAL", h, low_critical if h < 50 else high_critical))
        elif h <= low_warning or h >= high_warning:
            issues.append(("HUMIDITY_OUT_OF_RANGE", "WARNING", h, low_warning if h < 50 else high_warning))
        n, base = reading["noise_dba"], reading["noise_baseline_dba"]
        noise_warning, noise_critical = site.noise_above
        if n - base >= noise_critical:
            issues.append(("NOISE_HIGH", "CRITICAL", n, base + noise_critical))
        elif n - base >= noise_warning:
            issues.append(("NOISE_HIGH", "WARNING", n, base + noise_warning))
        return issues

    def _tick_watch(self, dt):
        """Vigilar una temperatura: repite la medición del punto cada tanto, sin moverse."""
        if self.watch_left <= 0 or self.last_reading is None:
            return
        self.watch_timer -= dt
        if self.watch_timer > 0:
            return
        self.last_reading = self.robot.read_environment(self.last_reading["checkpoint_id"])
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
        # Si hoy consume más de lo normal (lo mide el robot), la estimación escala igual.
        reference = NOMINAL_MOVING_DRAIN if self.robot.speed > 0 else NOMINAL_IDLE_DRAIN
        factor = max(1.0, self.robot.battery_drain_rate() / reference)
        drain = (meters / NOMINAL_SPEED_MPS * NOMINAL_MOVING_DRAIN
                 + len(below) * INSPECTION_SECONDS * NOMINAL_IDLE_DRAIN) * factor
        needed = site.battery_critical + drain
        if self.battery >= needed:
            return False
        m.low_battery = True
        self._raise_alert("WARNING", "BATTERY_INSUFFICIENT_FOR_BASEMENT", checkpoint_id, self.battery, round(needed, 1))
        self.robot.hold()
        self.state = "RETURNING"
        return True

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
            self.robot.hold()
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
            # A diferencia de cancelar, la ronda queda interrumpida: lo inspeccionado vale.
            if self.state in ("AT_BASE", "RETURNING"):
                return False, "INVALID_STATE"
            if self.mission is not None:
                self.mission.interrupted = True
            self._go_back()
            return True, None
        if name == "OPERATIONAL_STOP":
            if self.state in ("AT_BASE", "STOPPED"):
                return False, "INVALID_STATE"
            self.stopped_while_returning = self.state == "RETURNING"
            self.state = "STOPPED"
            self.robot.hold()
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
            if self.robot.obstacle_ahead(LIDAR_RANGE) is not None:
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
                # Rodea el obstáculo y sigue con el próximo checkpoint.
                self.robot.bypass_obstacle()
                m.next_index += 1
            self._resolve(alert)
            self.state = "RETURNING" if m.all_done else "ON_MISSION"
            return True, None
        if decision == "CONTINUE":
            self.watch_left = 0
            if alert["code"] == "WATER_DETECTED":
                self.robot.bypass_water()  # sigue, rodeando el agua sin pisarla
            self._resolve(alert)
            self.state = "RETURNING" if m.all_done else "ON_MISSION"
            return True, None
        if decision == "WATCH":
            # Se queda quieto mirando. Si es una temperatura, además repite la medición.
            if alert["code"] == "TEMPERATURE_HIGH":
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

    def _on_stairs(self):
        return self.site.on_stairs(*self.robot.position())

    # ------------------------------------------------------------ salida

    def plan_position(self):
        return self.robot.position()

    def drain_events(self):
        events, self.events = self.events, []
        return events

    def telemetry(self, link):
        """Arma el mensaje de telemetría con lo que informa el robot."""
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
        x, y = self.robot.position()
        speed = self.robot.speed
        return {
            "type": "telemetry",
            "robot_id": self.robot_id,
            "seq": self.sequence,
            "timestamp": now_ms(),
            "connection": link,
            "battery": {"percent": round(self.battery), "charging": self.robot.charging,
                        "est_minutes": round(self.battery / (NOMINAL_MOVING_DRAIN * 60))},
            "state": self.state,
            "mission": mission,
            "location": {
                "level": self.site.level_at(y),
                "from_cp": m.last_checkpoint if m else None,
                "to_cp": None if self.state == "RETURNING" or m is None or m.current is None else m.current[0],
                "x": round(x, 2), "y": round(y, 2), "heading_deg": self.robot.heading(),
                "confidence": self.robot.location_confidence(),
            },
            "motion": {"speed_mps": round(speed, 2), "moving": speed > 0},
            "active_alerts": sum(1 for a in self.alerts if not a["acknowledged"]),
            "environment": self.last_reading,
        }

    # ------------------------------------------------------------ auxiliares

    def _raise_alert(self, severity, code, checkpoint_id, measured, threshold, options=None, photo_id=None):
        options = options or []
        alert = {
            "id": f"a-{self.next_alert}", "timestamp": now_ms(), "severity": severity, "code": code,
            "checkpoint_id": checkpoint_id,
            "measured": round(measured, 1) if measured is not None else None,
            "threshold": threshold, "requires_decision": bool(options), "options": options,
            "acknowledged": False, "photo_id": photo_id,
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
