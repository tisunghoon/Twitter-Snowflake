# F-01: ID 발급 결과 확인

## 상태와 승인
- 원문: 2026-11-12 사용자 직접 지시. Agy 결과를 검증된 기반으로 채택하지 않고 F-01을 재작성합니다. GitHub 이슈는 없습니다.
- 담당: Codex(wG:p3). 구현·자체 검증·리뷰 수정·커밋을 기능 전체 단위로 담당합니다.
- 상태: 구현·필수 검증·독립 재리뷰·커밋 완료입니다.
- 확정: D-01(Java + Spring Boot), D-09(기능별 메인 담당과 새 컨텍스트 독립 리뷰), F-01→04 순서, 재작성 요청.
- D-02~07: 처음에는 미확정이었으며, 계획 검수 후 사용자의 구현 시작 지시로 확정했습니다.
- 기준: `e154684`. 인수 당시 수정 파일은 이 plan.md와 progress.md이며, 비추적 `.idea/`, `.omc/`, `context/.omc/`는 보존합니다.

## 기존 결과 검토와 재작성 범위
실제 파일 목록을 확인한 결과 제품 소스·테스트·빌드 파일·Wrapper·스크립트는 없습니다. 재작성 대상은 계획이며, 재사용할 검증된 제품 구현은 없습니다.
원본 계획·progress와 원본 전체 diff는 저장소 밖 `/Users/chosunghoon/snowflake-handoff-backups/20261112-123756`에 보존했습니다. Git 기준과 인수 당시 내용을 각각 비교할 수 있습니다.

기존 계획에서 다음을 보완합니다.
- 과거 에이전트의 환경 확인을 현재 실행 증거로 취급하지 않습니다. 로컬 JVM·의존성 해석·플러그인 호환성을 구현 시 다시 실행합니다.
- 잠금 선언만 확인하는 대신 고정 시각의 동시 호출 결과와 순번 집합을 단언합니다.
- 실패 시 상태 보존, 극단 시간의 산술 overflow, 최초 ID 0을 관찰 가능한 결과로 확인합니다.
- 빌드·스크립트·테스트·기록을 세 커밋에 무조건 묶지 않습니다. 300~400줄 이내 논리 단위로 나누고 각 단위도 검증·독립 검토합니다.
- 준비 확인 요청이 발급 순번을 소비하는 점, 포트 충돌과 자신이 시작한 프로세스의 종료를 실행 검사에 포함합니다.

## 사용자 행동과 계약 제안
사용자는 본문 없는 `POST /api/ids` 요청으로 ID 하나와 그 ID에서 복원한 필드를 받습니다.
성공 응답은 HTTP 200, 예시는 `{"id":"4198400","timestamp":"2026-01-01T00:00:00.001Z","workerId":1,"sequence":0}`입니다.
ID는 내부에서 long, JSON에서는 십진 문자열입니다. timestamp는 ISO-8601 UTC 문자열이고 나머지 두 필드는 정수입니다.
복원 시 시계를 다시 읽지 않습니다. 조회 API·DB·인증·UI·외부 조정·자동 재시도·재시작 후 고유성은 제외합니다.

D-02~07 제안은 [설계](../../design.md)를 따릅니다.
- Java 21 / Boot 4.1.2 / Gradle 8.14.7. Boot 공식 요구사항에서 Java 21과 Gradle 8.14 이상 지원을 재확인했습니다. 정확한 배포본·플러그인 동작은 아직 실행하지 않았습니다.
- 부호 1 / 시간 41 / 워커 10 / 순번 12비트, epoch `2026-01-01T00:00:00Z`입니다.
- `snowflake.worker-id`는 기본값 없이 필수이며 누락·범위 밖 설정은 시작 실패입니다.
- 범위 이탈→역행→소진 순서로 검사하고 세 오류는 즉시 HTTP 503과 code/message로 응답합니다. 임의 예외를 도메인 오류로 은폐하지 않습니다.
- 생성기 단일 Bean과 `synchronized nextId()`, 주입한 Clock을 사용합니다. 성공한 경우에만 상태를 갱신합니다.

공식 근거: [Boot 요구사항](https://docs.spring.io/spring-boot/system-requirements.html), [Gradle JVM 호환성](https://docs.gradle.org/current/userguide/compatibility.html).

## 변경 예상과 인터페이스
아래 파일은 모두 생성 예정이며 아직 존재하지 않습니다. 루트 패키지는 `com.example.snowflake`입니다.
- `settings.gradle`, `build.gradle`, `.gitignore`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`: Java 21 컴파일·JUnit·Boot 실행 JAR 구성입니다. Wrapper 원본과 체크섬을 공식 배포본으로 확인합니다.
- `core/SnowflakeFormat.java`: epoch·비트·마스크·허용 시각 상수입니다.
- `core/SnowflakeGenerator.java`: `SnowflakeGenerator(int workerId, Clock clock)`, `synchronized long nextId()`입니다. 범위 비교 후 시간 차를 계산하여 overflow를 방지합니다.
- `core/SnowflakeDecoder.java`: `static IdParts decode(long id)`이며 음수 입력을 거부합니다.
- `core/IdParts.java`: `record IdParts(Instant timestamp, int workerId, int sequence)`입니다.
- `core/SnowflakeErrorCode.java`, `SnowflakeException.java`: TIME_OUT_OF_RANGE / CLOCK_ROLLBACK / SEQUENCE_EXHAUSTED를 구분합니다.
- `SnowflakeApplication.java`, `config/SnowflakeProperties.java`, `SnowflakeConfig.java`: 필수 Integer 워커 검증과 Clock·생성기 Bean을 구성합니다. 누락을 primitive 기본값 0으로 흡수하지 않습니다.
- `api/IdController.java`, `IdResponse.java`, `ErrorResponse.java`, `IdExceptionHandler.java`: 성공 필드 복원과 도메인 오류 응답을 담당합니다.
- `src/test/java/com/example/snowflake/{core,config,api,support}/*`: 도메인·설정·실제 생성기 연결 API 검사와 thread-safe MutableClock입니다.
- `scripts/{doctor,smoke-test,verify}.sh`: 환경·HTTP·전체 검사입니다. 루트 README에 JDK 21과 필수 워커 실행법을 기록합니다.
- 기능 verification/review와 tasks/progress/decisions: 실제 결과와 승인 이력을 기록합니다.

Boot MVC와 Boot 테스트 의존성은 HTTP 및 JUnit 검증을 위해 사용합니다. 별도 validation 라이브러리 대신 설정 생성자에서 검사합니다.
서식 검사는 기존 명세에 맞춰 Spotless를 사용하며 플러그인과 formatter 버전은 공식 호환성 확인 후 고정합니다. DB·인증·접근 제어 데이터는 없습니다.

## 완료 조건과 검증
기존 AC 번호를 유지합니다. F-02/03까지 필수 보호를 미루지 않습니다.

| 조건 | 입력과 단언 | 검사 |
|---|---|---|
| AC-01 | epoch·worker 0 첫 ID=0, epoch+1ms·worker 1 첫 ID=4198400; 같은 ms 다음 ID는 +1 | core 단위 |
| AC-02 | 복원 필드가 입력과 일치; 최대 long은 마지막 유효 ms·worker 1023·sequence 4095; -1은 거부 | decoder 단위 |
| AC-03 | worker -1/1024, null Clock 생성 실패; 설정 누락/-1/1024 시작 실패; 0/1023 시작 성공 | 생성자·컨텍스트 |
| AC-04 | epoch-1/epoch+2^41/Long.MIN_VALUE/MAX_VALUE 시각은 범위 오류; 10→9→10→11ms는 seq0→역행→seq1→seq0; 4096회 성공 뒤 같은 ms에서 최소 2회 연속 SEQUENCE_EXHAUSTED, 역행 후 원래 소진 ms로 복귀해도 소진 오류, 다음 ms seq0; 고정 ms 8×256회 모두 성공·고유·순번 0~2047 | 제어 Clock·동시성 |
| AC-05 | 실제 생성기와 MVC 연결에서 200 응답 타입·복원 필드 일치; 세 오류 각각 503과 code; 임의 오류는 도메인 code로 변환하지 않음 | API 통합 |
| AC-06 | 실행 JAR의 실제 POST 연속 요청에서 id 증가·복원 일치; 워커·포트 지정; 정상·시작 실패·요청 실패에서 자기 PID만 종료 | HTTP 스모크 |

범위 오류 전후 유효 시각 요청에서도 다음 순번을 이어가는지 확인합니다. 첫 호출 실패 후 유효 시각의 첫 sequence는 0이어야 합니다.
동시성 검사는 시작 장벽·timeout·모든 Future 회수·finally executor 종료를 사용합니다. sleep으로 시간을 맞추지 않습니다.
마지막 유효 시각은 `epoch + 2^41 - 1`이며 그 다음 시각을 별도로 검사합니다.

## 실행 순서
1. Java 21과 공식 Wrapper 배포·의존성 해석을 확인하고 최소 빌드/컴파일 골격을 준비합니다. 기본 JVM 25와 구분합니다.
2. AC-01~04의 핵심 테스트를 먼저 작성하고 미구현 반환을 두어 동작 단언 실패를 실행·기록합니다. 컴파일 실패는 RED 증거로 쓰지 않습니다.
3. 생성·복원·오류·원자적 상태 갱신을 구현하고 core 검사를 통과시킵니다.
4. AC-03/05도 최소 컴파일 골격에서 동작 실패를 확인한 뒤 설정·API를 구현하고 통과시킵니다.
5. HTTP 스크립트와 실행 안내를 작성하고 전체 검증을 실행합니다. 준비 확인은 자신이 시작한 자식 프로세스가 살아 있는지 확인하고, 제한 시간 안에 실제 `POST /api/ids`의 HTTP 200과 JSON 계약을 확인하며, TCP 연결만으로 준비 완료로 판정하지 않습니다. 준비 요청이 소비한 순번을 후속 검사에 반영하고 실제 발급 번호를 하드코딩하지 않습니다.
   실패 경로는 worker=-1로 시작 실패, 정상 서버에 잘못된 검사 URL을 지정해 HTTP 실패를 유발합니다. 각 경우 종료 코드 비정상·자식 PID 소멸·로그 보존을 확인합니다. 별도 테스트 리스너가 포트를 점유한 상태에서 스모크를 실행하여 포트 충돌을 재현하고, 실패 후 기존 리스너가 계속 응답하는지 확인한 뒤 테스트가 만든 리스너만 정리합니다. 검사 URL은 스크립트의 테스트용 옵션으로 제공하며 제품 API는 추가하지 않습니다.
6. 새 컨텍스트 리뷰어가 승인 계약·규칙·기준 커밋·새 파일 포함 diff·실행 증거를 읽기 전용 검토합니다. 수정 후 영향을 받는 검사를 재실행하고 정확성 변경은 재리뷰합니다.
7. 검증·리뷰한 내용과 일치하는 변경만 작은 커밋으로 남깁니다. 기능 전체 완료 후 F-02로 이동합니다.

예정 명령(현재 미구축): `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`, `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew spotlessCheck check bootJar`, `./scripts/verify.sh`입니다.
verify는 환경→서식→컴파일/테스트→JAR→HTTP 순서로 실행하며 JDK 21을 명시합니다. 로그는 `build/verification/<실행 ID>/`에 보존합니다.
verification.md에 명령·종료 코드·대상 코드·환경·테스트 수·로그·미실행 항목을 남깁니다. UI는 없어 브라우저 검사는 적용하지 않습니다. 회귀 기준선은 없습니다.

## 커밋 계획
각 단위는 Codex가 구현하며 검증·새 컨텍스트 검토 후 커밋합니다. 기능 완료 전 다음 기능 구현은 시작하지 않습니다.
- `chore: Java 21 빌드와 테스트 실행 기반 구성`: Wrapper·빌드·환경 안내입니다. 공식 Wrapper 생성 파일은 자동 생성임을 표시하고 수작성 변경과 구분합니다.
- `feat: Snowflake 생성과 복원 구현`: core와 핵심 테스트입니다. 400줄을 넘으면 복원 계약과 생성 상태 전이로 분리합니다.
- `feat: 필수 워커 설정과 ID 발급 API 연결`: 설정·API·관련 테스트입니다. 줄 수 초과 시 설정 검증과 HTTP 연결로 분리합니다.
- `chore: 실행 JAR HTTP 검증 자동화`: 스크립트·실행 안내와 오류 시 정리 검증입니다.
- `docs: ID 발급 기능 검증과 리뷰 결과 기록`: 승인·실행·리뷰·재개 기록입니다.

## 남은 확인
D-01·D-09와 인계·재작성 자체는 재확인하지 않습니다.
계획 검수 후 사용자의 구현 시작 지시로 D-02~07과 실행 계획을 승인받았습니다. 이후 구현·검증·독립 리뷰·커밋을 완료했습니다.

## 실행 승인
2026-11-12 사용자: “f-01부터 구현과 테스트를 시작해”. 위 계획과 D-02~07 계약으로 구현을 시작합니다. 담당 Codex, 작업 브랜치 feat/f01-id-issuance입니다. 이전 승인 대기 표기는 계획 수립 당시 이력이며 현재는 승인 후 구현 중입니다.
