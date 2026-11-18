import random
import unittest

from go2server import site
from go2server.sim_body import KinematicBody
from go2server.mission_control import MissionControl
from go2server.sim_robot import SimulatedRobot


class MissionControlTest(unittest.TestCase):

    def setUp(self):
        s = site.load()
        self.robot = SimulatedRobot(s, KinematicBody(s), rng=random.Random(42))
        self.mission_control = MissionControl("go2-02", s, self.robot)
        self.events = []

    def run_until(self, condition, max_seconds=3000):
        for _ in range(max_seconds):
            if condition():
                return
            self.mission_control.tick(1.0)
            self.events += self.mission_control.drain_events()
        self.fail("La condición no se cumplió")

    def start_full_round(self):
        self.assertEqual((True, None), self.mission_control.handle("START_MISSION", {"mission": "FULL_ROUND"}))
        self.events += self.mission_control.drain_events()

    def results(self):
        return [e for e in self.events if e["kind"] == "CHECKPOINT_RESULT"]

    def finished(self):
        return next(e for e in self.events if e["kind"] == "MISSION_FINISHED")

    def test_full_round_inspects_every_checkpoint_and_returns(self):
        self.start_full_round()
        self.run_until(lambda: self.mission_control.state == "AT_BASE")
        self.assertEqual([f"CP{i}" for i in range(1, 9)], [r["checkpoint_id"] for r in self.results()])
        self.assertEqual("COMPLETED", self.finished()["result"])

    def test_obstacle_can_be_skipped_but_not_retried_while_it_is_there(self):
        self.start_full_round()
        self.robot.trigger("OBSTACLE")
        self.run_until(lambda: self.mission_control.state == "AWAITING_DECISION")
        alert = self.mission_control.alerts[0]
        self.assertEqual("OBSTACLE", alert["code"])
        self.assertEqual((False, "OBSTACLE_PERSISTS"),
                         self.mission_control.handle("INCIDENT_DECISION", {"alert_id": alert["id"], "decision": "RETRY"}))
        self.assertEqual((True, None),
                         self.mission_control.handle("INCIDENT_DECISION", {"alert_id": alert["id"], "decision": "SKIP"}))
        self.events += self.mission_control.drain_events()
        self.run_until(lambda: self.mission_control.state == "AT_BASE")
        skipped = [r for r in self.results() if r["status"] == "SKIPPED"]
        self.assertEqual([alert["checkpoint_id"]], [r["checkpoint_id"] for r in skipped])

    def test_once_the_obstacle_is_cleared_retry_goes_on_to_the_checkpoint(self):
        self.start_full_round()
        self.robot.trigger("OBSTACLE")
        self.run_until(lambda: self.mission_control.state == "AWAITING_DECISION")
        alert = self.mission_control.alerts[0]
        self.robot.trigger("CLEAR_OBSTACLE")
        self.assertEqual((True, None),
                         self.mission_control.handle("INCIDENT_DECISION", {"alert_id": alert["id"], "decision": "RETRY"}))
        self.run_until(lambda: any(r["checkpoint_id"] == alert["checkpoint_id"] for r in self.results()))
        result = next(r for r in self.results() if r["checkpoint_id"] == alert["checkpoint_id"])
        self.assertNotEqual("SKIPPED", result["status"])

    def test_low_battery_interrupts_the_round(self):
        self.start_full_round()
        self.run_until(lambda: len(self.results()) == 1)
        self.robot.trigger("LOW_BATTERY")
        self.run_until(lambda: self.mission_control.state == "AT_BASE")
        self.assertEqual("INTERRUPTED_LOW_BATTERY", self.finished()["result"])

    def test_without_battery_to_come_back_the_robot_does_not_go_down(self):
        self.start_full_round()
        self.run_until(lambda: len(self.results()) == 5)
        self.robot.trigger("LOW_BATTERY")
        self.run_until(lambda: self.mission_control.state == "AT_BASE")
        codes = [a["code"] for a in self.mission_control.alerts]
        self.assertIn("BATTERY_INSUFFICIENT_FOR_BASEMENT", codes)
        self.assertEqual(["CP1", "CP2", "CP3", "CP4", "CP5"], [r["checkpoint_id"] for r in self.results()])
        self.assertEqual("INTERRUPTED_LOW_BATTERY", self.finished()["result"])

    def test_telemetry_follows_the_contract(self):
        self.start_full_round()
        self.run_until(lambda: len(self.results()) == 2)
        message = self.mission_control.telemetry({"latency_ms": 80, "rssi_dbm": -61})
        self.assertEqual("telemetry", message["type"])
        self.assertEqual("CP3", message["mission"]["current_cp"])
        self.assertEqual(3, message["mission"]["current_position"])
        self.assertEqual("GROUND_FLOOR", message["location"]["level"])


class SeparationTest(unittest.TestCase):

    def test_the_round_logic_does_not_depend_on_the_simulation(self):
        """mission_control.py sólo conoce el contrato del robot: pasar a un Go2 real no lo toca."""
        import inspect
        from go2server import mission_control
        source = inspect.getsource(mission_control)
        for simulated in ("mujoco", "sim_body", "sim_scene", "sim_robot", "random"):
            self.assertNotIn(f"import {simulated}", source)
            self.assertNotIn(f".{simulated} import", source)


if __name__ == "__main__":
    unittest.main()
