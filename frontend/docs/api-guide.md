# API 통신 가이드

서버 통신 코드를 쓸 때 반드시 이 가이드를 따른다.
- **무엇을 주고받는지**(엔드포인트·DTO·열거형·공통 규약 값): 팀 Notion "API 명세". 서버에 반영된 API는 Swagger를 우선한다. 별도 계약 사본은 두지 않는다.
- **규칙**: `.agents/rules/20-api-data.md`(호출 계층·Query·작업·업로드), `.agents/rules/90-backend-contract.md`(계약·MSW)
- 이 문서는 그것을 **코드로 옮기는 절차와 예시**다.

> 예시 코드는 구현 목표이며, DTO 필드는 명세 상세를 확인하기 전의 **가정**이다. 실제 구현 전에 Notion·Swagger로 필드를 확인하고 바꾼다.

## 1. 아키텍처

| 환경 | API 연결 | MSW |
| --- | --- | --- |
| 개발(`__DEV__ === true`) | 같은 HTTP 클라이언트의 요청을 MSW가 가로채 응답 | 전 도메인 사용, 라우터 시작 전에 준비 완료 |
| 배포(`__DEV__ === false`) | `EXPO_PUBLIC_API_URL`의 실제 백엔드 서버 | 불러오거나 시작하지 않음 |
| jest·서버 없는 E2E | 명세에 맞춘 fixture 응답 | jest는 `msw/node`, E2E는 개발 빌드의 `msw/native` |

- 화면·Query·도메인 API 함수는 환경에 따라 달라지지 않는다. 환경별 연결은 API 호스트 설정과 앱 진입점의 MSW 초기화로 처리한다.
- `EXPO_PUBLIC_API_URL`은 호스트만 지정한다. 개발에서는 `https://api.yeogirok.test` 같은 MSW 요청용 주소, 배포에서는 실제 백엔드 HTTPS 주소를 설정한다. `/api/v1`은 `api/client.ts`가 붙인다.
- 개발 중 미처리 서비스 API는 오류로 처리한다. 실제 백엔드로 자동 통과시키거나 일부 도메인만 실제 서버에 연결하지 않는다.

```
app/ 화면 ──▶ features/<domain>/api/queries.ts ──▶ features/<domain>/api/<domain>.api.ts ──▶ api/client.ts ──▶ <호스트>/api/v1
             (xxxKeys · queryOptions · 훅)         (엔드포인트 1:1 함수)                      (axios · 인터셉터 · ApiError)
                                                          │                                       ▲
                                                          ▼                                       │ (개발·테스트) MSW가 네트워크에서 가로챔
                                             features/<domain>/model.ts                    api/mocks/handlers/<domain>.ts
                                             (DTO → 화면 모델, ContractMismatchError)
```

| 계층 | 위치 | 책임 | 금지 |
| --- | --- | --- | --- |
| 통신 | `api/client.ts` | axios 인스턴스(`baseURL`, 타임아웃 상수), 요청 인터셉터(인증 헤더), 응답 인터셉터(봉투 벗기기, 401 갱신 단일 실행, `ApiError` 정규화) | 도메인 함수 밖에서 `api` import |
| 공통 오류 | `api/errors.ts` | `ApiError`, `isApiError`, 공통 code(C001~C017) 처리와 문구 | 도메인 code 처리 |
| 도메인 API | `features/<domain>/api/<domain>.api.ts` | 엔드포인트 1:1 함수. 인자는 도메인 타입이고, 안에서 path·params·body로 매핑한다 | TanStack Query import, 헤더 조립, 목 분기 |
| 화면 모델 | `features/<domain>/model.ts` | DTO 타입(Notion·Swagger 명세와 1:1) + `toXxx` 변환 + 계약 검증 | UI 문구, 포맷팅 이외의 로직 |
| 서버 상태 | `features/<domain>/api/queries.ts` | `<domain>Keys` 팩토리, `queryOptions`, `useXxx` 훅, 뮤테이션 훅과 무효화 | axios·fetcher 직접 호출 |
| 오류 문구 | `features/<domain>/errors.ts` | 도메인 `code` → 사용자 문구 | 서버 `message`를 보여주거나 분기 기준으로 사용 |
| 목 | `api/mocks/handlers/<domain>.ts`, `api/mocks/fixtures/<domain>.ts` | MSW 핸들러와 계약 예시 응답(봉투 포함) | 실제 개인 정보·좌표·영수증 |
| UI | `app/`, `features/<domain>/components/` | 훅 사용, `isPending`/`isError` 분기 | `api/`·도메인 함수 직접 import |

## 2. 도메인 폴더 ↔ API 도메인

`features/` 폴더 이름은 API 도메인(오류 코드 접두어)과 1:1로 맞춘다. 한 엔드포인트를 두 폴더에서 호출하지 않는다.

| `features/` | 접두어 | 엔드포인트 | 비고 |
| --- | --- | --- | --- |
| `auth` | AU | API-001·002·005·006·007 | 토큰 보관(`expo-secure-store`)과 로그아웃 시 초기화도 여기 |
| `user` | U | API-008·009 | 회원 탈퇴 시 전체 초기화(규칙 80) |
| `common` | C | API-010·078·079 | 공통 기준 데이터 |
| `job` | J | API-011 | 작업 상태 조회 공용 훅 |
| `media` | M | API-012·013 | 업로드 대기열 |
| `course` | CO | API-014~019·074·075·083, `GET /main/popular-places` | 공개 코스 탐색·관리. 메인 인기 장소도 코스 가져가기 집계라 여기 둔다(오류는 공통 C003) |
| `room` | RO | API-020~025·028~032·076·077 | 여행방·장소 담기·초대·채팅(P1) |
| `place` | P | API-026·027 | 장소 검색·상세 |
| `recommendation` | AI | API-033~035·080~082 | 추천 온보딩·추천 결과 |
| `schedule` | SC | API-036~040 | 여행방 일정·AI 일정 초안 |
| `footprint` | F | API-041~045 | 발자취·방명록 |
| `statistics` | ST | API-046 | 방문 분포도 |
| `tip` | T | API-047~056 | AR 팁 |
| `record` | RE | API-057~073 | 여행 기록·AI 기록 초안·코스 공개 |

## 3. 공통 규약을 코드로

### 3-1. `api/client.ts`가 책임지는 것

```ts
// api/client.ts — 목표 형태
export const API_BASE_URL = process.env.EXPO_PUBLIC_API_URL; // 호스트만, 개발·배포 모두 필수
if (!API_BASE_URL) throw new Error("EXPO_PUBLIC_API_URL을 설정해야 합니다.");
export const TIMEOUT_QUERY_MS = 10_000;     // 조회
export const TIMEOUT_MUTATION_MS = 30_000;  // 생성·변경
export const TIMEOUT_UPLOAD_MS = 60_000;    // 파일 업로드

export const api = axios.create({
  baseURL: `${API_BASE_URL}/api/v1`,
  timeout: TIMEOUT_QUERY_MS,
  headers: { Accept: "application/json", "Content-Type": "application/json" },
});

// 요청: SecureStore의 accessToken을 `Authorization: Bearer`로. 소셜 로그인·토큰 발급·갱신(API-001·002·005·006)은 제외.
// 응답: api/envelope.ts의 unwrapEnvelope로 { success, data }를 벗긴다(도메인 함수는 data만 본다).
//       401 → 갱신 단일 실행(동시 401은 같은 Promise를 기다린다) → 원 요청 1회 재시도 → 실패 시 토큰 삭제 + 로그인 이동.
//       그 외 오류 → toApiError로 ApiError(status, code, message) 정규화. 네트워크 단절·타임아웃은 status 0, code "NETWORK" | "TIMEOUT".
```

- 도메인 함수는 경로만 쓴다: `api.get("/rooms")`. Base path를 붙이지 않는다.
- 빈 본문 성공(`data: null`)은 `Promise<void>`로 다룬다.
- 헤더는 `client.ts` 인터셉터만 붙인다. 예외는 두 가지다.
  - 멱등성 키: 해당 도메인 함수가 인자로 받은 키를 `config.headers`에 넣는다. 헤더 이름은 Notion·Swagger 명세를 따르며 상세 미확인이다.
  - 버전 값: 계약이 헤더로 정하면 위와 같이 처리한다.
- 생성·변경 요청은 `{ timeout: TIMEOUT_MUTATION_MS }`, 업로드는 `{ timeout: TIMEOUT_UPLOAD_MS }`를 넘긴다.

### 3-2. 타입 변환 규칙 (`model.ts`)

| 계약 값 | DTO 타입 | 화면 모델 | 변환 |
| --- | --- | --- | --- |
| datetime `2026-07-27 15:30:00+09` | `string` | `Date` | `lib/date.ts`의 `parseServerDateTime`. 날짜·시각 사이 공백과 `+09` 오프셋을 처리한다. 형식이 다르면 `ContractMismatchError` |
| date `2026-07-27` / time `15:30:00` | `string` | `LocalDate` / `LocalTime` 문자열 | 형식만 검증하고 시간대를 붙이지 않는다 |
| 고유 id | `number` | `number` | `Number.isSafeInteger` 검증 |
| 위경도 | `number` | `{ latitude, longitude }` | 범위 검증(C015와 같은 기준) |
| 금액(영수증 등) | 상세 미확인 | `KRW`(`lib/money.ts`, 문자열) | 형식 검증 후 변환. 합계는 서버 값 그대로 |
| 열거형 | 문자열 유니온 | 유니온 + `"UNKNOWN"` | 목록 밖 값은 `UNKNOWN`으로 흡수 |
| 다음 커서 | 상세 미확인 | 그대로 | `useInfiniteQuery`의 `getNextPageParam` |
| `null` 가능 필드 | `T \| null` | `T \| null` | 상태 의미가 있는 `null`(예: 주차 정보 없음)은 `??`로 기본값을 만들지 않는다. "정보 없음"으로 표시한다 |

- 서버 파생값(방문 집계, 영수증 합계, 이동 시간, 반응 수)은 **검증만 하고 계산하지 않는다**.
- DTO 타입 이름은 Notion·Swagger 명세와 같게 하고 `Dto` 접미만 붙인다. 두 도메인이 같이 쓰는 타입은 소유 도메인의 `model.ts`에서 export한다.

## 4. 새 도메인 API 추가하는 법 — 예시: 방문 분포도 조회와 발자취 저장

### ① `features/footprint/model.ts`

```ts
import { ContractMismatchError } from "@/lib/contract";
import { parseServerDateTime } from "@/lib/date";
import type { LocationFix } from "@/lib/native/location";

// 위치 증거 — 필드 구성은 계약 상세 미확인(가정)
export type LocationEvidenceDto = { latitude: number; longitude: number; accuracyM: number; measuredAt: string };
export type CreateFootprintRequestDto = { location: LocationEvidenceDto; guestbook: string | null };
export type FootprintDto = { footprintId: number; placeName: string | null; createdAt: string };

export type Footprint = { id: number; placeName: string | null; createdAt: Date };

export function toLocationEvidence(fix: LocationFix): LocationEvidenceDto {
  return { latitude: fix.latitude, longitude: fix.longitude, accuracyM: fix.accuracyM, measuredAt: fix.measuredAt };
}

export function toFootprint(dto: FootprintDto): Footprint {
  if (!Number.isSafeInteger(dto.footprintId)) throw new ContractMismatchError("footprintId");
  return { id: dto.footprintId, placeName: dto.placeName, createdAt: parseServerDateTime(dto.createdAt, "createdAt") };
}
```

### ② `features/footprint/api/footprint.api.ts`

```ts
import { api, TIMEOUT_MUTATION_MS } from "@/api/client";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency"; // 헤더 이름은 계약 §1(상세 미확인)
import { toFootprint, type CreateFootprintRequestDto, type Footprint, type FootprintDto } from "@/features/footprint/model";

export type CreateFootprintInput = { request: CreateFootprintRequestDto; idempotencyKey: string };

export async function createFootprint({ request, idempotencyKey }: CreateFootprintInput): Promise<Footprint> {
  const { data } = await api.post<FootprintDto>("/footprints", request, {
    timeout: TIMEOUT_MUTATION_MS,
    headers: { [IDEMPOTENCY_HEADER]: idempotencyKey },
  });
  return toFootprint(data);
}
```

- 함수 이름은 동사로 시작한다: `getXxx` / `createXxx` / `updateXxx` / `deleteXxx` / `searchXxx`.
- 조회 함수의 마지막 인자는 `signal?: AbortSignal`이다(TanStack Query가 취소에 쓴다).
- 호출부는 어떤 값이 path로 가고 어떤 값이 body로 가는지 몰라야 한다. 인자는 도메인 타입 하나로 받는다.

### ③ `features/footprint/api/queries.ts`

```ts
import { useMutation, useQueryClient } from "@tanstack/react-query";

import { createFootprint, type CreateFootprintInput } from "@/features/footprint/api/footprint.api";
import { statisticsKeys } from "@/features/statistics/api/queries";

export const footprintKeys = {
  all: ["footprint"] as const,
  mine: () => [...footprintKeys.all, "mine"] as const,
};

export function useCreateFootprint() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateFootprintInput) => createFootprint(input),
    retry: false, // 위치 증거 오류(C012~C014)와 중복 생성은 재시도로 해결되지 않는다
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: footprintKeys.all });
      void queryClient.invalidateQueries({ queryKey: statisticsKeys.all });
    },
  });
}
```

### ④ 화면

```tsx
function FootprintComposer({ fix }: { fix: LocationFix }) {
  const [guestbook, setGuestbook] = useState("");
  const [idempotencyKey] = useState(createIdempotencyKey); // 화면 진입 시 한 번 — 재시도에도 같은 키
  const { mutate, isPending, error } = useCreateFootprint();

  const onSubmit = () =>
    mutate({ request: { location: toLocationEvidence(fix), guestbook: guestbook || null }, idempotencyKey });

  return (
    <>
      <GuestbookInput value={guestbook} onChangeText={setGuestbook} />
      {error ? <FootprintError error={error} /> /* C012~C014는 '위치 다시 확인' 버튼 */ : null}
      <Button size="lg" className="h-button-lg" disabled={isPending} onPress={onSubmit}>
        <Text className="text-button">발자취 남기기</Text>
      </Button>
    </>
  );
}
```

## 5. 쿼리 키와 무효화 지도

키는 반드시 `<domain>Keys` 팩토리로만 만든다. 표에 없는 조합이 필요하면 표부터 고친다.

| 뮤테이션 | 무효화(invalidate) | 즉시 반영(setQueryData) |
| --- | --- | --- |
| `POST /footprints` (API-041) | `footprintKeys.all`, `statisticsKeys.all` | — |
| `PUT /tips/{id}/reaction` (API-052) | `tipKeys.detail(id)` stale 처리, 이전 위치로 자동 재조회하지 않음 | 선택 반응 낙관적 반영 → 실패 시 롤백, 성공 시 서버 집계 반영 |
| `POST /tips` (API-049) | `tipKeys.mine()` | `tipId`로 API-050 진행 확인, 202를 게시 완료로 표시하지 않음 |
| `PATCH /tips/{id}` · `POST …/retry` (API-054·051) | `tipKeys.status(id)` 초기화 후 새 작업 확인, 수정 시 본인 상세 재조회 | 이전 성공 본문·사진은 작업 성공 전 유지 |
| API-050에서 `operationStatus=SUCCEEDED` 확인 | `tipKeys.mine()`, 공개 상세·주변 검색 stale 처리 | `useTipPublicationRefresh` 호출 |
| `DELETE /tips/{id}` (API-055) | 본인 목록, 주변 검색 stale 처리 | 본인 상세·공개 상세·상태 캐시 제거 |
| `POST /rooms` (API-021) | `roomKeys.list()` | 응답 상세 → `roomKeys.detail(id)` |
| `PATCH /rooms/{id}` (API-023) | `roomKeys.list()`, `roomKeys.places(roomId)`, `scheduleKeys.room(roomId)` | 응답 상세 → `roomKeys.detail(roomId)` |
| `DELETE /rooms/{id}/members/me` (가정, TBD) | `roomKeys.list()` | — (나간 방의 캐시는 닫히는 화면이 다시 요청하지 않도록 바로 지우지 않고 gcTime에 맡긴다) |
| `POST /rooms/{id}/places` · `place-imports` (API-029·030) | `roomKeys.places(roomId)`, `roomKeys.detail(roomId)` | — |
| `DELETE /rooms/{id}/places/{roomPlaceId}` (API-032) | 완료·실패 모두 `roomKeys.places(roomId)`, `roomKeys.detail(roomId)` | — (화면이 되돌리기 토스트 동안 행을 숨겼다가 확정 시 요청) |
| `PUT /rooms/{id}/preferences` + `POST …/place-recommendations` (API-033·034) | `recommendationKeys.preference(roomId)` | — (추천 결과는 뮤테이션 `data`로만 쓴다) |
| `POST /rooms/{id}/schedule-drafts` (API-037, 작업) | — (진행은 `scheduleKeys.draft(roomId, draftId)`를 끝날 때까지 확인. 끝난 초안은 다시 받지 않는다) | — |
| `PUT /rooms/{id}/schedule` (API-039) | `roomKeys.detail(roomId)`. 409 C008이면 `scheduleKeys.room(roomId)`·`roomKeys.detail(roomId)` 다시 조회 | 응답 일정 → `scheduleKeys.room(roomId)` |
| `PATCH /rooms/{id}/schedule` (API-040) | 409 C008이면 `scheduleKeys.room(roomId)` 다시 조회 | 응답 일정 |
| `POST /records/{id}/drafts` (API-064) | 작업 완료 시 `recordKeys.drafts(recordId)` | — |
| `POST /records/{id}/publications` (API-073) | `recordKeys.detail(recordId)`, `courseKeys.mine()` | — |
| `POST /auth/token` (API-005) | 전체 캐시 `queryClient.clear()` 후 시작 | — |
| `POST /auth/logout`·`DELETE /users/me` (API-007·009) | `queryClient.clear()`, SecureStore 토큰 삭제, 업로드 대기열 비우기 | — |

## 6. 목록·지도·작업·업로드

### AR 팁 계약 반영 (2026-11-06)

사용자가 이 대화에 제공한 API-047~055·090·012·086 명세를 `features/tip/`, `features/media/`의 API 계층과 MSW에 반영했다. 연결된 Notion은 API-086 페이지를 404로 반환했고, wmux 명령을 사용할 수 없어 Swagger의 서버 반영 여부는 미확인이다. 이 단락은 연결·미확정 사항의 기록이며 계약 복사본은 두지 않는다.

- 탐색·현장 상세의 위치는 POST 본문으로만 보낸다. 새 위치 측정마다 `createTipLocationSession`을 호출한다. 쿼리 키는 무작위 세션 ID와 팁 ID만 사용하고 위치 쿼리의 `gcTime`은 0이다. 화면 이탈·위치 재측정 후 이전 세션을 재사용하지 않는다. `canInteract`는 상세·반응 호출 시 새 위치 검증을 생략하는 근거가 아니다.
- 본문은 CRLF·CR을 LF로 정규화하고, 앞뒤 공백은 보존하며 공백 포함 120자를 검사한다. 사진은 업로드 UUID 0~1개이고 미첨부의 `media=SKIPPED`는 정상 상태다. 반응 API에는 토글 명령 대신 `LIKE/DISLIKE/NONE` 최종 상태를 보낸다.
- 업로드는 API-012 → 별도 인증 없는 S3 PUT → API-086 READY 순서다. `useUploadMedia`는 파일별로 사용하며 같은 파일 객체의 재시도는 전송 성공 후 완료 확인만 반복한다. 다른 파일은 새 파일 객체로 전달한다. 만료·파일 검증 실패는 새 세션을 사용한다. MB 바이트 환산이 미정이므로 클라이언트에 새 크기 임계값을 가정하지 않고 서버 검증을 따른다.
- API-050의 `error`는 상세가 미정이므로 불투명 객체로 유지한다. placement는 앱 v1 형식으로 보내고 조회 시 객체를 보존한다. 작업 상태 `null`·`UNKNOWN`은 성공으로 해석하지 않는다. 처리 상태 조회 실패는 게시 실패와 구분하고 자동 확인은 최대 120초 분량으로 제한한다.
- 이 명세의 C004(버전)·C005(멱등키)·C008~010(위치)는 기존 도메인의 같은 코드와 의미가 다르다. `getTipErrorMessage`는 팁 요청 문맥에서 해석하며 다른 도메인의 오류 규칙을 바꾸지 않는다. 새 명세의 A001~003과 기존 공통 인증 갱신 코드(C005)의 대응은 공통 인증 계약 확인이 필요하다.
- AR 작성·주변 탐색·공개 상세·반응과 내 팁 목록·원격 수정/삭제·현장 재배치를 연결했다. 등록·수정 결과를 받으면 사용자 ID별로 미완료 tipId를 기기에 보관하고 재진입 시 API-050을 확인한다. CREATE의 REGISTERING/FAILED 목록도 서버에서 복원하며 공개 중인 UPDATE 작업은 기기 보관 ID로 찾는다. 기기 보관이 실패해도 서버의 접수 성공을 실패로 바꾸지 않는다.
- 같은 날 사용자 후속 확인으로 공급자는 `ARCORE_CLOUD_ANCHOR`, referenceId는 Cloud Anchor ID(최대 255자), expiresAt은 필수 미래 일시로 확정됐다. UTC ISO 시각을 KST `YYYY-MM-DD HH:mm:ss+09`로 바꾸며 소수 초는 버린다. 팁 종류는 PHOTO/FOOD/CAFE/TIP/TMI이고 등록 필수·수정 선택·탐색/공개 상세/본인 상세 응답에 포함한다. 최종 사용자 확인(2026-11-06)으로 API 종류 필드는 기존 `tipType`을 대체한 `category`다. 화면·기존 로컬 AR 모델의 종류 필드는 API 경계에서 변환한다. 탐색 요청의 category 필터는 계속 없으며 가까운 순 최대 10개를 표시한다.
- 앱이 보내는 placement v1 필드는 `schemaVersion: 1`, `anchorType: "CLOUD"`, `authorizationMode: "KEYLESS"` 세 가지다. Cloud Anchor 자체가 공간 원점·방향을 복원하므로 localPose·세션·기기명·operation·복원 오차·GPS를 보내지 않는다. 앵커 ID와 만료는 spatialReference에만 둔다. 네이티브는 서버 응답용 공개 복원 기록을 별도 검증하며 로컬 실험 기록의 hostSessionId 등을 요구하지 않는다. 배치 Hit의 기존 0.2~5m 제한과 기기 GPS 증거를 사용하고 지도 선택 위치로 등록하지 않는다. placement 크기 상한은 서버 결정 대기다.
- 네이티브 사진은 기존 JPEG 재인코딩으로 EXIF 위치를 제거한 후 원본 binary로 PUT한다. S3 전송도 앱의 별도 axios 경로를 사용하므로 개발 MSW와 실서버가 같은 요청 흐름을 사용한다. API-048 사진 URL을 다시 발급해야 하면 새 GPS로 API-048을 다시 호출하고 API-013으로 우회하지 않는다.
- 검증: `pnpm tip:check`(설치된 도구 직접 실행 시 `node scripts/check-tip-api.cjs`). MSW는 메타데이터·상태 전이·거리 정책을 확인하기 위한 목이며 실제 S3 파일 내용·공간 참조의 공급자 검증을 수행하지 않는다. 서버의 미정인 위치 정확도·유효 시각 기준을 목에 임의로 넣지 않는다.

### 6-1. 커서 페이지네이션

계약의 커서 파라미터 이름은 상세 미확인이다. 형태는 다음과 같다.

```ts
export const courseSearchInfiniteOptions = (filters: CourseFilters) =>
  infiniteQueryOptions({
    queryKey: courseKeys.search(filters),               // 필터 전부가 키에 들어간다
    queryFn: ({ pageParam, signal }) => searchCourses({ ...filters, cursor: pageParam ?? undefined }, signal),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,         // null이면 hasNextPage=false
  });
```

C010(커서 무효)이 오면 첫 페이지부터 다시 조회한다.

### 6-2. 화면 영역(지도) 조회

```ts
const COORD_DIGITS = 3; // 캐시 적중용 반올림 자릿수(약 100m)
const round = (v: number) => Number(v.toFixed(COORD_DIGITS));

export const visitStatisticsOptions = (area: MapArea, category: CategoryId | null) => {
  const key = { sw: [round(area.sw.lat), round(area.sw.lng)], ne: [round(area.ne.lat), round(area.ne.lng)], zoom: Math.round(area.zoom), category };
  return queryOptions({
    queryKey: statisticsKeys.area(key),
    queryFn: ({ signal }) => getVisitStatistics(key, signal),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  });
};
```

화면은 지도 이동이 끝난 뒤(디바운스) `area`를 바꾼다. 개별 좌표로 분포를 다시 만들지 않는다.

### 6-3. 비동기 작업 (AI 초안·팁 등록)

```ts
export const JOB_POLL_MS = 2_000;
export const JOB_MAX_WAIT_MS = 120_000;

export const jobStatusOptions = (jobId: number) =>
  queryOptions({
    queryKey: jobKeys.detail(jobId),
    queryFn: ({ signal }) => getJob(jobId, signal),
    refetchInterval: (q) => (isFinished(q.state.data?.status) ? false : JOB_POLL_MS),
  });
```

화면 흐름은 다음과 같다.
1. 작업 생성 mutation → 작업 id를 받는다.
2. `useQuery(jobStatusOptions(id))`로 진행 상태를 표시한다. 단계가 있으면 단계를 보여준다. 예: 사진 읽음 → 영수증 읽음 → 둘 맞추기.
3. 완료되면 결과 쿼리를 무효화하고 초안 화면으로 이동한다.
4. 실패하면 이유와 재시도를 보여준다.

`JOB_MAX_WAIT_MS`를 넘기면 "아직 처리 중"으로 안내한다.

### 6-4. 파일 업로드

```ts
export function useUploadMedia() {
  return useMutation({
    mutationFn: ({ file, onProgress }: UploadInput) => uploadMedia(file, onProgress), // multipart, TIMEOUT_UPLOAD_MS
    retry: false,
  });
}
```

`features/media/store.ts`(Zustand)에는 업로드 대기열만 둔다(파일 uri, 진행률, 상태, 받은 `mediaId`). 연결 요청(API-062·068)은 `mediaId`가 모두 나온 뒤 보낸다. 대기열은 서버 데이터의 복제가 아니라 업로드 진행 중인 로컬 상태다.

## 7. 에러 처리

- 모든 통신 실패는 `ApiError(status, code, message)`로 정규화된다.
  - `status`: HTTP 상태. 네트워크 단절·타임아웃은 `0`.
  - `code`: Notion·Swagger 명세의 오류 코드.
  - `message`: **개발자용**. 사용자에게 보여주지 않는다(이 프로젝트 계약).
- 분기는 `isApiError(error)` 타입 가드를 거친 뒤 `error.code`로 한다.
  - 공통 code는 `api/errors.ts`, 도메인 code는 `features/<domain>/errors.ts`에서 처리한다.
  - `status`로만 분기하지 않는다. 같은 409라도 C008과 C009는 뜻이 다르다.
- 쿼리: 컴포넌트는 `isPending`/`isError`/`error`만 보고, 재시도할 수 있는 폴백 UI를 렌더한다.
- 뮤테이션 + 폼: 로컬 오류 상태에 문구를 넣어 인라인으로 보여주고, 사용자 입력값은 유지한다.
- 재시도 정책은 `lib/query-client.ts`에 전역으로 둔다: 쿼리 1회, 뮤테이션 0회. 4xx는 재시도하지 않도록 `retry` 함수에서 `ApiError.status`를 본다.
- 401은 인터셉터가 처리하므로 화면에서 다루지 않는다. 403은 재시도하지 않는다.
- `console.log`로 오류를 흘리지 않는다. 디버깅은 React Query Devtools를 쓴다.

## 8. 낙관적 업데이트

- **허용**: 팁 반응(API-052), 코스 즐겨찾기(API-017·018)처럼 실패해도 되돌릴 수 있는 토글.
- **금지**: 발자취 저장, 팁 등록, AI 결과 적용, 코스 공개처럼 서버가 판정하거나 계산하는 요청. 응답을 받은 뒤 반영한다.

## 9. 목(MSW)

- 현재 설치와 실행 방법은 `docs/msw.md`를 따른다. MSW는 React Native 호환성을 위해 `2.3.5`로 고정했고, `index.ts`에서 네이티브·웹 초기화를 마친 뒤 라우터를 시작한다.
- 버전: **`msw@2`** 고정. `msw@3`은 `msw/native`를 없앴고, 대체 패키지 `@msw/react-native`는 npm에 아직 없다(2026-10-27 확인).
- 구조:
  - `api/mocks/handlers/<domain>.ts`: `http.get("*/api/v1/rooms", …)` 형태의 핸들러
  - `api/mocks/fixtures/<domain>.ts`: 계약 예시 응답
  - `api/mocks/handlers/index.ts`: 전 도메인 핸들러를 모은 `handlers` 배열을 내보낸다
  - `api/mocks/polyfills.ts`, `api/mocks/native.ts`: 앱용
  - `api/mocks/server.ts`: jest용
- 앱(개발 빌드) — 목표 형태 `[확인]`(처음 구현할 때 기기에서 동작을 확인한다):
  ```ts
  // api/mocks/polyfills.ts — Hermes에 이미 있는 API면 해당 줄은 지운다
  import "fast-text-encoding";
  import "react-native-url-polyfill/auto";

  // api/mocks/native.ts
  import { setupServer } from "msw/native";
  import { handlers } from "./handlers";
  export const server = setupServer(...handlers);

  // index.ts (package.json "main": "index.ts")
  async function enableMocking() {
    if (!__DEV__) return;
    await import("./api/mocks/polyfills");
    const { server } = await import("./api/mocks/native");
    server.listen({
      onUnhandledRequest(request, print) {
        const { pathname } = new URL(request.url);
        if (pathname === "/api/v1" || pathname.startsWith("/api/v1/")) {
          print.error(); // 미처리 서비스 API는 실제 서버로 보내지 않는다
        }
        // 외부 SDK·정적 리소스 등 서비스 API 밖의 요청은 통과
      },
    });
  }
  void enableMocking().then(() => import("expo-router/entry"));
  ```
  - 개발에서는 항상 전체 핸들러를 사용한다. MSW 준비가 실패하면 라우터도 시작하지 않는다.
  - 배포에서는 `__DEV__` 가드로 목 모듈·폴리필을 불러오거나 서버를 시작하지 않는다. 연결 대상은 배포 환경에 설정한 실제 백엔드다.
  - 진입점을 `index.ts`로 바꾸는 것은 MSW를 처음 설치할 때 한다. 그 전까지 `package.json`의 `main`은 `expo-router/entry`다.
- jest:
  - `jest.setup`에서 `msw/node`의 `setupServer(...handlers)`를 쓴다.
  - 순서: `beforeAll(listen)` → `afterEach(resetHandlers)` → `afterAll(close)`.
  - 오류 시나리오는 테스트 안에서 `server.use(http.post(..., () => HttpResponse.json({ success: false, code: "C014", message: "" }, { status: 422 })))`처럼 덮어쓴다.
- E2E(Maestro): `__DEV__ === true`인 개발 빌드에서 전체 API를 MSW로 처리해 서버 없이 같은 결과를 낸다(규칙 60). 배포 빌드는 실제 백엔드에 연결하므로 서버 없는 E2E에 쓰지 않는다.
- 목 응답은 **봉투 포함 계약 형태 그대로**다. 필드를 더하거나 빼지 않는다. 계약이 바뀌면 `fixtures`·핸들러·`model.ts`를 같이 고친다.
- 실제 개인 정보·좌표·영수증을 넣지 않는다. 공개 장소 좌표와 더미 값만 쓴다.
- `.env`는 추적하지 않는다(`.gitignore`). 공유할 형식·설명은 `.env.example`에만 둔다.

## 10. 금지 사항

- 컴포넌트·라우트에서 `api/` 인스턴스나 도메인 함수를 직접 호출하지 않는다. 항상 `features/<domain>/api/queries.ts`의 훅을 거친다.
- `api/client.ts`의 인스턴스를 인터셉터 없이 새로 만들지 않는다(인증·오류 정규화 우회 방지).
- 쿼리 키 문자열을 하드코딩하지 않는다. `<domain>Keys` 팩토리만 쓴다.
- `useEffect` + `useState`로 서버 데이터를 보관하지 않는다. 서버 상태는 TanStack Query가 소유한다.
- 집계·합계·이동 시간 같은 서버 파생값을 클라이언트에서 계산해 표시하지 않는다.
- 도메인 함수에 목 분기를 두지 않는다(MSW 사용).
- Notion·Swagger 명세에 없는 엔드포인트·필드·열거형 값을 가정해 코드에 넣지 않는다. 필요하면 백엔드에 요청하고 보고서에 `TBD`로 남긴다.
- 스펙과 실제 응답이 다르면 `model.ts`에서 우회하지 않는다. `ContractMismatchError`로 드러내고 보고한다.

## 11. 미확정 (TBD)

- 엔드포인트별 요청·응답 상세, 멱등성 헤더 이름, 버전 값 전달 방식, 위치 증거 필드, 커서 파라미터(Notion·Swagger에서 확인)
- API-033과 API-080의 중복, API-015와 API-083의 관계, API-084·085 URL(Notion·Swagger에서 확인)
- 작업 상태 열거형과 폴링 간격·최대 대기 시간 기준(서버와 합의)
