import { HttpResponse } from "msw";

/** 목 응답 지연(밀리초). 로딩 상태를 눈으로 확인할 수 있을 만큼만 둔다. */
export const MOCK_DELAY_MS = 300;

/** 계약의 성공 봉투. 빈 성공은 `data: null`. */
export function ok<T>(data: T, status = 200) {
  return HttpResponse.json({ success: true, data }, { status });
}

/** 계약의 오류 봉투. `message`는 개발자용 설명이다. */
export function fail(status: number, code: string, message: string) {
  return HttpResponse.json({ success: false, code, message }, { status });
}

/** 목 서버는 테스트 계정 한 명으로 응답한다. 개인 조회에는 서비스 Bearer 헤더가 필요하다. */
export function requireMockBearer(request: Request) {
  const authorization = request.headers.get("Authorization");

  if (authorization === null) {
    return fail(401, "A001", "서비스 액세스 토큰이 없습니다.");
  }

  if (!/^Bearer \S+$/i.test(authorization)) {
    return fail(401, "A003", "서비스 액세스 토큰이 유효하지 않습니다.");
  }
}

/** 없는 여행방(서버 `ROOM_NOT_FOUND`). */
export function roomNotFound() {
  return fail(404, "RO001", "여행방을 찾을 수 없습니다.");
}

/** 경로의 숫자 id를 읽는다. 숫자가 아니면 `null`. */
export function readId(value: string | readonly string[] | undefined): number | null {
  const id = Number(value);
  return Number.isSafeInteger(id) ? id : null;
}
