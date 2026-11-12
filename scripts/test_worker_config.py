"""실행 JAR의 필수 워커 설정을 환경 기본값 없이 검사합니다."""
import argparse
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--log-dir", required=True)
args = parser.parse_args()
root = Path(args.log_dir) / "invalid-config"
root.mkdir(parents=True, exist_ok=True)
jars = [p for p in Path("build/libs").glob("*.jar") if not p.name.endswith("-plain.jar")]
assert len(jars) == 1
clean = {key: value for key, value in os.environ.items()
    if not key.startswith("SNOWFLAKE_") and key not in {
        "SPRING_APPLICATION_JSON", "SPRING_CONFIG_LOCATION", "SPRING_CONFIG_ADDITIONAL_LOCATION",
        "SPRING_PROFILES_ACTIVE", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"}}
for value in (None, "-1", "1024"):
    name = "missing" if value is None else value
    command = [str(Path(os.environ["JAVA_HOME"]) / "bin/java"), "-jar", str(jars[0]),
        "--server.port=0", "--spring.config.location=optional:classpath:/__empty__.yml"]
    if value is not None: command.append(f"--snowflake.worker-id={value}")
    with (root / f"{name}.log").open("w") as log:
        process = subprocess.Popen(command, env=clean, stdout=log, stderr=log)
        try:
            code = process.wait(timeout=20)
            assert code != 0, name
        finally:
            if process.poll() is None:
                process.terminate()
                try: process.wait(timeout=5)
                except subprocess.TimeoutExpired: process.kill(); process.wait()
        assert process.poll() is not None
    text = (root / f"{name}.log").read_text()
    assert "snowflake" in text.lower() and "worker" in text.lower(), text
print("실제 JAR 워커 미설정/-1/1024 시작 실패 통과")
