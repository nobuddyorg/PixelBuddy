#!/usr/bin/env python3
"""Fails when cold startup is too slow compared with the system Settings app on the same emulator.

The budget in .github/startup-budget-ratio is PixelBuddy's median cold start divided by Settings' median cold start.
Measuring both on the same emulator in the same job makes the check independent of how fast the CI runner is: the
same code measured 382 ms on one runner and 645 ms on another.

Usage: scripts/check-startup.py <directory with *-benchmarkData.json> <file from scripts/reference-startup.sh>
"""
import pathlib
import sys

import benchmarks

ROOT = pathlib.Path(__file__).resolve().parent.parent


def check(results_dir, reference_file, budget):
    """Returns (ok, lines to print)."""
    startup = benchmarks.load(results_dir).get("coldStartup")
    if startup is None:
        return False, [f"::error::No coldStartup result in {results_dir}"]
    metric = startup["metrics"]["timeToInitialDisplayMs"]
    median = metric["median"]
    reference, fastest, slowest = benchmarks.reference_ms(reference_file)
    ratio = median / reference
    lines = [
        f"PixelBuddy cold startup: median {median:.0f} ms, min {metric['minimum']:.0f} ms, "
        f"max {metric['maximum']:.0f} ms",
        f"Settings cold startup: median {reference:.0f} ms, min {fastest} ms, max {slowest} ms",
        f"Cold startup on the emulator: PixelBuddy {median:.0f} ms, Settings {reference:.0f} ms, "
        f"ratio {ratio:.2f} of a {budget:.2f} budget",
    ]
    if ratio > budget:
        lines.append(
            f"::error::PixelBuddy starts {ratio:.2f}× as long as Settings, over the budget of {budget:.2f} "
            f"(.github/startup-budget-ratio)"
        )
        return False, lines
    return True, lines


def main():
    budget = float((ROOT / ".github" / "startup-budget-ratio").read_text().strip())
    ok, lines = check(sys.argv[1], sys.argv[2], budget)
    print("\n".join(lines))
    benchmarks.summary(next((line for line in lines if line.startswith("Cold startup")), lines[-1]))
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
