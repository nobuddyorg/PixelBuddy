#!/usr/bin/env python3
"""Reports frame timing of the home benchmarks and fails on a clear regression (#55).

Each test in FrameBenchmark gives frameDurationCpuMs and frameOverrunMs percentiles. The check uses the 90th
percentile of the CPU time per frame, scaled by the runner's speed: divided by the Settings app's median cold start
on the same emulator (in seconds), so a slow runner doesn't fail it. .github/frame-budget.json holds the limit per
test, about twice the first CI measurement (emulator frame times are noisy: few frames per run, software rendering),
so only a clear regression fails; a test without a limit is only reported.

Usage: scripts/check-frames.py <directory with *-benchmarkData.json> <file from scripts/reference-startup.sh>
"""
import json
import pathlib
import sys

import benchmarks

ROOT = pathlib.Path(__file__).resolve().parent.parent
TESTS = ["scrollAllApps", "dragRail", "typeSearch", "openAppMenu"]


def percentile(benchmark, metric, key):
    value = benchmark.get("sampledMetrics", {}).get(metric, {}).get(key)
    return None if value is None else float(value)


def fmt(value):
    return "–" if value is None else f"{value:.1f}"


def check(results_dir, reference_file, budgets):
    """Returns (ok, lines to print, markdown table for the job summary)."""
    results = benchmarks.load(results_dir)
    reference, _, _ = benchmarks.reference_ms(reference_file)
    ok = True
    lines = []
    table = [
        "| Frames | CPU P50 / P90 / P99 ms | Overrun P50 / P90 / P99 ms | Scaled CPU P90 | Budget |",
        "|---|---|---|---|---|",
    ]
    for name in TESTS:
        benchmark = results.get(name)
        if benchmark is None:
            lines.append(f"::error::No {name} result in {results_dir}")
            ok = False
            continue
        cpu = [percentile(benchmark, "frameDurationCpuMs", p) for p in ("P50", "P90", "P99")]
        overrun = [percentile(benchmark, "frameOverrunMs", p) for p in ("P50", "P90", "P99")]
        if cpu[1] is None:
            lines.append(f"::error::{name} has no frameDurationCpuMs")
            ok = False
            continue
        scaled = cpu[1] / (reference / 1000)
        budget = budgets.get(name)
        lines.append(
            f"{name}: CPU P90 {cpu[1]:.1f} ms, overrun P90 {fmt(overrun[1])} ms, "
            f"scaled {scaled:.1f} (budget {fmt(budget)})"
        )
        table.append(
            f"| {name} | {' / '.join(map(fmt, cpu))} | {' / '.join(map(fmt, overrun))} | {scaled:.1f} | "
            f"{fmt(budget)} |"
        )
        if budget is not None and scaled > budget:
            lines.append(
                f"::error::{name}: scaled CPU time per frame {scaled:.1f} is over the budget of {budget:.1f} "
                f"(.github/frame-budget.json)"
            )
            ok = False
    return ok, lines, table


def main():
    budgets = json.loads((ROOT / ".github" / "frame-budget.json").read_text())
    ok, lines, table = check(sys.argv[1], sys.argv[2], budgets)
    print("\n".join(lines))
    benchmarks.summary("\n".join(table))
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
