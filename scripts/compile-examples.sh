#!/usr/bin/env bash
# Compile every runnable example against the jar a consumer would actually get.
#
# `examples/` is not a Gradle source set — deliberately, so its files never reach the published jar
# or the coverage denominator — which also means `./gradlew check` never compiles them. Without
# this, an API change breaks every example silently and the first person to notice is a reader
# copying code that no longer builds.
#
# Mirrors the `examples` job in .github/workflows/ci.yml. Keep the two in step.
set -euo pipefail

cd "$(dirname "$0")/.."

count=$(find examples -maxdepth 1 -name '*.java' | wc -l | tr -d ' ')
if [ "$count" -lt 12 ]; then
    echo "expected >=12 examples, found $count" >&2
    exit 1
fi

# The version is pinned to a placeholder so the jar name below is predictable. Globbing it would
# be wrong: build/libs also holds the sources and javadoc jars once a publish task has run, and
# javac would read those as inputs.
./gradlew --quiet jar -Pversion=0.0.0-ci

out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT

javac -proc:none -Xlint:all -Werror \
    -d "$out" \
    -cp build/libs/mailkube-java-0.0.0-ci.jar \
    examples/*.java

echo "all $count examples compile"
