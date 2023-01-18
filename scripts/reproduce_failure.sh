#!/bin/bash
# This script takes a branch name and will attempt to reproduce the breaking update
# contained in that branch. The location for any logs created will be in the
# directory given as the second parameter to the script.

set -o errexit   # Abort on nonzero exit status
set -o nounset   # Abort on unbound variable
set -o pipefail  # Don't hide errors within pipes

branch=$1  # The name of a git branch containing a breaking update
log_dir=$2 # A directory where Maven logs will be stored

successful_dir="$log_dir/successful"
unreproducible_dir="$log_dir/unreproducible"
successful="$log_dir/successful.json"
unreproducible="$log_dir/unreproducible.json"

function create_dir_if_necessary() {
  if [ ! -d "$1" ]; then
    echo "Creating $1" | ts
    mkdir "$1"
  fi
}

create_dir_if_necessary "$log_dir"
create_dir_if_necessary "$successful_dir"
create_dir_if_necessary "$unreproducible_dir"

# Make sure we have a file to store the info about successful reproductions in
if [ ! -f "$successful" ]; then
  echo "{}" >> "$successful"
fi

# Make sure we have a file to store the info about failed reproductions in
if [ ! -f "$unreproducible" ]; then
  echo "{}" >> "$unreproducible"
fi

temp_log="$log_dir/temporary.log"
commit=$(echo "$branch" | grep -Po "(?<=-)[0-9a-f]{40}")

function log_reproduction_failure() {
  jq --arg c "$commit" --arg t "$1" '. + {($c): {"label": ($t)}}' "$unreproducible" | sponge "$unreproducible"
  mv "$temp_log" "$unreproducible_dir/$commit.log"
}

function log_reproduction_success() {
  jq --arg c "$commit" --arg t "$1" '. + {($c): {"label": ($t)}}' "$successful" | sponge "$successful"
  mv "$temp_log" "$successful_dir/$commit.log"
}

# Start by checking out the commit previous to the commit containing
# the breaking update
echo "Processing $branch" | ts
git checkout -fq "$branch"^

# Clean the directory to remove the results of any previous builds
# NOTE: This will remove all untracked files!
git clean -fdxq

# See if this commit compiles correctly
if ! mvn clean compile --log-file "$temp_log"; then
  echo "Compile step failed for previous commit of $branch" | ts
  log_reproduction_failure "PRECEDING_COMMIT_COMPILATION_FAILURE"
  exit 1
fi

# If this worked, see if the compile step works for the updated dependency
git checkout -fq "$branch"
if ! mvn clean compile --log-file "$temp_log"; then
  echo "Successfully reproduced failing compilation of $branch" | ts
  log_reproduction_success "COMPILATION_FAILURE"
  exit 0
fi

# If we could compile, check if the tests worked in the previous commit
git checkout -fq "$branch"^
if ! mvn clean test --log-file "$temp_log"; then
  echo "Test step failed for previous commit of $branch" | ts
  log_reproduction_failure "PRECEDING_COMMIT_TEST_FAILURE"
  exit 1
fi

# If the tests worked previously, see if the tests work for the updated dependency
git checkout -fq "$branch"
if ! mvn clean test --log-file "$temp_log"; then
  echo "Successfully reproduced failing test of $branch" | ts
  log_reproduction_success "TEST_FAILURE"
  exit 0
fi

# Else, everything worked and we could not reproduce the failure
log_reproduction_failure "NO_FAILURE"
echo "Could not reproduce breaking update in $branch" | ts
