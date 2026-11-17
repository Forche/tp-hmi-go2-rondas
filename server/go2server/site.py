"""Sitio del caso de uso: el mismo data center que DataCenterSite.kt en la app.

Coordenadas en metros sobre el plano (x hacia la derecha, y hacia abajo).
El subsuelo se dibuja debajo de la planta baja, en el mismo plano.
"""

import math

BASE = (3.3, 12.5)
BASEMENT_START_Y = 14.0
STAIRS_SEGMENT_INDEX = 7

ROUTE = [
    (3.3, 12.6), (6.2, 12.6), (6.2, 3.6), (21.8, 3.6), (21.8, 8.2),
    (8.0, 8.2), (8.0, 12.8), (29.6, 12.8), (29.6, 20.2), (10.5, 20.2),
]

# id -> (posición, zona)
CHECKPOINTS = {
    "CP1": ((15.2, 3.6), "HOT_AISLE"),
    "CP2": ((21.8, 8.2), "COOLING"),
    "CP3": ((15.2, 8.2), "COLD_AISLE"),
    "CP4": ((15.2, 12.8), "HOT_AISLE"),
    "CP5": ((24.1, 12.8), "NETWORK"),
    "CP6": ((22.8, 20.2), "ELECTRICAL"),
    "CP7": ((16.5, 20.2), "ELECTRICAL"),
    "CP8": ((10.5, 20.2), "ELECTRICAL"),
}

MISSIONS = {
    "FULL_ROUND": ["CP1", "CP2", "CP3", "CP4", "CP5", "CP6", "CP7", "CP8"],
    "GROUND_FLOOR_ROUND": ["CP1", "CP2", "CP3", "CP4", "CP5"],
}

# zona -> (advertencia, crítico) en grados
TEMPERATURE_LIMITS = {
    "COLD_AISLE": (27.0, 32.0),
    "HOT_AISLE": (38.0, 45.0),
    "COOLING": (25.0, 30.0),
    "NETWORK": (30.0, 35.0),
    "ELECTRICAL": (30.0, 35.0),
}

# zona -> (temperatura típica, ruido de base en dB(A))
TYPICAL_READINGS = {
    "COLD_AISLE": (23.5, 62.0),
    "HOT_AISLE": (31.5, 66.0),
    "COOLING": (19.5, 70.0),
    "NETWORK": (24.5, 64.0),
    "ELECTRICAL": (25.5, 52.0),
}

BATTERY_MIN_TO_START = 40
BATTERY_WARNING = 30
BATTERY_CRITICAL = 15

# Volúmenes de la escena: (nombre, izquierda, arriba, ancho, alto, altura en m)
OBSTACLE_BOXES = [
    ("fila_a", 9.2, 4.8, 12.0, 1.4, 2.0),
    ("fila_b", 9.2, 10.2, 12.0, 1.4, 2.0),
    ("clima", 22.6, 4.4, 3.0, 4.0, 1.9),
    ("rack_red", 22.6, 10.0, 3.0, 1.6, 2.0),
    ("ups", 8.0, 17.0, 5.0, 1.6, 1.6),
    ("baterias", 14.0, 17.0, 5.0, 1.6, 1.4),
    ("tablero", 20.0, 17.0, 5.6, 1.6, 1.8),
]


# Escena 3D: el subsuelo está 3 m más abajo y la escalera baja por x = 29.6 (tramo CP5 -> CP6).
BASEMENT_DEPTH = 3.0
STAIRS_TOP_Y = 13.0
STAIRS_BOTTOM_Y = 19.0
STAIRS_X = (28.6, 30.6)

# Salas para la escena: (nombre, izquierda, arriba, ancho, alto, nivel)
ROOMS = [
    ("noc", 0.8, 2.2, 5.2, 12.0, "GROUND_FLOOR"),
    ("sala", 6.8, 2.2, 19.6, 12.0, "GROUND_FLOOR"),
    ("pasillo_frio", 9.2, 6.2, 12.0, 4.0, "GROUND_FLOOR"),
    ("escalera_pb", 27.2, 2.2, 4.8, 12.0, "GROUND_FLOOR"),
    ("sala_electrica", 6.8, 16.4, 19.6, 5.0, "BASEMENT"),
    ("escalera_ss", 27.2, 16.4, 4.8, 5.0, "BASEMENT"),
]


def level_at(y):
    return "BASEMENT" if y >= BASEMENT_START_Y else "GROUND_FLOOR"


def level_z(level):
    return 0.0 if level == "GROUND_FLOOR" else -BASEMENT_DEPTH


def floor_z(y):
    """Altura del piso bajo el robot: en la escalera baja de forma pareja de un nivel al otro."""
    if y <= STAIRS_TOP_Y:
        return 0.0
    if y >= STAIRS_BOTTOM_Y:
        return -BASEMENT_DEPTH
    return -BASEMENT_DEPTH * (y - STAIRS_TOP_Y) / (STAIRS_BOTTOM_Y - STAIRS_TOP_Y)


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

    def distance_of(self, point):
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
        return best
