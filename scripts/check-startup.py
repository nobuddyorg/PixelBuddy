#!/usr/bin/env python3
"""Fails when cold startup is too slow compared with the system Settings app on the same emulator.

The budget in .github/startup-budget-ratio is PixelBuddy's median cold start divided by Settings' median cold start.
Measuring both on the same emulator in the same job makes the check independent of how fast the CI runner is: the
same code measured 382 ms on one runner and 645 ms on another.

Usage: scripts/check-startup.py <directory with *-benchmarkData.json> <file from scripts/reference-startup.sh>
"""
import json
import os
import pathlib
import statistics
import sys

root = pathlib.Path(__file__).resolve().parent.parent
budget = float((root / ".github" / "startup-budget-ratio").read_text().strip())
results = sorted(pathlib.Path(sys.argv[1]).rglob("*benchmarkData.json"))
if not results:
    print(f"::error::No benchmark results in {sys.argv[1]}")
    sys.exit(1)

medians = []
for path in results:
    for benchmark in json.loads(path.read_text())["benchmarks"]:
        if benchmark["name"] != "coldStartup":
            continue
        metric = benchmark["metrics"]["timeToInitialDisplayMs"]
        medians.append(metric["median"])
        print(f"PixelBuddy cold startup: median {metric['median']:.0f} ms, min {metric['minimum']:.0f} ms, "
              f"max {metric['maximum']:.0f} ms")
if not medians:
    print("::error::No coldStartup result")
    sys.exit(1)
median = max(medians)

reference_times = [int(line) for line in pathlib.Path(sys.argv[2]).read_text().split() if line.strip()]
if not reference_times:
    print(f"::error::No reference startup times in {sys.argv[2]}")
    sys.exit(1)
reference = statistics.median(reference_times)
print(f"Settings cold startup: median {reference:.0f} ms, min {min(reference_times)} ms, "
      f"max {max(reference_times)} ms")

ratio = median / reference
message = (f"Cold startup on the emulator: PixelBuddy {median:.0f} ms, Settings {reference:.0f} ms, "
           f"ratio {ratio:.2f} of a {budget:.2f} budget")
print(message)
summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary:
    with open(summary, "a") as out:
        out.write(message + "\n")
if ratio > budget:
    print(f"::error::PixelBuddy starts {ratio:.2f}× as long as Settings, over the budget of {budget:.2f} "
          f"(.github/startup-budget-ratio)")
    sys.exit(1)
