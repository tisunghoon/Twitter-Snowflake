"""두 워커의 실제 HTTP 발급과 자기 자식의 수명만 관리합니다."""
import argparse
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import time
from smoke_test import post, validate


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--log-dir", default="build/verification/multi-worker")
    parser.add_argument("--port1", type=int, default=0)
    parser.add_argument("--port2", type=int, default=0)
    parser.add_argument("--worker2", type=int, default=2)
    parser.add_argument("--test-path", default="/api/ids")
    parser.add_argument("--pause-after-first", action="store_true")
    parser.add_argument("--pause-after-ready", action="store_true")
    parser.add_argument("--fail-before-second", action="store_true")
    args = parser.parse_args()
    root = Path(args.log_dir); root.mkdir(parents=True, exist_ok=True)
    jars = [p for p in Path("build/libs").glob("*.jar") if not p.name.endswith("-plain.jar")]
    assert len(jars) == 1
    children = []
    stopping = [0]
    # 비동기 예외를 던지지 않아 Popen 반환과 PID 등록 사이의 정리 공백을 피합니다.
    def stop(signum, frame):
        stopping[0] = signum
    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)
    deadline = time.monotonic() + 120
    def check():
        if stopping[0]: raise SystemExit(128 + stopping[0])
        if time.monotonic() > deadline: raise TimeoutError("전체 제한 시간 초과")
    def pause():
        while True:
            check(); time.sleep(.05)
    try:
        for index, (requested_port, worker) in enumerate(((args.port1, 1), (args.port2, args.worker2))):
            check()
            folder = root / f"worker-{index + 1}"; folder.mkdir(exist_ok=True)
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", requested_port)); port = probe.getsockname()[1]
            log = (folder / "server.log").open("w")
            try:
                process = subprocess.Popen([str(Path(os.environ["JAVA_HOME"]) / "bin/java"),
                    "-jar", str(jars[0]), "--server.address=127.0.0.1", f"--server.port={port}",
                    f"--snowflake.worker-id={worker}"], stdout=log, stderr=log)
            except BaseException:
                log.close(); raise
            children.append((process, port, worker, folder, log))
            (folder / "server.pid").write_text(str(process.pid))
            if index == 0:
                (root / "first-started").touch()
                if args.pause_after_first: pause()
                if args.fail_before_second: raise RuntimeError("두 번째 시작 전 실패 주입")
        prepared = []
        for process, port, worker, folder, log in children:
            startup_deadline = time.monotonic() + 30
            while True:
                check()
                if process.poll() is not None: raise RuntimeError(f"서버 기동 실패: {folder}")
                startup = (folder / "server.log").read_text()
                if (f"Tomcat started on port {port} (http)" in startup
                        and "Started SnowflakeApplication" in startup):
                    body = post(port); prepared.append(validate(body, worker))
                    assert process.poll() is None
                    break
                if time.monotonic() > startup_deadline: raise TimeoutError(f"준비 시간 초과: {folder}")
                time.sleep(.05)
        (root / "both-ready").touch()
        if args.pause_after_ready: pause()
        ids = []
        for process, port, worker, folder, log in children:
            previous = None
            for _ in range(2048):
                check()
                assert all(child.poll() is None for child, *_ in children)
                value = validate(post(port, args.test_path), worker)
                assert process.poll() is None
                if previous is not None: assert value > previous
                previous = value; ids.append(value)
        assert len(ids) == len(set(ids)) == 4096
        assert not (set(ids) & set(prepared))
        (root / "ids.json").write_text(json.dumps([str(value) for value in ids]))
        print("두 워커 실제 HTTP 4096건 고유성·복원 통과")
    finally:
        errors = []
        for process, port, worker, folder, log in children:
            try:
                if process.poll() is None:
                    process.terminate()
                    try: process.wait(timeout=5)
                    except subprocess.TimeoutExpired: process.kill()
                process.wait(timeout=5)
                (folder / "cleanup.txt").write_text(f"pid={process.pid}, exit={process.returncode}\n")
            except Exception as error:
                errors.append(error)
            finally:
                log.close()
        if errors: raise RuntimeError(f"자식 정리 실패: {errors}")


if __name__ == "__main__":
    main()
