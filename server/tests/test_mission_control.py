import random
import unittest

from go2server import site
from go2server.sim_body import KinematicBody
from go2server.mission_control import MissionControl
from go2server.sim_random import RandomSituations
from go2server.sim_robot import SCENARIOS, SimulatedRobot


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

    def decide(self, alert, decision):
        return self.mission_control.handle("INCIDENT_DECISION", {"alert_id": alert["id"], "decision": decision})

    def test_water_stops_the_robot_and_continue_goes_around_it(self):
        self.start_full_round()
        self.robot.trigger("WATER")
        self.run_until(lambda: self.mission_control.state == "AWAITING_DECISION")
        alert = self.mission_control.alerts[0]
        self.assertEqual(("WATER_DETECTED", "CRITICAL", ["CONTINUE", "WATCH"]),
                         (alert["code"], alert["severity"], alert["options"]))
        self.assertGreater(self.robot.water_at - self.mission_control.distance, 1.0)  # frenó antes de pisarla
        self.assertEqual((True, None), self.decide(alert, "WATCH"))
        self.assertEqual("AWAITING_DECISION", self.mission_control.state)
        self.assertEqual((True, None), self.decide(alert, "CONTINUE"))
        self.run_until(lambda: self.mission_control.state == "AT_BASE")
        self.assertEqual("COMPLETED", self.finished()["result"])

    def test_smoke_ends_the_round_and_the_robot_goes_back_without_getting_closer(self):
        self.start_full_round()
        self.run_until(lambda: len(self.results()) == 1)
        self.robot.trigger("SMOKE")
        self.run_until(lambda: self.mission_control.state == "RETURNING")
        alert = self.mission_control.alerts[0]
        self.assertEqual(("SMOKE_DETECTED", "CRITICAL", False), (alert["code"], alert["severity"], alert["requires_decision"]))
        self.assertGreater(self.robot.smoke_at - self.mission_control.distance, 2.0)
        self.run_until(lambda: self.mission_control.state == "AT_BASE")
        self.assertEqual("INTERRUPTED", self.finished()["result"])

    def test_return_to_base_interrupts_the_round_and_cancel_cancels_it(self):
        for command, result in (("RETURN_TO_BASE", "INTERRUPTED"), ("CANCEL", "CANCELLED")):
            self.setUp()
            self.start_full_round()
            self.run_until(lambda: len(self.results()) == 1)
            self.assertEqual((True, None), self.mission_control.handle(command, {}))
            self.run_until(lambda: self.mission_control.state == "AT_BASE")
            self.assertEqual(result, self.finished()["result"])

    def test_humidity_and_noise_out_of_range_raise_alerts(self):
        self.start_full_round()
        self.robot.trigger("HUMIDITY")
        self.robot.trigger("NOISE")
        self.run_until(lambda: len(self.results()) == 1)
        self.assertEqual("ANOMALY", self.results()[0]["status"])
        codes = {a["code"] for a in self.mission_control.alerts}
        self.assertTrue({"HUMIDITY_OUT_OF_RANGE", "NOISE_HIGH"} <= codes)

    def test_telemetry_follows_the_contract(self):
        self.start_full_round()
        self.run_until(lambda: len(self.results()) == 2)
        message = self.mission_control.telemetry({"latency_ms": 80, "rssi_dbm": -61})
        self.assertEqual("telemetry", message["type"])
        self.assertEqual("CP3", message["mission"]["current_cp"])
        self.assertEqual(3, message["mission"]["current_position"])
        self.assertEqual("GROUND_FLOOR", message["location"]["level"])


class RandomSituationsTest(unittest.TestCase):

    def test_something_happens_every_few_minutes_and_the_round_enders_only_once(self):
        situations = RandomSituations(random.Random(3))
        seen = [situations.tick(10.0, "m-1") for _ in range(2000)]
        happened = [s for s in seen if s]
        self.assertGreater(len(happened), 100)
        self.assertLessEqual(happened.count("bateria"), 1)
        self.assertLessEqual(happened.count("humo"), 1)
        self.assertTrue(set(happened) <= set(SCENARIOS) | {"corte"})


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
