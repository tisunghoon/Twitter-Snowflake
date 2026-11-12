#!/usr/bin/env bash
set -euo pipefail
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
"$JAVA_HOME/bin/java" -version
command -v python3 >/dev/null
python3 -c 'import sys, json, urllib.request, subprocess, socket; assert sys.version_info >= (3, 10), "Python 3.10 이상이 필요합니다"'
printf 'JAVA_HOME=%s\n' "$JAVA_HOME"
