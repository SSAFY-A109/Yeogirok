# 여기록 API 계약 사본

> **상태: 미확정 초안.** 팀 API 명세가 아직 확정되지 않았다(2026-10-27 사용자 확인). 아래 목록과 공통 규약은 그 시점의 초안을 옮긴 참고본이다. 명세가 바뀌면 필요할 때 이 문서를 갱신한다. 그 전까지는 엔드포인트를 구현하기 직전에 명세·Swagger를 다시 확인하고, 이 문서와 다르면 명세를 따른 뒤 이 문서를 고친다.
> **원천**: 팀 Notion "API 명세"(SSAFY PJT › API 명세). 2026-10-27 대조 범위는 공통 규약 토글 5개(공통 실패 코드·성공 응답·실패 응답·날짜/시간 형식·오류 코드 규칙)와 API 목록 83행이다. **엔드포인트별 요청·응답 상세는 아직 읽지 않았다.**
> 서버에 반영된 API는 Swagger가 가장 정확하다(명세 머리말). 상세를 대조하면 §3에 옮기고 이 머리말의 일시를 갱신한다. 백엔드가 OpenAPI를 내보내면 `openapi/openapi.yaml`로 옮기고 §2·§3은 삭제한다(규칙 90).
> 표기: **상세 미확인** = 명세의 상세를 아직 읽지 않은 항목. **URL 미확인** = 경로가 비어 있는 항목.

## 1. 공통 규약

규칙 80·85·90과 `docs/api-guide.md`가 "계약을 따른다"고 한 값은 모두 이 표가 기준이다.

| 항목 | 값 | 출처 |
| --- | --- | --- |
| Base path | `/api/v1` (호스트는 `EXPO_PUBLIC_API_URL`) | API 목록 경로 |
| 인증 | 토큰이 필요한 API는 `Authorization: Bearer <서비스 액세스 토큰>`. 필요 여부는 API별 "토큰 필요" 속성. 토큰 수명 **상세 미확인** | 명세 "토큰" |
| 로그인 | 구글·카카오 공통: API-001 `GET /auth/{provider}/login` → API-002 콜백 → API-005 `POST /auth/token`으로 서비스 토큰 발급 | API 목록 공통 참고 |
| 토큰 갱신 | API-006 `POST /auth/token/refresh` — 요청·응답 형태 **상세 미확인** | API 목록 |
| 성공 응답 | `{ "success": true, "data": … }`. 빈 본문 성공은 `"data": null` | 명세 "성공 응답" |
| 오류 응답 | `{ "success": false, "code": "C002", "message": "…" }`. `code`는 고유하고 안정적인 분기 기준이다. **`message`는 개발자용 디버깅 설명이며 바뀔 수 있으므로 사용자 문구나 분기 기준으로 쓰지 않는다** | 명세 "실패 응답" |
| 오류 코드 형식 | 도메인 접두어 1~2글자 + 3자리 숫자. 공통은 `C`(§3-0). 같은 상황이면 여러 API에서 같은 code를 쓰고, 폐기한 code는 재사용하지 않는다 | 명세 "오류 코드 규칙" |
| 고유 id | 데이터베이스 키, 숫자 | 명세 "고유 아이디" |
| 명명 | **상세 미확인** (path는 kebab-case 복수형, 예 `/room-invitations`) | API 목록 |
| 날짜·시각 | date `2026-07-27`, time `15:30:00`, datetime `2026-07-27 15:30:00+09`. datetime은 날짜와 시각 사이가 **공백**이고 오프셋을 포함한다 | 명세 "날짜/시간 형식" |
| 시간대 | datetime에 오프셋(`+09`)이 포함된다. 오프셋이 없는 값은 받지 않는다고 가정하고, 오면 불일치로 보고한다 | 위 형식 |
| 페이지네이션 | 커서 방식(C010 커서 무효, C011 페이지 크기 범위 오류). 파라미터 이름·크기 범위·마지막 페이지 표시는 **상세 미확인** | 공통 실패 코드 |
| 멱등성 | 멱등키를 사용한다(C009 같은 멱등키에 다른 요청 내용). 헤더 이름과 적용 API는 **상세 미확인** | 공통 실패 코드 |
| 버전 충돌 | 자원 버전 비교(C008 요청한 자원의 버전과 현재 버전 불일치). 버전 값 전달 방식은 **상세 미확인** | 공통 실패 코드 |
| 위치 증거 | 현재 위치가 필요한 요청은 위치 증거를 보낸다. 누락 C012, 유효 시각 만료 C013, 정확도 미달 C014. 필드 구성은 **상세 미확인** | 공통 실패 코드 |
| 지도 조회 영역 | 영역 파라미터가 있다(C017 형식·범위 오류). 형식은 **상세 미확인** | 공통 실패 코드 |
| 비동기 작업 | 작업 상태 조회 API-011 `GET /jobs/{jobId}`(오류 접두어 `J`). 작업 상태 열거형은 **상세 미확인** | API 목록 |
| 파일 | 업로드 API-012 `POST /media` → `mediaId`. 접근 API-013 `GET /media/{mediaId}/access` | API 목록 |
| 실시간 | 여행방 채팅(P1)은 API-077 `POST /realtime/tickets`로 연결 티켓을 받는다. 프로토콜 **상세 미확인** | API 목록 |
| 공통 기준 데이터 | API-010 `GET /metadata` 공통 선택 항목과 정책 | API 목록 |

## 2. 엔드포인트 목록 (83행 · 2026-10-27 기준)

경로는 Base path를 뺀 값이다. P는 명세의 우선순위 속성이며, 비어 있으면 `—`로 적었다. 기능 ID와의 연결은 `docs/frontend-spec.md` §1에 있다.

| ID | 도메인 | 쓰임 | Method | URL | P |
| --- | --- | --- | --- | --- | --- |
| API-001 | AUTH | 소셜 로그인 시작 | GET | `/auth/{provider}/login` | P0 |
| API-002 | AUTH | 소셜 로그인 콜백 | GET | `/auth/{provider}/callback` | P0 |
| API-005 | AUTH | 로그인 완료 및 서비스 토큰 발급 | POST | `/auth/token` | P0 |
| API-006 | AUTH | 서비스 토큰 재발급 | POST | `/auth/token/refresh` | P0 |
| API-007 | AUTH | 로그아웃 | POST | `/auth/logout` | P0 |
| API-008 | USER | 내 계정 정보 조회 | GET | `/users/me` | P0 |
| API-009 | USER | 회원 탈퇴 | DELETE | `/users/me` | P0 |
| API-010 | COMMON | 공통 선택 항목과 정책 조회 | GET | `/metadata` | P0 |
| API-011 | JOB | 비동기 작업 상태 조회 | GET | `/jobs/{jobId}` | P0 |
| API-012 | MEDIA | 사진·영수증 파일 업로드 | POST | `/media` | P0 |
| API-013 | MEDIA | 접근 가능한 파일 조회 | GET | `/media/{mediaId}/access` | P0 |
| API-014 | COURSE | 공개된 코스 검색 | GET | `/courses` | P0 |
| API-015 | COURSE | 카테고리별 추천 코스 조회 | GET | `/courses/recommendations` | P0 |
| API-016 | COURSE | 공개된 코스 상세 조회 | GET | `/courses/{courseId}` | P0 |
| API-017 | COURSE | 코스 즐겨찾기 등록 | PUT | `/courses/{courseId}/bookmark` | P3 |
| API-018 | COURSE | 코스 즐겨찾기 해제 | DELETE | `/courses/{courseId}/bookmark` | P3 |
| API-019 | COURSE | 내 즐겨찾기 목록 조회 | GET | `/users/me/bookmarks` | P3 |
| API-020 | ROOM | 참여 여행방 목록 조회 | GET | `/rooms` | P0 |
| API-021 | ROOM | 여행방 생성 | POST | `/rooms` | P0 |
| API-022 | ROOM | 여행방 상세 조회 | GET | `/rooms/{roomId}` | P0 |
| API-023 | ROOM | 여행방 정보·이동 수단 수정 | PATCH | `/rooms/{roomId}` | P0 |
| API-024 | ROOM | 여행방 초대 생성 | POST | `/rooms/{roomId}/invitations` | P0 |
| API-025 | ROOM | 초대 링크로 여행방 참여 | POST | `/room-invitations/accept` | P0 |
| API-026 | PLACE | 여행지 검색 | GET | `/places` | P0 |
| API-027 | PLACE | 장소 상세·주차 정보 조회 | GET | `/places/{placeId}` | P0 |
| API-028 | ROOM | 여행방 후보 장소 목록 조회 | GET | `/rooms/{roomId}/places` | P1 |
| API-029 | ROOM | 검색·AI 추천 장소 담기 | POST | `/rooms/{roomId}/places` | P0 |
| API-030 | ROOM | 공개된 코스에서 선택 장소 가져오기 | POST | `/rooms/{roomId}/place-imports` | P0 |
| API-031 | ROOM | 지도 핀으로 장소 직접 추가 | POST | `/rooms/{roomId}/custom-places` | P3 |
| API-032 | ROOM | 담은 후보 장소 빼기 | DELETE | `/rooms/{roomId}/places/{roomPlaceId}` | P0 |
| API-033 | AI | 추천 온보딩 조건 저장 | PUT | `/rooms/{roomId}/preferences` | P0 |
| API-034 | AI | 여행지 추천 조회 | POST | `/rooms/{roomId}/place-recommendations` | P0 |
| API-035 | AI | 추천 장소 싫어요·취소 | PUT | `/rooms/{roomId}/place-recommendations/{requestId}/feedback` | P0 |
| API-036 | SCHEDULE | 여행방 일정 조회 | GET | `/rooms/{roomId}/schedule` | P0 |
| API-037 | SCHEDULE | 방문 순서·이동 시간 초안 생성 | POST | `/rooms/{roomId}/schedule-drafts` | P0 |
| API-038 | SCHEDULE | 생성한 일정 초안 조회 | GET | `/rooms/{roomId}/schedule-drafts/{draftId}` | P0 |
| API-039 | SCHEDULE | 검토한 AI 일정 적용 | PUT | `/rooms/{roomId}/schedule` | P0 |
| API-040 | SCHEDULE | 일정 수동 편집·순서 고정 | PATCH | `/rooms/{roomId}/schedule` | P0 |
| API-041 | FOOTPRINT | 현재 위치 발자취·방명록 저장 | POST | `/footprints` | P0 |
| API-042 | FOOTPRINT | 내 발자취 목록 조회 | GET | `/users/me/footprints` | P1 |
| API-043 | FOOTPRINT | 내 방명록 상세 조회 | GET | `/footprints/{footprintId}` | P1 |
| API-044 | FOOTPRINT | 내 방명록 본문 수정 | PATCH | `/footprints/{footprintId}` | P1 |
| API-045 | FOOTPRINT | 내 발자취 삭제 | DELETE | `/footprints/{footprintId}` | P2 |
| API-046 | STATISTICS | 카테고리별 방문 분포도 조회 | GET | `/visit-statistics` | P0 |
| API-047 | TIP | 현장 주변 공개 팁 탐색 | POST | `/tips/search` | P0 |
| API-048 | TIP | 현장 팁 상세·공간 복원 정보 조회 | POST | `/tips/{tipId}/view` | P0 |
| API-049 | TIP | AR 팁 등록 작업 생성 | POST | `/tips` | P0 |
| API-050 | TIP | 내 팁 등록·게시 상태 조회 | GET | `/tips/{tipId}/status` | P1 |
| API-051 | TIP | 실패한 팁 게시 재시도 | POST | `/tips/{tipId}/retry` | P1 |
| API-052 | TIP | 팁 좋아요·싫어요·취소 | PUT | `/tips/{tipId}/reaction` | P0 |
| API-053 | TIP | 내 팁 목록 조회 | GET | `/users/me/tips` | P2 |
| API-054 | TIP | 내 팁 본문·사진·배치 수정 | PATCH | `/tips/{tipId}` | P2 |
| API-055 | TIP | 내 팁 삭제 | DELETE | `/tips/{tipId}` | P2 |
| API-056 | TIP | 팁 신고 접수 | POST | `/tips/{tipId}/reports` | P2 |
| API-057 | RECORD | 여행방에서 기록 공간 생성 | POST | `/rooms/{roomId}/records` | P0 |
| API-058 | RECORD | 참여 여행 기록 목록 조회 | GET | `/records` | P0 |
| API-059 | RECORD | 여행 기록 상세 조회 | GET | `/records/{recordId}` | P0 |
| API-060 | RECORD | 기록 참여자 초대 생성 | POST | `/records/{recordId}/invitations` | P2 |
| API-061 | RECORD | 기록 초대 수락 | POST | `/record-invitations/accept` | P2 |
| API-062 | RECORD | 영수증·결제 자료 연결 | POST | `/records/{recordId}/materials` | P0 |
| API-063 | RECORD | 등록 자료·추출 결과 조회 | GET | `/records/{recordId}/materials` | P0 |
| API-064 | RECORD | OCR·LLM 기록 초안 생성 | POST | `/records/{recordId}/drafts` | P0 |
| API-065 | RECORD | AI 기록 초안 조회 | GET | `/records/{recordId}/drafts/{draftId}` | P0 |
| API-066 | RECORD | 실제 일정 수동 추가·AI 초안 확인 반영 | POST | `/records/{recordId}/visits` | P0 |
| API-067 | RECORD | 실제 방문 일정·시간·순서 수정 | PATCH | `/records/{recordId}/visits` | P0 |
| API-068 | RECORD | 일정에 일행 사진 연결 | POST | `/records/{recordId}/visits/{visitId}/photos` | P0 |
| API-069 | RECORD | 일정별 이야기 작성 | POST | `/records/{recordId}/visits/{visitId}/stories` | P0 |
| API-070 | RECORD | 일정별 이야기 수정 | PATCH | `/records/{recordId}/visits/{visitId}/stories/{storyId}` | P0 |
| API-071 | RECORD | 여행기 완성 처리 | POST | `/records/{recordId}/complete` | P0 |
| API-072 | RECORD | 공개할 코스 미리보기 | POST | `/records/{recordId}/publication-preview` | P0 |
| API-073 | RECORD | 확인한 여행 기록을 공개 코스로 게시 | POST | `/records/{recordId}/publications` | P0 |
| API-074 | COURSE | 내가 게시한 공개된 코스 목록 | GET | `/users/me/courses` | P0 |
| API-075 | COURSE | 공개할 코스 수정·공개 해제 | PATCH | `/courses/{courseId}` | P0 |
| API-076 | ROOM | 여행방 채팅 이력 조회 | GET | `/rooms/{roomId}/messages` | P1 |
| API-077 | REALTIME | 실시간 연결 티켓 발급 | POST | `/realtime/tickets` | P1 |
| API-078 | COMMON | 관심 지역(시) 목록 조회 | GET | `/regions` | — |
| API-079 | COMMON | 여행 컨셉 카테고리 목록 조회 | GET | `/categories` | — |
| API-080 | AI | 여행방 추천 조건 저장 | PUT | `/rooms/{roomId}/preferences` | — |
| API-081 | AI | 여행방 추천 조건 조회 | GET | `/rooms/{roomId}/preferences` | — |
| API-082 | AI | AI 장소 추천 결과 리스트 | GET | `/rooms/{roomId}/recommendations/places` | — |
| API-083 | COURSE | 추천 코스 조회 | GET | `/courses/recommended` | P0 |

**목록에서 확인이 필요한 점 (백엔드 문의)**
1. API-080은 API-033과 메서드·경로가 같다(`PUT /rooms/{roomId}/preferences`). API-083은 API-015와 용도가 같은데 경로가 다르다. 어느 쪽이 유효한지 확인이 필요하다.
2. API-078~082는 우선순위가 비어 있다.
3. API-084·085(AI 장소 추천 관련)는 목록에 URL이 비어 있어 이 표에서 뺐다(**URL 미확인**).
4. API-003·004는 번호가 비어 있다(소셜 로그인 통합으로 삭제된 것으로 보임 `[확인]`).

## 3-0. 오류 코드 카탈로그

확인된 코드만 적는다. 여기 없는 `code`는 도메인 기본 문구로 표시한다(§1 오류 응답).

| code | HTTP | 뜻 | 클라이언트 처리 |
| --- | --- | --- | --- |
| C001 | 400 | 필수 요청값 누락 | 개발 오류로 보고, 일반 오류 문구 |
| C002 | 400 | 요청 필드의 타입·형식 오류 | 〃 |
| C003 | 400 | 허용하지 않는 요청값 | 해당 필드 옆에 안내 |
| C004 | 401 | 서비스 액세스 토큰 누락 | 로그인으로 이동 |
| C005 | 401 | 서비스 액세스 토큰 만료 | 토큰 갱신 후 1회 재시도(규칙 90) |
| C006 | 401 | 서비스 액세스 토큰 무효·위변조 | 토큰 삭제 후 로그인 |
| C007 | 403 | 요청에 필요한 서비스 ROLE 부족 | 접근 불가 안내, 재시도 없음 |
| C008 | 409 | 요청한 자원의 버전과 현재 버전 불일치 | 최신 상태 다시 불러오기 + 반영 안 됨 안내 |
| C009 | 409 | 동일 멱등키에 다른 요청 내용 사용 | 개발 오류로 보고(키 재사용 버그) |
| C010 | 400 | 페이지 커서 무효 | 목록 처음부터 다시 조회 |
| C011 | 400 | 페이지 크기 범위 오류 | 개발 오류로 보고 |
| C012 | 422 | 현재 위치 증거 누락 | 위치 다시 측정 안내 |
| C013 | 422 | 현재 위치 증거의 유효 시각 만료 | 위치 다시 측정 안내 |
| C014 | 422 | 현재 위치 정확도 기준 미충족 | 탁 트인 곳에서 다시 측정 안내 |
| C015 | 400 | 위도·경도 범위 오류 | 개발 오류로 보고 |
| C016 | 400 | 시작·종료 날짜 또는 시각의 범위·순서 오류 | 해당 입력 옆에 안내 |
| C017 | 400 | 지도 조회 영역 형식·범위 오류 | 개발 오류로 보고 |

**도메인 접두어**: `C` 공통 · `AU` 인증 · `U` 사용자 · `J` 비동기 작업 · `M` 파일 · `CO` 공유 코스 · `RO` 여행방 · `P` 장소 · `AI` 추천 · `SC` 계획 일정 · `F` 발자취 · `ST` 방문 통계 · `T` AR 팁 · `RE` 여행 기록. 도메인별 code는 각 API 상세의 실패 응답 영역에 있다(**상세 미확인**).

## 3. 엔드포인트 상세

**상세 미확인.** 도메인을 구현하기 전에 명세(서버에 반영된 API는 Swagger)에서 해당 API를 읽고 아래 형식으로 옮긴다. 타입은 TypeScript 표기이고 `?`는 선택(응답에서는 조건부로 오거나 `null`)이다.

```ts
// <확인 일시와 근거(Notion 명세·Swagger)>

// POST /footprints — 201?
type CreateFootprintRequest = { /* 위치 증거, 방명록 본문 … 상세 미확인 */ };
type CreateFootprintResponse = { /* 상세 미확인 */ };
// 422 C012·C013·C014, 도메인 F*** 상세 미확인
```

### 3-1. 화면 선개발용 가정 DTO (2026-11-03, S15P31A109-83)

2026-11-03 기준 Swagger(`https://k15a109.p.ssafy.io/v3/api-docs`)에 등록된 경로가 없고, 팀 Notion API 명세의 상세도 대조하지 못했다. "여행방 함께 장소 모으기" 화면을 MSW로 먼저 만들기 위해 아래 엔드포인트의 요청·응답을 Figma 화면에서 역산해 **가정**했다. 명세가 나오면 이 표의 타입과 `api/mocks/`를 함께 고친다.

| API | 가정 타입 위치 | 비고 |
| --- | --- | --- |
| 020·021·022·023·024·028·029·032 | `features/room/model.ts` (`RoomSummaryDto`, `RoomDetailDto`, `CreateRoomRequestDto`, `UpdateRoomRequestDto`, `RoomInvitationDto`, `RoomPlaceDto`, `AddRoomPlacesRequestDto`) | 목록 응답은 `{ rooms }`, 장소 목록은 `{ places }`로 감쌌다고 가정 |
| 026 | `features/place/model.ts` (`PlaceDto`, `SearchPlacesResponseDto`) | 쿼리 `keyword`·`cursor`·`size`, 응답 `{ items, nextCursor }` |
| 033(=080)·081·034 | `features/recommendation/model.ts` | 조건 `{ categoryCodes, regionCode }`, 결과 `{ requestId, criteria, items }`. `routeHint`는 서버 문구 |
| 036 | `features/schedule/model.ts` (`RoomScheduleDto`) | 일정이 없으면 `data: null` |
| 078·079 | `features/common/model.ts` | 지역은 시/도 → 시/군/구 2단(`children`) |
| **목록에 없음** | `DELETE /rooms/{roomId}/members/me` → `{ roomDeleted }` | 여행방 나가기(W06-L·L2). **백엔드에 엔드포인트 요청 필요** |

- 도메인 오류 code(`RO`·`P`·`AI`·`SC`)는 미확정이라 화면은 공통 code와 도메인 기본 문구만 쓴다. MSW는 없는 여행방에 목 전용 `RO404`를 돌려준다.
- 버전 충돌(C008)용 버전 필드와 멱등성 헤더는 이 엔드포인트들에서 아직 보내지 않는다.

### 3-2. 방문 분포도 API-046 — 백엔드 코드 대조 (2026-11-06)

2026-11-05에는 화면에서 역산한 가정 DTO(`swLat`… `level`, `areas[{ name, visitorCount, visitCount, categoryShares }]`)로 만들었다. 2026-11-06 `origin/backend/dev`(fc7b266)의 `VisitStatisticsController`·`VisitStatisticsService`·`VisitStatisticsResponse`·`VisitGrid`·`BoundingBox`를 읽고 실제 계약으로 바꿨다. Swagger는 502로 대조하지 못했다. 타입은 `features/statistics/model.ts`, 목은 `api/mocks/handlers/statistics.ts`에 있다.

```ts
// GET /visit-statistics?bbox&zoom&category&from&to&metric
// bbox: "west,south,east,north"(필수). 범위 밖·west≥east·south≥north·숫자 아님 → 400 C013
// zoom: 웹 지도 zoom(필수, 0 이상, 음수 → 400 C003). 클라이언트는 카카오 level을 zoom = 20 − level로 바꿔 보낸다(근사, TBD)
// category: 공통 카테고리 code(선택, 없는 code → 400 C003). 주면 그 code를 고른 발자취만 센다
// from·to: yyyy-MM-dd(선택, from > to → 400 C012). 기간 선택(DEC-02) 전이라 앱은 보내지 않는다 → 전체 기간
// metric: VISIT_COUNT만(생략 가능, 다르면 400 ST001)
type VisitStatisticsResponseDto = {
  metric: "VISIT_COUNT";
  period: { from: string | null; to: string | null };
  aggregation: { unit: "LAT_LNG_GRID"; version: string; precision: string; minCount: number }; // precision 예 "0.01deg"
  cells: {
    cellId: string; // "배수:위도칸:경도칸"
    center: { latitude: number; longitude: number };
    count: number; // 이 칸의 발자취 수(탈퇴자 보존분 포함)
    categoryCounts: Record<string, number>; // 여러 개 고른 발자취는 각 code에 1씩 → 합이 count보다 클 수 있다. 0인 code는 없다
  }[]; // 북쪽 칸부터, 같은 줄은 서쪽부터
  updatedAt: string; // 응답을 만든 시각
};
```

- 서버가 격자로 묶는다: 기본 칸(설정 `base-cell-size-degrees`, 로컬 0.005°)에 zoom별 배수(설정 `zoom-multipliers`, 로컬 `15:1,13:2,11:8,9:32,0:128`)를 곱한 크기로 센다. 멀리 볼수록 칸이 커져 발바닥이 합쳐진다. 앱은 따로 묶거나 수를 더하지 않는다(규칙 80).
- 조회 영역을 한국 범위(124~132E, 33~39N)로 자르고, 걸치는 칸 전체를 덮도록 칸 경계까지 넓혀 센다. 그래서 큰 칸에서는 화면 가장자리 밖 발자취가 포함될 수 있다. 넓힌 영역의 칸 수가 `max-cells`(2500)를 넘으면 400 C013이다.
- `min-count`(DEC-07, 로컬 1)보다 적게 남은 칸은 응답에서 빠진다. 배포 값은 환경 변수다(TBD).
- 지역 이름·다녀간 사람 수·카테고리 비율은 응답에 없다. W15-A 카드는 칸의 발자취 수와 카테고리별 수, 기간·기준 시각만 보여 준다(2026-11-06 사용자 결정).

### 3-3. 발자취 저장 API-041 — 백엔드 코드 대조 (2026-11-06)

Swagger(`https://k15a109.p.ssafy.io/v3/api-docs`)가 502로 응답하지 않아, 같은 저장소 `origin/backend/dev`(90aaede, PR #46 머지)의 `FootprintController`·`FootprintCreateRequest`·`FootprintCreateResponse`·`FootprintCreateService`·`ExceptionType`을 읽어 옮겼다. 타입은 `features/footprint/model.ts`, 목은 `api/mocks/handlers/footprint.ts`에 있다. Swagger가 살아나면 다시 대조한다.

```ts
// POST /footprints — 201
// 헤더 Idempotency-Key(필수, 비면 400 C001). 같은 키 + 같은 내용 → 처음 저장한 결과, 다른 내용 → 409 C005, 처리 중 → 409 C018
type CreateFootprintRequest = {
  location: { latitude: number; longitude: number; capturedAt: string }; // 없거나 null → 422 C008, 타입·형식 오류 → 400 C002
  body?: string | null;    // 줄바꿈을 LF로 맞춘 뒤 코드 포인트 200자 초과 → 400 C003. 공백만이면 null로 저장
  categoryCodes: string[]; // 공통 카테고리 1~3개, 중복 없음. 없으면 400 C001, 어긋나면 400 C003
};
type CreateFootprintResponse = {
  id: number; latitude: number; longitude: number; body: string | null; categoryCodes: string[];
  visitedAt: string; createdAt: string; statisticsStatus: "PENDING" | "APPLIED" | "UNCHANGED"; // 지금은 항상 APPLIED
};
// capturedAt·visitedAt·createdAt 형식: "yyyy-MM-dd HH:mm:ss+09"
// 400 C011 위경도 범위, 422 C009 측정 시각이 서버 기준(max-age·max-future-skew 설정값, DEC-01) 밖
// 409 F003 오늘(Asia/Seoul 04:00 기준) 반경 안(설정 same-day-radius-meters, 오류 문구상 500m)에 이미 남긴 발자취가 있음
```

- 위치 증거에 정확도 필드가 없다. 규칙 80의 "정확도(m)와 측정 시각을 보낸다"와 다르므로, 클라이언트는 정확도를 화면에만 보여 주고 보내지 않는다. 규칙 80을 고칠지 백엔드에 필드를 요청할지 팀 확인이 필요하다(TBD).
- 저장되는 장소 이름은 서버가 역지오코딩으로 정한다. 화면의 "○○ 부근"은 기기 지오코더의 참고 값이다.
- **위치 수정(2026-11-06 사용자 결정, 거리 제한 없음):** 잰 위치가 빗나가면 W16 "위치 수정"으로 남길 곳을 지도에서 고칠 수 있다. 앱은 마지막 핀 좌표를 `location.latitude·longitude`로 보낸다.
- **백엔드 답변(2026-11-06):** 지금 계약 그대로 진행한다.
  - 서버는 핀 좌표를 사용자의 실제 위치로 보고 그대로 저장하고, 주소를 구하고, 500m 작성 제한(F003)을 검사하고, 내 발자취·방문 분포에 반영한다.
  - `accuracyMeters`·`measured`(원래 측정 좌표)는 당장 추가하지 않는다. 거리 제한이나 "직접 고른 위치" 표시가 필요해지면 그때 필드·오류 code·Notion 041·DB 컬럼을 같이 정한다. 서버는 모르는 필드를 무시한다.
  - 거리 제한·현장 방문 인정은 "제한 없음, 고른 위치를 실제 위치로 인정"으로 둔다. 규칙 80의 "정확도를 위치 증거로 보낸다"와 다르지만 백엔드 결정을 따른다.
  - `capturedAt`은 방문 시각(`visitedAt`)으로도 저장되며, 처음 잰 시각이나 핀을 확정한 시각을 보내면 된다. 앱은 둘 중 늦은 쪽을 보낸다. 유효 시간(C009)은 개발 서버 2분이고, 백엔드가 다음 작업 때 늘릴 예정이다.

**공통 오류 code가 §3-0과 다르다.** 백엔드 `ExceptionType`(2026-11-06)의 실제 번호는 다음과 같다.

| code | HTTP | 실제 뜻 |
| --- | --- | --- |
| C001·C002·C003 | 400 | 필수 누락·형식 오류·허용하지 않는 값 (§3-0과 같음) |
| C004 | 409 | 자원 버전 불일치 |
| C005 | 409 | 같은 멱등키로 다른 내용 |
| C006·C007 | 400 | 커서 무효·페이지 크기 범위 |
| C008·C009 | 422 | 위치 증거 없음·위치 증거 유효 시각 지남 |
| C011·C012·C013 | 400 | 위경도 범위·날짜 범위·지도 조회 영역 |
| C014·C015·C016·C017 | 500·404·405·415 | 서버 오류·경로 없음·메서드·Content-Type |
| C018 | 409 | 같은 요청 처리 중 |
| A001·A002·A003·A004 | 401·401·401·403 | 액세스 토큰 없음·만료·무효, 권한 부족 |

- 영향: `api/errors.ts`의 `COMMON_ERROR_MESSAGES`와 `api/client.ts`의 토큰 갱신 조건(`code === "C005"`, 서버의 만료 code는 `A002`)이 예전 번호를 쓴다. 이번 작업에서는 고치지 않았고, 발자취 화면만 `features/footprint/errors.ts`에서 서버 번호로 먼저 찾는다. §3-0과 공통 처리를 고치는 일은 팀 확인 뒤 따로 한다(TBD).
- API-046도 2026-11-06 실제 계약으로 옮겼다(§3-2). 방문 분포 화면도 서버 code(C003·C012·C013·ST001)를 `features/statistics/errors.ts`에서 먼저 찾는다.

## 4. 열거형과 기준 데이터

클라이언트 타입은 아래 값의 유니온으로 두고, 모르는 값은 `UNKNOWN`으로 흡수한다(규칙 90).

| 이름 | 값 | 비고 |
| --- | --- | --- |
| 소셜 로그인 provider | `google` `kakao` | API-001·002 경로 |
| 작업 상태 | **상세 미확인** | API-011 |
| 이동 수단 | **상세 미확인** (자차·대중교통). 화면 선개발용 가정 값 `CAR` `PUBLIC_TRANSIT`(§3-1) | API-023, 기능 TRP-10 |
| 팁 반응 | **상세 미확인** (좋아요·싫어요·취소) | API-052 |
| 기록 상태 | **상세 미확인** | API-059, 기능 REC-09 |
| 지역·카테고리 | 서버 기준 데이터(API-078·079, API-010) — 클라이언트에 하드코딩하지 않는다 | |

**서버 파생값(클라이언트에서 재계산 금지)**: 방문 분포도 집계(API-046), 영수증 합계·추출 금액(API-063), 이동 시간·주차 정보(API-037·027), 팁 반응 수(API-052).

## 5. 규칙과 다른 점 (이 계약이 우선)

| 항목 | 규칙의 기본값 | 이 계약 |
| --- | --- | --- |
| 요청 타임아웃 | 조회 10초 · 변경 30초 · 업로드 60초 | 명세에 값 없음 — 기본값 사용 |
| 서버 `message` 표시 | 계약이 허용하면 폴백 문구로 사용 가능 | **사용 금지**(개발자용 디버깅 설명) |

## 6. 이 문서를 갱신하는 법

1. 원천(Notion API 명세, 서버에 반영된 API는 Swagger)에서 바뀐 항목을 읽는다.
2. §2 표와 §3 타입을 고치고, 머리말의 대조 일시와 범위를 갱신한다.
3. 바뀐 필드를 쓰는 `features/<domain>/model.ts`, `api/mocks/fixtures/`, `api/mocks/handlers/`를 함께 고친다.
