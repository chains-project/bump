#!/bin/bash
# This script will create a separate git branch based on a JSON file containing
# a breaking update, of the format of those found in the dataset folder.
# The branch will contain the state of a GitHub project at the commit
# of the breaking update pull request.

set -o errexit   # Abort on nonzero exit status
set -o nounset   # Abort on unbound variable
set -o pipefail  # Don't hide errors within pipes

file=$1
repo_url=$(grep -Po "https://.*github.com/.*/.*(?=/pull)" "$file")
commit=$(grep -Po "[0-9a-f]{40}" "$file")
# The "slug" is the github user and project name, e.g. apache/maven
slug=$(grep -Po "(?<=\.com/).*/.*(?=/pull)" "$file")

branch_name="branch-$slug-$commit"
# Check so this branch does not exist
if [[ $(git branch --list "$branch_name") ]]; then
  echo "The branch $branch_name already exists" | ts
  exit 1
# Create a new branch for the breaking update
else
  git checkout -b "$branch_name" breaking-update-branch-template
fi

# Fetch the repository at the point of the breaking commit
git fetch "$repo_url" "$commit"
# Merge the fetched data into this branch
git merge FETCH_HEAD --allow-unrelated-histories --no-edit
# Make sure that the head is located at the breaking update commit
git reset "$commit" --hard

# Go back to the main branch in order to proceed to the next breaking update
git checkout main
git gc --auto
echo "Created branch $branch_name" | ts
