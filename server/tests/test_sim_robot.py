"""Tests con la física de MuJoCo: el robot camina, el LiDAR ve el obstáculo y la cámara saca fotos."""

import io
import math
import os
import random
import unittest

from go2server import site
from go2server.__main__ import DEFAULT_MODEL_DIR
from go2server.sim_body import PhysicsBody
from go2server.mission_control import MissionControl
from go2server.sim_robot import SimulatedRobot
from go2server.sim_scene import Scene

MODEL_DIR = os.path.abspath(DEFAULT_MODEL_DIR)
SITE_B = os.path.join(os.path.dirname(site.DEFAULT_SITE), "datacenter-edificio-b.json")


@unittest.skipUnless(os.path.exists(os.path.join(MODEL_DIR, "go2.xml")), "falta el modelo del Go2 (ver README)")
class SimulatedRobotTest(unittest.TestCase):

    def make(self, site_path=site.DEFAULT_SITE):
        self.site = site.load(site_path)
        self.scene = Scene(MODEL_DIR, self.site)
        self.body = PhysicsBody(self.scene)
        self.robot = SimulatedRobot(self.site, self.body, scene=self.scene, rng=random.Random(7))
        self.mission_control = MissionControl("go2-02", self.site, self.robot)
        self.events = []

    def run_until(self, condition, max_seconds=600):
        """Como el servidor: la física en cuadros cortos y el motor de la ronda una vez por segundo."""
        for _ in range(max_seconds):
            if condition():
                return
            for _ in range(10):
                self.body.frame(0.1)
            self.mission_control.tick(1.0)
            self.events += self.mission_control.drain_events()
        self.fail("La condición no se cumplió")

    def tilt_deg(self):
        w, x, y, z = self.scene.data.qpos[3:7]
        roll = math.atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y))
        pitch = math.asin(max(-1.0, min(1.0, 2 * (w * y - z * x))))
        return math.degrees(max(abs(roll), abs(pitch)))

    def test_the_robot_walks_to_the_first_checkpoint_and_the_position_comes_from_the_simulation(self):
        self.make()
        self.mission_control.handle("START_MISSION", {"mission": "FULL_ROUND"})
        self.run_until(lambda: any(e["kind"] == "CHECKPOINT_RESULT" for e in self.events))
        x, y = self.mission_control.plan_position()
        cx, cy = self.site.checkpoints["CP1"][0]
        self.assertLess(math.hypot(x - cx, y - cy), 0.3)
        self.assertLess(self.tilt_deg(), 15)
        self.assertGreater(self.scene.data.time, 30)  # caminó: no apareció ahí
        result = next(e for e in self.events if e["kind"] == "CHECKPOINT_RESULT")
        self.assertEqual(f"{self.mission_control.mission.id}_CP1", result["photo_id"])

    def test_the_lidar_stops_the_robot_before_the_box_and_it_goes_around_it_after_skip(self):
        self.make()
        self.mission_control.handle("START_MISSION", {"mission": "FULL_ROUND"})
        self.robot.trigger("OBSTACLE")
        self.run_until(lambda: self.mission_control.state == "AWAITING_DECISION")
        alert = self.mission_control.alerts[0]
        self.assertEqual("OBSTACLE", alert["code"])
        gap = self.body.obstacle_ahead(4.0)
        self.assertIsNotNone(gap)
        self.assertGreater(gap, 0.6)  # frenó antes de tocarla
        self.assertEqual((False, "OBSTACLE_PERSISTS"),
                         self.mission_control.handle("INCIDENT_DECISION", {"alert_id": alert["id"], "decision": "RETRY"}))
        self.mission_control.handle("INCIDENT_DECISION", {"alert_id": alert["id"], "decision": "SKIP"})
        skipped = alert["checkpoint_id"]
        self.run_until(lambda: any(e["kind"] == "CHECKPOINT_RESULT" and e["status"] != "SKIPPED"
                                   and e["checkpoint_id"] != skipped for e in self.events))
        self.assertLess(self.tilt_deg(), 15)

    def test_the_camera_sees_the_water_and_the_robot_walks_around_it_without_stepping_on_it(self):
        self.make()
        self.mission_control.handle("START_MISSION", {"mission": "FULL_ROUND"})
        self.robot.trigger("WATER")
        self.run_until(lambda: self.mission_control.state == "AWAITING_DECISION")
        alert = self.mission_control.alerts[0]
        self.assertEqual("WATER_DETECTED", alert["code"])
        self.assertIsNotNone(alert["photo_id"])
        self.mission_control.handle("INCIDENT_DECISION", {"alert_id": alert["id"], "decision": "CONTINUE"})
        water = self.site.path.point_at(self.robot.water_at)
        closest = float("inf")
        for _ in range(120):
            for _ in range(10):
                self.body.frame(0.1)
                x, y = self.body.position()
                closest = min(closest, math.hypot(x - water[0], y - water[1]))
            self.mission_control.tick(1.0)
            if self.mission_control.mission.inspected:
                break
        self.assertEqual(1, self.mission_control.mission.inspected)
        self.assertGreater(closest, 0.6)  # pasó por el costado del charco
        self.assertLess(self.tilt_deg(), 15)

    def test_the_front_camera_takes_a_jpeg(self):
        from PIL import Image
        self.make()
        jpeg = self.scene.photo(*self.body.pose())
        image = Image.open(io.BytesIO(jpeg))
        self.assertEqual(("JPEG", (320, 240)), (image.format, image.size))

    def test_a_single_floor_site_builds_its_scene_and_the_robot_stands(self):
        self.make(SITE_B)
        for _ in range(20):
            self.body.frame(0.1)
        self.assertLess(self.tilt_deg(), 5)
        self.assertGreater(self.scene.data.qpos[2], 0.2)


if __name__ == "__main__":
    unittest.main()
