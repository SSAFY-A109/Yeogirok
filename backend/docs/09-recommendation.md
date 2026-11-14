# 여행방 장소 추천 결정 사항

> 여행방 장소 추천(API-033·034·081)과 추천 담기(API-029 AI)를 만들면서 정한 규칙이다. 원천은 팀 Notion "API 명세"이고, 명세에 없는 값은 여기 적는다.
> 명세와 이 문서가 다르면 명세를 먼저 고치고 이 문서를 맞춘다.

## 1. 흐름

| 단계 | API | 비고 |
| --- | --- | --- |
| 저장한 조건 불러오기 | 081 | 저장 전이면 빈 조건 + version 1 |
| 지역·카테고리 저장 | 033 | 조건 version 비교. 여행방 version 은 그대로 |
| 추천 받기 | 034 | `Idempotency-Key` 필수. 저장된 조건으로 추천 |
| 추천 장소 담기 | 029 | `sourceType: AI`. 같은 API 의 검색 담기(SEARCH)는 장소 검색(API-026) 결과를 담는다 |

## 2. 결정 사항 (11/11)

### AI 서버를 쓰지 않는다

- AI 담당이 영수증 OCR 에 집중하기로 해 AI 서버 추천 경로(AI 내부 API-084·085)는 만들지 않는다.
- 추천은 백엔드 규칙 점수로 만들고, 이유 문장도 LLM 없이 점수 근거로 만든 템플릿이다.
- 앱 계약(033·034·081)과 담기 경로 이름(`sourceType: AI`)은 바꾸지 않는다.

### 조건 (033·081)

- `regionCode` 는 시/군/구 법정동 코드 5자리다(시도 2자리 + 시군구 3자리, 예: 강릉시 `51150`). 형식이 다르면 C003.
- `categoryCodes` 는 1개 이상(상한 없음), 공통 카테고리 코드만. 중복은 합친다. `skipped` 는 false 만(생략하면 false).
- 조건은 `travel_rooms.preferences` JSON 에 `{regionCode, categoryCodes, skipped, version}` 로 둔다.
- **조건 version 은 여행방 version 과 따로 센다.** 저장 전 1, 저장할 때마다 1 오른다. 조건만 바꿨는데 여행방 version 이
  오르면 다른 참여자의 방 수정·후보 변경이 C004 로 끝나서다. 그래서 `preferences` 컬럼을 `@OptimisticLock(excluded = true)`
  로 뺐다. 동시 저장은 방 행 잠금 + 조건 version 비교로 막는다.

### 추천 (034)

1. **후보**: TourAPI 국문 관광정보(KorService2) `areaBasedList2` 로 받는다.
   - 지역은 법정동 코드(`lDongRegnCd`=앞 2자리, `lDongSignguCd`=뒤 3자리). 구 지역 코드(areaCode)·구 분류(cat1~3)는 삭제 예정이라 쓰지 않는다.
   - 카테고리는 **11/2 확정 매핑**(`TourCategoryMapper`)으로 붙인다. 소분류(lclsSystm3) 예외 8개 → 중분류(lclsSystm2)
     기본값 → 카테고리 없음 순서다. 카테고리 없음(숙박·추천코스·VE08·VE09·VE11·VE12·기타의료관광·컨벤션센터)은 추천하지 않는다.
   - 조회 조건: 고른 카테고리가 될 수 있는 중분류를 대분류로 묶는다. 그 대분류에서 카테고리가 붙는 중분류를 **모두** 쓰면
     대분류 전체로 한 번, **일부만** 쓰면 중분류마다 부른다. 일부만 쓰는데 대분류 전체로 받으면 쓰지 않는 중분류가 받을 개수
     (100개)를 차지해 고른 카테고리 후보가 비기 때문이다(예: 랜드마크는 VE 8개 중 3개만 쓴다).
     예: 카페 → FD05·FD03, 랜드마크 → VE01·VE04·VE05, 자연 힐링 → NA 전체·VE03·EX05, 맛집+카페 → FD 전체 한 번,
     모든 카테고리 → 대분류 8개 전체.
   - 호출 상한: 합이 `max-tour-api-calls`(12, 8 이상)를 넘으면 중분류가 가장 많은 대분류부터 전체 조회로 바꿔 줄인다(TourAPI 일일 한도).
   - 호출은 가상 스레드로 동시에 하고 전체를 `fetch-timeout`(8초)까지만 기다린다. 넘긴 호출은 실패로 보고 나머지로 추천한다.
     멱등키 처리 중 표시 수명(30초) 안에 끝내려는 값이다(차례로 부르면 최악 8 × 5초 = 40초였다).
   - 정렬은 C(수정일순), 조회마다 100개. 대표 이미지가 있는 항목만 주는 Q 는 사진 없는 장소(작은 식당 등)를 아예 빼서 쓰지 않는다.
2. **점수**
   - 동선: 방에 담은 후보(빼지 않은 것) 좌표의 중심점과의 직선거리. `1 - 거리/기준거리`, 0 미만은 0. 기준거리 CAR 20km, TRANSIT 7km.
   - 인기: 최근 30일 동안 이 장소를 담은 **다른** 여행방 수(추천을 요청한 방·삭제된 방 제외, 같은 방 1회). 후보 중 최댓값 대비 `log(1+n)` 비율.
     담은 경로(검색·추천·공개 코스)를 가리지 않고 센다. 메인 인기 장소(API-093)는 공개 코스에서 가져간 것만 세므로 같은 장소라도
     화면마다 숫자가 다를 수 있다(의도: 추천은 "여행방들이 지금 담는 곳", 093 은 "공개 코스로 검증된 곳"). 기간 30일만 같다.
   - 담은 후보가 있으면 `0.7·동선 + 0.3·인기`(mode=PERSONALIZED), 없으면 인기만(mode=REGIONAL_POPULAR). 같으면 TourAPI 순서.
3. **섞기**: 카테고리마다 점수순으로 세우고 선택 순서대로 한 개씩 번갈아 뽑는다. 같은 장소는 처음 카테고리로 한 번만.
4. **이유**: 점수에 쓴 근거만. `카테고리명 · 담은 장소들과 1.2km · 최근 30일 여행방 3곳이 담았어요`. 카테고리 이름이
   없으면(travel_categories 행이 아직 없다) "선택한 카테고리", 담은 후보가 없으면 거리 문구, 인기 0 이면 인기 문구를 뺀다.
   `reasonEvidence` 는 같은 근거 목록이다.

- 이미 담은 장소도 빼지 않는다. 담기·빼기를 취향 점수로 쓰지 않는다. 이동 시간(분)은 만들지 않는다.
- `limit` 기본 20, 1~50(범위 밖 C003). 확인 순서: 방·참여(RO) → 조건 저장(C001) → limit(C003). `excludedPlaces` 는 TOUR_API 장소만 받고 결과에서 뺀다.
- 결과는 TourAPI 응답으로만 만들고 `places` 행은 만들지 않는다. 사진이 있으면 `imageAttribution` 은 "한국관광공사".
- 일부 조회만 실패하면 나머지로 추천한다. 모두 실패하면 C014. 결과가 0개면 빈 `items` 로 성공한다.
- 조건을 저장하지 않았으면 C001 이다. AI 도메인 오류 코드는 노션에서 확인하지 못해 공통 코드만 쓴다(노션 확인 필요).

### 추천 기록 (recommendation_logs)

- 테이블은 Flyway `V3__add_recommendation_logs.sql` 이 만든다. 노션 ERD 와 다른 점은 V1 과 같은 기준이다(ENUM 타입 대신
  varchar + 허용값 검사, 열 기본값·FK 없음, [08-flyway.md](08-flyway.md) 4절). 엔티티를 바꾸면 새 번호 파일을 함께 낸다.
- 추천 한 번에 REQUEST 행 하나. 행 ID 가 `recommendationRequestId` 다. 동기 처리라 status 는 SUCCEEDED 만 쓴다.
- `input_payload`: 지역·카테고리·조건 version·이동 수단·limit·excludedPlaces·담은 후보 수(roomPlaceCount)·
  TourAPI 호출 수(tourApiCalls)·실패 수(tourApiFailures)·받은 후보 수(fetchedPlaceCount).
- `result_payload`: 응답 그대로(ID 제외). 같은 멱등키 재전송은 이 값으로 응답을 다시 만든다(TourAPI 재호출 없음).
- ADD 는 추천 담기(029)에서 후보마다 남긴다. place_key 는 `TOUR_API:{providerPlaceId}`, place_snapshot 은 추천 결과의
  장소, rank·mode 는 원래 추천 요청 값, input_payload 는 `{recommendationRequestId, roomPlaceId}` 다.
- REMOVE 는 빼기(032)에서 남길 수 있지만 지금은 남기지 않는다(담기·빼기를 취향 점수로 쓰지 않아 쓰는 곳이 없다).

### 추천 담기 (029, sourceType=AI)

요청 `{ items: [{ placeRef: { provider: "TOUR_API", providerPlaceId }, sourceType: "AI", recommendationRequestId, rank }] }`
(1~50개, `Idempotency-Key` 필수), 응답 201 `{ added: RoomPlace[], addedCount, version }`.

1. **확인**: `recommendationRequestId` 가 이 방의 추천 요청 기록이고, 그 결과의 `rank` 위 장소가 `placeRef` 와 같아야 한다.
   방의 다른 참여자가 받은 추천도 담을 수 있다. 하나라도 틀리면 아무것도 담지 않고 C003.
2. **장소**: 같은 투어API 장소는 `places` 한 행을 재사용하고 기존 값을 고치지 않는다. 없으면 추천 결과에 저장된 장소 정보로
   만들고(TourAPI 재호출 없음), 법정동 코드·이름은 직접 추가(031)와 같은 단위(10자리)로 좌표에서 찾는다. 두 요청이 동시에
   처음 만들면 UNIQUE 충돌이 나므로 새 트랜잭션에서 먼저 만든 행을 다시 읽는다.
3. **후보**: 방 행을 잠그고 후보(`source_type=AI`)를 만든 뒤 여행방 version 을 1 올린다(확정 일정이 있으면 재계산 필요도 켠다).
   같은 장소를 여러 번 담으면 후보도 여러 개다(팀 확정 정책).
- 검색 담기(`sourceType: SEARCH`)는 places 에 없는 장소만 TourAPI 상세(detailCommon2)로 확인해 만든다. TourAPI 에 없으면 C003, 부르지 못하면 P003 이다. 한 요청에 AI 와 섞어 보낼 수 있고 ADD 기록은 AI 후보만 남긴다.
- 같은 멱등키 재전송은 담았던 후보(요청 순서)와 지금 여행방 version 을 준다.

## 3. 실제 TourAPI 확인 (11/11)

`TourApiLiveTest` 는 환경 변수 `TOUR_API_KEY` 가 있을 때만 실제 TourAPI 를 부른다(없으면 건너뛴다, CI 영향 없음).

```bash
TOUR_API_KEY=Decoding키 ./gradlew test --tests '*TourApiLiveTest' -i
```

강릉시(51150)로 돌린 결과:

- 응답 경로·필드(`response.header.resultCode`, `response.body.items.item`, `contentid`·`mapx`·`mapy`·`lclsSystm1~3` 등)가 코드와 맞다.
  구 코드(`areacode`·`cat1~3`)는 빈 값으로 온다.
- 카페(FD/FD05) 20개를 읽었고 좌표·분류가 맞다. 사진 없는 장소도 받는다(정렬 C).
- 모든 카테고리 조회에서 8개 카테고리 모두 후보가 있다. 매핑에서 빠진 코드는 VE09·VE11·VE12 뿐이고 매핑표대로 카테고리 없음이다.
- FD 대분류 전체 조회는 100개에서 잘린다(맛집+카페를 함께 고를 때). 카페 후보가 모자라면 `rows-per-query` 를 늘린다.

장소 검색(API-026)·검색 담기(API-029 SEARCH)용 호출도 강릉시로 확인했다(11/13):

- 키워드 검색(`searchKeyword2`) "해변"은 totalCount 26, 첫 쪽 항목의 이름·주소·좌표·사진·`lclsSystm1~3` 을 읽는다.
  검색어가 이름에 들어간 장소는 종류와 관계없이 나온다(공중화장실 VE01, 음식점 FD01 등).
- 공통 정보(`detailCommon2`)는 검색 결과와 같은 장소를 같은 값으로 주고, `lclsSystm1~3` 도 온다. 그래서 검색 담기로 만든
  장소에도 공통 카테고리가 붙는다.

## 4. 남은 일

- API-078 지역 목록(시도 → 시군구 법정동 코드) 서버 구현과 데이터 출처 결정.
- 서버 `infra/.env` 에 `TOUR_API_KEY` 값을 넣고 `infra/docker-compose.yml` 의 예약 주석(`# TOUR_API_KEY: ${TOUR_API_KEY}`)을 푼다(infra 파트).
