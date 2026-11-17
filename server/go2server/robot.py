"""Motor de la ronda del robot: mismo comportamiento que RobotSimulator.kt en la app.

Es sincrónico: tick() avanza el tiempo simulado y handle() decide si acepta una orden.
Las alertas y los eventos usan directamente el formato del contrato JSON.
"""

import random
import time

from . import site

SPEED_MPS = 0.5
INSPECTION_SECONDS = 4.0
OBSTACLE_GAP = 0.6
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
    def __init__(self, robot_id, rng=None):
        self.robot_id = robot_id
        self.rng = rng or random.Random()
        self.route = site.RoutePath(site.ROUTE)
        self.state = "AT_BASE"
        self.battery = 92.0
        self.distance = 0.0
        self.speed = 0.0
        self.forward = True
        self.mission = None
        self.alerts = []
        self.last_reading = None
        self.sequence = 0
        self.next_alert = 1
        self.events = []
        self.inspection_left = 0.0
        self.obstacle_armed = False
        self.obstacle_at = None
        self.high_temperature_armed = False
        self.drain_multiplier = 1.0
        self.low_battery_alerted = False
        self.critical_battery_alerted = False
        self.watch_left = 0
        self.watch_timer = 0.0
        self.stopped_while_returning = False
        self.link_available = True
        self.seconds_without_link = 0.0

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
            self.speed = 0.0
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
        self.speed = 0.0
        self.drain_multiplier = 1.0
        self.battery = min(100.0, self.battery + CHARGE_RATE * dt)
        if self.battery > site.BATTERY_WARNING + 5:
            self.low_battery_alerted = False
        if self.battery > site.BATTERY_CRITICAL + 5:
            self.critical_battery_alerted = False

    def _advance_mission(self, dt):
        m = self.mission
        if m is None:
            return
        if self.inspection_left > 0:
            self.speed = 0.0
            self.inspection_left -= dt
            if self.inspection_left <= 0:
                self._complete_inspection(m)
            return
        target = m.current
        if target is None:
            self.state = "RETURNING"
            return
        checkpoint_id, target_distance = target
        if self.obstacle_armed and self.obstacle_at is None:
            gap = target_distance - self.distance
            if gap > 1.5:
                self.obstacle_at = self.distance + min(3.0, gap / 2)
                self.obstacle_armed = False
        obstacle = self.obstacle_at
        stop_at = min(target_distance, obstacle - OBSTACLE_GAP) if obstacle is not None else target_distance
        self._move_towards(stop_at, dt)
        if obstacle is not None and self.distance >= stop_at - EPSILON and stop_at < target_distance:
            self.speed = 0.0
            self.state = "AWAITING_DECISION"
            self._raise_alert("WARNING", "OBSTACLE", checkpoint_id, None, None, ["SKIP", "RETRY"])
            return
        if self.distance >= target_distance - EPSILON:
            self.speed = 0.0
            self.inspection_left = INSPECTION_SECONDS

    def _move_towards(self, target, dt):
        delta = target - self.distance
        if abs(delta) < EPSILON:
            self.speed = 0.0
            return
        self.forward = delta > 0
        step = SPEED_MPS * dt
        self.distance = target if abs(delta) <= step else self.distance + (step if self.forward else -step)
        self.speed = SPEED_MPS - self.rng.uniform(0.0, 0.08)

    def _arrive_at_base(self):
        self.distance = 0.0
        self.speed = 0.0
        self.inspection_left = 0.0
        self.obstacle_at = None
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
        self._emit({"kind": "CHECKPOINT_RESULT", "mission_id": m.id, "checkpoint_id": checkpoint_id,
                    "status": status, "reading": reading})
        waits = False
        for code, severity, value, threshold in issues:
            needs_decision = code == "TEMPERATURE_HIGH" and severity == "CRITICAL"
            waits = waits or needs_decision
            self._raise_alert(severity, code, checkpoint_id, value, threshold,
                              ["CONTINUE", "WATCH"] if needs_decision else [])
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
        zone = site.CHECKPOINTS[checkpoint_id][1]
        temperature, noise_baseline = site.TYPICAL_READINGS[zone]
        if self.high_temperature_armed:
            self.high_temperature_armed = False
            temperature = site.TEMPERATURE_LIMITS[zone][1] + 1.1
        else:
            temperature += self.rng.uniform(-0.6, 0.6)
        return {
            "checkpoint_id": checkpoint_id,
            "temperature_c": round(temperature, 1),
            "humidity_pct": round(45 + self.rng.uniform(-4, 4), 1),
            "noise_dba": round(noise_baseline + self.rng.uniform(-1.5, 1.5), 1),
            "noise_baseline_dba": noise_baseline,
        }

    def _evaluate(self, checkpoint_id, reading):
        zone = site.CHECKPOINTS[checkpoint_id][1]
        warning, critical = site.TEMPERATURE_LIMITS[zone]
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

    def _drain_battery(self, dt):
        if self.state == "AT_BASE":
            return
        rate = MOVING_DRAIN if self.speed > 0 else IDLE_DRAIN
        self.battery = max(0.0, self.battery - rate * dt * self.drain_multiplier)

    def _check_battery(self):
        if self.state == "AT_BASE":
            return
        if self.battery < site.BATTERY_WARNING and not self.low_battery_alerted:
            self.low_battery_alerted = True
            if self.mission:
                self.mission.low_battery = True
            self._raise_alert("WARNING", "BATTERY_LOW", None, self.battery, float(site.BATTERY_WARNING))
        if self.battery < site.BATTERY_CRITICAL and not self.critical_battery_alerted:
            self.critical_battery_alerted = True
            if self.mission:
                self.mission.low_battery = True
            self._raise_alert("CRITICAL", "BATTERY_CRITICAL", None, self.battery, float(site.BATTERY_CRITICAL))
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
            self.state, self.speed = "PAUSED", 0.0
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
            self.state, self.speed = "STOPPED", 0.0
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
        if self.battery < site.BATTERY_MIN_TO_START:
            return False, "LOW_BATTERY"
        ids = [checkpoint_id] if mission_type == "SPOT_CHECK" and checkpoint_id else site.MISSIONS.get(mission_type, [])
        targets = sorted(
            ((cp, self.route.distance_of(site.CHECKPOINTS[cp][0])) for cp in ids if cp in site.CHECKPOINTS),
            key=lambda t: t[1],
        )
        if not targets:
            return False, "INVALID_STATE"
        self.mission = Mission(f"m-{now_ms()}", mission_type, targets)
        self.obstacle_at = None
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
            return False, "OBSTACLE_PERSISTS"
        if decision == "SKIP":
            target = m.current
            if target is not None:
                m.skipped += 1
                m.last_checkpoint = target[0]
                self._emit({"kind": "CHECKPOINT_RESULT", "mission_id": m.id, "checkpoint_id": target[0],
                            "status": "SKIPPED", "reading": None})
                self.distance = target[1]  # rodea el obstáculo y sigue después del checkpoint
                self.obstacle_at = None
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
        self.obstacle_at = None
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
        segment = self.route.segment_index_at(self.distance)
        _, y = self.route.point_at(self.distance)
        top = site.ROUTE[segment][1]
        bottom = site.ROUTE[segment + 1][1]
        return segment == site.STAIRS_SEGMENT_INDEX and top + 0.5 < y < bottom - 0.5

    # ------------------------------------------------------------ escenarios (consola del servidor)

    def trigger(self, scenario):
        if scenario == "LOW_BATTERY":
            self.battery = min(self.battery, site.BATTERY_WARNING + 1.0)
            self.drain_multiplier = LOW_BATTERY_DRAIN_MULTIPLIER
        elif scenario == "OBSTACLE":
            self.obstacle_armed = True
        elif scenario == "HIGH_TEMPERATURE":
            self.high_temperature_armed = True

    # ------------------------------------------------------------ salida

    def plan_position(self):
        return site.BASE if self.state == "AT_BASE" else self.route.point_at(self.distance)

    def heading(self):
        return self.route.heading_at(self.distance, self.forward)

    def drain_events(self):
        events, self.events = self.events, []
        return events

    def telemetry(self, position, link):
        """Arma el mensaje de telemetría. La posición la pasa quien la lee del modelo de MuJoCo."""
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
                "level": site.level_at(y),
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

    def _raise_alert(self, severity, code, checkpoint_id, measured, threshold, options=None):
        options = options or []
        alert = {
            "id": f"a-{self.next_alert}", "timestamp": now_ms(), "severity": severity, "code": code,
            "checkpoint_id": checkpoint_id,
            "measured": round(measured, 1) if measured is not None else None,
            "threshold": threshold, "requires_decision": bool(options), "options": options,
            "acknowledged": False,
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
