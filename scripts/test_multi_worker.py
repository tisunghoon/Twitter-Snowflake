"""다중 서버 성공과 실패·중단 정리를 실제 프로세스로 검사합니다."""
import argparse
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import time
import uuid

parser = argparse.ArgumentParser()
parser.add_argument("--log-dir", required=True)
parser.add_argument("--normal-only", action="store_true")
args = parser.parse_args()
root = Path(args.log_dir) / ("run-" + uuid.uuid4().hex)
print(f"다중 워커 하네스 로그: {root}", flush=True)

def verify_cleanup(folder, expected):
    paths = list(folder.glob("worker-*/server.pid"))
    assert len(paths) == expected, (folder, len(paths), expected)
    for path in paths:
        try:
            os.kill(int(path.read_text()), 0)
        except ProcessLookupError:
            pass
        else:
            raise AssertionError(f"잔여 자식: {path.read_text()}")
        assert (path.parent / "cleanup.txt").exists()

def run(name, options=(), interrupt=None, success=False, expected=2):
    folder = root / name
    folder.mkdir(parents=True, exist_ok=True)
    with (folder / "harness.log").open("w") as log:
        process = subprocess.Popen(["./scripts/multi-worker-smoke-test.sh", "--log-dir", str(folder),
            *options], stdout=log, stderr=log)
        try:
            if interrupt:
                marker = "first-started" if "--pause-after-first" in options else "both-ready"
                deadline = time.monotonic() + 35
                while not (folder / marker).exists():
                    assert process.poll() is None
                    assert time.monotonic() < deadline
                    time.sleep(.05)
                process.send_signal(interrupt)
            code = process.wait(timeout=140)
            assert (code == 0) == success, (name, code)
            if interrupt: assert code == 128 + interrupt, (name, code)
            verify_cleanup(folder, expected)
        finally:
            if process.poll() is None:
                process.terminate()
                try: process.wait(timeout=15)
                except subprocess.TimeoutExpired: process.kill(); process.wait()
    return folder

normal = run("normal", success=True)
assert "4096" in (normal / "harness.log").read_text(), "4096건 검증 결과가 없습니다"
assert len(json.loads((normal / "ids.json").read_text())) == 4096
if not args.normal_only:
    run("second-invalid", ["--worker2", "-1"])
    run("http-failure", ["--test-path", "/missing"])
    run("before-second-failure", ["--fail-before-second"], expected=1)
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0)); listener.listen()
        run("second-occupied", ["--port2", str(listener.getsockname()[1])], expected=1)
        with socket.create_connection(listener.getsockname(), timeout=2):
            connection, _ = listener.accept(); connection.close()
        for sig in (signal.SIGINT, signal.SIGTERM):
            run(f"first-only-{sig.name}", ["--pause-after-first"], interrupt=sig, expected=1)
            with socket.create_connection(listener.getsockname(), timeout=2):
                connection, _ = listener.accept(); connection.close()
            run(f"both-ready-{sig.name}", ["--pause-after-ready"], interrupt=sig)
    print("다중 서버 실패·중단·기존 리스너 보존 통과")
