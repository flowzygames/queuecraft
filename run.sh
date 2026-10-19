#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
javac --release 21 -d build src/*.java
if [[ "${1:-}" == "test" ]]; then
  java -cp build QueueCraftTest
else
  java -cp build QueueCraft "$@"
fi
