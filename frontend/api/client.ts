import { create, isAxiosError, isCancel, type InternalAxiosRequestConfig } from "axios";

import { clearTokens, getTokens, setTokens } from "../features/auth/token-store";
import { isTokenPair } from "../features/auth/tokens";
import { unwrapEnvelope } from "./envelope";
import { ApiError, toApiError } from "./errors";

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

export const TIMEOUT_QUERY_MS = 10_000;
export const TIMEOUT_MUTATION_MS = 30_000;
export const TIMEOUT_UPLOAD_MS = 60_000;

export const api = create({
  baseURL: `${API_BASE_URL}/api/v1`,
  timeout: TIMEOUT_QUERY_MS,
  headers: { Accept: "application/json", "Content-Type": "application/json" },
});

type AuthRequestConfig = InternalAxiosRequestConfig & { authRetried?: boolean };
type UnauthorizedHandler = () => void | Promise<void>;

let onUnauthorized: UnauthorizedHandler | undefined;
let refreshPromise: Promise<string> | undefined;
let unauthorizedPromise: Promise<void> | undefined;

export function setUnauthorizedHandler(handler: UnauthorizedHandler): void {
  onUnauthorized = handler;
}

function isPublicAuthRequest(config: InternalAxiosRequestConfig): boolean {
  const path = config.url?.split("?")[0].replace(/\/$/, "");
  return (
    (config.method === "post" && (path === "/auth/token" || path === "/auth/token/refresh")) ||
    (config.method === "get" && /^\/auth\/[^/]+\/(login|callback)$/.test(path ?? ""))
  );
}

async function invalidateSession(): Promise<void> {
  if (!unauthorizedPromise) {
    unauthorizedPromise = (async () => {
      try {
        await clearTokens();
      } finally {
        await onUnauthorized?.();
      }
    })().finally(() => { unauthorizedPromise = undefined; });
  }
  await unauthorizedPromise;
}

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
      if (
        !isTokenPair(data) ||
        !("accessTokenExpiresIn" in data) || typeof data.accessTokenExpiresIn !== "number" ||
        !Number.isFinite(data.accessTokenExpiresIn) ||
        !("refreshTokenExpiresIn" in data) || typeof data.refreshTokenExpiresIn !== "number" ||
        !Number.isFinite(data.refreshTokenExpiresIn)
      ) {
        throw new ApiError(200, "CONTRACT_MISMATCH", "토큰 갱신 응답이 현재 계약과 다릅니다.");
      }
      await setTokens(data);
      return data.accessToken;
    })().catch(async (error: unknown) => {
      await invalidateSession();
      throw toApiError(error);
    }).finally(() => { refreshPromise = undefined; });
  }
  return refreshPromise;
}

api.interceptors.request.use(async (config) => {
  if (typeof FormData !== "undefined" && config.data instanceof FormData) {
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

api.interceptors.response.use(
  (response) => {
    response.data = unwrapEnvelope(response.data, response.status);
    return response;
  },
  async (error: unknown) => {
    if (isCancel(error)) {
      throw error;
    }

    const apiError = toApiError(error);
    const config: AuthRequestConfig | undefined = isAxiosError(error) ? error.config : undefined;

    if (apiError.status !== 401 || !config || isPublicAuthRequest(config)) {
      throw apiError;
    }
    if (config.authRetried || apiError.code !== "C005") {
      await invalidateSession();
      throw apiError;
    }

    config.authRetried = true;

    try {
      const tokens = await getTokens();
      const currentAuthorization = tokens ? `Bearer ${tokens.accessToken}` : undefined;
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
