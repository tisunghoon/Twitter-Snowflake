# F-04 검증 기록

## 현재 검증 상태

F-04의 필수 검증과 독립 제품 리뷰 및 필요한 재리뷰를 통과했습니다. 기능 검증 당시 전체 JUnit은 22건이며, 최종 기능 커밋은 `712c150`입니다. 해당 전체 실행 로그는 `build/verification/20261112-133820/verify.log`입니다.
현재 전체 기능의 완료 기준 HEAD는 `6223b0b`입니다. 이후 동료 재검토의 실행 보고와 이번 문서 점검은 [진행 기록](../../progress.md)에서 구분합니다. 이번 문서 수정에서는 제품 테스트를 다시 실행하지 않았습니다.

## 과거 기록: 기능 구현 당시 실행·수정·리뷰 확인

아래 명령·건수·상태는 각각 기록 당시의 내용입니다. 미커밋·확장 기능 미실행·리뷰가 다음 단계라는 표현도 당시 상태이며, 후속 결과와 현재 상태를 대체하지 않습니다. 로그 경로의 현재 존재 확인은 해당 명령을 이번에 직접 실행했다는 뜻이 아닙니다.
2026-11-12 Codex, 기준1e3fca0. 선행 F-03 JUnit21건·전체verify·독립재리뷰 통과입니다.
- RED: scripts/test_multi_worker.py --normal-only를 최소골격에 실행해4096건 검증 결과 부재 단언 실패(종료1). build/verification/f04/multi-red.log.
- GREEN: 고정시각 worker1/2 각각2048개 총4096개 고유성 core검사 추가, 기존제품 변경없음. 전체JUnit22건 실패0.
- 실제 최종명령: ./scripts/verify.sh 종료0, build/verification/20261112-133524/verify.log.
- AC-01/03: 두 Java 자식을 함께 기동, 각 전용 로그의 정확한포트 기동·자식생존·실제POST 확인. worker당2048건 합계4096건 고유성과 독립 Python비트복원·timestamp 일치. 준비2건은 별도집계·본ID와 중복없음.
- AC-02: SnowflakeMultiWorkerTest는 동일 epoch+10ms·worker1/2·각seq0~2047·합계4096개 고유를 단언합니다.
- AC-04: 실제 JAR에서 워커 미설정/-1/1024 각각20초내 비정상종료·worker설정 원인로그 확인. 환경/외부config 기본값을 테스트범위에서 제거했습니다. config 0/1023 정상경계 회귀도 통과.
- AC-05: 두번째worker=-1·잘못된URL·두번째시작전실패·포트선점, 첫자식만있는시점/양쪽준비후 SIGINT/SIGTERM. 자기PID 종료·wait회수·cleanup파일·기존리스너 생존을 검사했습니다.
- AC-06: README에 두JDK21/같은JAR/서로다른포트·워커 명령·종료·중복과정렬 한계를 기록했습니다. 자동검사는 동일한기동옵션을 실제실행했습니다.
- 전체회귀: Spotless·main/test컴파일·JUnit22건·JAR·단일HTTP21건·실패정리·포트경쟁회귀까지 통과. UI없음으로 브라우저미적용.
- Python은3.10이상 버전·표준모듈 doctor검사를 서버시작전에 수행합니다. 자동설치없음.
로그: multi-worker/normal/ids.json(4096건), 각 worker-N/server.log·server.pid·cleanup.txt, 실패시나리오별harness.log, invalid-config/*.log.
독립제품리뷰는 다음단계입니다.

### 리뷰 수정 후 최종 전체 검증
R-01 같은경로 재실행의 과거 표식 재사용을 실행별UUID 디렉터리와 정확한 자식수 단언으로 수정했습니다. 신호 종료코드도128+signum으로 확인합니다. 수정후 ./scripts/verify.sh 종료0, 최신 build/verification/20261112-133820/verify.log. 전체22건·빌드·단일/동시/두워커HTTP·실패와중단정리 통과입니다. 새하네스경로는 multi-worker/run-9757c1ee3df34c139883d1f9a620ae0c/ 아래입니다.

독립재리뷰 통과. 최종제품 커밋712c150(선행e1f430a/eaea06b 포함)과 검증한 src/scripts/빌드 파일의 일치를 확인했습니다. F-01~04 모두 필수검증·독립리뷰·차단해소·제품커밋 완료입니다.
