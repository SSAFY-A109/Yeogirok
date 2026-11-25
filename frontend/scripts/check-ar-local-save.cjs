const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const Module = require("node:module");
const ts = require("typescript");

function load(relative, mocks = {}) {
  const filename = path.resolve(__dirname, relative);
  const loaded = new Module(filename, module);
  loaded.filename = filename;
  loaded.paths = Module._nodeModulePaths(path.dirname(filename));
  const originalRequire = loaded.require.bind(loaded);

  loaded.require = (id) => {
    return Object.hasOwn(mocks, id) ? mocks[id] : originalRequire(id);
  };

  loaded._compile(ts.transpileModule(fs.readFileSync(filename, "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText, filename);
  return loaded.exports;
}

async function main() {
  const mods = {};

  const registerMod = (name) => {
    return (config, action) => {
      mods[name] = action; return config;
    };
  };

  const plugin = load("../plugins/with-ar-tip.cjs", { "expo/config-plugins": Object.fromEntries(
    ["withAndroidManifest", "withAppBuildGradle", "withProjectBuildGradle", "withMainApplication", "withDangerousMod"]
      .map((name) => {
        return [name, registerMod(name)];
      })
  ) });
  plugin({});
  // Repeated prebuilds must remove a previous API key and preserve unrelated metadata.
  const manifestMod = { modResults: { manifest: { application: [{
    $: {}, activity: [], "meta-data": [
      { $: { "android:name": "com.google.android.ar.API_KEY", "android:value": "previous-build-key" } },
      { $: { "android:name": "test.other", "android:value": "keep" } },
    ],
  }] } } };
  mods.withAndroidManifest(manifestMod); mods.withAndroidManifest(manifestMod);
  const metadata = manifestMod.modResults.manifest.application[0]["meta-data"];

  assert.equal(metadata.some((item) => {
    return item.$["android:name"] === "com.google.android.ar.API_KEY";
  }), false);

  assert.equal(metadata.filter((item) => {
    return item.$["android:name"] === "com.google.ar.core";
  }).length, 1);

  assert.equal(metadata.find((item) => {
    return item.$["android:name"] === "test.other";
  }).$["android:value"], "keep");

  const detailActivities = manifestMod.modResults.manifest.application[0].activity.filter((item) => {
    return item.$["android:name"] === "com.yeogirokarpoc.geo.TipDetailSheet";
  });

  assert.equal(detailActivities.length, 1);
  assert.equal(detailActivities[0].$["android:exported"], "false");
  assert.equal(detailActivities[0].$["android:process"], undefined);
  assert.equal(detailActivities[0].$["android:theme"], "@style/ArTipDetailTheme");

  const arActivities = manifestMod.modResults.manifest.application[0].activity.filter((item) => {
    return item.$["android:name"] === "com.yeogirokarpoc.geo.GeoArActivity";
  });

  assert.equal(arActivities[0].$["android:process"], ":geospatial");
  const isolatedArSource = fs.readFileSync(path.resolve(__dirname, "../native/ar/android/java/com/yeogirokarpoc/geo/GeoArActivity.kt"), "utf8");
  // The isolated AR process intentionally skips SoLoader/React initialization.
  assert.doesNotMatch(isolatedArSource, /import com\.facebook\.react\.|\.reactHost\b|emitDeviceEvent/);

  const gradleMod = { modResults: { language: "groovy", contents: "\n// BEGIN YEOGIROK AR\nold-api-key-setup\n// END YEOGIROK AR\n" } };
  mods.withAppBuildGradle(gradleMod); mods.withAppBuildGradle(gradleMod);
  assert.equal((gradleMod.modResults.contents.match(/implementation 'com.google.android.gms:play-services-auth:/g) ?? []).length, 1);
  assert.equal(gradleMod.modResults.contents.includes("old-api-key-setup"), false);
  assert.equal(gradleMod.modResults.contents.includes("arcoreApiKey"), false);
  const contracts = load("../features/tip/native/ar/contracts.ts");

  const record = (anchorId, expiresAtEstimate = "2100-01-01T00:00:00Z", ttlDays = 1) => {
    return JSON.stringify({
      schemaVersion: 1, kind: "yeogirok-cloud-anchor", ttlDays, anchorId, expiresAtEstimate,
      hostedAt: new Date(Date.parse(expiresAtEstimate) - ttlDays * 86400000).toISOString(),
    });
  };

  let result = record("first-anchor");
  const stored = [];
  const writes = [];
  let failSave = false;
  let selected;
  const reactions = new Map();
  let failReaction = false;
  let detailCloseCount = 0;
  let photoResult = JSON.stringify({ photoName: "00000000-0000-4000-8000-000000000001.jpg", photoUri: "file:///test/tip-photos/00000000-0000-4000-8000-000000000001.jpg" });
  let cameraPermission = "granted";
  const geo = {
    getStatus: async () => {
      return { arcoreAuthMode: "KEYLESS", kakaoKeyConfigured: false };
    },

    openTipPlacement: async () => {
      return result;
    },

    openTipExplorer: async (id) => {
      selected = id; return result;
    },

    openCloudMap: async () => {
      return null;
    },
  };
  const tips = {
    getTipDetail: async (id) => {
      const tip = stored.find((item) => {
        return item.id === id;
      });

      if (!tip) {
        throw new Error("TIP_UNAVAILABLE");
      }

      return JSON.stringify({ ...tip, reaction: reactions.get(id) ?? null });
    },

    toggleTipReaction: async (id, choice) => {
      if (failReaction) {
        throw new Error("REACTION_FAILED");
      }

      const next = reactions.get(id) === choice ? null : choice;
      reactions.set(id, next);
      return next;
    },

    closeTipDetail: async () => {
      detailCloseCount++;
    },

    listTips: async () => {
      return JSON.stringify(stored);
    },

    saveTip: async (raw) => {
      if (failSave) {
        throw new Error("SAVE_FAILED");
      }

      const draft = JSON.parse(raw);
      writes.push(draft);
      const tip = { schemaVersion: 1, id: `local-${stored.length}`, createdAt: "2026-11-02T00:00:00Z", ...draft };
      stored.push(tip);
      return JSON.stringify(tip);
    },

    openTipAR: async () => {
      return null;
    },

    pickPhoto: async () => {
      return photoResult;
    },

    capturePhoto: async () => {
      return photoResult;
    },
  };

  const freshAdapter = () => {
    return load("../features/tip/native/ar/index.ts", {
      "./contracts": contracts,
      "react-native": { Platform: { OS: "android" }, NativeModules: { GeoValidation: geo, TipExperience: tips }, PermissionsAndroid: { PERMISSIONS: { CAMERA: "camera" }, RESULTS: { GRANTED: "granted" },

        request: async () => {
          return cameraPermission;
        } } },
    });
  };

  const ar = freshAdapter();
  assert.equal((await ar.loadArTips()).arcoreConfigured, true);
  const placement = await ar.placeTip();
  assert.equal(placement.anchorId, "first-anchor");
  assert.equal(writes.length, 1);
  assert.equal(writes[0].cloudRecord.discoveryLocation, undefined);
  assert.equal(writes[0].body, "위치 복원 테스트용 임시 마커");

  // A fresh JS adapter loads the native store and reopens the saved marker by ID.
  const reopened = freshAdapter();
  const saved = (await reopened.loadArTips()).tips[0];
  assert.equal(saved.placement.anchorId, placement.anchorId);
  stored[0].body = "직접 작성한 팁";
  await reopened.exploreTips(saved.id);
  assert.equal(selected, saved.id);
  assert.equal(writes.length, 1);
  assert.equal(stored[0].body, "직접 작성한 팁");

  result = null;
  assert.equal(await ar.placeTip(), null);
  assert.equal(writes.length, 1);
  result = record("expired-anchor", "2000-01-01T00:00:00Z");
  await assert.rejects(ar.placeTip(), /ANCHOR_EXPIRED/);
  assert.equal(writes.length, 1);
  result = record("second-anchor");
  failSave = true;
  await assert.rejects(ar.placeTip(), /SAVE_FAILED/);
  assert.equal(stored.length, 1);
  failSave = false;
  await ar.placeTip();
  assert.equal((await reopened.loadArTips()).tips.length, 2);
  // Existing one-day records stay readable; new one-year records survive reload/reopen too.
  result = record("year-anchor", "2100-01-01T00:00:00Z", 365);
  const year = await ar.placeTip();
  assert.equal(JSON.parse(year.raw).ttlDays, 365);
  const mixed = await reopened.loadArTips();

  assert.deepEqual(mixed.tips.map((tip) => {
    return JSON.parse(tip.placement.raw).ttlDays;
  }), [1, 1, 365]);

  await reopened.exploreTips(mixed.tips[2].id);
  assert.equal(writes.length, 3);
  assert.equal(selected, mixed.tips[2].id);
  assert.equal(contracts.isPlacementExpired(year, Date.parse("2099-12-31T23:59:59Z")), false);
  assert.equal(contracts.isPlacementExpired(year, Date.parse(year.expiresAtEstimate)), true);

  for (const ttlDays of [0, 366, -1, 1.5, "365"]) {
    const invalid = JSON.parse(record("bad-ttl")); invalid.ttlDays = ttlDays;

    assert.throws(() => {
      return contracts.parsePlacement(JSON.stringify(invalid));
    }, /INVALID_RESULT/);
  }

  const badExpiry = JSON.parse(year.raw); badExpiry.expiresAtEstimate = "2100-01-02T00:00:00Z";

  assert.throws(() => {
    return contracts.parsePlacement(JSON.stringify(badExpiry));
  }, /INVALID_RESULT/);

  const badDate = JSON.parse(year.raw); badDate.hostedAt = "invalid";

  assert.throws(() => {
    return contracts.parsePlacement(JSON.stringify(badDate));
  }, /INVALID_RESULT/);

  const keyless = JSON.parse(year.raw); keyless.authorizationMode = "KEYLESS";
  assert.equal(contracts.parsePlacement(JSON.stringify(keyless)).anchorId, year.anchorId);
  const photo = await ar.addTipPhoto("library");
  assert.equal(photo.photoName, "00000000-0000-4000-8000-000000000001.jpg");
  assert.deepEqual(await ar.addTipPhoto("camera"), photo);
  cameraPermission = "denied";
  await assert.rejects(ar.addTipPhoto("camera"), /CAMERA_PERMISSION/);
  cameraPermission = "granted";
  photoResult = null;
  assert.equal(await ar.addTipPhoto("library"), null);

  assert.throws(() => {
    return contracts.parsePhoto(JSON.stringify({ photoName: photo.photoName, photoUri: "https://example.com/image.jpg" }));
  }, /INVALID_RESULT/);

  await assert.rejects(ar.saveLocalTip("", placement), /BODY_REQUIRED/);
  await assert.rejects(ar.saveLocalTip("x".repeat(2001), placement), /BODY_TOO_LONG/);
  await assert.rejects(ar.saveLocalTip("본문", placement), /LOCATION_REQUIRED/);
  const located = JSON.parse(record("located-anchor"));
  located.visitLocation = { source: "DEVICE_GPS", latitude: 37.5665, longitude: 126.978, accuracyMeters: 8, capturedAt: "2026-11-02T00:00:00Z" };
  const locatedPlacement = contracts.parsePlacement(JSON.stringify(located));
  assert.equal(locatedPlacement.visitAccuracyMeters, 8);
  await ar.saveLocalTip("사진과 방문 위치가 있는 팁", locatedPlacement, photo);
  assert.equal(writes.at(-1).photoName, photo.photoName);
  located.visitLocation.accuracyMeters = -1;

  assert.throws(() => {
    return contracts.parsePlacement(JSON.stringify(located));
  }, /INVALID_RESULT/);

  const writesBeforeDraft = writes.length;
  result = record("draft-anchor");
  const draftPlacement = await ar.placeTip(false);
  assert.equal(writes.length, writesBeforeDraft);
  await ar.exploreTips(null, false);
  assert.equal(writes.length, writesBeforeDraft);
  await assert.rejects(ar.saveTipDraft("", draftPlacement, photo, null), /TYPE_REQUIRED/);
  await assert.rejects(ar.saveTipDraft("", draftPlacement, photo, "unknown"), /TYPE_REQUIRED/);
  await assert.rejects(ar.saveTipDraft("", draftPlacement, null, "포토"), /BODY_REQUIRED/);
  await assert.rejects(ar.saveTipDraft("", draftPlacement, photo, "포토"), /BODY_REQUIRED/);
  await assert.rejects(ar.saveTipDraft(" \n\t ", draftPlacement, photo, "포토"), /BODY_REQUIRED/);
  await assert.rejects(ar.saveTipDraft("x".repeat(121), draftPlacement, photo, "포토"), /DRAFT_BODY_TOO_LONG/);
  const expiredDraft = contracts.parsePlacement(record("expired-draft", "2000-01-01T00:00:00Z"));
  await assert.rejects(ar.saveTipDraft("본문", expiredDraft, null, "포토"), /ANCHOR_EXPIRED/);
  assert.equal(writes.length, writesBeforeDraft);
  failSave = true;
  await assert.rejects(ar.saveTipDraft("본문", draftPlacement, null, "포토"), /SAVE_FAILED/);
  assert.equal(writes.length, writesBeforeDraft);
  failSave = false;
  const savedDraft = await ar.saveTipDraft(" 팁 ", draftPlacement, null, "포토");
  assert.equal(savedDraft.body, "팁");
  assert.equal(savedDraft.photo, null);
  assert.equal(savedDraft.tipType, "포토");
  assert.equal(savedDraft.draft, true);
  assert.equal(writes.at(-1).photoName, null);
  assert.equal((await reopened.loadArTips()).tips.at(-1).body, "팁");
  assert.equal((await reopened.loadArTips()).tips.at(-1).photo, null);
  await ar.saveTipDraft("x".repeat(120), draftPlacement, photo, "TMI");
  assert.equal(writes.at(-1).photoName, photo.photoName);
  assert.equal(writes.at(-1).body.length, 120);
  assert.equal((await reopened.loadArTips()).tips.at(-1).tipType, "TMI");
  const malformedDraft = { ...stored.at(-1), tipType: "unknown" };

  assert.throws(() => {
    return contracts.parseLocalTip(JSON.stringify(malformedDraft));
  }, /INVALID_RESULT/);

  const withoutPhoto = { ...stored.at(-1) };
  delete withoutPhoto.photoName;
  assert.equal(contracts.parseLocalTip(JSON.stringify(withoutPhoto)).photo, null);

  // Drafts saved before content became required must remain readable.
  assert.equal(contracts.parseLocalTip(JSON.stringify({ ...stored.at(-1), body: "" })).body, "");

  assert.throws(() => {
    return contracts.parseLocalTip(JSON.stringify({ ...stored.at(-1), photoName: "invalid.jpg" }));
  }, /INVALID_RESULT/);

  assert.equal((await ar.loadTipDetail(savedDraft.id)).tip.id, savedDraft.id);
  assert.equal((await ar.loadTipDetail(savedDraft.id)).reaction, null);
  assert.equal(await ar.toggleTipReaction(savedDraft.id, "LIKE"), "LIKE");
  assert.equal((await freshAdapter().loadTipDetail(savedDraft.id)).reaction, "LIKE");
  assert.equal(await ar.toggleTipReaction(savedDraft.id, "LIKE"), null);
  assert.equal(await ar.toggleTipReaction(savedDraft.id, "DISLIKE"), "DISLIKE");
  failReaction = true;
  await assert.rejects(ar.toggleTipReaction(savedDraft.id, "LIKE"), /REACTION_FAILED/);
  assert.equal((await ar.loadTipDetail(savedDraft.id)).reaction, "DISLIKE");
  failReaction = false;
  await assert.rejects(ar.loadTipDetail("missing-tip"), /TIP_UNAVAILABLE/);

  assert.throws(() => {
    return contracts.parseTipDetail(JSON.stringify({ ...stored.at(-1), reaction: "UNKNOWN" }));
  }, /INVALID_RESULT/);

  await ar.closeTipDetail();
  assert.equal(detailCloseCount, 1);

  console.log("AR local save checks passed: auto-save, reload/reopen, no overwrite, cancel, expiry, failure/retry");
  console.log("AR compose checks passed: photo selection/capture/cancel, local URI, body limits, location evidence");
  console.log("AR Keyless TTL checks passed: mixed 1/365-day reload, reopen, expiry, invalid lifetimes");
  console.log("AR Keyless config checks passed: stale API key removal, prebuild idempotence, auth dependency");
  console.log("AR service flow checks passed: no placeholder, required type/body, optional photo, 120-char limit, draft reload, failure/retry");
  console.log("AR shared sheet bridge checks passed: internal host, detail read, persisted reaction toggle/cancel, failure preservation, close");
}

main().catch((error) => {
  console.error(error); process.exitCode = 1;
});
