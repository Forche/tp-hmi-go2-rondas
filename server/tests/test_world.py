import os
import unittest

from go2server.__main__ import DEFAULT_MODEL_DIR
from go2server.world import World

MODEL_DIR = os.path.abspath(DEFAULT_MODEL_DIR)


@unittest.skipUnless(os.path.exists(os.path.join(MODEL_DIR, "go2.xml")), "falta el modelo del Go2 (ver README)")
class WorldTest(unittest.TestCase):

    def test_the_robot_is_placed_where_the_plan_says(self):
        world = World(MODEL_DIR)
        world.place(15.2, 3.6, 90)
        x, y = world.plan_position()
        self.assertAlmostEqual(15.2, x, places=4)
        self.assertAlmostEqual(3.6, y, places=4)


if __name__ == "__main__":
    unittest.main()
