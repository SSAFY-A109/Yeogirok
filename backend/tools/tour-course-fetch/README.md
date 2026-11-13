# 투어API 여행코스 받아 오기·판정·묶음 (S15P31A109-218·222)

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
| `courses/{id}/common.json` · `photo-cache/` · `region-cache.json` | 묶음용 소개 글 원본, 사진 원본, 카카오 지역 코드 캐시 |
| `bundles/{bundleId}/` · `bundles/index.json` | 묶음 파일·사진, 만든 묶음 목록 |

판정 규칙과 파일 모양: `.handoff/specs/tour-course-seed.md` 3절(로컬 문서). 일차 배분·날짜·시각은 이 스크립트에서만 계산하고 적재 작업은 그대로 읽는다.

## 묶음 파일 만들기 (S15P31A109-222)

판정을 통과한 코스로 적재용 묶음을 만든다. 코스 소개 글(detailCommon2)은 투어API 호출 상한에 들어가고, 장소 사진은 원본 그대로 받으며, 지역 코드는 카카오 좌표→법정동으로 채운다. 받은 소개 글·사진·지역 코드는 데이터 폴더에 캐시해 다시 부르지 않는다.

```powershell
$env:TOUR_API_KEY = "<인증키 Decoding 값>"
$env:KAKAO_REST_API_KEY = "<카카오 REST 키>"     # 없으면 regionCode·regionName 을 비우고 다음에 다시 채운다

python tour_fetch.py bundle --count 20                 # 처리 순서대로, 앞 묶음에 없는 통과 코스 20개
python tour_fetch.py bundle --ids 2916281,1234567      # 고른 코스만
python tour_fetch.py bundle --count 10 --bundle-id 20261115-01 --max-calls 50

Remove-Item Env:TOUR_API_KEY, Env:KAKAO_REST_API_KEY
```

- 결과: `bundles/{bundleId}/bundle-{bundleId}.json` + `bundles/{bundleId}/photos/`. 묶음 ID 기본값은 `오늘날짜-순번`. 만든 묶음 목록은 `bundles/index.json`(다음 `--count` 는 여기 있는 코스를 뺀다).
- 사진을 못 받아 어떤 날의 사진이 0장이 되면 그 코스는 묶음에서 빠진다(출력에 사유).
- 투어API 글의 `<br>`·태그·HTML 엔티티는 정리해서 담는다.

## 로컬 백엔드에 넣어 보기

로컬 DB·Redis(`infra/docker-compose.local.yml`)와 로컬 실행 설정(IntelliJ 환경 변수 또는 `backend/.env`)이 있는 상태에서, 아래 환경 변수를 더해 백엔드를 한 번 띄운다. 사진은 로컬 설정의 사진 버킷(기본 dev 버킷 `yeogirok-a109-photos-dev`)의 `record-photo/` 에 올라간다.

```powershell
$bundle = "$env:USERPROFILE\yeogirok-tour-data\bundles\20261114-01"
$env:COURSE_SEED_ENABLED = "true"
$env:COURSE_SEED_SOURCE = "local"
$env:COURSE_SEED_LOCATION = $bundle
cd C:\Users\SSAFY\Desktop\Yeogirok\backend
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
# 로그에서 "공식 코스 적재 끝. 넣음=…, 건너뜀=…, 실패=…" 를 확인한다
```

- 다시 띄워도 이미 넣은 코스는 건너뛴다. 내리기는 `$env:COURSE_SEED_UNPUBLISH = "2916281"`(또는 `ALL`), 다시 넣기는 `$env:COURSE_SEED_RESEED = "2916281"`. 같은 값은 한 번만 처리하고 묶음 폴더 `.done/` 에 표시를 남긴다.
- 끝나면 `Remove-Item Env:COURSE_SEED_*` 로 꺼 둔다.

## 넣은 뒤 015·014·016 응답 보기

015·014·016 은 로그인(USER 토큰)이 필요하다. 로컬 백엔드와 같은 `JWT_SECRET`(Base64)으로 토큰을 만든다(운영 비밀값은 쓰지 않는다). 회원 번호는 로컬 DB 의 아무 회원(예: 공식 코스 시스템 계정)을 쓴다.

```powershell
$env:JWT_SECRET = "<로컬 JWT_SECRET 값>"
$token = python tour_token.py 1
$h = @{ Authorization = "Bearer $token" }
$base = "http://localhost:8080/api/v1"
$out = "$env:USERPROFILE\yeogirok-tour-data\responses"; New-Item -ItemType Directory -Force $out | Out-Null

Invoke-RestMethod "$base/courses/recommendations" -Headers $h | ConvertTo-Json -Depth 20 | Out-File -Encoding utf8 "$out\015.json"
Invoke-RestMethod "$base/courses" -Headers $h | ConvertTo-Json -Depth 20 | Out-File -Encoding utf8 "$out\014.json"
$id = (Get-Content "$out\015.json" -Raw | ConvertFrom-Json).data.items[0].id
Invoke-RestMethod "$base/courses/$id" -Headers $h | ConvertTo-Json -Depth 20 | Out-File -Encoding utf8 "$out\016.json"
Remove-Item Env:JWT_SECRET
```

- 프론트에 줄 예시: 위 파일을 그대로 쓰면 된다. 공개 코스 응답에는 닉네임·이메일이 없고 작성자는 `{id, profileImageUrl}` 뿐이며, 공식 코스 작성자는 시스템 계정(프로필 사진 없음)이다. 사진 `url` 은 15분짜리 서명 주소라 공유 전에 지우거나 그대로 두어도 곧 만료된다. 실제 사용자 기록이 섞인 DB 라면 공식 코스(제목이 아닌 방 제목 `tourapi-course:` 로 확인)만 골라 준다.

## 서버에 넣기 (S15P31A109-223, 사용자 작업)

서버 compose 가 `COURSE_SEED_*` 를 backend 에 넘겨야 한다(infra PR #131). 켜고 끄는 법은 `infra/README.md` 9단계 "공식 코스 적재"도 본다.

1. 묶음 폴더 내용을 운영 사진 버킷의 `course-seed/` 아래로 올린다(`bundle-{bundleId}.json` 은 `course-seed/` 바로 아래, 사진은 `course-seed/photos/`).
2. 서버 `.env` 에 `COURSE_SEED_ENABLED=true`, `COURSE_SEED_LOCATION=course-seed/` 를 넣고 **`docker compose up -d backend`** 로 다시 띄운다(`restart` 는 바뀐 `.env` 를 읽지 않는다).
3. 로그에서 "공식 코스 적재 끝" 줄을 확인한다.
4. **끝나면 바로 끈다**: `COURSE_SEED_ENABLED=false` 로 바꾸고 `COURSE_SEED_UNPUBLISH`·`COURSE_SEED_RESEED` 를 지운 뒤 `docker compose up -d backend` 한 번 더. `backend/dev` 머지 때마다 배포로 백엔드가 새로 뜨므로 켜 둔 채면 배포마다 다시 돈다.

- 끄는 걸 잊어도: 같은 `UNPUBLISH`·`RESEED` 값(과 주인)은 한 번만 처리한다. 처리하면 `course-seed/.done/{해시}.json` 표시를 남기고, 같은 값이면 다음 실행에서 내리기·다시 넣기를 건너뛴다(새 코스 넣기는 두 번 돌아도 결과가 같다). 같은 값으로 다시 처리하려면 그 표시 파일을 지운다.
- 비상시 DB 에서 내리기: `update travel_rooms set deleted_at = now() where deleted_at is null and title like 'tourapi-course:%';`

## 테스트

```powershell
python -m unittest -v    # 가짜 HTTP 서버(투어API·이미지·카카오)로. 실제로는 부르지 않는다
```
