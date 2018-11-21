#!/bin/bash

set -ex

if [ -z "$( which genhtml )" ]; then
  echo genhtml not found on PATH.  Did you install lcov?
  false
fi

bazel coverage //...

WORKSPACE="$( bazel info workspace )"

pushd "$WORKSPACE"/src/main/java
find "$WORKSPACE"/bazel-testlogs/ -name coverage.dat \
  | xargs genhtml -o "$WORKSPACE"/coverage/
