# 투어API 여행코스 받아 오기·판정 (S15P31A109-218)

투어API 여행코스(contentTypeId=25)를 받아 공식 계정 공개 코스로 넣을 수 있는지 판정한다. 백엔드와 떨어진 로컬 스크립트이고, 결과 파일은 코스 적재 작업(221·222)이 읽는다.

- Python 3.12 표준 라이브러리만 쓴다(추가 설치 없음).
- 받은 파일은 저장소 밖 `%USERPROFILE%\yeogirok-tour-data` 에 둔다(`--data-dir` 로 변경). **깃에 올리지 않는다.**
- 키는 환경 변수 `TOUR_API_KEY`(공공데이터포털 일반 인증키 Decoding 값)로만 받는다. 요청 주소·키는 저장하거나 출력하지 않는다.

## 실행 (PowerShell)

```powershell
cd C:\Users\SSAFY\Desktop\Yeogirok\backend\tools\tour-course-fetch
$env:TOUR_API_KEY = "<인증키 Decoding 값>"     # 이 창에만 둔다

# 받기·판정. 먼저 닿는 조건에서 저장하고 정상 종료한다.
python tour_fetch.py fetch --max-calls 300 --max-courses 100 --target-pass 20

python tour_fetch.py judge     # 저장된 원본으로 판정만 다시(호출 0번)
python tour_fetch.py report    # 마지막 판정 보고 다시 출력(호출 0번)

Remove-Item Env:TOUR_API_KEY   # 끝나면 지운다
```

| 인자 | 뜻 |
| --- | --- |
| `--max-calls` (필수) | 이번 실행에서 보낼 최대 HTTP 요청 수. 재시도도 센다 |
| `--max-courses` | 이번 실행에서 판정까지 끝낼 코스 수 |
| `--target-pass` | 누적 통과 코스가 이 수가 되면 멈춘다 |
| `--data-dir` | 데이터 폴더(기본 `%USERPROFILE%\yeogirok-tour-data`) |
| `--seed` | 코스 처리 순서(기본 `yeogirok-218`). 바꾸면 순서가 바뀐다 |

- 첫 실행은 장소 사전(areaBasedList2 1,000건씩, 약 50번)부터 받는다. 다 받기 전에는 코스를 처리하지 않는다. **사전은 한 번에 받는다**(`--max-calls` 60 이상): 여러 날에 나눠 받으면 그사이 투어API 데이터가 바뀌어 페이지가 밀리고 일부 장소가 빠질 수 있다(건수가 totalCount 와 다르면 경고).
- 페이지 크기는 처음 받은 페이지의 값을 계속 쓴다. 다음 실행에서 `--dict-rows`·`--info-rows` 를 바꿔도 이미 받기 시작한 사전·코스는 처음 크기로 이어 받는다.
- 다시 실행하면 받은 사전 페이지와 처리한 코스를 건너뛰고 이어서 한다. 하루 한도(1,000번)를 넘기지 않게 `--max-calls` 를 나눠 쓴다(034 장소 추천과 같은 키).
- 4xx(401·403·429 포함)·JSON 이 아닌 응답·resultCode≠0000 이면 즉시 멈추고, 키를 가린 응답 본문(앞 1,000자)과 상태 코드를 `runs/` 실행 기록에 남긴다. 연결 끊김·시간 초과·5xx·본문을 읽다 끊긴 경우는 2번까지 다시 시도하고, 그래도 안 되면 멈춘 이유를 남긴다.

## 결과 파일

| 경로 | 내용 |
| --- | --- |
| `dict/page-0001.json` … · `dict/meta.json` | 장소 사전 원본, 건수·totalCount·중복 확인 |
| `courses/{id}/info-p1.json` … · `intro.json` | 코스별 detailInfo2·detailIntro2 원본 |
| `judged/{id}.json` | 판정 결과. 통과 코스는 장소별 일차·순서·날짜·시각과 사전 값 |
| `state.json` · `runs/*.json` · `report.json` | 진행 상태·누적 호출 수, 실행 기록, 판정 보고 |

판정 규칙과 파일 모양: `.handoff/specs/tour-course-seed.md` 3절(로컬 문서). 일차 배분·날짜·시각은 이 스크립트에서만 계산하고 적재 작업은 그대로 읽는다.

## 테스트

```powershell
python -m unittest -v    # 가짜 HTTP 서버와 가짜 응답으로. 실제 투어API 는 부르지 않는다
```
