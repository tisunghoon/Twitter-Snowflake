# F-03 검증 기록
2026-11-12 Codex, 기준60078e1. 선행F-02 JUnit16건·전체verify 통과입니다.
새 동시 실행 하네스는 최소 컴파일 골격에서 결과 누락·예외 은폐·timeout 미구현의 동작단언3건 실패(RED)를 확인한 뒤 구현했습니다. build/verification/f03/harness-red.log 및 green.log 참조.
`JAVA_HOME=Java21 ./gradlew spotlessApply test` 종료0, JUnit21건 실패0. 기존 제품 생성기는 변경하지 않았습니다.
- AC-01: 기존8×256 고정ms 고유ID·순번집합 검사 재사용.
- AC-02: 총4097회 동시호출 중4096성공·소진1건, 모든순번0~4095·중복없음·이후소진 유지.
- AC-03/05: RANDOM_PORT 실제 서버8작업자×256 POST, 모두200·고유2048, id문자열·복원시각/워커/순번 일치. 별도순차2건 증가 및 본집합과 중복없음.
- AC-04: 하네스의 모든결과 회수, 작업예외/HTTP실패 전파, 단일deadline timeout·취소·executor종료를 검사합니다. HttpClient는 try-with-resources, 서버는 DirtiesContext로 종료합니다.
최종 `./scripts/verify.sh` 종료0, build/verification/20261112-133011/verify.log: 서식·컴파일·JUnit21건·JAR·실제HTTP·실패정리·포트경쟁회귀 통과. UI 없음. 독립제품리뷰는 다음 단계입니다.

리뷰 R-01/R-02 수정 후 최종전체verify 종료0: build/verification/20261112-133222/verify.log. 리뷰어 직접전체21건 rerun통과·재리뷰통과. 제품생성기변경없음, 검증소스와 커밋일치를 확인합니다.
