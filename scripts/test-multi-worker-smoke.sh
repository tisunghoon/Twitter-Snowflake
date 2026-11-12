#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/doctor.sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
python3 scripts/test_multi_worker.py "$@"
python3 scripts/test_worker_config.py "$@"
