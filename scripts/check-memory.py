#!/usr/bin/env python3
"""Reports home's memory (MemoryBenchmark) and fails above the limits in .github/memory-budget.json (#56).

The limits are per metric name, in kB (e.g. "memoryHeapSizeMaxKb"); a metric without a limit is only reported.
The limits are absolute, about 1.5× the highest CI measurement. The same code measured a heap of 9.2 MB on most runners
but 21.2 MB on one (anonymous RSS 67 vs 79 MB), steady within a run: the Java heap's size depends on the machine, not
only on the app, so the limits sit above both and still catch a real leak.

Usage: scripts/check-memory.py <directory with *-benchmarkData.json>
"""
import json
import pathlib
import sys

import benchmarks

ROOT = pathlib.Path(__file__).resolve().parent.parent


def check(results_dir, budgets):
    """Returns (ok, lines to print)."""
    benchmark = benchmarks.load(results_dir).get("homeMemory")
    if benchmark is None:
        return False, [f"::error::No homeMemory result in {results_dir}"]
    memory = {name: m for name, m in benchmark.get("metrics", {}).items() if name.startswith("memory")}
    if not memory:
        return False, ["::error::homeMemory has no memory metrics"]
    ok = True
    lines = []
    for name, metric in sorted(memory.items()):
        median = metric["median"]
        budget = budgets.get(name)
        limit = "no limit" if budget is None else f"limit {budget / 1024:.0f} MB"
        lines.append(f"Home memory {name}: median {median / 1024:.1f} MB, max {metric['maximum'] / 1024:.1f} MB "
                     f"({limit})")
        if budget is not None and median > budget:
            lines.append(f"::error::{name} median {median / 1024:.1f} MB is over the limit of "
                         f"{budget / 1024:.0f} MB (.github/memory-budget.json)")
            ok = False
    return ok, lines


def main():
    budgets = json.loads((ROOT / ".github" / "memory-budget.json").read_text())
    ok, lines = check(sys.argv[1], budgets)
    print("\n".join(lines))
    benchmarks.summary("\n".join(line for line in lines if not line.startswith("::")))
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
