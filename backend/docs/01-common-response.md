# 공통 응답 양식

> 원천은 팀 Notion "API 명세"다(대조본: `frontend/docs/api-contract.md` §1·§3-0). 명세가 바뀌면 이 문서와 `ExceptionType`을 함께 고친다.

## 1. 응답 모양

```json
{ "success": true,  "data": { … } }
{ "success": true }
{ "success": false, "code": "C002", "message": "…" }
```

| 항목 | 규칙 |
| --- | --- |
| 빈 성공 | 돌려줄 데이터가 없으면 `data` 필드를 생략한다 |
| `code` | 실패 응답에만 있다. 프론트는 `code`로만 분기한다 |
| `message` | 개발자용 디버깅 설명. 프론트는 표시·분기에 쓰지 않는다 |
| 코드 형식 | 도메인 접두어 + 3자리 숫자. 공통은 `C`, 인증·인가는 `A` (`C001`, `A001`) |
| 폐기 코드 | 재사용하지 않는다. `ExceptionType`에 `@Deprecated`로 남기고 지우지 않는다 |

## 2. 코드 구조

```
global/
├── response/
│   ├── ApiResponseBody<T>       sealed abstract — success
│   ├── SuccessResponseBody<T>   final — data (null 이면 생략)
│   ├── FailedResponseBody       final — code, message
│   └── ApiResponseUtil          정적 팩토리 (createSuccessResponse / createFailureResponse)
└── exception/
    ├── ExceptionType            enum (HttpStatus, code, message)
    ├── BusinessException        RuntimeException + ExceptionType
    └── GlobalExceptionHandler   @RestControllerAdvice → ResponseEntity<ApiResponseBody<Void>>
```

- `ApiResponseBody`는 `SuccessResponseBody`, `FailedResponseBody`만 허용하는 sealed 클래스라 응답 모양이 두 가지 외로 늘어나지 않는다.
- 컨트롤러는 `ResponseEntity<ApiResponseBody<T>>`를 반환한다.

  ```java
  return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(data));
  ```

- 서비스는 규칙 위반을 `throw new BusinessException(ExceptionType.XXX)`로 던진다. `GlobalExceptionHandler`가 `ExceptionType`의 HTTP 상태·코드·메시지로 실패 응답을 만든다.
- 디버깅에 도움이 되면 `new BusinessException(type, message)`로 메시지를 덮어쓸 수 있다. 분기 기준은 여전히 `code`다.

## 3. 공통 오류 코드 (C)

| code | HTTP | 뜻 | 매핑 |
| --- | --- | --- | --- |
| C001 | 400 | 필수 요청값 누락 | `MissingServletRequestParameterException`, `MissingRequestHeaderException`, `@NotNull`·`@NotBlank`·`@NotEmpty` 위반 |
| C002 | 400 | 타입·형식 오류 | `MethodArgumentTypeMismatchException`, `HttpMessageNotReadableException` |
| C003 | 400 | 허용하지 않는 요청값 | 그 외 `@Valid` 제약 위반, 파라미터 제약 위반(`HandlerMethodValidationException`) |
| C004~C013 | | 버전 불일치, 멱등키, 커서·페이지 크기, 위치 증거, 좌표·날짜 범위, 지도 영역 | 해당 기능을 만들 때 추가 |
| C014 | 500 | 예상하지 못한 서버 오류 | 그 외 모든 예외 |
| C015 | 404 | 없는 경로 | `NoResourceFoundException` |
| C016 | 405 | 허용되지 않은 HTTP 메서드 | `HttpRequestMethodNotSupportedException` |
| C017 | 415 | 지원하지 않는 Content-Type | `HttpMediaTypeNotSupportedException` |

- `@Valid` 위반은 `FieldError.getCode()`로 누락(C001)과 값 오류(C003)를 가른다.
- C014는 원인을 스택과 함께 로그에만 남기고, 응답에는 고정 문구만 보낸다(SQL 조각·테이블 이름 노출 방지).

## 4. 인증·인가 오류 코드 (A)

| code | HTTP | 뜻 | 매핑 |
| --- | --- | --- | --- |
| A001 | 401 | 액세스 토큰 누락 | AuthenticationEntryPoint (회원 기능에서 구현) |
| A002 | 401 | 액세스 토큰 만료 | AuthenticationEntryPoint (회원 기능에서 구현) |
| A003 | 401 | 액세스 토큰 무효·위변조 | AuthenticationEntryPoint (회원 기능에서 구현) |
| A004 | 403 | ROLE 부족 | AccessDeniedHandler, `AuthorizationDeniedException` |

- 401은 세 갈래(A001·A002·A003)로 나눈다. 프론트는 A002일 때만 토큰을 갱신하고 다시 시도한다.
- 시큐리티 필터 단계의 실패(401·403)는 `@RestControllerAdvice`까지 오지 않는다. EntryPoint·AccessDeniedHandler에서도 `ApiResponseUtil`로 만든 객체를 스프링의 JSON 매퍼 빈으로 직렬화한다(JSON 문자열 직접 작성 금지).
- 로그인·토큰 재발급 관련 코드(A005 이후)는 회원 기능에서 추가한다.

## 5. 팀 확인 필요

- 코드 체계를 `C` 공통 / `A` 인증·인가로 정리하면서 번호를 처음부터 다시 매겼다. Notion 명세(기존 C004~C007, C008~C017, AU 접두어)를 이 문서에 맞춰 고쳐야 한다.
- 빈 성공에서 `data`를 생략하는 것은 명세 초안(`"data": null` 포함)과 다르다. 명세 수정이 필요하다.
