const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const Module = require("node:module");
const ts = require("typescript");
const { http, HttpResponse } = require("msw");
const { setupServer } = require("msw/node");

const root = path.resolve(__dirname, "..");
const originalLoad = Module._load;
const originalTs = require.extensions[".ts"];
const tokens = new Map();
let publicTips;
let publicSessionId;
const explorerListeners = new Set();
const explorerUpdates = [];
let holdExplorer = false;
let finishExplorer;

require.extensions[".ts"] = (module, filename) => {
  const source = fs.readFileSync(filename, "utf8");
  module._compile(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText, filename);
};

Module._load = function (id, ...args) {
  if (id === "react-native") {
    return {
      DeviceEventEmitter: { addListener: (name, listener) => {
        assert.equal(name, "ArTipNearbyRefresh");
        explorerListeners.add(listener);
        return { remove: () => {
          explorerListeners.delete(listener);
        } };
      } },
      Platform: { OS: "android" },
      PermissionsAndroid: { PERMISSIONS: { ACCESS_FINE_LOCATION: "location" }, RESULTS: { GRANTED: "granted" },

        request: async () => {
          return "granted";
        } },
      NativeModules: {
        TipExperience: { readPhoto: async () => {
          return JSON.stringify({ base64: "AQIDBA==", sizeBytes: 4 });
        },

        getCurrentLocation: async () => {
          return JSON.stringify({ ...location, capturedAt: "2026-11-06T03:00:00.999Z" });
        } },
        GeoValidation: { openPublicTipExplorer: async (raw, origin, sessionId) => {
          publicTips = JSON.parse(raw); publicSessionId = sessionId;
          assert.deepEqual(JSON.parse(origin), location);

          return holdExplorer ? new Promise((resolve) => {
            finishExplorer = resolve;
          }) : null;
        },

        updatePublicTips: async (requestId, raw, error) => {
          explorerUpdates.push({ requestId, tips: raw === null ? null : JSON.parse(raw), error });
          return true;
        } },
      },
    };
  }

  if (id === "expo-secure-store") {
    return {
      getItemAsync: async (key) => {
        return tokens.get(key) ?? null;
      },

      setItemAsync: async (key, value) => {
        tokens.set(key, value);
      },

      deleteItemAsync: async (key) => {
        tokens.delete(key);
      },
    };
  }

  return originalLoad.call(this, id.startsWith("@/") ? path.join(root, id.slice(2)) : id, ...args);
};

process.env.EXPO_PUBLIC_API_URL = "https://api.yeogirok.test";
const { ApiError } = require("../api/errors.ts");
const { setTokens } = require("../features/auth/token-store.ts");
const tipApi = require("../features/tip/api/tip.api.ts");
const model = require("../features/tip/model.ts");
const options = require("../features/tip/api/queries.ts");
const { getTipErrorMessage } = require("../features/tip/errors.ts");
const mediaApi = require("../features/media/api/media.api.ts");
const mediaModel = require("../features/media/model.ts");
const { uploadMedia } = require("../features/media/upload.ts");
const { mockDb, resetMockDb } = require("../api/mocks/db.ts");
const { tipHandlers } = require("../api/mocks/handlers/tip.ts");
const { mediaHandlers } = require("../api/mocks/handlers/media.ts");
const { tipFixture } = require("../api/mocks/fixtures/tip.ts");
const publicAdapter = require("../features/tip/native/ar/public.ts");
const pendingTips = require("../features/tip/pending.ts");
const server = setupServer(...tipHandlers, ...mediaHandlers);
const location = { latitude: 37.5665, longitude: 126.978, accuracyMeters: 8, capturedAt: "2026-11-06 12:00:00+09" };
const request = { category: "PHOTO", body: " 유효한 팁\r\n둘째 줄 ", location, spatialReference: tipFixture.spatialReference, placement: tipFixture.placement };

function hasCode(code) {
  return (error) => {
    return error instanceof ApiError && error.code === code;
  };
}

async function check() {
  await setTokens({ accessToken: "mock-access-token", refreshToken: "mock-refresh-token" });
  server.listen({ onUnhandledRequest: "error" });
  resetMockDb();
  const tipRequests = [];

  server.events.on("request:start", ({ request }) => {
    const pathname = new URL(request.url).pathname;

    if (request.method === "POST" && pathname === "/api/v1/tips" || request.method === "PATCH" && /^\/api\/v1\/tips\/\d+$/.test(pathname)) {
      tipRequests.push(request.clone().json());
    }
  });

  try {
    await pendingTips.rememberPendingTip(1, 123);
    await pendingTips.rememberPendingTip(1, 123);
    assert.deepEqual(await pendingTips.getPendingTipIds(1), [123]);
    assert.deepEqual(await pendingTips.getPendingTipIds(2), []);
    await pendingTips.forgetPendingTip(1, 123);
    assert.deepEqual(await pendingTips.getPendingTipIds(1), []);
    assert.equal(publicAdapter.toServerDateTime("2026-11-06T05:00:00.999Z"), "2026-11-06 14:00:00+09");
    assert.equal((await publicAdapter.measureTipLocation()).capturedAt, "2026-11-06 12:00:00+09");
    const spatial = publicAdapter.toTipSpatialRequest({ anchorId: "host-anchor", expiresAtEstimate: "2100-01-01T00:00:00Z", raw: JSON.stringify({ localPose: {}, hostDeviceLabel: "private", visitLocation: location }) });
    assert.deepEqual(Object.keys(spatial.placement).sort(), ["anchorType", "authorizationMode", "schemaVersion"]);
    assert.equal(JSON.stringify(spatial).includes("private"), false);
    assert.equal(spatial.spatialReference.referenceId, "host-anchor");

    assert.throws(() => {
      publicAdapter.toTipSpatialRequest({ anchorId: "x".repeat(256), expiresAtEstimate: "2100-01-01T00:00:00Z" });
    }, /계약/);

    assert.deepEqual(Array.from((await publicAdapter.prepareTipPhotoUpload({ photoName: "photo.jpg" })).body), [1, 2, 3, 4]);

    for (const [value, code] of [[null, "C001"], [123, "C002"], ["unknown", "C003"]]) {
      assert.throws(() => {
        model.validateTipType(value);
      }, hasCode(code));
    }

    assert.equal(model.normalizeTipBody(" a\r\nb\rc "), " a\nb\nc ");
    assert.equal(model.normalizeTipBody("x".repeat(118) + "\r\na").length, 120);

    for (const value of ["", " \n\t ", "x".repeat(121)]) {
      assert.throws(() => {
        model.normalizeTipBody(value);
      }, hasCode("C003"));
    }

    assert.throws(() => {
      model.normalizeTipBody(null);
    }, hasCode("C001"));

    assert.throws(() => {
      model.normalizeTipBody(123);
    }, hasCode("C002"));

    const session = options.createTipLocationSession(location);
    const samePlace = options.createTipLocationSession(location);
    assert.notEqual(session.id, samePlace.id);
    assert.equal(JSON.stringify(options.nearbyTipsOptions(session).queryKey).includes("37.5665"), false);
    assert.equal(options.tipViewOptions(1, session).gcTime, 0);
    const page = await tipApi.searchTips({ location, limit: 1 });
    assert.equal(page.items[0].tipType, "PHOTO");
    assert.equal((await tipApi.viewTip({ tipId: 1, location })).tipType, "PHOTO");
    assert.equal((await tipApi.getMyTip(2)).tipType, "PHOTO");
    await publicAdapter.openPublicTipExplorer(page, location, tipApi.searchTips);
    assert.equal(publicTips[0].cloudRecord.kind, "yeogirok-public-cloud-anchor");
    assert.equal(publicTips[0].cloudRecord.anchorId, tipFixture.spatialReference.referenceId);
    assert.equal(JSON.stringify(publicTips).includes("hostSessionId"), false);
    assert.equal(explorerListeners.size, 0);

    holdExplorer = true;
    const measurements = [];
    let failRefresh = false;
    let finishSearch;

    const exploration = publicAdapter.openPublicTipExplorer(page, location, async (evidence) => {
      measurements.push(evidence);

      if (failRefresh) {
        throw new ApiError(503, "T006", "Temporary failure");
      }

      return new Promise((resolve) => {
        finishSearch = () => {
          resolve(page);
        };
      });
    });

    const listener = [...explorerListeners][0];

    const nativeEvent = (sessionId, requestId) => {
      return JSON.stringify({ sessionId, requestId, location: { ...location, capturedAt: "2026-11-06T03:00:00.999Z" } });
    };

    await listener("invalid JSON");
    await listener(nativeEvent("previous-camera-session", "old"));
    assert.equal(measurements.length, 0);
    const refreshing = listener(nativeEvent(publicSessionId, "refresh-1"));
    finishSearch();
    await refreshing;
    assert.deepEqual(measurements[0], location);
    assert.equal(explorerUpdates[0].requestId, "refresh-1");
    assert.equal(explorerUpdates[0].tips[0].id, String(page.items[0].id));
    failRefresh = true;
    await listener(nativeEvent(publicSessionId, "refresh-failed"));
    assert.equal(explorerUpdates[1].tips, null);
    assert.match(explorerUpdates[1].error, /일시적으로/);
    failRefresh = false;
    const lateRefresh = listener(nativeEvent(publicSessionId, "late-refresh"));
    finishExplorer(null);
    await exploration;
    finishSearch();
    await lateRefresh;
    assert.equal(explorerListeners.size, 0);
    assert.equal(explorerUpdates.length, 2);
    holdExplorer = false;
    assert.equal(page.items.length, 1);
    assert.equal(page.totalCount, 2);
    assert.equal(page.nextCursor, "1");
    assert.equal((await tipApi.searchTips({ location, cursor: page.nextCursor, limit: 1 })).nextCursor, null);
    const far = { ...location, latitude: location.latitude + 0.0001 };

    assert.equal((await tipApi.searchTips({ location: far })).items.every((tip) => {
      return tip.canInteract === false && tip.distanceMeters > 5 && tip.distanceMeters <= 50;
    }), true);

    await assert.rejects(tipApi.viewTip({ tipId: 1, location: far }), hasCode("T004"));
    assert.equal((await tipApi.searchTips({ location: { ...location, latitude: 38 } })).items.length, 0);
    assert.deepEqual((await tipApi.viewTip({ tipId: 1, location })).photos, []);
    assert.equal(model.toTipDetail({ ...tipFixture, author: { id: null, nickname: "탈퇴한 사용자" } }).author.id, null);

    for (const distanceMeters of [5, 50]) {
      const item = { id: 1, ...location, summary: "팁", spatialReference: tipFixture.spatialReference, placement: {}, distanceMeters, canInteract: distanceMeters <= 5 };
      assert.equal(model.toTipSearchPage({ items: [item], nextCursor: null, totalCount: 1, appliedRadiusMeters: 50, interactionRadiusMeters: 5 }).items[0].canInteract, distanceMeters <= 5);
    }

    assert.throws(() => {
      model.validateTipLocation({ ...location, longitude: 181 });
    }, /계약/);

    assert.throws(() => {
      model.validateTipLocation({ ...location, capturedAt: "2026-11-06T12:00:00+09:00" });
    }, /계약/);

    const liked = await tipApi.setTipReaction({ tipId: 1, reaction: "LIKE", location });
    assert.deepEqual(await tipApi.setTipReaction({ tipId: 1, reaction: "LIKE", location }), liked);
    assert.equal((await tipApi.setTipReaction({ tipId: 1, reaction: "DISLIKE", location })).myReaction, "DISLIKE");
    assert.equal((await tipApi.setTipReaction({ tipId: 1, reaction: "NONE", location })).myReaction, null);
    assert.equal((await tipApi.setTipReaction({ tipId: 1, reaction: "NONE", location })).myReaction, null);
    await assert.rejects(tipApi.setTipReaction({ tipId: 1, reaction: "LIKE", location: far }), hasCode("T004"));
    await assert.rejects(tipApi.setTipReaction({ tipId: 2, reaction: "LIKE", location }), hasCode("T009"));

    for (const [category, code] of [[undefined, "C001"], [null, "C001"], [123, "C002"], ["unknown", "C003"]]) {
      await assert.rejects(tipApi.createTip({ request: { ...request, category }, idempotencyKey: "invalid-category" }), hasCode(code));

      if (category !== undefined) {
        await assert.rejects(tipApi.updateTip({ tipId: 2, request: { version: 1, category, body: "수정" }, idempotencyKey: "invalid-update-category" }), hasCode(code));
      }
    }

    const created = await tipApi.createTip({ request, idempotencyKey: "tip-create-one" });
    assert.deepEqual(await tipApi.createTip({ request, idempotencyKey: "tip-create-one" }), created);
    await assert.rejects(tipApi.createTip({ request: { ...request, body: "다른 내용" }, idempotencyKey: "tip-create-one" }), hasCode("C005"));
    await assert.rejects(tipApi.viewTip({ tipId: created.tipId, location }), hasCode("T001"));
    assert.equal((await tipApi.getTipStatus(created.tipId)).operationStatus, "RUNNING");
    const published = await tipApi.getTipStatus(created.tipId);
    assert.equal(published.status, "PUBLISHED");
    assert.equal(published.steps.media, "SKIPPED");
    assert.equal((await tipApi.getMyTip(created.tipId)).body, " 유효한 팁\n둘째 줄 ");
    const unknown = model.toTipStatus({ ...mockDb.tips.get(created.tipId).state, operationStatus: "NEW_STATE" });
    assert.equal(unknown.operationStatus, "UNKNOWN");
    assert.equal(model.isTipOperationPending(unknown), false);
    assert.equal(model.toTipStatus({ ...mockDb.tips.get(created.tipId).state, operationStatus: null }).operationStatus, null);

    const original = await tipApi.getMyTip(created.tipId);
    const updated = await tipApi.updateTip({ tipId: created.tipId, idempotencyKey: "tip-update-one", request: { version: original.version, category: "CAFE", body: "수정안", uploadIds: [] } });
    assert.equal(updated.status, "PUBLISHED");
    assert.equal((await tipApi.getMyTip(created.tipId)).body, original.body);
    assert.equal((await tipApi.getMyTip(created.tipId)).tipType, "PHOTO");
    assert.equal((await tipApi.viewTip({ tipId: created.tipId, location })).body, original.body);
    const mock = mockDb.tips.get(created.tipId);
    mock.state.operationStatus = "FAILED";
    mock.state.retryable = true;
    mock.state.error = { code: "T006" };
    assert.equal((await tipApi.getTipStatus(created.tipId)).status, "PUBLISHED");
    const retry = await tipApi.retryTip({ tipId: created.tipId, idempotencyKey: "tip-retry-one", request: { replacementUploadIds: [] } });
    assert.equal(retry.completed, false);
    await tipApi.getTipStatus(created.tipId);
    const finished = await tipApi.getTipStatus(created.tipId);
    assert.equal(finished.operationStatus, "SUCCEEDED");
    assert.equal((await tipApi.getMyTip(created.tipId)).body, "수정안");
    assert.equal((await tipApi.getMyTip(created.tipId)).tipType, "CAFE");
    assert.equal((await tipApi.viewTip({ tipId: created.tipId, location })).tipType, "CAFE");

    assert.equal((await tipApi.searchTips({ location })).items.find((tip) => {
      return tip.id === created.tipId;
    }).tipType, "CAFE");

    assert.equal(finished.version, original.version + 1);
    assert.equal((await tipApi.retryTip({ tipId: created.tipId, request: {}, idempotencyKey: "already-done" })).completed, true);
    await assert.rejects(tipApi.updateTip({ tipId: created.tipId, request: { version: original.version, body: "충돌" }, idempotencyKey: "conflict" }), hasCode("C004"));
    await assert.rejects(tipApi.updateTip({ tipId: created.tipId, request: { version: finished.version, location }, idempotencyKey: "incomplete-placement" }), hasCode("C001"));

    const categoryOriginal = await tipApi.getMyTip(2);
    await tipApi.updateTip({ tipId: 2, request: { version: categoryOriginal.version, category: "TMI" }, idempotencyKey: "category-only" });
    assert.equal((await tipApi.getMyTip(2)).tipType, categoryOriginal.tipType);
    await tipApi.getTipStatus(2);
    await tipApi.getTipStatus(2);
    assert.equal((await tipApi.getMyTip(2)).tipType, "TMI");
    assert.equal((await tipApi.getMyTip(2)).body, categoryOriginal.body);

    let puts = 0;
    let completes = 0;
    let serviceRequests = 0;

    server.events.on("request:start", ({ request }) => {
      if (request.url.startsWith("https://uploads.yeogirok.test")) {
        puts += 1;
        assert.equal(request.headers.has("Authorization"), false);
        assert.equal(request.headers.get("Content-Type"), "image/jpeg");
      } else {
        serviceRequests += 1;
        assert.equal(request.headers.get("Authorization"), "Bearer mock-access-token");
      }
    });

    const task = {
      file: { metadata: { purpose: "TIP_PHOTO", fileName: "photo.jpg", contentType: "image/jpeg", sizeBytes: 4 }, body: new Uint8Array([1, 2, 3, 4]) },
      sessionKey: "upload-one", completeKey: "complete-one", session: null, transferred: false, ready: null,
    };
    const stages = [];

    server.use(http.post("*/api/v1/uploads/:uploadId/complete", ({ params }) => {
      completes += 1;

      if (completes === 1) {
        return HttpResponse.json({ success: false, code: "M012", message: "일시 장애" }, { status: 503 });
      }

      return HttpResponse.json({ success: true, data: { uploadId: params.uploadId, status: "READY", imageId: null, materialId: null, contentType: "image/jpeg", sizeBytes: 4 } });
    }));

    await assert.rejects(uploadMedia(task, (stage) => {
      stages.push(stage);
    }), hasCode("M012"));

    const ready = await uploadMedia(task, (stage) => {
      stages.push(stage);
    });

    assert.equal(ready.imageId, null);
    assert.equal(puts, 1, "완료 확인 재시도는 PUT을 반복하지 않는다.");
    assert.deepEqual(stages, ["session", "transfer", "complete", "complete", "ready"]);
    assert.equal(serviceRequests, 3);
    server.resetHandlers();
    const uploadSession = await mediaApi.createUpload({ request: task.file.metadata, idempotencyKey: "upload-two" });
    await assert.rejects(mediaApi.completeUpload({ uploadId: uploadSession.uploadId, idempotencyKey: "complete-two" }), hasCode("M006"));
    await mediaApi.putUpload(uploadSession, task.file.body);
    const completed = await mediaApi.completeUpload({ uploadId: uploadSession.uploadId, idempotencyKey: "complete-two" });
    assert.equal(completed.status, "READY");
    assert.deepEqual(await mediaApi.completeUpload({ uploadId: uploadSession.uploadId, idempotencyKey: "complete-two" }), completed);
    const withPhoto = await tipApi.createTip({ request: { ...request, uploadIds: [completed.uploadId] }, idempotencyKey: "tip-with-photo" });
    await tipApi.getTipStatus(withPhoto.tipId);
    assert.equal((await tipApi.getTipStatus(withPhoto.tipId)).steps.media, "SUCCEEDED");
    assert.equal((await tipApi.getMyTip(withPhoto.tipId)).photos.length, 1);
    await assert.rejects(mediaApi.putUpload({ ...uploadSession, uploadExpiresAt: new Date(0) }, task.file.body), hasCode("M011"));

    server.use(http.put("https://uploads.yeogirok.test/mock/:uploadId", () => {
      return new HttpResponse(null, { status: 403 });
    }));

    await assert.rejects(mediaApi.putUpload(uploadSession, task.file.body), hasCode("S3_UPLOAD_FAILED"));
    server.resetHandlers();

    assert.throws(() => {
      mediaModel.validateUploadRequest({ ...task.file.metadata, contentType: "image/heic" });
    }, hasCode("M002"));

    const sentBodies = await Promise.all(tipRequests);

    assert.equal(sentBodies.some((body) => {
      return body.category === "PHOTO";
    }), true);

    assert.equal(sentBodies.some((body) => {
      return body.category === "CAFE";
    }), true);

    assert.equal(sentBodies.every((body) => {
      return !Object.hasOwn(body, "tipType");
    }), true);

    await tipApi.deleteTip({ tipId: created.tipId, version: finished.version });
    assert.equal((await tipApi.deleteTip({ tipId: created.tipId, version: finished.version })).status, "DELETED");
    await assert.rejects(tipApi.getMyTip(created.tipId), hasCode("T012"));
    await assert.rejects(tipApi.viewTip({ tipId: created.tipId, location }), hasCode("T001"));

    assert.equal((await tipApi.getMyTips()).items.some((tip) => {
      return tip.id === created.tipId;
    }), false);

    assert.match(getTipErrorMessage(new ApiError(422, "C008", "secret")), /위치/);
    assert.match(getTipErrorMessage(new ApiError(409, "C004", "secret")), /최신/);
    assert.match(getTipErrorMessage(new ApiError(401, "C004", "secret")), /로그인/);
    console.log("Tip API checks passed: category request/response/update, location POST/privacy, 5m/50m, body normalization, no-photo publish, final-state reactions, jobs/retry/version/delete, upload PUT/READY/recovery, live explorer refresh/session/cleanup");
  } finally {
    server.close();
    Module._load = originalLoad;
    require.extensions[".ts"] = originalTs;
  }
}

check().catch((error) => {
  console.error(error); process.exitCode = 1;
});
