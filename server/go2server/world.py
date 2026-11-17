"""Escena de MuJoCo: el modelo del Go2 de MuJoCo Menagerie dentro del data center.

El movimiento es cinemático: la base del modelo se ubica sobre el recorrido en cada ciclo y las
patas quedan en la pose de pie ("home"). No hay controlador de locomoción. La posición que se
informa a la app se lee del estado del modelo.

La planta baja es un bloque de 3 m de alto; el subsuelo queda al nivel del piso de la escena y
se llega por la escalera. Sobre cada checkpoint hay un cartel con el resultado de la ronda actual.
"""

import math
import os

import mujoco
import numpy as np

from . import site

STANDING_HEIGHT = 0.27

# Colores (r g b a) de la escena.
SLAB = "0.80 0.82 0.85 1"
ROOM_COLORS = {
    "noc": "0.62 0.76 0.92 1",
    "sala": "0.88 0.89 0.91 1",
    "pasillo_frio": "0.70 0.84 0.97 1",
    "escalera_pb": "0.86 0.82 0.74 1",
    "sala_electrica": "0.62 0.64 0.68 1",
    "escalera_ss": "0.70 0.68 0.64 1",
}
EQUIPMENT_COLORS = {
    "fila_a": "0.20 0.22 0.26 1",
    "fila_b": "0.20 0.22 0.26 1",
    "clima": "0.40 0.68 0.90 1",
    "rack_red": "0.27 0.40 0.62 1",
    "ups": "0.92 0.66 0.22 1",
    "baterias": "0.86 0.52 0.18 1",
    "tablero": "0.78 0.36 0.20 1",
}
STEP_COLOR = "0.72 0.72 0.74 1"

# Estado de cada checkpoint -> color del disco.
MARKER_COLORS = {
    "PENDING": (0.12, 0.37, 0.68, 1.0),
    "INSPECTING": (0.95, 0.75, 0.15, 1.0),
    "OK": (0.18, 0.65, 0.32, 1.0),
    "ANOMALY": (0.85, 0.22, 0.18, 1.0),
    "SKIPPED": (0.50, 0.52, 0.55, 1.0),
}

# Carteles fijos (los equipos de planta baja no llevan: los taparían los de los checkpoints): (texto, x, y del plano, altura sobre su nivel, nivel). La fuente de MuJoCo es ASCII.
STATIC_LABELS = [
    ("PLANTA BAJA", 2.0, 1.2, 0.6, "GROUND_FLOOR"),
    ("NOC", 3.4, 3.4, 0.6, "GROUND_FLOOR"),
    ("Escalera", 29.6, 8.0, 0.6, "GROUND_FLOOR"),
    ("SUBSUELO", 3.0, 18.5, 0.3, "BASEMENT"),
    ("UPS", 10.5, 17.8, 2.2, "BASEMENT"),
    ("Baterias", 16.5, 17.8, 2.0, "BASEMENT"),
    ("Tablero", 22.8, 17.8, 2.4, "BASEMENT"),
    ("Base", 3.3, 12.5, 0.5, "GROUND_FLOOR"),
]

SCENE_TEMPLATE = """<mujoco model="go2 data center">
  <include file="go2.xml"/>
  <visual>
    <headlight diffuse="0.55 0.55 0.55" ambient="0.35 0.35 0.35" specular="0 0 0"/>
    <global azimuth="90" elevation="-55" offwidth="1280" offheight="800"/>
    <quality shadowsize="4096"/>
  </visual>
  <asset>
    <texture type="2d" name="piso" builtin="checker" mark="edge" rgb1="0.30 0.32 0.36" rgb2="0.27 0.29 0.33"
      markrgb="0.22 0.23 0.26" width="300" height="300"/>
    <material name="piso" texture="piso" texuniform="true" texrepeat="1 1"/>
  </asset>
  <worldbody>
    <light pos="16 -11 20" dir="0.2 0.3 -1" directional="true" castshadow="true" diffuse="0.5 0.5 0.5"/>
    <geom name="floor" pos="16.4 -11 {basement_z}" size="26 18 0.05" type="plane" material="piso"/>
{geoms}
  </worldbody>
</mujoco>
"""


def _box(name, left, top, width, depth, bottom, height, rgba):
    """Caja alineada al plano: left/top/width/depth en coordenadas del plano, z desde `bottom`."""
    cx, cy = left + width / 2, -(top + depth / 2)
    return (f'    <geom name="{name}" type="box" pos="{cx:.2f} {cy:.2f} {bottom + height / 2:.3f}" '
            f'size="{width / 2:.2f} {depth / 2:.2f} {height / 2:.3f}" rgba="{rgba}" contype="0" conaffinity="0"/>')


def _marker(name, x, y, z, rgba, radius=0.45):
    return (f'    <geom name="{name}" type="cylinder" pos="{x:.2f} {-y:.2f} {z + 0.03:.2f}" size="{radius} 0.02" '
            f'rgba="{rgba}" contype="0" conaffinity="0"/>')


def build_scene_xml():
    depth = site.BASEMENT_DEPTH
    sx0, sx1 = site.STAIRS_X
    geoms = [
        # Planta baja: un bloque macizo con un hueco donde arranca la escalera.
        _box("pb_bloque", 0.8, 2.2, 31.2, site.STAIRS_TOP_Y - 2.2, -depth, depth, SLAB),
        _box("pb_borde_izq", 0.8, site.STAIRS_TOP_Y, sx0 - 0.8, 14.2 - site.STAIRS_TOP_Y, -depth, depth, SLAB),
        _box("pb_borde_der", sx1, site.STAIRS_TOP_Y, 32.0 - sx1, 14.2 - site.STAIRS_TOP_Y, -depth, depth, SLAB),
    ]
    for name, left, top, width, height, level in site.ROOMS:
        z = site.level_z(level)
        if name == "escalera_pb":  # el piso de la escalera de planta baja termina donde empiezan los escalones
            height = site.STAIRS_TOP_Y - top
        geoms.append(_box(f"piso_{name}", left + 0.1, top + 0.1, width - 0.2, height - 0.2, z, 0.02, ROOM_COLORS[name]))
    # Escalones: bajan de forma pareja desde la planta baja hasta el subsuelo.
    steps = 12
    run = (site.STAIRS_BOTTOM_Y - site.STAIRS_TOP_Y) / steps
    for i in range(steps):
        top_z = -depth * (i + 0.5) / steps
        geoms.append(_box(f"escalon_{i + 1}", sx0, site.STAIRS_TOP_Y + i * run, sx1 - sx0, run, -depth,
                          top_z + depth, STEP_COLOR))
    for name, left, top, width, depth_m, height in site.OBSTACLE_BOXES:
        bottom = site.level_z(site.level_at(top))
        geoms.append(_box(name, left, top, width, depth_m, bottom, height, EQUIPMENT_COLORS[name]))
    rgba = " ".join(str(c) for c in MARKER_COLORS["PENDING"])
    for cp, ((x, y), _) in site.CHECKPOINTS.items():
        geoms.append(_marker(f"cp_{cp.lower()}", x, y, site.level_z(site.level_at(y)), rgba))
    geoms.append(_marker("base", site.BASE[0], site.BASE[1], 0.0, "0.86 0.91 0.97 1", radius=0.8))
    return SCENE_TEMPLATE.format(geoms="\n".join(geoms), basement_z=-depth)


def checkpoint_label(cp, mark):
    """Texto del cartel de un checkpoint. `mark` = (estado, lectura) o None si todavía no se inspeccionó."""
    if mark is None:
        return cp
    status, reading = mark
    if status == "INSPECTING":
        return f"{cp}  midiendo..."
    if status == "SKIPPED" or reading is None:
        return f"{cp}  salteado"
    word = "OK" if status == "OK" else "ANOMALIA"
    return (f"{cp}  {word}  {reading['temperature_c']:.1f} C  {reading['humidity_pct']:.0f} %HR  "
            f"{reading['noise_dba']:.0f} dB")


class World:
    def __init__(self, model_dir, with_viewer=False):
        # El include de go2.xml se resuelve relativo al archivo de escena: va en la carpeta del modelo.
        scene_path = os.path.join(model_dir, "generated_scene.xml")
        with open(scene_path, "w", encoding="utf-8") as f:
            f.write(build_scene_xml())
        self.model = mujoco.MjModel.from_xml_path(scene_path)
        self.data = mujoco.MjData(self.model)
        mujoco.mj_resetDataKeyframe(self.model, self.data, self.model.key("home").id)
        self.home_qpos = self.data.qpos.copy()
        self.marker_ids = {cp: self.model.geom(f"cp_{cp.lower()}").id for cp in site.CHECKPOINTS}
        self.marks = {}
        self.viewer = None
        if with_viewer:
            from mujoco import viewer as mujoco_viewer  # sólo si se pide la ventana
            # Sin los paneles laterales y con la cámara de frente: se ven los dos niveles y la escalera.
            self.viewer = mujoco_viewer.launch_passive(
                self.model, self.data, show_left_ui=False, show_right_ui=False)
            with self.viewer.lock():
                self.set_overview_camera(self.viewer.cam)
                self.draw_labels(self.viewer.user_scn)
            self.viewer.sync()

    @staticmethod
    def set_overview_camera(cam):
        cam.lookat[:] = [16.4, -12.0, -1.5]
        cam.distance = 40.0
        cam.elevation = -50.0
        cam.azimuth = 90.0

    def show_checkpoints(self, marks):
        """Estado de cada checkpoint en la ronda actual: {cp: (estado, lectura)}. Se dibuja en el próximo place()."""
        self.marks = dict(marks)

    def place(self, plan_x, plan_y, heading_deg):
        """Ubica la base del robot. En el plano y crece hacia abajo; en MuJoCo, hacia arriba."""
        yaw = math.radians(-heading_deg)
        qpos = self.home_qpos.copy()
        qpos[0], qpos[1], qpos[2] = plan_x, -plan_y, site.floor_z(plan_y) + STANDING_HEIGHT
        qpos[3:7] = [math.cos(yaw / 2), 0.0, 0.0, math.sin(yaw / 2)]
        if self.viewer is not None:
            with self.viewer.lock():
                self._apply(qpos)
                self.draw_labels(self.viewer.user_scn)
            self.viewer.sync()
        else:
            self._apply(qpos)

    def draw_labels(self, scene):
        """Carteles fijos y de checkpoints en una escena de usuario (la del visor o la de un render)."""
        scene.ngeom = 0
        for text, x, y, height, level in STATIC_LABELS:
            self._label(scene, text, x, y, site.level_z(level) + height)
        for cp, ((x, y), _) in site.CHECKPOINTS.items():
            self._label(scene, checkpoint_label(cp, self.marks.get(cp)), x, y, site.level_z(site.level_at(y)) + 0.9)

    @staticmethod
    def _label(scene, text, x, y, z):
        if scene.ngeom >= scene.maxgeom:
            return
        geom = scene.geoms[scene.ngeom]
        mujoco.mjv_initGeom(geom, mujoco.mjtGeom.mjGEOM_LABEL, np.zeros(3), np.array([x, -y, z]),
                            np.eye(3).flatten(), np.array([1, 1, 1, 1], dtype=np.float32))
        geom.label = text
        scene.ngeom += 1

    def _apply(self, qpos):
        self.data.qpos[:] = qpos
        self.data.qvel[:] = 0.0
        for cp, geom_id in self.marker_ids.items():
            status = (self.marks.get(cp) or ("PENDING", None))[0]
            self.model.geom_rgba[geom_id] = MARKER_COLORS[status]
        mujoco.mj_forward(self.model, self.data)

    def plan_position(self):
        """Posición de la base leída del modelo, en coordenadas del plano."""
        return float(self.data.qpos[0]), float(-self.data.qpos[1])
