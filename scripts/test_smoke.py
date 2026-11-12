"""시작 실패·HTTP 실패·포트 충돌에서 정리와 기존 리스너 보존을 검사합니다."""
import argparse
import os
from pathlib import Path
import socket
import subprocess
import sys

parser = argparse.ArgumentParser()
parser.add_argument("--log-dir", required=True)
args = parser.parse_args()
root = Path(args.log_dir)

def check(name, options):
    folder = root / name
    result = subprocess.run([sys.executable, "scripts/smoke_test.py", "--log-dir", str(folder),
        *options], capture_output=True, text=True, timeout=45)
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "probe.log").write_text(result.stdout + result.stderr)
    assert result.returncode != 0, name
    pid_file = folder / "server.pid"
    if pid_file.exists():
        pid = int(pid_file.read_text())
        try:
            os.kill(pid, 0)
        except ProcessLookupError:
            pass
        else:
            raise AssertionError(f"잔여 자식: {pid}")
        assert (folder / "cleanup.txt").exists()

check("invalid-worker", ["--worker", "-1"])
check("http-failure", ["--test-path", "/missing"])
with socket.socket() as listener:
    listener.bind(("127.0.0.1", 0))
    listener.listen()
    port = listener.getsockname()[1]
    check("occupied-port", ["--port", str(port)])
    with socket.create_connection(("127.0.0.1", port), timeout=2):
        connection, _ = listener.accept()
        connection.close()
print("시작 실패·HTTP 실패·기존 리스너 보존 통과")
