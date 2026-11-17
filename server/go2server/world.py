"""Escena de MuJoCo: el modelo del Go2 de MuJoCo Menagerie dentro del data center.

El movimiento es cinemático: la base del modelo se ubica sobre el recorrido en cada ciclo y las
patas quedan en la pose de pie ("home"). No hay controlador de locomoción. La posición que se
informa a la app se lee del estado del modelo.
"""

import math
import os

import mujoco

from . import site

STANDING_HEIGHT = 0.27

SCENE_TEMPLATE = """<mujoco model="go2 data center">
  <include file="go2.xml"/>
  <visual>
    <headlight diffuse="0.6 0.6 0.6" ambient="0.3 0.3 0.3" specular="0 0 0"/>
    <global azimuth="-90" elevation="-55"/>
  </visual>
  <asset>
    <texture type="2d" name="piso" builtin="checker" mark="edge" rgb1="0.82 0.85 0.88" rgb2="0.76 0.79 0.83"
      markrgb="0.6 0.6 0.6" width="300" height="300"/>
    <material name="piso" texture="piso" texuniform="true" texrepeat="1 1"/>
  </asset>
  <worldbody>
    <light pos="16 -11 12" dir="0 0 -1" directional="true"/>
    <geom name="floor" pos="16.4 -11 0" size="20 14 0.05" type="plane" material="piso"/>
{geoms}
  </worldbody>
</mujoco>
"""


def _box(name, left, top, width, depth, height, rgba):
    cx, cy = left + width / 2, -(top + depth / 2)
    return (f'    <geom name="{name}" type="box" pos="{cx:.2f} {cy:.2f} {height / 2:.2f}" '
            f'size="{width / 2:.2f} {depth / 2:.2f} {height / 2:.2f}" rgba="{rgba}" contype="0" conaffinity="0"/>')


def _marker(name, x, y, rgba, radius=0.35):
    return (f'    <geom name="{name}" type="cylinder" pos="{x:.2f} {-y:.2f} 0.01" size="{radius} 0.01" '
            f'rgba="{rgba}" contype="0" conaffinity="0"/>')


def build_scene_xml():
    geoms = [_box(*box, rgba="0.55 0.6 0.66 1") for box in site.OBSTACLE_BOXES]
    geoms += [_marker(f"cp_{cp.lower()}", x, y, "0.12 0.37 0.68 1") for cp, ((x, y), _) in site.CHECKPOINTS.items()]
    geoms.append(_marker("base", site.BASE[0], site.BASE[1], "0.86 0.91 0.97 1", radius=0.6))
    return SCENE_TEMPLATE.format(geoms="\n".join(geoms))


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
        self.viewer = None
        if with_viewer:
            from mujoco import viewer as mujoco_viewer  # sólo si se pide la ventana
            self.viewer = mujoco_viewer.launch_passive(self.model, self.data)

    def place(self, plan_x, plan_y, heading_deg):
        """Ubica la base del robot. En el plano y crece hacia abajo; en MuJoCo, hacia arriba."""
        yaw = math.radians(-heading_deg)
        qpos = self.home_qpos.copy()
        qpos[0], qpos[1], qpos[2] = plan_x, -plan_y, STANDING_HEIGHT
        qpos[3:7] = [math.cos(yaw / 2), 0.0, 0.0, math.sin(yaw / 2)]
        if self.viewer is not None:
            with self.viewer.lock():
                self._apply(qpos)
            self.viewer.sync()
        else:
            self._apply(qpos)

    def _apply(self, qpos):
        self.data.qpos[:] = qpos
        self.data.qvel[:] = 0.0
        mujoco.mj_forward(self.model, self.data)

    def plan_position(self):
        """Posición de la base leída del modelo, en coordenadas del plano."""
        return float(self.data.qpos[0]), float(-self.data.qpos[1])
