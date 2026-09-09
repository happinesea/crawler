#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root_dir"

chmod +x ./gradlew
java -version
./gradlew --version
./gradlew clean test integrationTest bootJar --stacktrace
