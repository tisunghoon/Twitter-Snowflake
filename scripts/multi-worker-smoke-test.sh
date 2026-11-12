#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/doctor.sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
exec python3 scripts/multi_worker_probe.py "$@"
