# F-01 검증 기록

2026-11-12, 담당 Codex(wG:p3), 기준 e154684 이후 미커밋 제품 변경 대상입니다.

## 실제 환경과 근거
- Java: Temurin 21.0.11, Gradle 8.14.7 Wrapper. 공식 배포 SHA256을 wrapper properties에 고정했습니다.
- Boot 4.1.2, Spotless 8.11.0 / google-java-format 1.25.2 AOSP 의존성 해석과 실행을 확인했습니다.
- Java 기본값과 구분하여 JAVA_HOME을 `/usr/libexec/java_home -v 21`로 지정했습니다.
- Gradle 로컬 소켓·다운로드 및 실제 HTTP 실행은 샌드박스 제약으로 승인받은 실행 환경에서 수행했습니다.

## TDD
- 최소 컴파일 골격에서 `./gradlew test --tests 'com.example.snowflake.core.*'`: 종료 1, 8건 실패. ID 기대값·입력 거부·상태 전이 미구현을 확인했습니다.
- 최소 웹/설정 골격 포함 `./gradlew test`: 종료 1, 12건 실패. 200 응답 필드 부재와 필수 워커/Bean 검증 누락을 확인했습니다.
- 원본 로그: `build/verification/f01/core-red.log`, `all-red.log`.
- 구현 후 `./gradlew spotlessApply test bootJar`: 종료 0. 임의 예외 비은폐 검사 추가 포함 총 13건 통과했습니다.

## 전체 자체 검증
`./scripts/verify.sh`: 종료 0. 로그 `build/verification/20261112-131734/verify.log`.
- spotlessCheck·main/test Java 컴파일·check·bootJar 통과.
- JUnit 13건, 실패·오류 0. `build/test-results/test/TEST-*.xml`.
- AC-01/02: ID 0·4198400·복원 최대값·음수 거부.
- AC-03: 생성자 워커/null, 설정 누락/-1/1024/문자열 실패, 0/1023 단일 Bean.
- AC-04: 극단 시간·마지막 유효 ms·오류 후 상태 보존·반복 소진·역행 복귀·8×256 고유성과 순번 집합.
- AC-05: 실제 생성기와 MVC의 정상 JSON·세 도메인 오류·임의 예외 비은폐.
- AC-06: 실제 JAR에서 POST 21건, ID 증가와 Python 정수 기반 독립 비트 복원 일치. responses.json·server.log·cleanup.txt 보존.
- 실패 경로: invalid-worker, http-failure, occupied-port 하위 probe.log에 결과 보존. 종료 코드 비정상과 자기 PID 종료, 기존 리스너 생존 확인.
- UI 없음으로 브라우저 검사는 적용하지 않습니다. F-02~04 확장 시나리오는 아직 미실행입니다.

## 남은 검증
새 컨텍스트 독립 리뷰와 R-01 재리뷰를 통과했습니다. 최종 커밋 일치 확인을 수행합니다.

## 독립 리뷰 R-01 회귀와 재검증
독립 리뷰어가 포트 선점 검사→Java bind 사이 경쟁에서 타 서버 응답으로 거짓 성공하는 경로를 재현했습니다. `test_port_race.py`를 추가한 수정 전 검사는 종료 1(경쟁 서버의 21건 응답을 성공으로 오인)이며 `build/verification/f01/port-race-red.log`에 보존했습니다.
자식 전용 서버 로그에서 정확한 포트의 Tomcat 시작과 애플리케이션 시작 완료를 확인한 뒤에만 POST하도록 수정했습니다. 요청 전후 자식 생존도 확인합니다.
수정 후 `./scripts/verify.sh` 종료 0, 최신 로그 `build/verification/20261112-132131/verify.log`. 서식·컴파일·JUnit 13건·JAR·21건 HTTP·세 실패 경로 및 새 포트 경쟁 회귀 검사가 모두 통과했습니다. 포트 경쟁 시 타 서버 호출 0건·자기 Java PID 종료·경쟁 리스너 생존을 확인했습니다.
