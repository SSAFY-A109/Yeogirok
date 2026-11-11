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
| 페이지네이션 | 커서 방식(C006 커서 무효, C007 페이지 크기 범위 오류). 여행방 목록(API-020)은 `cursor`·`limit`(기본 20, 최대 50)을 받고 `{ items, nextCursor }`를 준다. 마지막 쪽은 `nextCursor: null`이다. 다른 목록은 해당 API에서 확인한다 | 공통 실패 코드 |
| 멱등성 | 멱등키를 사용한다(C005 같은 멱등키에 다른 요청 내용). 여행방에서는 생성(021)·초대 링크(024)·초대 수락(025)·장소 담기(029·030·031)·일정 초안 생성과 조정(037·084)·일정 저장(039)이 헤더를 요구한다. 백엔드 공통 멱등키 처리(`backend/dev`, 2026-11-04 확인)는 `Idempotency-Key` 헤더를 쓰고, 같은 요청이 처리 중이면 409 `C018`을 준다 | 공통 실패 코드 |
| 버전 충돌 | 자원 버전 비교(409 C004 요청한 자원의 버전과 현재 버전 불일치). 여행방은 응답의 `version`을 받아 수정(023 본문 `version`)·후보 빼기(032 쿼리 `version`)·복원(094 본문 `version`)·일정 요청(`baseVersion`)에 싣는다 | 공통 실패 코드 |
| 위치 증거 | 현재 위치가 필요한 요청은 위치 증거를 보낸다. 누락 C008, 유효 시각 만료 C009, 정확도 미달 C010. 필드 구성은 **상세 미확인** | 공통 실패 코드 |
| 지도 조회 영역 | 영역 파라미터가 있다(C013 형식·범위 오류). 형식은 **상세 미확인** | 공통 실패 코드 |
| 비동기 작업 | 작업 상태 조회 API-011 `GET /jobs/{jobId}`(오류 접두어 `J`). 작업 상태 열거형은 **상세 미확인** | API 목록 |
| 파일 | 업로드 API-012 `POST /media` → `mediaId`. 접근 API-013 `GET /media/{mediaId}/access` | API 목록 |
| 실시간 | 여행방 채팅(P1)은 API-077 `POST /realtime/tickets`로 연결 티켓을 받아 WebSocket `/ws/v1`에 붙는다. 프레임은 §3-3 | API 목록, `backend/dev` 12211b2 |
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
| C004 | 409 | 요청한 자원의 버전과 현재 버전 불일치 | 최신 상태 다시 불러오기 + 반영 안 됨 안내 |
| C005 | 409 | 동일 멱등키에 다른 요청 내용 사용 | 개발 오류로 보고(키 재사용 버그) |
| C006 | 400 | 페이지 커서 무효 | 목록 처음부터 다시 조회 |
| C007 | 400 | 페이지 크기 범위 오류 | 개발 오류로 보고 |
| C008 | 422 | 현재 위치 증거 누락 | 위치 다시 측정 안내 |
| C009 | 422 | 현재 위치 증거의 유효 시각 만료 | 위치 다시 측정 안내 |
| C010 | 422 | 현재 위치 정확도 기준 미충족 | 탁 트인 곳에서 다시 측정 안내 |
| C011 | 400 | 위도·경도 범위 오류 | 개발 오류로 보고 |
| C012 | 400 | 시작·종료 날짜 또는 시각의 범위·순서 오류 | 해당 입력 옆에 안내 |
| C013 | 400 | 지도 조회 영역 형식·범위 오류 | 개발 오류로 보고 |
| C014 | 500 | 예상하지 못한 서버 오류 | 일반 오류 문구, 다시 시도 |
| C015 | 404 | 없는 경로 | 개발 오류로 보고 |
| C016 | 405 | 허용되지 않은 HTTP 메서드 | 개발 오류로 보고 |
| C017 | 415 | 지원하지 않는 Content-Type | 개발 오류로 보고 |
| C018 | 409 | 같은 멱등키 요청을 처리하는 중 | 잠시 후 다시 시도 안내 |
| A001 | 401 | 서비스 액세스 토큰 누락 | 로그인으로 이동 |
| A002 | 401 | 서비스 액세스 토큰 만료 | 토큰 갱신 후 1회 재시도(규칙 90) |
| A003 | 401 | 서비스 액세스 토큰 무효·위변조 | 토큰 삭제 후 로그인 |
| A004 | 403 | 요청에 필요한 서비스 ROLE 부족 | 접근 불가 안내, 재시도 없음 |
| RO001 | 404 | 여행방 없음 | "찾을 수 없어요" 안내 |
| RO002 | 403 | 여행방 참여자가 아님 | 접근 불가 안내 |
| RO005 | 404 | 초대 토큰이 유효하지 않음 | 링크를 다시 받도록 안내 |
| RO007 | 409 | 여행방 정원(10명) 가득 참 | 정원 안내(W08-F) |
| RO009 | 404 | 후보 장소가 그 여행방에 없음 | 목록 다시 조회 |
| RO010 | 409 | 일정이 있는 여행방의 날짜를 비우려 함 | 날짜 필드 옆에 안내 |

2026-11-10에 팀 Notion "API 명세"의 공통 실패 코드 표와 `backend/dev`(`8778cb2`)의 `ExceptionType`을 대조해 번호를 고쳤다. 이전 표(C004~C006 인증, C008 버전 충돌, C009 멱등키 등)는 폐기된 번호다.

**도메인 접두어**: `C` 공통 · `A` 인증·인가 · `U` 사용자 · `J` 비동기 작업 · `M` 파일 · `CO` 공유 코스 · `RO` 여행방 · `P` 장소 · `AI` 추천 · `SC` 계획 일정 · `F` 발자취 · `ST` 방문 통계 · `T` AR 팁 · `RE` 여행 기록. 도메인별 code는 각 API 상세의 실패 응답 영역에 있다(**상세 미확인**).

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
| 020·021·022·023·024·025·028·032·087·094 | `features/room/model.ts` | **가정 아님** — 2026-11-10 `backend/dev`(`8778cb2`)의 room 컨트롤러·DTO로 맞췄다(아래 "여행방 실제 계약" 단락) |
| 029 | `features/room/model.ts` (`AddRoomPlacesRequestDto`, `AddRoomPlacesResponseDto`) | **가정 아님** — 서버에는 아직 없고 2026-11-10 Notion 명세 상세로 맞췄다. 요청 `{ items[{ placeRef, sourceType, recommendationRequestId?, rank? }] }` + `Idempotency-Key`, 응답 `{ added, addedCount, version }` |
| 026 | `features/place/model.ts` (`PlaceDto`, `SearchPlacesResponseDto`) | **가정 아님** — 2026-11-10 Notion 명세 상세로 맞췄다(아래 "026·029·033·034·081" 단락). 쿼리 `q`·`regionCode`·`cursor`·`limit`, 응답 `{ items, nextCursor, totalCount }` |
| 033(=080)·034 | `features/recommendation/model.ts` | **가정 아님** — 2026-11-10 Notion 명세 상세로 맞췄다(아래 단락). 조건 요청 `{ version, regionCode, categoryCodes, skipped: false }`, 추천 결과 `{ recommendationRequestId, mode, items[{ rank, place, reason, reasonEvidence }], generatedAt }` |
| 081 | `features/recommendation/model.ts` (`RecommendationPreferenceDto`) | **가정(TBD)** — 상세 페이지 미확인. API-033 응답과 같은 `{ roomId, preferences, version }`, 저장한 조건이 없으면 빈 본문으로 가정 |
| 036·037·038·084·039 | `features/schedule/model.ts` | **가정 아님** — 2026-11-10 `backend/dev`(`8778cb2`)의 schedule 컨트롤러·DTO와 `backend/docs/06-schedule.md`로 맞췄다. 037은 202 `{ jobId, draftKey, status }`(헤더 `Idempotency-Key`, 본문 `baseVersion`·`roomPlaceIds`·`days`·`fixedItems?`), 038은 `{ status(QUEUED·RUNNING·SUCCEEDED·FAILED), baseVersion, draftVersion, conditions, schedule, error }`, 084는 바꾼 항목만 보내는 재계산, 039는 고친 전체 상태(`days`·`unassignedItemIds`)를 보내는 저장이다. 일차 시각은 `HH:mm:ss`, 항목 시각은 datetime이며 모르면 `null`이다. pace는 없다. 확정 일정 직접 편집(040)과 작업 상태 조회(011)는 앱이 부르지 않는다 |
| 078·079 | `features/common/model.ts` | 지역은 시/도 → 시/군/구 2단(`children`) |

- 도메인 오류 code는 `RO`(여행방)·`SC`(일정)가 서버에 있다(§3-0, `features/room/errors.ts`, `features/schedule/errors.ts`). `AI`(추천)는 아직 없어 도메인 기본 문구만 쓴다.

2026-11-09 최신 `backend/dev`(`473ba06`)의 `RoomController`, `RoomCreateRequest`, `RoomCreateResponse`를 대조해 여행방 생성(API-021)을 실제 계약에 맞췄다. 같은 요청 재시도에는 동일한 `Idempotency-Key`를 사용한다. Swagger는 HTTP 502였고, 같은 브랜치의 `RoomPlaceService`에는 코스 장소 가져오기(API-030)가 미구현으로 명시되어 있다.

2026-11-09 사용자가 제공한 API-030 명세로 새 여행방 생성 후 공개 코스의 선택 방문을 가져오는 클라이언트와 MSW를 연결했다. 배포 서버의 API-030 구현 여부는 미확인이다.

**026·029·033·034·081 — Notion 명세 상세 반영 (2026-11-10).** 이 다섯 API는 서버에 아직 없다. Figma에서 역산했던 가정 DTO를 팀 Notion "API 명세" 상세 페이지 기준으로 바꿨고, MSW 목도 같은 모양이다. 서버에 올라오면 Swagger로 다시 대조한다.

- 장소는 전역 숫자 id가 없다. `provider`(`TOUR_API`·`MANUAL`)와 `providerPlaceId`(string, 없으면 `null`)로 구별하고, 담을 때는 `placeRef: { provider, providerPlaceId }`로 가리킨다. 화면의 목록 키와 "이미 담긴 곳" 판정은 `toPlaceKey(provider, providerPlaceId)`(`features/room/model.ts`)를 쓴다. `providerPlaceId`가 없는 장소는 가리킬 수 없어 담기 대상에서 뺀다.
- 검색·추천 응답의 장소는 `{ provider, providerPlaceId, name, latitude, longitude, address, categoryCode, imageUrl, imageAttribution }`다. 카테고리 이름은 주지 않으므로 공통 카테고리 목록(API-079)에서 코드로 찾아 보여 준다.
- API-026 `GET /places`: 쿼리 `q`(필수)·`regionCode`·`cursor`·`limit`, 응답 `{ items, nextCursor, totalCount }`. 실패는 400 `P002`(검색어 조건), 502 `P003`(외부 장소 정보 공급자), 공통 `C006`(커서)·`C007`(페이지 크기). `limit` 허용 범위는 명세에 없다(TBD).
- API-029 `POST /rooms/{roomId}/places`: `Idempotency-Key` 필수. 본문 `{ items: [{ placeRef, sourceType: "SEARCH" } | { placeRef, sourceType: "AI", recommendationRequestId, rank }] }`(1개 이상), 201 `{ added, addedCount, version }`. 같은 장소를 새 요청으로 여러 번 담을 수 있다(팀 확정 정책). 화면은 Figma W12대로 이미 담긴 곳을 "담음" 비활성으로 둔다.
- API-033 `PUT /rooms/{roomId}/preferences`: 본문 `{ version, regionCode, categoryCodes, skipped }`. `regionCode`는 시/군/구 코드(추천 실행 전 필수), `categoryCodes`는 API-079의 코드로 1개 이상(상한 없음), `skipped`는 `false`만 받는다. 선택 필드(startAt·endAt·companions·tasteKeywords·transportMode·style·includeMeals·freeText)는 W10 입력이 아니라 보내지 않는다. 응답 `{ roomId, preferences: { regionCode, categoryCodes, skipped }, version }`.
  - **TBD:** 요청의 `version`이 조건의 version인지 여행방 version인지 명세에 설명이 없다. 응답의 `version`을 받아 두었다가 다음 저장에 보낸다(처음에는 API-081 응답의 값, 저장한 조건이 없으면 1).
- API-034 `POST /rooms/{roomId}/place-recommendations`: `Idempotency-Key` 필수, 저장된 조건으로 추천한다. 본문 `{ limit?, excludedPlaces?: PlaceRef[] }`(화면은 둘 다 보내지 않는다), 응답 `{ recommendationRequestId, mode, items: [{ rank, place, reason, reasonEvidence }], generatedAt }`. 예전 가정의 `requestId`·`criteria`·`routeHint`는 명세에 없어 지웠다. 결과 화면의 "○○ 기준" 줄은 사용자가 고른 카테고리 이름으로 만든다. 도메인 오류 code(`AI`)는 명세에 아직 없다(TBD).
- 확정 정책: 추천 카테고리는 1개 이상 필수(건너뛰기 없음), 추천 지역은 사용자가 직접 고른다(담은 장소로 자동 선택하지 않는다. 전에 저장한 값은 되살린다).

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
- **마커 위치(백엔드 요청, TBD):** 서버의 `center`는 칸 한가운데라, 해안에 걸친 칸은 발자취가 육지에 있어도 마커가 바다에 찍힌다(실기기에서 확인, 2026-11-06). 칸 안 발자취 좌표의 평균(수로 가중)을 `center`로 달라고 백엔드에 요청한다. 앱은 `center`를 그대로 그리므로 서버가 바꾸면 앱 수정은 없다. 목은 이 평균 위치로 미리 응답한다(`api/mocks/handlers/statistics.ts`).

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
- **위치 수정(2026-11-06 사용자 결정):** 잰 위치가 빗나가면 W16 "위치 수정"으로 남길 곳을 지도에서 고칠 수 있다. 앱은 마지막 핀 좌표를 `location.latitude·longitude`로 보낸다.
- **위치 수정 범위(2026-11-11 사용자 결정):** 잰 위치에서 100m 안에서만 고칠 수 있다(`features/footprint/model.ts`의 `FOOTPRINT_ADJUST_RADIUS_METERS`). 앱에서만 막는다. 서버는 핀 좌표만 받아 이 제한을 확인하지 못하므로, 서버에서도 막으려면 원래 측정 좌표 필드가 필요하다(TBD, 백엔드 확인 필요). 아래 백엔드 답변의 "제한 없음"은 서버 기준으로는 그대로다.
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

### 3-2. 여행방 실제 계약 (2026-11-10, backend/dev 8778cb2)

Swagger는 요청 필드 이름과 헤더·쿼리만 보여 주고 응답은 `{ success }`로만 나온다. 응답 모양과 오류 코드는 `backend/dev`의 `domain/room` 컨트롤러·DTO와 `ExceptionType`을 읽어 맞췄다.

| API | 요청 | 응답 | 비고 |
| --- | --- | --- | --- |
| 020 `GET /rooms` | 쿼리 `cursor?`·`limit?`(기본 20, 최대 50) | `{ items[{ id, title, startDate, endDate, transportMode, memberCount, members[{ userId, nickname, profileImageUrl }], placeCount, recordIds, chatReadState }], nextCursor }` | 화면이 다가오는·지난 여행으로 나누므로 `nextCursor`가 `null`일 때까지 모두 받는다 |
| 021 `POST /rooms` | 헤더 `Idempotency-Key`, `{ title, startDate, endDate, transportMode }` | 201 `{ id, version, isCreator }` | `transportMode`는 `CAR`·`TRANSIT`. 출발일이 오늘보다 앞이면 C012 |
| 022 `GET /rooms/{roomId}` | — | `{ id, title, startDate, endDate, transportMode, members[{ userId, nickname, isCreator }], places, recordIds, version, chatReadState, schedule }` | 정원(10명)·"나" 표시·일정 생성 시각은 응답에 없다. 정원은 상수, "나"는 내 계정 id와 비교한다 |
| 023 `PATCH /rooms/{roomId}` | `{ version, title?, startDate?, endDate?, transportMode?, previewOnly? }` | `{ id, version, applied, scheduleNeedsRecalculation, affectedItems[{ scheduleItemId, roomPlaceId, name, visitDate }], schedule }` | 일정이 있는 방의 날짜·이동 수단을 바꾸면 `previewOnly: true`로 빠질 항목을 먼저 받아 W06-E2에 보여 준다. 날짜를 비우면 RO010 |
| 024 `POST /rooms/{roomId}/invitations` | 헤더 `Idempotency-Key` | `{ invitationId, inviteToken, expiresAt: null, inviteUrl }` | 링크는 방마다 하나이고 만료가 없다 |
| 025 `POST /room-invitations/accept` | 헤더 `Idempotency-Key`, `{ inviteToken }` | `{ roomId, membershipStatus: "JOINED", isCreator }` | 참여 확인 화면 `app/invite/[token].tsx`. 정원 초과 RO007, 잘못된 토큰 RO005 |
| 028 `GET /rooms/{roomId}/places` | 쿼리 `state?`(ACTIVE·REMOVED·ALL) | `{ items[{ id, place{ provider, providerPlaceId, name, latitude, longitude, address, categoryCode, categoryName, regionCode, regionName, imageUrl, imageAttribution }, sourceType, sourceRecordId, sourceRecord, addedBy, removedAt }], version }` | `sourceType`은 SEARCH·COURSE·AI·MANUAL. `addedBy`는 사용자 id라 닉네임은 참여자 목록에서 찾는다 |
| 032 `DELETE /rooms/{roomId}/places/{roomPlaceId}` | 쿼리 `version` | `{ removed, version, scheduleNeedsRecalculation, undoExpiresAt: null }` | 후보 목록에서만 뺀다. 일정 항목은 남는다 |
| 094 `PATCH /rooms/{roomId}/places/{roomPlaceId}` | `{ version, action: "RESTORE" }` | `{ restored, roomPlaceId, version, scheduleNeedsRecalculation }` | "되돌리기" 토스트가 부른다. 시간 제한 없음 |
| 087 `DELETE /rooms/{roomId}/members/me` | — | 본문 없음 | 마지막 참여자가 나가면 서버가 방을 지운다. 삭제 여부는 응답에 없어 나가기 전 인원으로 판단한다 |
| 076 `GET /rooms/{roomId}/messages` | 쿼리 `beforeMessageId?`(이 id보다 과거), `afterMessageId?`+`throughMessageId?`(재연결 복구), `limit?`(기본 20, 최대 50) | `{ items[{ id, sender{ id, nickname }, body, createdAt, clientMessageId }], hasMore, nextAfterMessageId, replayThroughMessageId, chatReadState{ lastReadMessageId, hasUnreadMessages } }` | `backend/dev` 12211b2 기준(2026-11-11). `items`는 오래된 → 최신. 탈퇴한 회원은 `sender.id`·`clientMessageId`가 `null`이고 이름이 "탈퇴한 사용자"다. `limit` 범위 밖 C007, 조회 방향 혼용·범위 밖 id C003. 조회만으로는 읽은 위치가 바뀌지 않는다. 과거 이력은 `beforeMessageId`, 재연결 복구는 `afterMessageId`(받은 것이 없으면 0)로 받는다. 복구 첫 쪽의 `replayThroughMessageId`를 다음 쪽의 `throughMessageId`로, `nextAfterMessageId`를 다음 `afterMessageId`로 보낸다 |
| 088 `PATCH /rooms/{roomId}/messages/read` | `{ lastReadMessageId }` | `{ roomId, lastReadMessageId, hasUnreadMessages }` | `backend/dev` 12211b2 기준(2026-11-11). 화면에서 실제로 본 위치만 보낸다. 더 작은 값을 보내면 기존 값을 유지하고 200이다. 누락 C001, 정수 아님 C002, 양수가 아니거나 그 방의 메시지가 아니면 C003. 안 읽음(`hasUnreadMessages`)은 읽은 위치보다 큰 타인 메시지가 있는지다(020·022·076과 같은 계산) |
| 077 `POST /realtime/tickets` | 헤더 `Idempotency-Key`, `{ roomId }` | 201 `{ ticket, expiresAt, connectPath: "/ws/v1", roomId }` | `backend/dev` 12211b2 기준(2026-11-11). 티켓은 30초 안에 한 번만 쓸 수 있다. 같은 멱등키로 다시 요청하면 처음 티켓이 오므로 연결할 때마다 새 키를 쓴다. 참여자 아님 RO002, 여행방 없음 RO001 |

- 여행방 `version`은 `features/room/api/queries.ts`의 `resolveRoomVersion`·`storeRoomVersion`이 캐시에서 읽고 쓴다. version을 싣는 변경 요청은 같은 scope로 차례로 보낸다.
- 초대 주소(`inviteUrl`)는 서버 설정 `ROOM_INVITE_URL_BASE` + `/{inviteToken}`이다. 앱이 이 주소를 직접 여는 설정(앱 링크·유니버설 링크)은 아직 없다(`TBD`).
- 지도 핀으로 장소 직접 추가(031)와 기록 공간 생성(057)은 서버에 있지만 화면이 없다.

### 3-3. 여행방 채팅 WebSocket (2026-11-11, backend/dev 12211b2)

`backend/docs/07-chat.md`와 `domain/chat/realtime`의 `ChatFrames`·`ChatWebSocketHandler`를 읽어 맞췄다. 주소는 API 호스트의 `wss://…/ws/v1`이고, 티켓·토큰을 주소나 헤더에 넣지 않는다. 프레임은 JSON 텍스트다(STOMP 아님).

| 방향 | 프레임 | 비고 |
| --- | --- | --- |
| 앱 → 서버 | `{ type: "AUTH", ticket }` | 연결 뒤 첫 프레임. 10초 안에 보내지 않으면 A001로 닫힌다 |
| 서버 → 앱 | `{ type: "READY", roomId, userId, latestMessageId }` | 이때부터 보낼 수 있다. `latestMessageId`는 방의 최신 저장 id(없으면 `null`) |
| 앱 → 서버 | `{ type: "SEND_MESSAGE", roomId, clientMessageId, body }` | `body`는 1~100자(코드 포인트), 앞뒤 공백 그대로 저장. `clientMessageId`는 1~100자 |
| 서버 → 앱 | `{ type: "MESSAGE_ACK", roomId, clientMessageId, replayed, message }` | 보낸 사람에게만. 같은 `clientMessageId`·같은 본문을 다시 보내면 새로 저장하지 않고 `replayed: true` |
| 서버 → 앱 | `{ type: "MESSAGE_CREATED", roomId, message }` | 방의 모든 연결에(보낸 사람 포함). 순서가 바뀌거나 빠질 수 있어 `message.id`로 정렬·중복 제거한다 |
| 앱 → 서버 | `{ type: "PING" }` | 25초마다. 서버는 60초 동안 프레임이 없으면 닫는다 |
| 서버 → 앱 | `{ type: "PONG", roomId, latestMessageId }` | 최신 id가 받은 것보다 크면 API-076 복구 조회로 확인한다 |
| 서버 → 앱 | `{ type: "ERROR", roomId, clientMessageId, code, message, retryable }` | `code`로만 분기한다. `clientMessageId`가 있으면 그 메시지의 실패다 |

- `message`는 API-076의 메시지와 같은 모양이다.
- `ERROR` code: A001(AUTH 없음)·A003(티켓 무효·만료·재사용, 탈퇴)·RO001(방 없음)·RO002(참여자 아님)은 보낸 뒤 서버가 연결을 닫는다. C001·C002·C003(입력 오류), C005(같은 `clientMessageId`에 다른 본문)는 연결을 유지한다. C014만 `retryable: true`이고 같은 `clientMessageId`·같은 본문으로 다시 보낼 수 있다.
- 나가기(087)·방 삭제·회원 탈퇴가 일어나면 서버가 `ERROR`(RO002·RO001·A003)를 보내고 닫는다.
- 앱 처리: RO001·RO002는 다시 연결하지 않는다. 그 밖에 닫히면 새 티켓으로 다시 연결하고(지수 백오프 + 무작위), 저장 완료를 받지 못한 메시지를 같은 `clientMessageId`로 다시 보낸다.
- 서버에 없는 것: 접속 중인 인원 수, 타인 읽음 표시, 사진, 앱 푸시(API-095·096은 Notion에만 있다).