"""Reading Macrobenchmark results (*benchmarkData.json) and the Settings reference startup for the CI checks."""
import json
import pathlib
import statistics


def load(results_dir):
    """Every benchmark entry, by test name, from the result files under results_dir."""
    entries = {}
    for path in sorted(pathlib.Path(results_dir).rglob("*benchmarkData.json")):
        for benchmark in json.loads(path.read_text())["benchmarks"]:
            entries[benchmark["name"]] = benchmark
    return entries


def reference_ms(path):
    """Median cold start of the Settings app (scripts/reference-startup.sh), the yardstick for the runner's speed."""
    times = [int(line) for line in pathlib.Path(path).read_text().split() if line.strip()]
    if not times:
        raise ValueError(f"No reference startup times in {path}")
    return statistics.median(times), min(times), max(times)


def summary(text):
    """Adds a line to the GitHub job summary, if there is one."""
    import os

    target = os.environ.get("GITHUB_STEP_SUMMARY")
    if target:
        with open(target, "a") as out:
            out.write(text + "\n")
