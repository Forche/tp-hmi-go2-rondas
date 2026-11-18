"""Sitio de la ronda, cargado de un archivo JSON (formato go2-sitio/1, ver sitios/README.md).

Es el mismo archivo que usa la app para el robot simulado: plano, checkpoints, recorrido, misiones
y umbrales. Coordenadas en metros sobre el plano (x hacia la derecha, y hacia abajo); el subsuelo,
si hay, se dibuja debajo de la planta baja en el mismo plano.
"""

import json
import math
import os

FORMAT = "go2-sitio/1"
DEFAULT_SITE = os.path.join(os.path.dirname(__file__), "..", "..", "sitios", "datacenter-edificio-a.json")

ZONES = {"COLD_AISLE", "HOT_AISLE", "COOLING", "NETWORK", "ELECTRICAL"}
MISSION_TYPES = {"FULL_ROUND", "GROUND_FLOOR_ROUND"}
AREA_KINDS = {"ROOM", "RACK_ROW", "COLD_AISLE", "EQUIPMENT", "STAIRS", "LEVEL_LABEL"}
MAX_GAP_TO_ROUTE = 0.5

# Lo que no forma parte del mapa del robot: la app no lo recibe.
SIMULATION_ONLY_KEYS = ("simulation", "scene_3d")


class SiteError(ValueError):
    """El archivo no es un sitio válido."""


class Site:
    def __init__(self, raw):
        self.raw = raw
        _check(raw.get("format") == FORMAT, f"el formato tiene que ser \"{FORMAT}\"")
        try:
            self.site_id = raw["site_id"]
            self.name = raw["name"]
            self.width = float(raw["width"])
            self.height = float(raw["height"])
            self.base = _point(raw["base"])
            self.basement_start_y = raw.get("basement_start_y")
            stairs = raw.get("stairs")
            self.stairs = None if stairs is None else (
                float(stairs["left"]), float(stairs["right"]), float(stairs["top"]), float(stairs["bottom"]))
            self.areas = [dict(a) for a in raw.get("areas", [])]
            # id -> (posición, zona)
            self.checkpoints = {c["id"]: ((float(c["x"]), float(c["y"])), c["zone"]) for c in raw["checkpoints"]}
            self.route = [_point(p) for p in raw["route"]]
            self.missions = {m["type"]: list(m["checkpoints"]) for m in raw["missions"]}
            thresholds = raw["thresholds"]
            battery = thresholds["battery"]
            self.battery_min_to_start = int(battery["min_to_start"])
            self.battery_warning = int(battery["warning"])
            self.battery_critical = int(battery["critical"])
            # zona -> (advertencia, crítico) en grados
            self.temperature_limits = {
                zone: (float(l["warning"]), float(l["critical"])) for zone, l in thresholds["temperature"].items()}
            # (advertencia, crítico): humedad baja y alta en %, ruido en dB(A) por encima del de base
            self.humidity_low = (float(thresholds["humidity_low"]["warning"]), float(thresholds["humidity_low"]["critical"]))
            self.humidity_high = (float(thresholds["humidity_high"]["warning"]), float(thresholds["humidity_high"]["critical"]))
            self.noise_above = (float(thresholds["noise_above_baseline"]["warning"]),
                                float(thresholds["noise_above_baseline"]["critical"]))
            simulation = raw["simulation"]
            self.humidity_pct = float(simulation.get("humidity_pct", 45.0))
            # zona -> (temperatura típica, ruido de base en dB(A))
            self.typical_readings = {
                zone: (float(r["temperature_c"]), float(r["noise_dba"]))
                for zone, r in simulation["typical_readings"].items()}
            scene = raw.get("scene_3d") or {}
            self.basement_depth = float(scene.get("basement_depth", 3.0)) if self.basement_start_y is not None else 0.0
            self.labels = [(l["text"], float(l["x"]), float(l["y"]), float(l.get("height", 0.5)))
                           for l in scene.get("labels", [])]
        except (KeyError, TypeError, ValueError) as e:
            raise SiteError(f"falta un dato o tiene un valor inválido ({e})") from e
        self._validate()
        self.path = RoutePath(self.route)

    def _validate(self):
        _check(len(self.route) >= 2, "el recorrido necesita al menos dos puntos")
        _check(len(self.checkpoints) == len(self.raw["checkpoints"]), "hay ids de checkpoint repetidos")
        path = RoutePath(self.route)
        for cp, (position, zone) in self.checkpoints.items():
            _check(zone in ZONES, f"{cp}: zona desconocida \"{zone}\"")
            _check(zone in self.temperature_limits, f"{cp}: faltan los umbrales de temperatura de {zone}")
            _check(zone in self.typical_readings, f"{cp}: faltan las lecturas típicas de {zone}")
            _check(path.gap_to(position) <= MAX_GAP_TO_ROUTE, f"{cp} no está sobre el recorrido")
        for mission, ids in self.missions.items():
            _check(mission in MISSION_TYPES, f"tipo de misión desconocido \"{mission}\"")
            _check(ids, f"la misión {mission} no tiene checkpoints")
            for cp in ids:
                _check(cp in self.checkpoints, f"la misión {mission} usa {cp}, que no existe")
        _check("FULL_ROUND" in self.missions, "falta la misión FULL_ROUND")
        for area in self.areas:
            _check(area.get("kind") in AREA_KINDS, f"tipo de área desconocido \"{area.get('kind')}\"")
        _check(self.stairs is None or self.basement_start_y is not None, "hay escalera pero no hay subsuelo")

    # ------------------------------------------------------------ niveles

    def level_at(self, y):
        return "BASEMENT" if self.basement_start_y is not None and y >= self.basement_start_y else "GROUND_FLOOR"

    def level_z(self, level):
        return 0.0 if level == "GROUND_FLOOR" else -self.basement_depth

    def on_stairs(self, x, y, margin=0.5):
        if self.stairs is None:
            return False
        left, right, top, bottom = self.stairs
        return left <= x <= right and top + margin < y < bottom - margin

    def floor_z(self, x, y):
        """Altura del piso bajo el robot: en la escalera baja de forma pareja de un nivel al otro."""
        if self.stairs is not None:
            left, right, top, bottom = self.stairs
            if left <= x <= right and top < y < bottom:
                return -self.basement_depth * (y - top) / (bottom - top)
        return self.level_z(self.level_at(y))

    def app_payload(self):
        """El mapa que el robot le pasa a la app: sin los datos de la simulación ni de la escena 3D."""
        return {k: v for k, v in self.raw.items() if k not in SIMULATION_ONLY_KEYS}


def load(path=DEFAULT_SITE):
    try:
        with open(path, encoding="utf-8") as f:
            raw = json.load(f)
    except OSError as e:
        raise SiteError(f"no se pudo abrir {path}: {e.strerror}") from e
    except json.JSONDecodeError as e:
        raise SiteError(f"{path} no es un JSON válido (línea {e.lineno})") from e
    return Site(raw)


def _point(p):
    return float(p["x"]), float(p["y"])


def _check(condition, message):
    if not condition:
        raise SiteError(message)


class RoutePath:
    """Polilínea del recorrido: el robot se ubica por la distancia recorrida desde la base."""

    def __init__(self, points):
        self.points = points
        self.cumulative = [0.0]
        for (ax, ay), (bx, by) in zip(points, points[1:]):
            self.cumulative.append(self.cumulative[-1] + math.hypot(bx - ax, by - ay))

    @property
    def length(self):
        return self.cumulative[-1]

    def segment_index_at(self, distance):
        d = min(max(distance, 0.0), self.length)
        for i in range(len(self.points) - 1):
            if d <= self.cumulative[i + 1]:
                return i
        return len(self.points) - 2

    def point_at(self, distance):
        i = self.segment_index_at(distance)
        (ax, ay), (bx, by) = self.points[i], self.points[i + 1]
        seg = self.cumulative[i + 1] - self.cumulative[i]
        t = 0.0 if seg == 0 else (min(max(distance, 0.0), self.length) - self.cumulative[i]) / seg
        return ax + (bx - ax) * t, ay + (by - ay) * t

    def heading_at(self, distance, forward):
        """Rumbo en grados del plano (0 = este, 90 = sur)."""
        i = self.segment_index_at(distance)
        (ax, ay), (bx, by) = self.points[i], self.points[i + 1]
        deg = math.degrees(math.atan2(by - ay, bx - ax)) + (0 if forward else 180)
        return round(deg % 360)

    def _closest(self, point):
        px, py = point
        best, best_gap = 0.0, float("inf")
        for i in range(len(self.points) - 1):
            (ax, ay), (bx, by) = self.points[i], self.points[i + 1]
            dx, dy = bx - ax, by - ay
            length_sq = dx * dx + dy * dy
            t = 0.0 if length_sq == 0 else min(max(((px - ax) * dx + (py - ay) * dy) / length_sq, 0.0), 1.0)
            gap = math.hypot(px - (ax + dx * t), py - (ay + dy * t))
            if gap < best_gap:
                best_gap, best = gap, self.cumulative[i] + t * math.hypot(dx, dy)
        return best, best_gap

    def distance_of(self, point):
        return self._closest(point)[0]

    def gap_to(self, point):
        return self._closest(point)[1]
