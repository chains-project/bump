#!/bin/bash
# Tests whether pre-commit Docker images in the benchmark still build
# successfully when Maven 3 is replaced with Maven 4.
#
# Usage: ./scripts/test_maven4_compat.sh [PARALLEL_JOBS]
#   PARALLEL_JOBS  Number of containers to run in parallel (default: 4)
#
# Outputs:
#   maven4_results.json          — commit hash → SUCCESS|FAILURE
#   reproductionLogs/maven4/     — per-commit build logs

set -o nounset
set -o pipefail

MAVEN4_URL="https://dlcdn.apache.org/maven/maven-4/4.0.0-rc-5/binaries/apache-maven-4.0.0-rc-5-bin.tar.gz"
MAVEN4_DIR="/tmp/apache-maven-4.0.0-rc-5"
BENCHMARK_DIR="data/benchmark"
RESULTS_FILE="maven4_results.json"
LOG_DIR="reproductionLogs/maven4"
PARALLEL="${1:-4}"

# Download Maven 4 once
if [ ! -d "$MAVEN4_DIR" ]; then
  echo "Downloading Maven 4..."
  wget -q "$MAVEN4_URL" -O /tmp/maven4.tar.gz
  tar -xzf /tmp/maven4.tar.gz -C /tmp
  rm /tmp/maven4.tar.gz
fi

mkdir -p "$LOG_DIR"
[ -f "$RESULTS_FILE" ] || echo "{}" > "$RESULTS_FILE"

test_one() {
  local json_file=$1
  local commit
  commit=$(basename "$json_file" .json)
  local image
  image=$(jq -r '.preCommitReproductionCommand' "$json_file" | awk '{print $NF}')
  local log="$LOG_DIR/$commit.log"

  docker pull --quiet "$image" >> "$log" 2>&1 || true

  local workdir
  workdir=$(docker inspect "$image" --format='{{.Config.WorkingDir}}' 2>/dev/null)
  # Fall back to /root/project if image has no WORKDIR set
  workdir="${workdir:-/root/project}"

  local status="FAILURE"
  if timeout 600 docker run --rm \
      -v "$MAVEN4_DIR:/opt/maven4:ro" \
      --entrypoint /bin/bash \
      "$image" \
      -c "export PATH=/opt/maven4/bin:\$PATH && cd '$workdir' && mvn package -DskipTests --no-transfer-progress" \
      >> "$log" 2>&1; then
    status="SUCCESS"
  fi

  # jq writes to a temp file then atomically replaces to avoid races
  local tmp
  tmp=$(mktemp)
  jq --arg c "$commit" --arg s "$status" '. + {($c): $s}' "$RESULTS_FILE" > "$tmp" && mv "$tmp" "$RESULTS_FILE"

  echo "$commit: $status"
}

export -f test_one
export MAVEN4_DIR LOG_DIR RESULTS_FILE

echo "Running Maven 4 compatibility test on all benchmark files (parallelism: $PARALLEL)..."
find "$BENCHMARK_DIR" -name "*.json" | sort | xargs -P "$PARALLEL" -I{} bash -c 'test_one "$@"' _ {}

echo ""
echo "Done. Results written to $RESULTS_FILE"
echo "Summary:"
jq 'to_entries | group_by(.value) | map({(.[0].value): length}) | add' "$RESULTS_FILE"
