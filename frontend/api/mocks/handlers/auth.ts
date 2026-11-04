import { http, HttpResponse } from "msw";

import { failedRefreshTokensFixture, serviceTokensFixture, socialCredentialsFixture } from "../fixtures/auth";

type Provider = "google" | "kakao";

const activeRefreshTokens = new Map<string, Provider>();
const usedRefreshTokens = new Set<string>();
let tokenSequence = 0;

function issueMockTokens(provider: Provider) {
  tokenSequence += 1;
  const tokens = {
    ...serviceTokensFixture,
    accessToken: `${serviceTokensFixture.accessToken}-${tokenSequence}`,
    refreshToken: `${serviceTokensFixture.refreshToken}-${tokenSequence}`,
  };
  activeRefreshTokens.set(tokens.refreshToken, provider);
  return tokens;
}

function failure(code: string, status: number) {
  return HttpResponse.json({ success: false, code, message: "Mock authentication error" }, { status });
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

    return HttpResponse.json({ success: true, data: { ...issueMockTokens(body.provider), isNewUser: true } });
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
    return HttpResponse.json({ success: true, data: issueMockTokens(provider) });
  }),
];
