const assert = require("node:assert/strict");
const { createServer } = require("node:http");
const { once } = require("node:events");
const { http, HttpResponse } = require("msw");
const { onUnhandledRequest } = require("../.expo/msw-check/api/mocks/on-unhandled-request");
const { resetMockDb } = require("../.expo/msw-check/api/mocks/db");
const { footprintPeriod, countFootprintBody } = require("../.expo/msw-check/features/footprint/model");
const { formatServerDateTime } = require("../.expo/msw-check/lib/date");

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

async function checkRoomCourseImport(origin) {
  const url = `${origin}/api/v1/rooms`;
  const body = { title: "코스 여행방", startDate: null, endDate: null, transportMode: "TRANSIT" };
  const headers = { Authorization: "Bearer mock-access-token", "Content-Type": "application/json" };

  const post = (requestHeaders, requestBody = body) => {
    return fetch(url, { method: "POST", headers: requestHeaders, body: JSON.stringify(requestBody) });
  };

  const missingKey = await post(headers);
  assert.equal(missingKey.status, 400);
  assert.equal((await missingKey.json()).code, "C001");

  const keyedHeaders = { ...headers, "Idempotency-Key": "room-create-check" };
  const results = await Promise.all([post(keyedHeaders), post(keyedHeaders)]);

  assert.ok(results.every((response) => {
    return response.status === 201;
  }));

  const [created, repeated] = await Promise.all(results.map((response) => {
    return response.json();
  }));

  assert.deepEqual(created, { success: true, data: { id: 100, version: 1, isCreator: true } });
  assert.deepEqual(repeated, created);

  const conflict = await post(keyedHeaders, { ...body, title: "다른 여행방" });
  assert.equal(conflict.status, 409);
  assert.equal((await conflict.json()).code, "C005");

  const placesUrl = `${url}/${created.data.id}/places`;
  const importHeaders = { ...headers, "Idempotency-Key": "course-import-check" };

  const importVisits = (visitIds, requestHeaders = importHeaders) => {
    return fetch(`${url}/${created.data.id}/place-imports`, {
      method: "POST",
      headers: requestHeaders,
      body: JSON.stringify({ recordId: 501, visitIds }),
    });
  };

  const invalidVisit = await importVisits([701, 731]);
  assert.equal(invalidVisit.status, 400);
  assert.equal((await invalidVisit.json()).code, "CO007");
  assert.deepEqual((await (await fetch(placesUrl)).json()).data.items, []);

  const imported = await importVisits([701, 702]);
  assert.equal(imported.status, 201);
  const result = (await imported.json()).data;

  assert.deepEqual(result.added.map(({ sourceType, sourceRecordId, place }) => {
    return [sourceType, sourceRecordId, place.providerPlaceId];
  }), [["COURSE", 501, "101"], ["COURSE", 501, "102"]]);

  assert.deepEqual((await (await importVisits([701, 702])).json()).data, result);
  const repeatedVisit = await importVisits([701], { ...headers, "Idempotency-Key": "course-import-another-selection" });
  assert.equal(repeatedVisit.status, 201);
  assert.equal((await (await fetch(placesUrl)).json()).data.items.length, 3);
  console.info("여행방 생성·코스 가져오기 MSW: 응답 계약, 원본 방문 검증, 저장과 멱등성 확인");
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
  assert.equal(countFootprintBody("가".repeat(198) + "\r\n😀"), 200);
  assert.equal(await request("/users/me/footprints", { status: 401, authenticated: false }), "A001");
  assert.equal(await request("/users/me/tips", { status: 401, authenticated: false }), "A001");
  assert.equal(await request("/footprints/99", { status: 403 }), "F002");
  assert.equal(await request("/users/me/tips/1", { status: 403 }), "T002");

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

  const statsPath = "/visit-statistics?bbox=126.97,37.55,126.99,37.57&zoom=16";

  const visits = async () => {
    return (await request(statsPath)).cells.reduce((sum, cell) => {
      return sum + cell.count;
    }, 0);
  };

  const initialVisits = await visits();
  const initialActivity = (await request("/users/me")).activityCounts.footprints;
  const createFootprint = { status: 201, method: "POST", key: "create-my-footprint", body: {
    location: { latitude: 37.5665, longitude: 126.978, capturedAt: formatServerDateTime(new Date()) },
    body: "새 방명록", categoryCodes: ["CULTURE"],
  } };
  const created = await request("/footprints", createFootprint);
  const myCreated = await request(`/footprints/${created.id}`);
  assert.equal(myCreated.body, createFootprint.body.body);
  assert.equal((await request("/users/me/footprints?limit=1")).items[0].id, created.id);
  assert.equal((await request("/users/me")).activityCounts.footprints, initialActivity + 1);
  assert.equal(await visits(), initialVisits + 1);
  await request(`/footprints/${created.id}`, { method: "PATCH", body: { version: myCreated.version, body: "수정한 방명록" } });
  assert.deepEqual(await request("/footprints", createFootprint), created);
  await request(`/footprints/${created.id}?version=${myCreated.version + 1}`, { method: "DELETE" });
  assert.equal((await request("/users/me")).activityCounts.footprints, initialActivity);
  assert.equal(await visits(), initialVisits);

  const tips = await request("/users/me/tips?status=PUBLISHED&limit=1");
  assert.equal(tips.items[0].category, "PHOTO");
  assert.ok(tips.nextCursor);
  assert.equal("body" in tips.items[0], false);
  const tip = await request("/users/me/tips/2");
  assert.deepEqual(tip.photos, []);
  const patchTip = { status: 202, method: "PATCH", key: "my-tip-check", body: { version: tip.version, category: "FOOD" } };
  const accepted = await request("/tips/2", patchTip);
  assert.equal(accepted.version, tip.version);
  assert.equal((await request("/users/me/tips/2")).category, "PHOTO");
  assert.equal((await request("/tips/2", patchTip)).jobId, accepted.jobId);
  assert.equal(await request("/tips/2", { ...patchTip, status: 409, body: { ...patchTip.body, category: "TMI" } }), "C005");

  assert.equal((await request("/tips/2/status")).operationStatus, "RUNNING");
  const job = await request("/tips/2/status");
  assert.equal(job.jobId, accepted.jobId);
  assert.equal(job.operationStatus, "SUCCEEDED");
  assert.equal((await request("/users/me/tips/2")).category, "FOOD");
  const deletion = { method: "DELETE" };
  assert.equal(await request(`/tips/2?version=${tip.version}`, { ...deletion, status: 409 }), "C004");
  assert.equal((await request(`/tips/2?version=${job.version}`, deletion)).status, "DELETED");
  assert.equal((await request(`/tips/2?version=${job.version}`, deletion)).status, "DELETED");
  assert.equal(await request("/users/me/tips/2", { status: 404 }), "T012");

  const jpeg = Uint8Array.of(255, 216, 255, 217);
  const upload = await request("/uploads", {
    status: 201, method: "POST", key: "my-photo-session", body: { purpose: "TIP_PHOTO", fileName: "photo.jpg", contentType: "image/jpeg", sizeBytes: jpeg.length },
  });
  assert.equal((await fetch(upload.uploadUrl, { method: upload.uploadMethod, headers: upload.uploadHeaders, body: jpeg })).status, 200);
  const ready = await request(`/uploads/${upload.uploadId}/complete`, { method: "POST", key: "my-photo-ready" });
  assert.equal(ready.status, "READY");
  const photoJob = await request("/tips/3", { status: 202, method: "PATCH", key: "my-photo-link", body: { version: 1, uploadIds: [ready.uploadId] } });
  assert.equal((await request("/tips/3?version=1", deletion)).status, "DELETED");

  const deleted = await request("/tips/3/status");
  assert.equal(deleted.jobId, photoJob.jobId);
  assert.equal(deleted.status, "DELETED");
  assert.equal(await request("/users/me/tips/3", { status: 404 }), "T012");
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
          await checkRoomCourseImport(origin);
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
