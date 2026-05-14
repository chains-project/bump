#!/usr/bin/env python3
"""
Filter test-failure benchmarks to those whose PR was merged on GitHub.

Reads from: data/benchmark_test_failures/
Writes to:  data/benchmark_test_failures_merged/

Usage:
    export GITHUB_TOKEN=ghp_...   # recommended — avoids 60 req/hr rate limit
    python3 scripts/filter_merged_test_failures.py
"""

import json
import os
import re
import shutil
import time
from pathlib import Path

import requests

INPUT_DIR  = Path(__file__).parent.parent / "data" / "benchmark_test_failures"
OUTPUT_DIR = Path(__file__).parent.parent / "data" / "benchmark_test_failures_merged"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

try:
    from tokens import GITHUB_TOKEN
except ImportError:
    GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN", "")
PR_URL_RE = re.compile(r"https://github\.com/([^/]+)/([^/]+)/pull/(\d+)")

session = requests.Session()
session.headers["Accept"] = "application/vnd.github+json"
if GITHUB_TOKEN:
    session.headers["Authorization"] = f"Bearer {GITHUB_TOKEN}"


def is_merged(owner: str, repo: str, pr_number: str) -> bool:
    """Return True if the PR was merged. Blocks and retries on rate limit."""
    url = f"https://api.github.com/repos/{owner}/{repo}/pulls/{pr_number}/merge"
    while True:
        resp = session.get(url, timeout=15)
        if resp.status_code == 204:
            return True
        if resp.status_code in (404, 301):
            return False
        if resp.status_code == 403 and "rate limit" in resp.text.lower():
            reset = int(resp.headers.get("X-RateLimit-Reset", time.time() + 60))
            wait = max(reset - int(time.time()), 1)
            print(f"  Rate limited — waiting {wait}s (set GITHUB_TOKEN to avoid this)")
            time.sleep(wait + 1)
            continue
        resp.raise_for_status()


def main():
    if not GITHUB_TOKEN:
        print("Warning: GITHUB_TOKEN not set — unauthenticated limit is 60 req/hr.\n")

    files = sorted(INPUT_DIR.glob("*.json"))
    merged_count = 0

    for i, path in enumerate(files, 1):
        data = json.loads(path.read_text())
        m = PR_URL_RE.match(data.get("url", ""))

        if not m:
            print(f"[{i}/{len(files)}] SKIP  {path.name}")
            continue

        owner, repo, number = m.group(1), m.group(2), m.group(3)

        try:
            merged = is_merged(owner, repo, number)
        except requests.HTTPError as e:
            print(f"[{i}/{len(files)}] ERROR {owner}/{repo}#{number}: {e}")
            continue

        print(f"[{i}/{len(files)}] {'MERGED' if merged else 'not merged':10s}  {owner}/{repo}#{number}")

        if merged:
            shutil.copy(path, OUTPUT_DIR / path.name)
            merged_count += 1

        time.sleep(0.05)

    print(f"\nMerged: {merged_count} / {len(files)}  →  {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
