import { http, HttpResponse } from "msw";

import { failedRefreshTokensFixture, googleServerAuthCodeFixture, meFixture, serviceTokensFixture, socialCredentialsFixture } from "../fixtures/auth";

/** 탈퇴(API-009)는 로그인 후 이 시간 안에 발급된 액세스 토큰만 받는다. */
const REAUTH_WINDOW_MS = 5 * 60 * 1000;
const MOCK_USER_ID = 1;

type Provider = "google" | "kakao";
type IssuedAccessToken = { provider: Provider; issuedAt: number; fromLogin: boolean };

const activeRefreshTokens = new Map<string, Provider>();
const usedRefreshTokens = new Set<string>();
const issuedAccessTokens = new Map<string, IssuedAccessToken>();
let tokenSequence = 0;

function issueMockTokens(provider: Provider, fromLogin: boolean) {
  tokenSequence += 1;
  const tokens = {
    ...serviceTokensFixture,
    accessToken: `${serviceTokensFixture.accessToken}-${tokenSequence}`,
    refreshToken: `${serviceTokensFixture.refreshToken}-${tokenSequence}`,
  };
  activeRefreshTokens.set(tokens.refreshToken, provider);
  issuedAccessTokens.set(tokens.accessToken, { provider, issuedAt: Date.now(), fromLogin });
  return tokens;
}

function failure(code: string, status: number) {
  return HttpResponse.json({ success: false, code, message: "Mock authentication error" }, { status });
}

function readBearer(request: Request): string | null {
  const header = request.headers.get("Authorization");
  return header?.startsWith("Bearer ") ? header.slice("Bearer ".length) : null;
}

async function readJson(request: Request): Promise<unknown> {
  return request.json().catch(() => {
    return null;
  });
}

export const authHandlers = [
  http.post("*/api/v1/auth/token", async ({ request }) => {
    const body = await readJson(request);

    if (
      typeof body !== "object" || body === null ||
      !("provider" in body) || !("idToken" in body) || !("nonce" in body) ||
      typeof body.idToken !== "string" || !body.idToken.trim() ||
      typeof body.nonce !== "string" || !body.nonce.trim()
    ) {
      return failure("C001", 400);
    }

    if (body.provider !== "google" && body.provider !== "kakao") {
      return failure("C003", 400);
    }

    const credentials = socialCredentialsFixture[body.provider];

    if (body.idToken !== credentials.idToken) {
      return failure("A005", 401);
    }

    if (body.nonce !== credentials.nonce) {
      return failure("A006", 401);
    }

    return HttpResponse.json({ success: true, data: { ...issueMockTokens(body.provider, true), isNewUser: true } });
  }),
  http.post("*/api/v1/auth/token/refresh", async ({ request }) => {
    const body = await readJson(request);

    if (
      typeof body !== "object" || body === null ||
      !("refreshToken" in body) || typeof body.refreshToken !== "string" || !body.refreshToken.trim()
    ) {
      return failure("C001", 400);
    }

    if (body.refreshToken === failedRefreshTokensFixture.expired) {
      return failure("A008", 401);
    }

    if (body.refreshToken === failedRefreshTokensFixture.revoked) {
      return failure("A009", 401);
    }

    if (usedRefreshTokens.has(body.refreshToken)) {
      return failure("A010", 401);
    }

    const provider = activeRefreshTokens.get(body.refreshToken);

    if (!provider) {
      return failure("A007", 401);
    }

    activeRefreshTokens.delete(body.refreshToken);
    usedRefreshTokens.add(body.refreshToken);
    return HttpResponse.json({ success: true, data: issueMockTokens(provider, false) });
  }),
  http.post("*/api/v1/auth/logout", async ({ request }) => {
    const body = await readJson(request);

    if (
      typeof body !== "object" || body === null ||
      !("refreshToken" in body) || typeof body.refreshToken !== "string" || !body.refreshToken.trim()
    ) {
      return failure("C001", 400);
    }

    activeRefreshTokens.delete(body.refreshToken);
    return HttpResponse.json({ success: true, data: null });
  }),
  http.get("*/api/v1/users/me", ({ request }) => {
    const accessToken = readBearer(request);
    const provider = (accessToken && issuedAccessTokens.get(accessToken)?.provider) ?? "google";

    return HttpResponse.json({ success: true, data: { id: MOCK_USER_ID, ...meFixture, loginProviders: [provider] } });
  }),
  http.delete("*/api/v1/users/me", async ({ request }) => {
    const accessToken = readBearer(request);
    const issued = accessToken ? issuedAccessTokens.get(accessToken) : undefined;

    if (!issued || !issued.fromLogin || Date.now() - issued.issuedAt > REAUTH_WINDOW_MS) {
      // 실제 HTTP 상태는 계약 미확인(TBD). 401이면 공통 클라이언트가 세션을 끝내므로 목은 403으로 둔다.
      return failure("A011", 403);
    }

    if (issued.provider === "google") {
      const body = await readJson(request);

      if (typeof body !== "object" || body === null || !("googleAuthCode" in body) || typeof body.googleAuthCode !== "string") {
        return failure("C001", 400);
      }

      if (body.googleAuthCode !== googleServerAuthCodeFixture) {
        return failure("A029", 400);
      }
    }

    for (const [refreshToken, provider] of activeRefreshTokens) {
      if (provider === issued.provider) {
        activeRefreshTokens.delete(refreshToken);
      }
    }

    return HttpResponse.json({ success: true, data: { withdrawn: true, dataCleanupStatus: "QUEUED" } });
  }),
];
