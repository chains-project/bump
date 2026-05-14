#!/usr/bin/env python3
"""Filter benchmark JSONs where failureCategory is TEST_FAILURE."""

import json
import shutil
from pathlib import Path

BENCHMARK_DIR = Path(__file__).parent.parent / "data" / "benchmark"
OUTPUT_DIR = Path(__file__).parent.parent / "data" / "benchmark_test_failures"

OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

all_files = sorted(BENCHMARK_DIR.glob("*.json"))
matched = 0
matched_human = 0
for json_file in all_files:
    with open(json_file) as f:
        data = json.load(f)
    if data.get("failureCategory") == "TEST_FAILURE":
        shutil.copy(json_file, OUTPUT_DIR / json_file.name)
        matched += 1

total = len(all_files)
print(f"Extracted {matched} / {total} files to {OUTPUT_DIR}")
