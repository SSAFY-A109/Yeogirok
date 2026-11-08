const assert = require("node:assert/strict");
const { createServer } = require("node:http");
const { once } = require("node:events");
const { http, HttpResponse } = require("msw");
const { onUnhandledRequest } = require("../.expo/msw-check/api/mocks/on-unhandled-request");
const { resetMockDb } = require("../.expo/msw-check/api/mocks/db");
const { footprintPeriod, guestbookLength } = require("../.expo/msw-check/features/footprint/model");

async function checkCourseHandlers(origin) {
  const paths = ["/courses", "/courses/recommendations", "/courses/501", "/main/popular-places"];
  const headers = { Authorization: "Bearer mock-access-token" };

  async function get(path) {
    const response = await fetch(`${origin}/api/v1${path}`, { headers });
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.success, true);
    return body.data;
  }

  await Promise.all(paths.map(async (path) => {
    const response = await fetch(`${origin}/api/v1${path}`);
    assert.equal(response.status, 401);
    assert.equal((await response.json()).code, "A001");
  }));

  const [search, recommendations, detail, popular] = await Promise.all([
    get("/courses?regionCode=51&duration=DAY_TRIP&transportMode=TRANSIT"),
    get("/courses/recommendations?regionCode=51&limit=1"),
    get("/courses/501"),
    get("/main/popular-places?category=NATURE"),
  ]);

  assert.deepEqual(search.items.map((course) => {
    return course.id;
  }), [502]);

  assert.equal(search.totalCount, 1);
  assert.equal(search.nextCursor, null);
  assert.equal(detail.authors.length, 3);
  assert.equal(detail.days[0].items[0].visitId, 701);
  assert.ok(popular.items.length > 0 && popular.items.length <= 5);

  assert.ok(popular.items.every((item) => {
    return item.place.categoryCode === "NATURE";
  }));

  assert.equal(recommendations.items[0].id, 504);
  assert.ok(recommendations.nextCursor);
  const next = await get(`/courses/recommendations?regionCode=51&limit=1&cursor=${encodeURIComponent(recommendations.nextCursor)}`);
  assert.equal(next.items[0].id, 501);
  assert.deepEqual(next.basis, recommendations.basis);
  console.info("코스 MSW: 네 조회 API의 인증, 검색 필터·개수, 추천 커서, 작성자·방문 ID, 인기 장소 필터 확인");
}

async function checkMyHandlers(origin) {
  async function request(path, { status = 200, method = "GET", body, key, authenticated = true } = {}) {
    const headers = { "Content-Type": "application/json" };

    if (authenticated) {
      headers.Authorization = "Bearer mock-access-token";
    }

    if (key) {
      headers["Idempotency-Key"] = key;
    }

    const response = await fetch(`${origin}/api/v1${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    const result = await response.json();
    assert.equal(response.status, status, `${method} ${path}`);
    assert.equal(result.success, status < 400);
    return result.success ? result.data : result.code;
  }

  assert.deepEqual(footprintPeriod("month", new Date("2026-03-31T13:00:00Z")), { from: "2026-02-28", to: "2026-03-31" });
  assert.equal(guestbookLength("가".repeat(198) + "\r\n😀"), 200);
  assert.equal(await request("/users/me/footprints", { status: 401, authenticated: false }), "A001");
  assert.equal(await request("/users/me/tips", { status: 401, authenticated: false }), "A001");
  assert.equal(await request("/footprints/99", { status: 403 }), "F002");
  assert.equal(await request("/users/me/tips/99", { status: 403 }), "T002");

  const first = await request("/users/me/footprints?category=NATURE&limit=1");
  assert.equal(first.items[0].id, 1);
  assert.ok(first.nextCursor);
  const second = await request(`/users/me/footprints?category=NATURE&limit=1&cursor=${first.nextCursor}`);
  assert.equal(second.items[0].id, 2);
  assert.equal(second.items[0].body, null);
  assert.equal(await request("/users/me/footprints?bbox=invalid", { status: 400 }), "C013");

  const footprint = await request("/footprints/1");
  const patchFootprint = { method: "PATCH", body: { version: footprint.version, body: "가".repeat(198) + "\r\n😀" } };
  const saved = await request("/footprints/1", patchFootprint);
  assert.equal(saved.body, "가".repeat(198) + "\n😀");
  assert.equal(await request("/footprints/1", { ...patchFootprint, status: 409 }), "C004");
  assert.equal(await request("/footprints/1", { method: "PATCH", status: 400, body: { version: saved.version, body: "😀".repeat(201) } }), "C003");
  const cleared = await request("/footprints/1", { method: "PATCH", body: { version: saved.version, body: " \r\n " } });
  assert.equal(cleared.body, null);
  assert.equal((await request(`/footprints/1?version=${cleared.version}`, { method: "DELETE" })).statisticsStatus, "APPLIED");
  assert.equal(await request("/footprints/1", { status: 404 }), "F001");

  const tips = await request("/users/me/tips?status=PUBLISHED&limit=1");
  assert.equal(tips.items[0].category, "PHOTO");
  assert.ok(tips.nextCursor);
  assert.equal("body" in tips.items[0], false);
  const tip = await request("/users/me/tips/1");
  assert.deepEqual(tip.photos, []);
  const patchTip = { status: 202, method: "PATCH", key: "my-tip-check", body: { version: tip.version, category: "RESTAURANT" } };
  const accepted = await request("/tips/1", patchTip);
  assert.equal(accepted.version, tip.version);
  assert.equal((await request("/users/me/tips/1")).category, "PHOTO");
  assert.equal((await request("/tips/1", patchTip)).jobId, accepted.jobId);
  assert.equal(await request("/tips/1", { ...patchTip, status: 409, body: { ...patchTip.body, category: "TMI" } }), "C005");

  await new Promise((resolve) => {
    setTimeout(resolve, 1_600);
  });

  const job = await request("/tips/1/status");
  assert.equal(job.jobId, accepted.jobId);
  assert.equal(job.operationStatus, "SUCCEEDED");
  assert.equal((await request("/users/me/tips/1")).category, "RESTAURANT");
  const deletion = { method: "DELETE" };
  assert.equal(await request(`/tips/1?version=${tip.version}`, { ...deletion, status: 409 }), "C004");
  assert.equal((await request(`/tips/1?version=${job.version}`, deletion)).status, "DELETED");
  assert.equal((await request(`/tips/1?version=${job.version}`, deletion)).status, "DELETED");
  assert.equal(await request("/users/me/tips/1", { status: 404 }), "T012");

  const jpeg = Uint8Array.of(255, 216, 255, 217);
  const upload = await request("/uploads", {
    status: 201, method: "POST", key: "my-photo-session", body: { purpose: "TIP_PHOTO", fileName: "photo.jpg", contentType: "image/jpeg", sizeBytes: jpeg.length },
  });
  assert.equal((await fetch(upload.uploadUrl, { method: upload.uploadMethod, headers: upload.uploadHeaders, body: jpeg })).status, 200);
  const ready = await request(`/uploads/${upload.uploadId}/complete`, { method: "POST", key: "my-photo-ready" });
  assert.equal(ready.status, "READY");
  const photoJob = await request("/tips/2", { status: 202, method: "PATCH", key: "my-photo-link", body: { version: 1, uploadIds: [ready.uploadId] } });
  assert.equal((await request("/tips/2?version=1", deletion)).status, "DELETED");

  await new Promise((resolve) => {
    setTimeout(resolve, 1_600);
  });

  const deleted = await request("/tips/2/status");
  assert.equal(deleted.jobId, photoJob.jobId);
  assert.equal(deleted.status, "DELETED");
  assert.equal(await request("/users/me/tips/2", { status: 404 }), "T012");
  console.info("My MSW: 소유권, 필터·커서, 방명록 정규화·삭제, 팁 비동기 수정·멱등성·사진 업로드·삭제 확인");
}

async function check() {
  let networkRequests = 0;

  const backend = createServer((_request, response) => {
    networkRequests += 1;
    response.end("external resource");
  });

  backend.listen(0, "127.0.0.1");
  await once(backend, "listening");
  const origin = `http://127.0.0.1:${backend.address().port}`;

  try {
    for (const name of ["native", "server"]) {
      const { server } = require(`../.expo/msw-check/api/mocks/${name}`);
      const url = `${origin}/api/v1/__msw_check__`;
      const fixture = { success: true, data: { mocked: true } };
      server.listen({ onUnhandledRequest });

      try {
        server.use(http.get(url, () => {
          return HttpResponse.json(fixture);
        }));

        assert.deepEqual(await (await fetch(url)).json(), fixture);
        assert.equal(networkRequests, 0);

        server.use(http.get(url, () => {
          return HttpResponse.json(
            { success: false, code: "MSW_CHECK", message: "test only" },
            { status: 422 },
          );
        }));

        const errorResponse = await fetch(url);
        assert.equal(errorResponse.status, 422);
        assert.equal((await errorResponse.json()).code, "MSW_CHECK");

        server.resetHandlers();

        for (const path of ["/api/v1", "/api/v1/__msw_check__"]) {
          const printError = console.error;

          try {
            console.error = () => {};

            await assert.rejects(fetch(`${origin}${path}`));
          } finally {
            console.error = printError;
          }
        }

        assert.equal(networkRequests, 0, "미등록 서비스 API는 네트워크로 나가면 안 됩니다.");

        if (name === "server") {
          await checkCourseHandlers(origin);
          await checkMyHandlers(origin);
          resetMockDb();
          assert.equal(networkRequests, 0);
        }

        for (const path of ["/asset.svg", "/api/v10/resource"]) {
          assert.equal(await (await fetch(`${origin}${path}`)).text(), "external resource");
        }

        assert.equal(networkRequests, 2);
        console.info(`MSW ${name}: 응답·오류·초기화·미등록 API 차단·외부 요청 통과 확인`);
      } finally {
        server.close();
        networkRequests = 0;
      }
    }
  } finally {
    backend.closeAllConnections();

    await new Promise((resolve) => {
      return backend.close(resolve);
    });
  }
}

check().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
