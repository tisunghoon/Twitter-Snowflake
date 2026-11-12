# Twitter-Snowflake

Java 21과 Spring Boot로 시간·워커·순번을 조합하는 로컬 학습용 ID 생성기입니다.

## 실행
macOS에 JDK 21과 Python 3.10 이상이 필요합니다. Gradle은 체크섬을 고정한 Wrapper로 실행합니다.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew bootJar
java -jar build/libs/twitter-snowflake-0.0.1-SNAPSHOT.jar --snowflake.worker-id=1 --server.port=8080
```

```bash
curl -X POST http://localhost:8080/api/ids
./scripts/verify.sh
```

`id`는 십진 문자열이며 `timestamp`, `workerId`, `sequence`는 반환 ID에서 복원합니다.
워커는 0~1023의 필수 값이며 기본값이 없습니다. 같은 밀리초에는 워커당 4096개까지 발급합니다.
시간 범위 이탈·시계 역행·순번 소진은 HTTP 503과 도메인 오류 코드를 반환합니다. 자동 재시도는 없습니다.
같은 워커를 여러 생성기에서 재사용하거나 재시작하면 중복 가능성이 있습니다. 전역 엄격 정렬은 보장하지 않습니다.

## 검증
`./scripts/verify.sh`는 JVM 확인·서식·컴파일·JUnit·JAR·실제 HTTP·실패 정리를 검사합니다.
로그는 `build/verification/<실행 시각>/`에 보존합니다. 스모크 검사는 자신이 만든 Java 자식만 종료합니다.
선택한 포트가 사용 중이면 실패하며 기존 프로세스를 종료하지 않습니다. Python은 표준 라이브러리만 사용합니다.

```bash
./scripts/smoke-test.sh --port 18080 --worker 1 --log-dir build/verification/manual
```

[작업 진입 지침](context/AGENT.md)과 [설계 결정](context/decisions.md)을 확인합니다.
