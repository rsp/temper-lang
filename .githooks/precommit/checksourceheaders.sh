#!/bin/bash

set -e

pushd "$(git rev-parse --show-toplevel)" >& /dev/null

EXIT_CODE=0

for nocopy in $( \
        find .  -name \*.java \
        | xargs grep -c '^// Licensed under the Apache License, Version 2.0' \
        | perl -ne 'print if s/:0$//'
); do
    if ! git check-ignore -q "$nocopy"; then
        echo "Missing copyright header: $nocopy"
        EXIT_CODE=1
    fi
done

popd >& /dev/null

if [ "$EXIT_CODE" -ne "0" ]; then
    echo
    echo "Add a copyright header like

// Copyright $(date +%Y) Google, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
"

fi

exit "$EXIT_CODE"



