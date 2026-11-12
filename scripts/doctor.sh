#!/usr/bin/env bash
set -euo pipefail
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
"$JAVA_HOME/bin/java" -version
if ! command -v python3 >/dev/null; then
    printf 'Python 3.10 이상을 설치해 주세요: https://www.python.org/downloads/\n' >&2
    exit 1
fi
python3 -c 'import sys, json, urllib.request, subprocess, socket; assert sys.version_info >= (3, 10), "Python 3.10 이상이 필요합니다: https://www.python.org/downloads/"'
printf 'JAVA_HOME=%s\n' "$JAVA_HOME"
