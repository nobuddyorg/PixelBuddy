#!/usr/bin/env python3
"""Fails when the median cold startup is slower than the budget in .github/startup-budget-ms.

Usage: scripts/check-startup.py <directory with *-benchmarkData.json from the startup macrobenchmark>
"""
import json
import os
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parent.parent
budget = int((root / ".github" / "startup-budget-ms").read_text().strip())
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
        print(f"Cold startup: median {metric['median']:.0f} ms, min {metric['minimum']:.0f} ms, "
              f"max {metric['maximum']:.0f} ms (budget {budget} ms)")

if not medians:
    print("::error::No coldStartup result")
    sys.exit(1)
median = max(medians)
summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary:
    with open(summary, "a") as out:
        out.write(f"Cold startup on the emulator: median {median:.0f} ms of a {budget} ms budget\n")
if median > budget:
    print(f"::error::Cold startup median {median:.0f} ms is over the budget of {budget} ms (.github/startup-budget-ms)")
    sys.exit(1)
