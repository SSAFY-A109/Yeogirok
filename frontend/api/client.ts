import { create, isAxiosError, isCancel, type InternalAxiosRequestConfig } from "axios";

import { clearTokens, getTokens, setTokens } from "../features/auth/token-store";
import { isTokenPair } from "../features/auth/tokens";
import { unwrapEnvelope } from "./envelope";
import { ApiError, toApiError } from "./errors";

/** 환경 변수에 설정한 API 호스트. `/api/v1`은 클라이언트가 붙인다. */
export const API_BASE_URL = process.env.EXPO_PUBLIC_API_URL?.replace(/\/+$/, "");

if (!API_BASE_URL) {
  throw new Error("EXPO_PUBLIC_API_URL을 설정해야 합니다.");
}

const apiHost = new URL(API_BASE_URL);

if (
  apiHost.protocol !== "https:" || apiHost.pathname !== "/" ||
  apiHost.search || apiHost.hash || apiHost.username || apiHost.password
) {
  throw new Error("EXPO_PUBLIC_API_URL에는 경로 없는 HTTPS 호스트만 설정해야 합니다.");
}

/** 조회 요청의 기본 제한 시간(밀리초). */
export const TIMEOUT_QUERY_MS = 10_000;
/** 생성·변경·삭제 요청에서 `config.timeout`으로 지정할 제한 시간(밀리초). */
export const TIMEOUT_MUTATION_MS = 30_000;
/** 파일 업로드 요청에서 `config.timeout`으로 지정할 제한 시간(밀리초). */
export const TIMEOUT_UPLOAD_MS = 60_000;

/**
 * 도메인 API 함수에서 사용하는 공통 axios 클라이언트.
 *
 * `get<T>(path, config)`, `delete<T>(path, config)`와
 * `post<T>(path, body, config)`, `put<T>(path, body, config)`,
 * `patch<T>(path, body, config)`를 axios와 같은 방식으로 호출한다.
 * `path`는 `/api/v1`을 제외한 명세의 경로이며, `T`는 봉투 안 `data`의 타입이다.
 *
 * 응답 객체는 유지하고 `response.data`에 봉투를 벗긴 값을 담는다.
 * 빈 성공 응답(`data: null`)은 `undefined`이므로 응답 타입을 `void`로 지정한다.
 * 인증 헤더·토큰 갱신은 인터셉터가 처리하며, 요청 취소는 `config.signal`로 전달한다.
 * 실패는 `ApiError`로 전달하되 axios 취소 오류는 그대로 유지한다.
 *
 * @example 조회 — Query 함수가 받은 signal을 그대로 전달한다.
 * ```ts
 * const { data } = await api.get<ResponseDto>(path, { signal });
 * return data;
 * ```
 *
 * @example 생성·변경 — 본문은 두 번째 인자, 설정은 세 번째 인자다.
 * ```ts
 * const { data } = await api.post<ResponseDto>(path, body, {
 *   timeout: TIMEOUT_MUTATION_MS,
 * });
 * return data;
 * ```
 *
 * @example 빈 성공 응답을 받는 삭제
 * ```ts
 * await api.delete<void>(path, { timeout: TIMEOUT_MUTATION_MS });
 * ```
 *
 * @example 파일 업로드 — FormData의 Content-Type boundary는 어댑터가 설정한다.
 * ```ts
 * const { data } = await api.post<ResponseDto>(path, formData, {
 *   timeout: TIMEOUT_UPLOAD_MS,
 * });
 * return data;
 * ```
 */
export const api = create({
  baseURL: `${API_BASE_URL}/api/v1`,
  timeout: TIMEOUT_QUERY_MS,
  headers: { Accept: "application/json", "Content-Type": "application/json" },
});

/** 재요청에도 표식을 유지해, 같은 요청이 401로 무한 재시도되는 것을 막는다. */
type AuthRequestConfig = InternalAxiosRequestConfig & { authRetried?: boolean };
type UnauthorizedHandler = () => void | Promise<void>;

let onUnauthorized: UnauthorizedHandler | undefined;
// 동시 요청은 진행 중인 갱신·세션 정리 Promise를 공유한다.
let refreshPromise: Promise<string> | undefined;
let unauthorizedPromise: Promise<void> | undefined;

/**
 * 인증을 복구할 수 없을 때 실행할 앱의 세션 종료 처리를 등록한다.
 *
 * 토큰을 삭제한 뒤 호출하며, 로그인 이동·Query 캐시 초기화는 전달한 함수가 담당한다.
 * 앱의 인증 진입점에서 등록하고, 다시 등록하면 이전 함수를 교체한다.
 *
 * @param handler 로그인 이동과 사용자별 상태 정리를 수행하는 동기 또는 비동기 함수.
 * @returns 반환값 없음.
 * @example
 * ```ts
 * setUnauthorizedHandler(onSessionExpired);
 * ```
 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler): void {
  onUnauthorized = handler;
}

/** 서비스 토큰을 보내지 않고, 401 자동 갱신도 적용하지 않을 인증 요청인지 판별한다. */
function isPublicAuthRequest(config: InternalAxiosRequestConfig): boolean {
  const path = config.url?.split("?")[0].replace(/\/$/, "");
  return (
    (config.method === "post" && (path === "/auth/token" || path === "/auth/token/refresh")) ||
    (config.method === "get" && /^\/auth\/[^/]+\/(login|callback)$/.test(path ?? ""))
  );
}

/** 동시에 발생한 인증 실패에 대해 토큰 삭제와 앱의 세션 종료 처리를 한 번 수행한다. */
async function invalidateSession(): Promise<void> {
  if (!unauthorizedPromise) {
    unauthorizedPromise = (async () => {
      try {
        await clearTokens();
      } finally {
        // 저장소 삭제가 실패해도 앱의 인증 실패 처리는 실행한다.
        await onUnauthorized?.();
      }
    })().finally(() => { unauthorizedPromise = undefined; });
  }
  await unauthorizedPromise;
}

/**
 * 저장된 리프레시 토큰으로 토큰 쌍을 회전하고, 새 액세스 토큰을 반환한다.
 *
 * 진행 중인 갱신이 있으면 같은 Promise를 반환한다. 실패하면 세션을 정리한다.
 * @returns 새 토큰 쌍을 저장한 뒤의 액세스 토큰.
 * @throws {ApiError} 갱신 요청 실패, 토큰 부재 또는 응답 계약 불일치.
 */
async function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const tokens = await getTokens();

      if (!tokens) {
        throw new ApiError(401, "C004", "갱신할 인증 토큰이 없습니다.");
      }

      const { data } = await api.post<unknown>("/auth/token/refresh", {
        refreshToken: tokens.refreshToken,
      }, { timeout: TIMEOUT_MUTATION_MS });
      // 현재 B안의 두 만료 필드까지 확인해 폐기된 응답 계약을 받아들이지 않는다.
      if (
        !isTokenPair(data) ||
        !("accessTokenExpiresIn" in data) || typeof data.accessTokenExpiresIn !== "number" ||
        !Number.isFinite(data.accessTokenExpiresIn) ||
        !("refreshTokenExpiresIn" in data) || typeof data.refreshTokenExpiresIn !== "number" ||
        !Number.isFinite(data.refreshTokenExpiresIn)
      ) {
        throw new ApiError(200, "CONTRACT_MISMATCH", "토큰 갱신 응답이 현재 계약과 다릅니다.");
      }
      // 회전 전 리프레시 토큰은 즉시 무효이므로 새 토큰 쌍을 함께 저장한다.
      await setTokens(data);
      return data.accessToken;
    })().catch(async (error: unknown) => {
      await invalidateSession();
      throw toApiError(error);
    }).finally(() => { refreshPromise = undefined; });
  }
  return refreshPromise;
}

// 요청 전: 본문에 맞는 헤더를 정하고, 인증이 필요한 요청에 최신 토큰을 붙인다.
api.interceptors.request.use(async (config) => {
  if (typeof FormData !== "undefined" && config.data instanceof FormData) {
    // false는 axios가 기본 Content-Type을 다시 붙이는 것도 막는다.
    config.headers.setContentType(false);
  }

  if (isPublicAuthRequest(config)) {
    config.headers.delete("Authorization");
    return config;
  }

  const tokens = await getTokens();

  if (tokens) {
    config.headers.set("Authorization", `Bearer ${tokens.accessToken}`);
  } else {
    config.headers.delete("Authorization");
  }

  return config;
});

// 응답 후: 성공 봉투를 해제하고, 실패는 인증 복구 또는 공통 오류 처리로 보낸다.
api.interceptors.response.use(
  (response) => {
    response.data = unwrapEnvelope(response.data, response.status);
    return response;
  },
  async (error: unknown) => {
    // Query 등의 요청 취소가 일반 API 실패로 취급되지 않게 보존한다.
    if (isCancel(error)) {
      throw error;
    }

    const apiError = toApiError(error);
    const config: AuthRequestConfig | undefined = isAxiosError(error) ? error.config : undefined;

    if (apiError.status !== 401 || !config || isPublicAuthRequest(config)) {
      throw apiError;
    }
    // 액세스 토큰 만료(C005)만 갱신한다. 재요청도 401이면 세션을 종료한다.
    if (config.authRetried || apiError.code !== "C005") {
      await invalidateSession();
      throw apiError;
    }

    config.authRetried = true;

    try {
      const tokens = await getTokens();
      const currentAuthorization = tokens ? `Bearer ${tokens.accessToken}` : undefined;
      // 다른 요청이 이미 갱신했다면, 늦게 도착한 이전 토큰의 401은 새 토큰으로 재요청한다.
      if (!currentAuthorization || config.headers.get("Authorization") === currentAuthorization) {
        await refreshAccessToken();
      }
      return api.request(config);
    } catch (refreshError) {

      if (isCancel(refreshError)) {
        throw refreshError;
      }

      throw toApiError(refreshError);
    }
  },
);
