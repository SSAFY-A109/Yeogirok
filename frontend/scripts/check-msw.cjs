const assert = require("node:assert/strict");
const { createServer } = require("node:http");
const { once } = require("node:events");
const { http, HttpResponse } = require("msw");
const { onUnhandledRequest } = require("../.expo/msw-check/api/mocks/on-unhandled-request");

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
