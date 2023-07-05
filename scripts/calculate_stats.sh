#!/bin/bash
# This script will perform some rudimentary analysis of the number of breaking updates found
# and the status of reproduction.

function get_fraction_as_percentage() {
  fraction=$(bc -l <<< "($1 * 100) / ($2 * 100) * 100")
  LC_NUMERIC=en_US.UTF-8 printf "%0.2f%%" "$fraction"
}

# Calculate basic numbers about the mining of breaking updates.
num_breaking=$(find dataset -iname "*.json" | wc -l)

# Calculate numbers concerning the reproduction process.
num_compilation_failure=$(grep -ro "\"COMPILATION_FAILURE\"" dataset | wc -l)
num_test_failure=$(grep -ro "\"TEST_FAILURE\"" dataset | wc -l)
num_dependency_resolution_failure=$(grep -ro "\"DEPENDENCY_RESOLUTION_FAILURE\"" dataset | wc -l)
num_mvn_enforcer_failure=$(grep -ro "\"MAVEN_ENFORCER_FAILURE\"" dataset | wc -l)
num_unknown_failure=$(grep -ro "\"UNKNOWN_FAILURE\"" dataset | wc -l)
num_unreproducible=$(find reproduction/unreproducible -type f | wc -l)
num_reproduced=$(("$num_compilation_failure" + "$num_test_failure" + "$num_dependency_resolution_failure" + "$num_mvn_enforcer_failure" + "$num_unknown_failure"))
num_attempted=$(("$num_reproduced" + "$num_unreproducible"))
num_not_attempted=$(("$num_breaking" - "$num_reproduced"))

# Create STATS variable to use in the README.
STATS="## Stats
As of $(LC_TIME=en_US.UTF-8 date +"%b %-d %Y"):
  * The dataset consists of $num_reproduced reproducible breaking updates.
    - Of these breaking updates, $num_compilation_failure ($(get_fraction_as_percentage "$num_compilation_failure" "$num_attempted")) fail compilation with the updated dependency.
    - $num_test_failure ($(get_fraction_as_percentage "$num_test_failure" "$num_attempted")) fail tests with the updated dependency.
    - $num_dependency_resolution_failure ($(get_fraction_as_percentage "$num_dependency_resolution_failure" "$num_attempted")) have dependency resolution failures with the updated dependency.
    - $num_mvn_enforcer_failure ($(get_fraction_as_percentage "$num_mvn_enforcer_failure" "$num_attempted")) fail after updating the dependency due to maven enforcer failures.
    - $num_unknown_failure ($(get_fraction_as_percentage "$num_unknown_failure" "$num_attempted")) fail due to unknown failures after updating the dependency.
  * Reproduction has been attempted for $num_attempted breaking updates, and $num_unreproducible ($(get_fraction_as_percentage "$num_unreproducible" "$num_attempted")) could not be locally reproduced.
  * For the remaining $num_not_attempted breaking updates in the dataset, reproduction has not been attempted yet."
export STATS
