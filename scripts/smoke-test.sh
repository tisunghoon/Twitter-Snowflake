#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
exec python3 scripts/smoke_test.py "$@"
