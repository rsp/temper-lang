#!/bin/bash
# Run tests pre commit.

set -e

bazel build //...
bazel test src/test/...
