const assert = require("node:assert/strict");
const Module = require("node:module");
const { http, HttpResponse, delay } = require("msw");
const { setupServer } = require("msw/node");
const axios = require("axios");

const stored = new Map();
const load = Module._load;
Module._load = function (id, ...args) {
  if (id === "expo-secure-store") return {
    getItemAsync: async (key) => stored.get(key) ?? null,
    setItemAsync: async (key, value) => { stored.set(key, value); },
    deleteItemAsync: async (key) => { stored.delete(key); },
  };
  return load.call(this, id, ...args);
};

process.env.EXPO_PUBLIC_API_URL = "https://api.yeogirok.test/";
const { api, setUnauthorizedHandler, TIMEOUT_MUTATION_MS, TIMEOUT_UPLOAD_MS } = require("../.expo/api-check/api/client");
api.defaults.adapter = "fetch";
const { ApiError, isApiError } = require("../.expo/api-check/api/errors");
const { clearTokens, getTokens, setTokens } = require("../.expo/api-check/features/auth/token-store");
Module._load = load;

const origin = "https://api.yeogirok.test/api/v1";
const server = setupServer();
const oldTokens = { accessToken: "old-access", refreshToken: "old-refresh" };
const newTokens = {
  accessToken: "new-access", accessTokenExpiresIn: 3600,
  refreshToken: "new-refresh", refreshTokenExpiresIn: 7200,
};
const success = (data) => HttpResponse.json({ success: true, data });
const failure = (code, status = 401) => HttpResponse.json({ success: false, code, message: "developer only" }, { status });
const hasCode = (code, status) => (error) => isApiError(error) && error.code === code && error.status === status;

async function check() {
  let redirects = 0;
  setUnauthorizedHandler(() => { redirects += 1; });
  server.listen({ onUnhandledRequest: "error" });
  try {
    assert.equal(api.defaults.timeout, 10_000);
    assert.equal(TIMEOUT_MUTATION_MS, 30_000);
    assert.equal(TIMEOUT_UPLOAD_MS, 60_000);
    await setTokens(oldTokens);
    server.use(http.get(`${origin}/__client_check__`, ({ request }) => {
      assert.equal(request.headers.get("Authorization"), "Bearer old-access");
      assert.equal(request.headers.get("Accept"), "application/json");
      return success({ items: [1, 2] });
    }));
    assert.deepEqual((await api.get("/__client_check__")).data, { items: [1, 2] });
    server.use(http.delete(`${origin}/__client_check__`, () => success(null)));
    assert.equal((await api.delete("/__client_check__")).data, undefined);
    server.use(http.post(`${origin}/__client_check__`, async ({ request }) => {
      assert.ok(request.headers.get("Content-Type").startsWith("multipart/form-data; boundary="));
      assert.equal((await request.formData()).get("description"), "test upload");
      return success(null);
    }));
    const form = new FormData();
    form.append("description", "test upload");
    await api.post("/__client_check__", form, { timeout: TIMEOUT_UPLOAD_MS });

    for (const path of ["/auth/token", "/auth/token/refresh", "/auth/google/login", "/auth/kakao/callback"]) {
      const method = path.includes("/token") ? "post" : "get";
      server.use(http[method](`${origin}${path}`, ({ request }) => {
        assert.equal(request.headers.get("Authorization"), null);
        return success({});
      }));
      await api[method](path);
    }

    for (const [code, status] of [["C007", 403], ["C008", 409], ["C014", 422], ["C002", 400]]) {
      server.use(http.get(`${origin}/__client_check__`, () => failure(code, status)));
      await assert.rejects(api.get("/__client_check__"), hasCode(code, status));
    }
    for (const body of [{ items: [] }, { success: true }, { success: false, code: 12 }]) {
      server.use(http.get(`${origin}/__client_check__`, () => HttpResponse.json(body)));
      await assert.rejects(api.get("/__client_check__"), hasCode("CONTRACT_MISMATCH", 200));
    }
    server.use(http.get(`${origin}/__client_check__`, () => HttpResponse.text("gateway error", { status: 502 })));
    await assert.rejects(api.get("/__client_check__"), hasCode("CONTRACT_MISMATCH", 502));
    server.use(http.get(`${origin}/__client_check__`, () => HttpResponse.error()));
    await assert.rejects(api.get("/__client_check__"), hasCode("NETWORK", 0));
    server.use(http.get(`${origin}/__client_check__`, async () => { await delay(80); return success({}); }));
    await assert.rejects(api.get("/__client_check__", { timeout: 10, adapter: "fetch" }), hasCode("TIMEOUT", 0));
    const controller = new AbortController();
    const pending = api.get("/__client_check__", { signal: controller.signal });
    controller.abort();
    await assert.rejects(pending, axios.isCancel);

    let refreshes = 0;
    let retries = 0;
    server.use(
      http.post(`${origin}/auth/token/refresh`, async ({ request }) => {
        refreshes += 1;
        assert.equal(request.headers.get("Authorization"), null);
        assert.deepEqual(await request.json(), { refreshToken: "old-refresh" });
        await delay(20);
        return success(newTokens);
      }),
      http.get(`${origin}/__client_check__`, async ({ request }) => {
        if (request.headers.get("Authorization") === "Bearer new-access") {
          retries += 1;
          return success({ authenticated: true });
        }
        if (new URL(request.url).searchParams.has("late")) await delay(100);
        return failure("C005");
      }),
    );
    const results = await Promise.all([
      api.get("/__client_check__"), api.get("/__client_check__"), api.get("/__client_check__?late=1"),
    ]);
    assert.ok(results.every(({ data }) => data.authenticated));
    assert.equal(refreshes, 1, "동시·늦게 도착한 401도 토큰 회전을 중복 실행하면 안 됩니다.");
    assert.equal(retries, 3);
    assert.deepEqual(await getTokens(), { accessToken: "new-access", refreshToken: "new-refresh" });
    assert.equal(redirects, 0);

    await setTokens(oldTokens);
    server.use(http.get(`${origin}/__client_check__`, () => failure("C005")));
    await assert.rejects(api.get("/__client_check__"), hasCode("C005", 401));
    assert.equal(refreshes, 2);
    assert.equal(await getTokens(), null);
    assert.equal(redirects, 1, "재요청도 401이면 종료하고 로그인 처리를 해야 합니다.");

    for (const code of ["A007", "A008", "A009", "A010"]) {
      await setTokens(oldTokens);
      server.use(http.post(`${origin}/auth/token/refresh`, async () => { await delay(10); return failure(code); }));
      const failed = await Promise.allSettled([api.get("/__client_check__"), api.get("/__client_check__")]);
      assert.ok(failed.every((result) => result.status === "rejected" && hasCode(code, 401)(result.reason)));
      assert.equal(await getTokens(), null);
    }
    assert.equal(redirects, 5, "갱신 실패 시 대기 요청 수와 무관하게 로그인 처리를 한 번 실행해야 합니다.");

    await setTokens(oldTokens);
    server.use(http.post(`${origin}/auth/token/refresh`, () => success({ ...oldTokens, expiresIn: 3600, tokenType: "Bearer" })));
    await assert.rejects(api.get("/__client_check__"), hasCode("CONTRACT_MISMATCH", 200));
    assert.equal(await getTokens(), null);

    for (const code of ["C004", "C006"]) {
      await setTokens(oldTokens);
      server.use(http.get(`${origin}/__client_check__`, () => failure(code)));
      await assert.rejects(api.get("/__client_check__"), hasCode(code, 401));
      assert.equal(await getTokens(), null);
    }
    await clearTokens();
    server.use(http.get(`${origin}/__client_check__`, ({ request }) => {
      assert.equal(request.headers.get("Authorization"), null);
      return success({ public: true });
    }));
    assert.deepEqual((await api.get("/__client_check__")).data, { public: true });
    assert.ok(new ApiError(0, "NETWORK", "test") instanceof Error);
    console.info("API client: 봉투·인증 제외·오류·타임아웃·취소·동시/지연 401·재시도 제한·회전·실패 정리 확인");
  } finally {
    server.close();
  }
}

check().catch((error) => { console.error(error); process.exitCode = 1; });
