"""Situaciones aleatorias: para que una ronda de prueba se parezca más a una noche real.

Mientras el robot está en ronda, cada tanto (entre 45 s y 2 min de tiempo simulado) pasa algo al azar:
una medición fuera de rango, una caja en el pasillo, agua en el piso, la batería que se descarga, un
corte de enlace o, menos seguido, humo. La batería baja y el humo terminan la ronda, así que pasan como
mucho una vez por ronda. Es parte de la simulación del lugar, no de la lógica de la ronda.
"""

import random

# (situación, peso): las más comunes, más seguido.
WEIGHTS = [
    ("temperatura", 3), ("humedad", 3), ("ruido", 3), ("caja", 3), ("obstaculo", 2), ("agua", 2),
    ("corte", 1), ("bateria", 1), ("humo", 1),
]
ONCE_PER_ROUND = {"bateria", "humo"}
MIN_SECONDS, MAX_SECONDS = 45.0, 120.0


class RandomSituations:

    def __init__(self, rng=None):
        self.rng = rng or random.Random()
        self.mission_id = None
        self.used = set()
        self.countdown = self._next_wait()

    def tick(self, dt, mission_id):
        """Pasaron `dt` segundos simulados con el robot en ronda. Devuelve la situación que toca, o None."""
        if mission_id != self.mission_id:
            self.mission_id, self.used = mission_id, set()
            self.countdown = self._next_wait()
        self.countdown -= dt
        if self.countdown > 0:
            return None
        self.countdown = self._next_wait()
        options = [(name, weight) for name, weight in WEIGHTS if name not in self.used]
        name = self.rng.choices([n for n, _ in options], weights=[w for _, w in options])[0]
        if name in ONCE_PER_ROUND:
            self.used.add(name)
        return name

    def _next_wait(self):
        return self.rng.uniform(MIN_SECONDS, MAX_SECONDS)
