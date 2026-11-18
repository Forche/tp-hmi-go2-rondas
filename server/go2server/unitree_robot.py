"""Esbozo: el mismo contrato (robot_interface.py) implementado sobre un Go2 EDU real. No está implementado.

Con esta clase en lugar de SimulatedRobot, la lógica de la ronda, el WebSocket y la app no cambian.
Correría en la computadora del Go2 EDU o en una PC de su red, y le hablaría al robot con unitree_sdk2
(el SDK de Unitree, que por dentro usa DDS: mensajes publicados por temas en la red del robot).

Lo que haría cada parte, en grandes líneas (los nombres del SDK hay que verificarlos contra la
documentación de Unitree para la versión del robot):

- Moverse (go_to, hold, bypass_obstacle): el controlador de caminata que el Go2 trae de fábrica,
  con el cliente de movimiento del SDK (SportClient: moverse con una velocidad, frenar). Encima hace
  falta un seguidor de camino propio que convierta "ir hasta tal distancia del recorrido" en
  velocidades, usando la ubicación del robot.
- Saber dónde está (distance, position, heading, location_confidence): el estado de movimiento que
  publica el robot (rt/sportmodestate) da posición y velocidad por odometría, que acumula error. Para
  seguir el recorrido con precisión hace falta ubicarse sobre el mapa del sitio (el mapa del LiDAR de
  la puesta en marcha). La simulación esconde este problema: ahí la posición es exacta.
- LiDAR (obstacle_ahead): la nube de puntos del LiDAR del robot (tema de DDS del LiDAR), filtrada a la
  franja del camino que tiene por delante.
- Cámara (take_photo): el cliente de video del SDK (VideoClient) para pedir una imagen de la cámara frontal.
- Sensores del ambiente (read_environment): el módulo de sensores agregado al robot (no viene con el
  Go2), por su propia interfaz.
- Batería (battery, charging, battery_drain_rate): el estado de bajo nivel del robot (rt/lowstate)
  trae el estado de la batería; el consumo sale de la corriente que informa.
- Base de carga (dock): depende de la base que se use; el Go2 no trae una base de carga autónoma.
"""

from .robot_interface import RobotInterface


class UnitreeRobot(RobotInterface):
    """No implementado: ver la explicación al principio del archivo."""

    def __init__(self, site, network_interface):
        raise NotImplementedError(
            "UnitreeRobot es un esbozo: describe qué usaría del SDK de Unitree, pero no está implementado.")
