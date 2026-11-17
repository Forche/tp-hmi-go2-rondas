"""Escena de MuJoCo: el modelo del Go2 de MuJoCo Menagerie dentro del data center.

El movimiento es cinemático: la base del modelo se ubica sobre el recorrido en cada ciclo y las
patas quedan en la pose de pie ("home"). No hay controlador de locomoción. La posición que se
informa a la app se lee del estado del modelo.

La escena se arma con el archivo de sitio: salas, equipos (con su altura), escalera y checkpoints.
Si hay subsuelo, la planta baja es un bloque macizo y el subsuelo queda al nivel del piso de la
escena. Sobre cada checkpoint hay un cartel con el resultado de la ronda actual.
"""

import math
import os

import mujoco
import numpy as np


STANDING_HEIGHT = 0.27

# Colores (r g b a) de la escena. Un área del sitio puede traer el suyo ("color": "#RRGGBB").
SLAB = "0.80 0.82 0.85 1"
FLOOR_COLORS = {
    ("ROOM", "GROUND_FLOOR"): "0.88 0.89 0.91 1",
    ("ROOM", "BASEMENT"): "0.62 0.64 0.68 1",
    ("COLD_AISLE", "GROUND_FLOOR"): "0.70 0.84 0.97 1",
    ("COLD_AISLE", "BASEMENT"): "0.70 0.84 0.97 1",
    ("STAIRS", "GROUND_FLOOR"): "0.86 0.82 0.74 1",
    ("STAIRS", "BASEMENT"): "0.70 0.68 0.64 1",
}
# Tipo de área con volumen -> (color, altura en m) si el sitio no los indica.
VOLUME_DEFAULTS = {"RACK_ROW": ("0.20 0.22 0.26 1", 2.0), "EQUIPMENT": ("0.92 0.66 0.22 1", 1.6)}
STEP_COLOR = "0.72 0.72 0.74 1"
BASE_COLOR = "0.86 0.91 0.97 1"

# Estado de cada checkpoint -> color del disco.
MARKER_COLORS = {
    "PENDING": (0.12, 0.37, 0.68, 1.0),
    "INSPECTING": (0.95, 0.75, 0.15, 1.0),
    "OK": (0.18, 0.65, 0.32, 1.0),
    "ANOMALY": (0.85, 0.22, 0.18, 1.0),
    "SKIPPED": (0.50, 0.52, 0.55, 1.0),
}

SCENE_TEMPLATE = """<mujoco model="go2 - {name}">
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
    <light pos="{cx} {cy} 20" dir="0.2 0.3 -1" directional="true" castshadow="true" diffuse="0.5 0.5 0.5"/>
    <geom name="floor" pos="{cx} {cy} {floor_z}" size="{sx} {sy} 0.05" type="plane" material="piso"/>
{geoms}
  </worldbody>
</mujoco>
"""


def _rgba(color, default):
    """"#RRGGBB" del archivo de sitio -> "r g b 1" de MuJoCo."""
    if not color:
        return default
    h = color.lstrip("#")
    return " ".join(f"{int(h[i:i + 2], 16) / 255:.2f}" for i in (0, 2, 4)) + " 1"


def _box(name, left, top, width, depth, bottom, height, rgba):
    """Caja alineada al plano: left/top/width/depth en coordenadas del plano, z desde `bottom`."""
    cx, cy = left + width / 2, -(top + depth / 2)
    return (f'    <geom name="{name}" type="box" pos="{cx:.2f} {cy:.2f} {bottom + height / 2:.3f}" '
            f'size="{width / 2:.2f} {depth / 2:.2f} {height / 2:.3f}" rgba="{rgba}" contype="0" conaffinity="0"/>')


def _marker(name, x, y, z, rgba, radius=0.45):
    return (f'    <geom name="{name}" type="cylinder" pos="{x:.2f} {-y:.2f} {z + 0.03:.2f}" size="{radius} 0.02" '
            f'rgba="{rgba}" contype="0" conaffinity="0"/>')


def _ground_floor_slab(site):
    """Con subsuelo, la planta baja es un bloque macizo con un hueco donde arranca la escalera."""
    depth = site.basement_depth
    ground = [a for a in site.areas
              if a["kind"] != "LEVEL_LABEL" and site.level_at(a["top"]) == "GROUND_FLOOR"]
    left = min(a["left"] for a in ground)
    top = min(a["top"] for a in ground)
    right = max(a["left"] + a["width"] for a in ground)
    bottom = max(a["top"] + a["height"] for a in ground)
    if site.stairs is None:
        return [_box("pb_bloque", left, top, right - left, bottom - top, -depth, depth, SLAB)]
    sx0, sx1, stairs_top, _ = site.stairs
    return [
        _box("pb_bloque", left, top, right - left, stairs_top - top, -depth, depth, SLAB),
        _box("pb_borde_izq", left, stairs_top, sx0 - left, bottom - stairs_top, -depth, depth, SLAB),
        _box("pb_borde_der", sx1, stairs_top, right - sx1, bottom - stairs_top, -depth, depth, SLAB),
    ]


def build_scene_xml(site):
    depth = site.basement_depth
    geoms = _ground_floor_slab(site) if depth > 0 else []
    for i, area in enumerate(site.areas):
        kind, left, top, width, height = area["kind"], area["left"], area["top"], area["width"], area["height"]
        level = site.level_at(top)
        z = site.level_z(level)
        if kind in VOLUME_DEFAULTS:
            color, volume_height = VOLUME_DEFAULTS[kind]
            geoms.append(_box(f"volumen_{i}", left, top, width, height, z,
                              float(area.get("height_m", volume_height)), _rgba(area.get("color"), color)))
        elif (kind, level) in FLOOR_COLORS:
            if kind == "STAIRS" and site.stairs is not None and top < site.stairs[2] < top + height:
                height = site.stairs[2] - top  # el piso termina donde empiezan los escalones
            geoms.append(_box(f"piso_{i}", left + 0.1, top + 0.1, width - 0.2, height - 0.2, z, 0.02,
                              _rgba(area.get("color"), FLOOR_COLORS[(kind, level)])))
    if site.stairs is not None and depth > 0:
        # Escalones: bajan de forma pareja desde la planta baja hasta el subsuelo.
        sx0, sx1, stairs_top, stairs_bottom = site.stairs
        steps = 12
        run = (stairs_bottom - stairs_top) / steps
        for i in range(steps):
            top_z = -depth * (i + 0.5) / steps
            geoms.append(_box(f"escalon_{i + 1}", sx0, stairs_top + i * run, sx1 - sx0, run, -depth,
                              top_z + depth, STEP_COLOR))
    rgba = " ".join(str(c) for c in MARKER_COLORS["PENDING"])
    for cp, ((x, y), _) in site.checkpoints.items():
        geoms.append(_marker(f"cp_{cp.lower()}", x, y, site.level_z(site.level_at(y)), rgba))
    bx, by = site.base
    geoms.append(_marker("base", bx, by, site.level_z(site.level_at(by)), BASE_COLOR, radius=0.8))
    name = "".join(c if c.isascii() and c not in '<>&"' else "-" for c in site.name)
    return SCENE_TEMPLATE.format(
        name=name, geoms="\n".join(geoms), floor_z=-depth,
        cx=f"{site.width / 2:.2f}", cy=f"{-site.height / 2:.2f}",
        sx=f"{site.width * 0.8:.1f}", sy=f"{site.height * 0.8:.1f}")


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
    def __init__(self, model_dir, site, with_viewer=False):
        self.site = site
        # El include de go2.xml se resuelve relativo al archivo de escena: va en la carpeta del modelo.
        scene_path = os.path.join(model_dir, "generated_scene.xml")
        with open(scene_path, "w", encoding="utf-8") as f:
            f.write(build_scene_xml(site))
        self.model = mujoco.MjModel.from_xml_path(scene_path)
        self.data = mujoco.MjData(self.model)
        mujoco.mj_resetDataKeyframe(self.model, self.data, self.model.key("home").id)
        self.home_qpos = self.data.qpos.copy()
        self.marker_ids = {cp: self.model.geom(f"cp_{cp.lower()}").id for cp in site.checkpoints}
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

    def set_overview_camera(self, cam):
        cam.lookat[:] = [self.site.width / 2, -self.site.height / 2 - 1.0, -self.site.basement_depth / 2]
        cam.distance = max(self.site.width, self.site.height) * 1.22
        cam.elevation = -50.0
        cam.azimuth = 90.0

    def show_checkpoints(self, marks):
        """Estado de cada checkpoint en la ronda actual: {cp: (estado, lectura)}. Se dibuja en el próximo place()."""
        self.marks = dict(marks)

    def place(self, plan_x, plan_y, heading_deg):
        """Ubica la base del robot. En el plano y crece hacia abajo; en MuJoCo, hacia arriba."""
        yaw = math.radians(-heading_deg)
        qpos = self.home_qpos.copy()
        qpos[0], qpos[1], qpos[2] = plan_x, -plan_y, self.site.floor_z(plan_x, plan_y) + STANDING_HEIGHT
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
        site = self.site
        for text, x, y, height in site.labels:
            self._label(scene, text, x, y, site.level_z(site.level_at(y)) + height)
        for cp, ((x, y), _) in site.checkpoints.items():
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
