const assert = require("node:assert/strict");
const { createServer } = require("node:http");
const { once } = require("node:events");
const { http, HttpResponse } = require("msw");
const { onUnhandledRequest } = require("../.expo/msw-check/on-unhandled-request");

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
      const { server } = require(`../.expo/msw-check/${name}`);
      const url = `${origin}/api/v1/__msw_check__`;
      const fixture = { success: true, data: { mocked: true } };
      server.listen({ onUnhandledRequest });

      try {
        server.use(http.get(url, () => HttpResponse.json(fixture)));
        assert.deepEqual(await (await fetch(url)).json(), fixture);
        assert.equal(networkRequests, 0);

        server.use(http.get(url, () => HttpResponse.json(
          { success: false, code: "MSW_CHECK", message: "test only" },
          { status: 422 },
        )));
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
    await new Promise((resolve) => backend.close(resolve));
  }
}

check().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
