import copy
import json
import os
import random
import unittest

from go2server import site
from go2server.robot import Robot

SITES_DIR = os.path.dirname(site.DEFAULT_SITE)


def raw_site(name="datacenter-edificio-a.json"):
    with open(os.path.join(SITES_DIR, name), encoding="utf-8") as f:
        return json.load(f)


class SiteTest(unittest.TestCase):

    def test_every_example_site_loads(self):
        for name in os.listdir(SITES_DIR):
            if name.endswith(".json"):
                with self.subTest(name):
                    site.load(os.path.join(SITES_DIR, name))

    def test_the_app_gets_the_map_without_simulation_data(self):
        payload = site.load().app_payload()
        self.assertNotIn("simulation", payload)
        self.assertNotIn("scene_3d", payload)
        self.assertEqual(8, len(payload["checkpoints"]))

    def test_a_checkpoint_off_the_route_is_rejected(self):
        raw = copy.deepcopy(raw_site())
        raw["checkpoints"][0]["y"] += 2.0
        with self.assertRaisesRegex(site.SiteError, "CP1 no está sobre el recorrido"):
            site.Site(raw)

    def test_a_mission_with_an_unknown_checkpoint_is_rejected(self):
        raw = copy.deepcopy(raw_site())
        raw["missions"][0]["checkpoints"].append("CP9")
        with self.assertRaisesRegex(site.SiteError, "CP9"):
            site.Site(raw)

    def test_the_level_comes_from_the_site(self):
        building_a = site.load()
        self.assertEqual("BASEMENT", building_a.level_at(20.2))
        self.assertTrue(building_a.on_stairs(29.6, 16.0))
        building_b = site.Site(raw_site("datacenter-edificio-b.json"))
        self.assertEqual("GROUND_FLOOR", building_b.level_at(13.0))
        self.assertFalse(building_b.on_stairs(21.6, 11.2))

    def test_the_robot_runs_the_round_of_another_site(self):
        robot = Robot("go2-02", site.Site(raw_site("datacenter-edificio-b.json")), random.Random(1))
        self.assertEqual((True, None), robot.handle("START_MISSION", {"mission": "FULL_ROUND"}))
        events = []
        for _ in range(3000):
            if robot.state == "AT_BASE" and events:
                break
            robot.tick(1.0)
            events += robot.drain_events()
        results = [e["checkpoint_id"] for e in events if e["kind"] == "CHECKPOINT_RESULT"]
        self.assertEqual(["CP1", "CP2", "CP3", "CP4", "CP5"], results)


if __name__ == "__main__":
    unittest.main()
