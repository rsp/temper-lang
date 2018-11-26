#!/bin/bash
# Make sure each file has legal boilerplate.

set -e

pushd "$(git rev-parse --show-toplevel)" >& /dev/null

EXIT_CODE=0

for nocopy in $( \
    find .  -name \*.java \
         -not -exec git check-ignore -q --no-index {} \; \
         -exec egrep -qi 'do.?not.?(submit|commit)' -- {} \; \
         -print \
); do
    echo "DO NOT COMMIT detected: $nocopy"
    EXIT_CODE=1
done

popd >& /dev/null

exit "$EXIT_CODE"
