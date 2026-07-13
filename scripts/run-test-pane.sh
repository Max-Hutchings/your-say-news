#!/usr/bin/env bash
set -uo pipefail

if (( $# < 2 )); then
  echo "Usage: $0 <suite-name> <command> [args...]" >&2
  exit 2
fi

suite_name="$1"
shift

"$@"
test_status=$?

echo
if (( test_status == 0 )); then
  echo "PASS: $suite_name tests completed successfully."
else
  echo "FAIL: $suite_name tests exited with status $test_status."
fi
echo "Select this pane and press 'r' to rerun the suite, or 'q' to close the test runner."

# Keep the completed output visible as a live pane. mprocs terminates this loop
# before rerunning or quitting, while the test result remains clear in the log.
while true; do
  sleep 3600
done
