# Redis 연결과 공통 멱등키 처리

> `Idempotency-Key` 헤더를 받는 API는 모두 `global.idempotency.IdempotencyService`를 쓴다. API마다 따로 만들지 않는다.
> 발자취(041)·AR 팁(049·054)·파일(012·086)이 먼저 쓰고, 로그인·AI 작업·영수증·채팅도 같은 연결 설정과 처리를 재사용한다.

## 1. Redis 연결

Spring Boot의 `spring.data.redis.*`를 그대로 쓴다. Redis를 쓰는 코드는 `StringRedisTemplate`을 주입받는다.

| 환경 변수 | local 기본값 | prod |
| --- | --- | --- |
| `REDIS_HOST` | `localhost` | 기본값 없음 (없으면 시작 실패) |
| `REDIS_PORT` | `6379` | 기본값 없음 (없으면 시작 실패) |
| `REDIS_PASSWORD` | 없음 | 기본값 없음 (없으면 시작 실패) |

- 로컬 Redis는 `infra/docker-compose.local.yml`의 `redis`(redis:7.4-alpine, 비밀번호 없음)다.
- 서버 Redis 컨테이너·메모리 정책·영속성·비밀번호는 서버 담당이 맡는다.

## 2. 멱등키 설정값

| 설정 | 환경 변수 | local 기본값 | prod | 뜻 |
| --- | --- | --- | --- | --- |
| `yeogirok.idempotency.retention` | `IDEMPOTENCY_RETENTION` | `24h` | 기본값 없음 | 처리가 끝난 멱등키 보관 기간. 요청 종류와 상관없이 같다(11/3 결정) |
| `yeogirok.idempotency.processing-timeout` | `IDEMPOTENCY_PROCESSING_TIMEOUT` | `30s` | 기본값 없음 | '처리 중' 표시 수명. 서버가 처리 도중 죽어도 이 시간이 지나면 풀린다(제안값) |

값이 없거나 0 이하면 앱이 시작할 때 실패한다.

## 3. 동작

Redis 키는 `request:{userId}:{scope}:{target}:{requestKey}`다. 값에는 응답 전체가 아니라 `{requestHash, resultId}`만 저장한다.

| 상황 | 결과 |
| --- | --- |
| 처음 보낸 요청 | '처리 중' 표시를 SET NX로 걸고 처리한다. 끝나면 값을 `{requestHash, resultId}`로 바꿔 보관 기간 동안 둔다 |
| 같은 키 + 같은 요청, 처리 끝남 | 처리하지 않고 저장된 결과 ID로 **다시 조회한** 결과를 돌려준다 |
| 같은 키 + 다른 요청 내용 | 409 `C005` |
| 빈 `Idempotency-Key` | 400 `C001` |

- 요청 해시는 요청 객체를 JSON으로 바꾼 SHA-256이다. Map은 키 순서로 직렬화하므로 순서만 다른 같은 내용은 같은 요청이다.
- Redis 키가 남아 있다는 것만으로 DB 중복 반영을 막았다고 보지 않는다. DB 유일 제약·version으로도 막는다.

## 4. 쓰는 법

```java
@PostMapping("/api/v1/footprints")
public ResponseEntity<ApiResponseBody<FootprintResponse>> create(
	@RequestHeader("Idempotency-Key") String idempotencyKey,
	@Valid @RequestBody FootprintCreateRequest request) {

	Long userId = …; // 로그인 사용자
	FootprintResponse response = idempotencyService.execute(
		new IdempotencyKey(userId, "footprint-create", "-", idempotencyKey),
		request,
		() -> footprintService.create(userId, request),   // 실제 처리 (한 번만 실행)
		FootprintResponse::footprintId,                    // 다시 조회할 때 쓸 결과 ID
		id -> footprintService.get(userId, Long.parseLong(id)));  // 같은 요청을 다시 보냈을 때

	return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseUtil.createSuccessResponse(response));
}
```

- `scope`는 API마다 하나씩 정한다(예: `footprint-create`, `tip-publish`, `tip-edit`, `upload-create`). `:`는 쓸 수 없다.
- `target`은 요청 대상 자원 ID다. 새로 만드는 요청처럼 대상이 없으면 `"-"`를 쓴다.
- **트랜잭션 밖(컨트롤러)에서 부른다.** 실제 처리의 트랜잭션이 커밋된 뒤에 결과 ID를 저장해야, 다시 보낸 요청이 아직 커밋되지 않은 결과를 찾다가 실패하지 않는다.
- 처리가 끝난 뒤 결과 ID 저장이 실패하면(Redis 장애 등) 결과는 그대로 돌려주고 로그만 남긴다. '처리 중' 표시는 수명이 지나면 풀린다.

## 5. 테스트

`IdempotencyServiceTest`는 Testcontainers로 실제 Redis(redis:7.4-alpine)를 띄운다. 로컬과 GitHub Actions 모두 Docker가 있어야 한다.

## 6. 팀 확인 필요

- '처리 중' 표시 수명 30초는 제안값이다.
