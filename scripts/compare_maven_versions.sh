#!/bin/bash
# Runs mvn package -DskipTests with both Maven 3 and Maven 4 against every
# pre-commit Docker image in data/benchmark/ and records the results.
#
# Both runs use the same Java version. If the image ships Java < 17 (Maven 4's
# minimum), Java 17 is installed for both runs so the comparison is fair.
# No other changes are made to the environment.
#
# Usage:
#   ./scripts/compare_maven_versions.sh [PARALLEL_JOBS]
#
# Output layout:
#   results/<commit>/maven3.log     — Maven 3 build output
#   results/<commit>/maven4.log     — Maven 4 build output
#   results/<commit>/result.json    — per-commit verdict
#   maven_comparison_summary.json  — aggregated summary across all commits
#
# Resume: commits that already have a result.json are skipped.
#
# Self-invocation pattern: when called with a single *.json argument the script
# processes just that one file. The parallel loop calls itself this way via
# xargs so exported-function portability is not a concern.

set -o nounset
set -o pipefail

MAVEN4_URL="https://dlcdn.apache.org/maven/maven-4/4.0.0-rc-5/binaries/apache-maven-4.0.0-rc-5-bin.tar.gz"
BENCHMARK_DIR="data/benchmark"
RESULTS_DIR="results"
SUMMARY_FILE="maven_comparison_summary.json"
PARALLEL="${1:-4}"

# ── Single-file mode (called by the parallel loop below) ─────────────────────
if [ $# -eq 1 ] && [[ "$1" == *.json ]]; then
  json_file=$1
  commit=$(basename "$json_file" .json)
  out_dir="$RESULTS_DIR/$commit"

  if [ -f "$out_dir/result.json" ]; then
    echo "$commit: SKIPPED (already done)"
    exit 0
  fi

  mkdir -p "$out_dir"

  image=$(jq -r '.preCommitReproductionCommand' "$json_file" | awk '{print $NF}')
  java_version=$(jq -r '.javaVersionUsedForReproduction' "$json_file")

  # Pull image (retry up to 3 times)
  pulled=false
  for attempt in 1 2 3; do
    if docker pull --quiet "$image" >> "$out_dir/pull.log" 2>&1; then
      pulled=true
      break
    fi
    sleep 10
  done
  if [ "$pulled" = false ]; then
    echo "$commit: PULL_FAILED"
    echo '{"maven3":"PULL_FAILED","maven4":"PULL_FAILED","note":"image pull failed"}' > "$out_dir/result.json"
    exit 0
  fi

  # Find the Maven 3 binary path before we override /usr/bin/mvn in the image
  maven3_bin=$(docker run --rm --entrypoint sh "$image" \
    -c "readlink -f /usr/bin/mvn 2>/dev/null || which mvn" 2>/dev/null || echo "")
  if [ -z "$maven3_bin" ]; then
    echo "$commit: MAVEN3_NOT_FOUND"
    echo '{"maven3":"MAVEN3_NOT_FOUND","maven4":"MAVEN3_NOT_FOUND","note":"could not locate mvn in base image"}' > "$out_dir/result.json"
    docker rmi -f "$image" > /dev/null 2>&1 || true
    exit 0
  fi

  # Build one test image per commit: only change is Maven 4 (+ Java 17 if needed)
  needs_java17=false
  if [ "$java_version" -lt 17 ] 2>/dev/null; then
    needs_java17=true
  fi

  tmpdir=$(mktemp -d)
  {
    echo "FROM $image"
    if [ "$needs_java17" = true ]; then
      # Detect Alpine vs Debian/Ubuntu and install Java 17 accordingly,
      # then create a stable symlink /usr/lib/jvm/java-17 for JAVA_HOME.
      cat <<'JAVA17_BLOCK'
RUN if command -v apk >/dev/null 2>&1; then \
      apk add --no-cache openjdk17-jdk \
      && ln -sf /usr/lib/jvm/java-17-openjdk /usr/lib/jvm/java-17; \
    else \
      apt-get update -qq \
      && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jdk-headless \
      && rm -rf /var/lib/apt/lists/* \
      && ln -sf /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-17; \
    fi
ENV JAVA_HOME=/usr/lib/jvm/java-17
JAVA17_BLOCK
    fi
    # Install Maven 4 and point /usr/bin/mvn at it
    echo "RUN wget -q $MAVEN4_URL -O /tmp/maven4.tar.gz \\"
    echo "    && tar -xzf /tmp/maven4.tar.gz -C /opt \\"
    echo "    && rm /tmp/maven4.tar.gz \\"
    echo "    && ln -sf /opt/apache-maven-4.0.0-rc-5/bin/mvn /usr/bin/mvn"
  } > "$tmpdir/Dockerfile"

  test_image="maven-compat-test:$commit"
  if ! docker build -q -t "$test_image" "$tmpdir" >> "$out_dir/pull.log" 2>&1; then
    echo "$commit: IMAGE_BUILD_FAILED"
    echo '{"maven3":"IMAGE_BUILD_FAILED","maven4":"IMAGE_BUILD_FAILED","note":"docker build of test image failed"}' \
      > "$out_dir/result.json"
    rm -rf "$tmpdir"
    exit 0
  fi
  rm -rf "$tmpdir"

  # Maven 3 run: call the original binary directly (Java 17 env applies if installed)
  maven3_status="FAILURE"
  if timeout 600 docker run --rm "$test_image" \
      sh -c "'$maven3_bin' package -DskipTests -B" \
      > "$out_dir/maven3.log" 2>&1; then
    maven3_status="SUCCESS"
  fi

  # Maven 4 run: /usr/bin/mvn now points to Maven 4
  maven4_status="FAILURE"
  if timeout 600 docker run --rm "$test_image" \
      sh -c "mvn package -DskipTests -B" \
      > "$out_dir/maven4.log" 2>&1; then
    maven4_status="SUCCESS"
  fi

  # Remove test image to keep disk usage bounded
  docker rmi -f "$test_image" > /dev/null 2>&1 || true
  # Remove pre-commit base image too
  docker rmi -f "$image" > /dev/null 2>&1 || true

  java_upgraded="$needs_java17"
  printf '{"maven3":"%s","maven4":"%s","javaVersionOriginal":"%s","javaUpgradedTo17":%s}\n' \
    "$maven3_status" "$maven4_status" "$java_version" "$java_upgraded" \
    > "$out_dir/result.json"

  echo "$commit: maven3=$maven3_status maven4=$maven4_status (java_orig=$java_version upgraded=$java_upgraded)"
  exit 0
fi

# ── Orchestration mode ────────────────────────────────────────────────────────
mkdir -p "$RESULTS_DIR"

total=$(find "$BENCHMARK_DIR" -name "*.json" | wc -l)
echo "Processing $total benchmark files with parallelism=$PARALLEL ..."
echo "Results will be written to $RESULTS_DIR/<commit>/{maven3.log,maven4.log,result.json}"
echo ""

find "$BENCHMARK_DIR" -name "*.json" | sort \
  | xargs -P "$PARALLEL" -I{} "$0" {}

# ── Summary generation ────────────────────────────────────────────────────────
echo ""
echo "Generating $SUMMARY_FILE ..."

python3 - <<PYEOF
import json, glob, os

results_dir = "$RESULTS_DIR"
summary_file = "$SUMMARY_FILE"

details = {}
for path in sorted(glob.glob(f"{results_dir}/*/result.json")):
    commit = os.path.basename(os.path.dirname(path))
    with open(path) as f:
        details[commit] = json.load(f)

def tally(tool):
    counts = {"SUCCESS": 0, "FAILURE": 0, "OTHER": 0}
    for r in details.values():
        s = r.get(tool, "")
        if s in counts:
            counts[s] += 1
        else:
            counts["OTHER"] += 1
    return counts

summary = {
    "total": len(details),
    "maven3": tally("maven3"),
    "maven4": tally("maven4"),
    "details": details,
}

with open(summary_file, "w") as f:
    json.dump(summary, f, indent=2)

m3 = summary["maven3"]
m4 = summary["maven4"]
print(f"Total processed : {summary['total']}")
print(f"Maven 3  — SUCCESS: {m3['SUCCESS']:4d}  FAILURE: {m3['FAILURE']:4d}  OTHER: {m3['OTHER']}")
print(f"Maven 4  — SUCCESS: {m4['SUCCESS']:4d}  FAILURE: {m4['FAILURE']:4d}  OTHER: {m4['OTHER']}")
print(f"Summary written to {summary_file}")
PYEOF
