"""실행 JAR의 실제 HTTP 계약을 확인하고 자신이 만든 서버를 정리합니다."""
import argparse
import datetime
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import time
import urllib.error
import urllib.request


def post(port, path="/api/ids"):
    request = urllib.request.Request(f"http://127.0.0.1:{port}{path}", method="POST")
    with urllib.request.urlopen(request, timeout=2) as response:
        assert response.status == 200
        return json.load(response)


def validate(body, worker):
    raw = body["id"]
    assert isinstance(raw, str) and raw.isascii() and raw.isdecimal(), body
    value = int(raw)
    assert 0 <= value <= (1 << 63) - 1
    assert type(body["workerId"]) is int and body["workerId"] == worker
    assert type(body["sequence"]) is int and body["sequence"] == value & 4095
    assert (value >> 12) & 1023 == worker
    timestamp = datetime.datetime.fromisoformat(body["timestamp"].replace("Z", "+00:00"))
    epoch = datetime.datetime(2026, 1, 1, tzinfo=datetime.timezone.utc)
    delta = timestamp - epoch
    millis = delta.days * 86400000 + delta.seconds * 1000 + delta.microseconds // 1000
    assert delta.microseconds % 1000 == 0 and millis == value >> 22
    return value


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=0)
    parser.add_argument("--worker", type=int, default=1)
    parser.add_argument("--test-path", default="/api/ids")
    parser.add_argument("--log-dir", default="build/verification/smoke")
    args = parser.parse_args()
    log_dir = Path(args.log_dir)
    log_dir.mkdir(parents=True, exist_ok=True)
    jars = list(Path("build/libs").glob("*.jar"))
    jars = [p for p in jars if not p.name.endswith("-plain.jar")]
    assert len(jars) == 1, jars
    # 선점 검사 이후의 bind 경쟁은 자식 종료와 HTTP 준비 검사로 실패 처리합니다.
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", args.port))
        port = probe.getsockname()[1]
    process = None
    def interrupted(signum, frame):
        raise SystemExit(128 + signum)
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    try:
        with (log_dir / "server.log").open("w") as log:
            process = subprocess.Popen([
                str(Path(os.environ["JAVA_HOME"]) / "bin/java"), "-jar", str(jars[0]),
                "--server.address=127.0.0.1", f"--server.port={port}",
                f"--snowflake.worker-id={args.worker}"], stdout=log, stderr=log)
            (log_dir / "server.pid").write_text(str(process.pid))
            deadline = time.monotonic() + 30
            while True:
                if process.poll() is not None:
                    raise RuntimeError(f"서버 시작 실패: exit={process.returncode}, log={log_dir}")
                startup = (log_dir / "server.log").read_text()
                bound = f"Tomcat started on port {port} (http)" in startup
                started = "Started SnowflakeApplication" in startup
                if not (bound and started):
                    if time.monotonic() >= deadline:
                        raise TimeoutError("자식 서버 기동 완료 제한 시간 초과")
                    time.sleep(0.1)
                    continue
                try:
                    ready = post(port)
                    validate(ready, args.worker)
                    assert process.poll() is None
                    break
                except (urllib.error.URLError, TimeoutError, ConnectionError):
                    if time.monotonic() >= deadline:
                        raise TimeoutError("HTTP 준비 제한 시간 초과")
                    time.sleep(0.1)
            bodies = [ready]
            previous = validate(ready, args.worker)
            for _ in range(20):
                assert process.poll() is None
                body = post(port, args.test_path)
                assert process.poll() is None
                current = validate(body, args.worker)
                assert current > previous
                previous = current
                bodies.append(body)
            (log_dir / "responses.json").write_text(json.dumps(bodies, indent=2))
            print(f"HTTP 200·복원·증가 21건 통과: port={port}, worker={args.worker}")
    finally:
        if process is not None:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
            process.wait(timeout=5)
            assert process.poll() is not None
            (log_dir / "cleanup.txt").write_text(f"pid={process.pid}, exit={process.returncode}\n")


if __name__ == "__main__":
    main()
