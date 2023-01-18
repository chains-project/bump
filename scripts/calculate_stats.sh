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

# Print nicely formatted stats
printf "As of %s:\n" "$(LC_TIME=en_US.UTF-8 date +"%b %-d %Y")"
printf "  * The dataset consists of %d breaking updates from %d different projects.\n" \
       "$num_breaking" "$num_repos"
printf "  * Reproduction has been attempted for %s (%s) of these breaking updates.\n" \
       "$num_attempted" "$(get_fraction_as_percentage "$num_attempted" "$num_breaking")"
printf "    - Of these reproductions, %s (%s) fail compilation with the updated dependency.\n" \
       "$num_compilation_failure" "$(get_fraction_as_percentage "$num_compilation_failure" "$num_attempted")"
printf "    - %s (%s) fail tests with the updated dependency.\n" \
       "$num_test_failure" "$(get_fraction_as_percentage "$num_test_failure" "$num_attempted")"
printf "    - The remaining %s (%s) could not be locally reproduced.\n" \
       "$num_unreproducible" "$(get_fraction_as_percentage "$num_unreproducible" "$num_attempted")"
