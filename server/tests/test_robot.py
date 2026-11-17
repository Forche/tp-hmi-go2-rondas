import random
import unittest

from go2server import site
from go2server.robot import Robot


class RobotTest(unittest.TestCase):

    def setUp(self):
        self.robot = Robot("go2-02", site.load(), random.Random(42))
        self.events = []

    def run_until(self, condition, max_seconds=3000):
        for _ in range(max_seconds):
            if condition():
                return
            self.robot.tick(1.0)
            self.events += self.robot.drain_events()
        self.fail("La condición no se cumplió")

    def start_full_round(self):
        self.assertEqual((True, None), self.robot.handle("START_MISSION", {"mission": "FULL_ROUND"}))
        self.events += self.robot.drain_events()

    def results(self):
        return [e for e in self.events if e["kind"] == "CHECKPOINT_RESULT"]

    def finished(self):
        return next(e for e in self.events if e["kind"] == "MISSION_FINISHED")

    def test_full_round_inspects_every_checkpoint_and_returns(self):
        self.start_full_round()
        self.run_until(lambda: self.robot.state == "AT_BASE")
        self.assertEqual([f"CP{i}" for i in range(1, 9)], [r["checkpoint_id"] for r in self.results()])
        self.assertEqual("COMPLETED", self.finished()["result"])

    def test_obstacle_can_be_skipped_but_not_retried(self):
        self.start_full_round()
        self.robot.trigger("OBSTACLE")
        self.run_until(lambda: self.robot.state == "AWAITING_DECISION")
        alert = self.robot.alerts[0]
        self.assertEqual("OBSTACLE", alert["code"])
        self.assertEqual((False, "OBSTACLE_PERSISTS"),
                         self.robot.handle("INCIDENT_DECISION", {"alert_id": alert["id"], "decision": "RETRY"}))
        self.assertEqual((True, None),
                         self.robot.handle("INCIDENT_DECISION", {"alert_id": alert["id"], "decision": "SKIP"}))
        self.events += self.robot.drain_events()
        self.run_until(lambda: self.robot.state == "AT_BASE")
        skipped = [r for r in self.results() if r["status"] == "SKIPPED"]
        self.assertEqual([alert["checkpoint_id"]], [r["checkpoint_id"] for r in skipped])

    def test_low_battery_interrupts_the_round(self):
        self.start_full_round()
        self.run_until(lambda: len(self.results()) == 1)
        self.robot.trigger("LOW_BATTERY")
        self.run_until(lambda: self.robot.state == "AT_BASE")
        self.assertEqual("INTERRUPTED_LOW_BATTERY", self.finished()["result"])

    def test_telemetry_follows_the_contract(self):
        self.start_full_round()
        self.run_until(lambda: len(self.results()) == 2)
        message = self.robot.telemetry(self.robot.plan_position(), {"latency_ms": 80, "rssi_dbm": -61})
        self.assertEqual("telemetry", message["type"])
        self.assertEqual("CP3", message["mission"]["current_cp"])
        self.assertEqual(3, message["mission"]["current_position"])
        self.assertEqual("GROUND_FLOOR", message["location"]["level"])


if __name__ == "__main__":
    unittest.main()
