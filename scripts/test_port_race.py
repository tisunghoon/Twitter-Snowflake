"""빈 포트 검사와 실제 bind 사이에 다른 정상 응답 서버가 끼어드는 경합입니다."""
import argparse
import http.server
import importlib.util
import json
import os
from pathlib import Path
import socket
import sys
import threading

parser = argparse.ArgumentParser()
parser.add_argument("--log-dir", required=True)
args = parser.parse_args()
folder = Path(args.log_dir) / "port-race"
spec = importlib.util.spec_from_file_location("smoke", "scripts/smoke_test.py")
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)
original_popen = smoke.subprocess.Popen
server = None
thread = None
requests = 0

class Competitor(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        global requests
        requests += 1
        body = json.dumps({"id": str(4096 + requests),
            "timestamp": "2026-01-01T00:00:00Z", "workerId": 1,
            "sequence": requests}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def log_message(self, *arguments):
        pass

def race(command, **kwargs):
    global server, thread
    port = int(next(item.split("=", 1)[1] for item in command
        if item.startswith("--server.port=")))
    server = http.server.ThreadingHTTPServer(("127.0.0.1", port), Competitor)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return original_popen(command, **kwargs)

smoke.subprocess.Popen = race
try:
    sys.argv = ["smoke_test.py", "--log-dir", str(folder)]
    rejected = False
    try:
        smoke.main()
    except (RuntimeError, TimeoutError):
        rejected = True
    assert rejected, "경쟁 서버 응답으로 거짓 성공했습니다"
    assert requests == 0, "자식 포트 소유권 확인 전에 경쟁 서버를 호출했습니다"
    pid = int((folder / "server.pid").read_text())
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        pass
    else:
        raise AssertionError(f"Java 자식 잔류: {pid}")
    with socket.create_connection(server.server_address, timeout=2):
        pass
    print("포트 경합 거짓 성공 차단·Java 정리·경쟁 리스너 생존 통과")
finally:
    smoke.subprocess.Popen = original_popen
    if server is not None:
        server.shutdown()
        server.server_close()
    if thread is not None:
        thread.join(timeout=5)
