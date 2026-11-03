import { http, HttpResponse } from "msw";

import { failedRefreshTokensFixture, serviceTokensFixture, socialCredentialsFixture } from "../fixtures/auth";

const activeRefreshTokens = new Set<string>();
const usedRefreshTokens = new Set<string>();
let tokenSequence = 0;

function issueMockTokens() {
  tokenSequence += 1;
  const tokens = {
    ...serviceTokensFixture,
    accessToken: `${serviceTokensFixture.accessToken}-${tokenSequence}`,
    refreshToken: `${serviceTokensFixture.refreshToken}-${tokenSequence}`,
  };
  activeRefreshTokens.add(tokens.refreshToken);
  return tokens;
}

function failure(code: string, status: number) {
  return HttpResponse.json({ success: false, code, message: "Mock authentication error" }, { status });
}

export const authHandlers = [
  http.post("*/api/v1/auth/token", async ({ request }) => {
    const body: unknown = await request.json().catch(() => {
      return null;
    });

    if (
      typeof body !== "object" || body === null ||
      !("provider" in body) || !("idToken" in body) ||
      typeof body.idToken !== "string" || !body.idToken.trim()
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

    if ("nonce" in body && body.nonce !== credentials.nonce) {
      return failure("A006", 401);
    }

    return HttpResponse.json({ success: true, data: { ...issueMockTokens(), isNewUser: true } });
  }),
  http.post("*/api/v1/auth/token/refresh", async ({ request }) => {
    const body: unknown = await request.json().catch(() => {
      return null;
    });

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

    if (!activeRefreshTokens.has(body.refreshToken)) {
      return failure("A007", 401);
    }

    activeRefreshTokens.delete(body.refreshToken);
    usedRefreshTokens.add(body.refreshToken);
    return HttpResponse.json({ success: true, data: issueMockTokens() });
  }),
];
