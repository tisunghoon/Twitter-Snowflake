#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
run_dir="build/verification/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$run_dir"
exec > >(tee "$run_dir/verify.log") 2>&1
step() {
    printf '[%s] 시작:' "$(date -u +%FT%TZ)"
    printf ' %s' "$@"
    printf '\n'
    if "$@"; then
        printf '[%s] 종료 코드: 0\n' "$(date -u +%FT%TZ)"
    else
        result=$?
        printf '[%s] 종료 코드: %s, 후속 단계 미실행\n' "$(date -u +%FT%TZ)" "$result"
        return "$result"
    fi
}
step ./scripts/doctor.sh
step ./gradlew --version
step ./gradlew spotlessCheck check bootJar
step ./scripts/smoke-test.sh --log-dir "$run_dir"
step python3 scripts/test_smoke.py --log-dir "$run_dir"
step python3 scripts/test_port_race.py --log-dir "$run_dir"
printf '검증 로그: %s\n' "$run_dir"
