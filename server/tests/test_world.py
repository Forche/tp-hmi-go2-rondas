"""Tests con la física de MuJoCo: el robot camina, el LiDAR ve el obstáculo y la cámara saca fotos."""

import io
import math
import os
import random
import unittest

from go2server import site
from go2server.__main__ import DEFAULT_MODEL_DIR
from go2server.body import PhysicsBody
from go2server.robot import Robot
from go2server.world import World

MODEL_DIR = os.path.abspath(DEFAULT_MODEL_DIR)
SITE_B = os.path.join(os.path.dirname(site.DEFAULT_SITE), "datacenter-edificio-b.json")


@unittest.skipUnless(os.path.exists(os.path.join(MODEL_DIR, "go2.xml")), "falta el modelo del Go2 (ver README)")
class WorldTest(unittest.TestCase):

    def make(self, site_path=site.DEFAULT_SITE):
        self.site = site.load(site_path)
        self.world = World(MODEL_DIR, self.site)
        self.body = PhysicsBody(self.world)
        self.robot = Robot("go2-02", self.site, random.Random(7), body=self.body)
        self.events = []

    def run_until(self, condition, max_seconds=600):
        """Como el servidor: la física en cuadros cortos y el motor de la ronda una vez por segundo."""
        for _ in range(max_seconds):
            if condition():
                return
            for _ in range(10):
                self.body.frame(0.1)
            self.robot.tick(1.0)
            self.events += self.robot.drain_events()
        self.fail("La condición no se cumplió")

    def tilt_deg(self):
        w, x, y, z = self.world.data.qpos[3:7]
        roll = math.atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y))
        pitch = math.asin(max(-1.0, min(1.0, 2 * (w * y - z * x))))
        return math.degrees(max(abs(roll), abs(pitch)))

    def test_the_robot_walks_to_the_first_checkpoint_and_the_position_comes_from_the_simulation(self):
        self.make()
        self.robot.handle("START_MISSION", {"mission": "FULL_ROUND"})
        self.run_until(lambda: any(e["kind"] == "CHECKPOINT_RESULT" for e in self.events))
        x, y = self.robot.plan_position()
        cx, cy = self.site.checkpoints["CP1"][0]
        self.assertLess(math.hypot(x - cx, y - cy), 0.3)
        self.assertLess(self.tilt_deg(), 15)
        self.assertGreater(self.world.data.time, 30)  # caminó: no apareció ahí
        result = next(e for e in self.events if e["kind"] == "CHECKPOINT_RESULT")
        self.assertEqual(f"{self.robot.mission.id}_CP1", result["photo_id"])

    def test_the_lidar_stops_the_robot_before_the_box_and_it_goes_around_it_after_skip(self):
        self.make()
        self.robot.handle("START_MISSION", {"mission": "FULL_ROUND"})
        self.robot.trigger("OBSTACLE")
        self.run_until(lambda: self.robot.state == "AWAITING_DECISION")
        alert = self.robot.alerts[0]
        self.assertEqual("OBSTACLE", alert["code"])
        gap = self.body.obstacle_ahead(4.0)
        self.assertIsNotNone(gap)
        self.assertGreater(gap, 0.6)  # frenó antes de tocarla
        self.assertEqual((False, "OBSTACLE_PERSISTS"),
                         self.robot.handle("INCIDENT_DECISION", {"alert_id": alert["id"], "decision": "RETRY"}))
        self.robot.handle("INCIDENT_DECISION", {"alert_id": alert["id"], "decision": "SKIP"})
        skipped = alert["checkpoint_id"]
        self.run_until(lambda: any(e["kind"] == "CHECKPOINT_RESULT" and e["status"] != "SKIPPED"
                                   and e["checkpoint_id"] != skipped for e in self.events))
        self.assertLess(self.tilt_deg(), 15)

    def test_the_front_camera_takes_a_jpeg(self):
        from PIL import Image
        self.make()
        jpeg = self.world.photo(*self.body.pose())
        image = Image.open(io.BytesIO(jpeg))
        self.assertEqual(("JPEG", (320, 240)), (image.format, image.size))

    def test_a_single_floor_site_builds_its_scene_and_the_robot_stands(self):
        self.make(SITE_B)
        for _ in range(20):
            self.body.frame(0.1)
        self.assertLess(self.tilt_deg(), 5)
        self.assertGreater(self.world.data.qpos[2], 0.2)


if __name__ == "__main__":
    unittest.main()
