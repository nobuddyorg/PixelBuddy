"""Tests for the CI benchmark checks: python3 -m unittest discover -s scripts"""
import importlib.util
import json
import pathlib
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent


def script(name):
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), HERE / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


check_startup = script("check-startup")
check_frames = script("check-frames")
check_memory = script("check-memory")


class Results:
    """A results directory with benchmark entries and a reference file."""

    def __init__(self, entries, reference=(800, 790, 810)):
        self.dir = tempfile.TemporaryDirectory()
        root = pathlib.Path(self.dir.name)
        (root / "out").mkdir()
        (root / "out" / "x-benchmarkData.json").write_text(json.dumps({"benchmarks": entries}))
        (root / "reference.txt").write_text("\n".join(map(str, reference)))
        self.results = root / "out"
        self.reference = root / "reference.txt"


def startup(median):
    return {"name": "coldStartup", "metrics": {"timeToInitialDisplayMs": {"median": median, "minimum": median - 20,
                                                                           "maximum": median + 40}}}


def frames(name, cpu_p90, overrun_p90=-4.0):
    return {
        "name": name,
        "sampledMetrics": {
            "frameDurationCpuMs": {"P50": cpu_p90 / 2, "P90": cpu_p90, "P95": cpu_p90, "P99": cpu_p90 * 2},
            "frameOverrunMs": {"P50": -8.0, "P90": overrun_p90, "P95": 0.0, "P99": 3.0},
        },
    }


class CheckTest(unittest.TestCase):
    def results(self, entries, reference=(800, 790, 810)):
        r = Results(entries, reference)
        self.addCleanup(r.dir.cleanup)
        return r


class StartupTest(CheckTest):
    def test_within_the_ratio_passes(self):
        r = self.results([startup(336)])
        ok, lines = check_startup.check(r.results, r.reference, 0.53)
        self.assertTrue(ok)
        self.assertIn("ratio 0.42 of a 0.53 budget", lines[-1])

    def test_a_slow_runner_slows_both_and_still_passes(self):
        r = self.results([startup(600)], reference=(1400, 1500, 1450))
        self.assertTrue(check_startup.check(r.results, r.reference, 0.53)[0])

    def test_over_the_ratio_fails(self):
        r = self.results([startup(500)])
        ok, lines = check_startup.check(r.results, r.reference, 0.53)
        self.assertFalse(ok)
        self.assertTrue(lines[-1].startswith("::error::"))

    def test_missing_result_fails(self):
        r = self.results([])
        self.assertFalse(check_startup.check(r.results, r.reference, 0.53)[0])


class FramesTest(CheckTest):
    def all_tests(self, cpu_p90):
        return [frames(name, cpu_p90) for name in check_frames.TESTS]

    def test_without_budgets_frames_are_only_reported(self):
        r = self.results(self.all_tests(40.0))
        ok, lines, table = check_frames.check(r.results, r.reference, {})
        self.assertTrue(ok)
        self.assertEqual(len(check_frames.TESTS), len(lines))
        self.assertEqual(2 + len(check_frames.TESTS), len(table))

    def test_cpu_time_is_scaled_by_the_runner_speed(self):
        # 8 ms per frame with Settings starting in 0.8 s: 10 per second of Settings start.
        r = self.results(self.all_tests(8.0))
        ok, lines, _ = check_frames.check(r.results, r.reference, {"dragRail": 12.0})
        self.assertTrue(ok)
        self.assertIn("scaled 10.0 (budget 12.0)", lines[1])

    def test_over_the_budget_fails(self):
        r = self.results(self.all_tests(8.0))
        ok, lines, _ = check_frames.check(r.results, r.reference, {"scrollAllApps": 9.0})
        self.assertFalse(ok)
        self.assertTrue(any(line.startswith("::error::scrollAllApps") for line in lines))

    def test_a_missing_test_fails(self):
        r = self.results([frames("dragRail", 8.0)])
        self.assertFalse(check_frames.check(r.results, r.reference, {})[0])


def memory(heap_kb):
    return {
        "name": "homeMemory",
        "metrics": {
            "memoryHeapSizeMaxKb": {"median": heap_kb, "minimum": heap_kb, "maximum": heap_kb + 512},
            "memoryRssAnonMaxKb": {"median": heap_kb * 3, "minimum": heap_kb * 3, "maximum": heap_kb * 3},
        },
    }


class MemoryTest(CheckTest):
    def test_without_limits_memory_is_only_reported(self):
        r = self.results([memory(20 * 1024)])
        ok, lines = check_memory.check(r.results, {})
        self.assertTrue(ok)
        self.assertIn("memoryHeapSizeMaxKb: median 20.0 MB, max 20.5 MB (no limit)", lines[0])

    def test_over_the_limit_fails(self):
        r = self.results([memory(40 * 1024)])
        ok, lines = check_memory.check(r.results, {"memoryHeapSizeMaxKb": 32 * 1024})
        self.assertFalse(ok)
        self.assertTrue(lines[1].startswith("::error::memoryHeapSizeMaxKb"))

    def test_a_missing_result_fails(self):
        r = self.results([])
        self.assertFalse(check_memory.check(r.results, {})[0])


if __name__ == "__main__":
    unittest.main()
