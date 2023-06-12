#!/bin/bash
# This script will perform some rudimentary analysis of the number of breaking updates found
# and the status of reproduction.

function get_fraction_as_percentage() {
  fraction=$(bc -l <<< "($1 * 100) / ($2 * 100) * 100")
  LC_NUMERIC=en_US.UTF-8 printf "%0.2f%%" "$fraction"
}

# Calculate basic numbers about the mining of breaking updates.
num_repos=$(grep -Proh "github.com/\w+/\w+/" dataset/*.json | sort | uniq | wc -l)
num_breaking=$(find dataset -iname "*.json" | wc -l)

# Calculate numbers concerning the reproduction process.
num_compilation_failure=$(grep -ro "\"COMPILATION_FAILURE\"" dataset | wc -l)
num_test_failure=$(grep -ro "\"TEST_FAILURE\"" dataset | wc -l)
num_unreproducible=$(find reproduction/unreproducible -type f | wc -l)
num_reproduced=$(("$num_compilation_failure" + "$num_test_failure"))
num_attempted=$(("$num_reproduced" + "$num_unreproducible"))

# Create STATS variable to use in the README.
STATS="## Stats
As of $(LC_TIME=en_US.UTF-8 date +"%b %-d %Y"):
  * The dataset consists of $num_breaking breaking updates from $num_repos different projects.
  * Reproduction has been attempted for $num_attempted ($(get_fraction_as_percentage "$num_attempted" "$num_breaking")) of these breaking updates.
    - Of these reproductions, $num_compilation_failure ($(get_fraction_as_percentage "$num_compilation_failure" "$num_attempted")) fail compilation with the updated dependency.
    - $num_test_failure ($(get_fraction_as_percentage "$num_test_failure" "$num_attempted")) fail tests with the updated dependency.
    - The remaining $num_unreproducible ($(get_fraction_as_percentage "$num_unreproducible" "$num_attempted")) could not be locally reproduced."
export STATS
