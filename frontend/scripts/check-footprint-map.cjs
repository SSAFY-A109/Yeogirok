const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const Module = require("node:module");
const vm = require("node:vm");
const ts = require("typescript");

function load(relative) {
  const filename = path.resolve(__dirname, relative);
  const loaded = new Module(filename, module);
  loaded.filename = filename;
  loaded.paths = Module._nodeModulePaths(path.dirname(filename));
  const originalRequire = loaded.require.bind(loaded);

  loaded.require = (id) => {
    return id.startsWith("@/assets/") ? 1 : originalRequire(id);
  };

  loaded._compile(ts.transpileModule(fs.readFileSync(filename, "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText, filename);

  return loaded.exports;
}

const { buildKakaoMapHtml, buildSetMarkersScript, parseKakaoMapMessage } = load("../lib/native/map/kakao-map-bridge.ts");
const { footprintPinImage } = load("../lib/native/map/footprint-pin.ts");
const { mockFootprints } = load("../features/user/mocks/my-data.ts");
const state = { messages: [], markers: [], bounds: [], center: null };
let sdkScript;

class Position {
  constructor(x, y) {
    this.x = x;
    this.y = y;
  }
}

class MapStub {
  constructor() {
    this.events = {};
  }

  setBounds(bounds) {
    state.bounds.push(bounds.points);
  }

  setCenter(center) {
    state.center = center;
  }
}

class MarkerStub {
  constructor(options) {
    this.options = options;
    this.events = {};
  }
}

const maps = {
  Map: MapStub,
  Marker: MarkerStub,
  LatLng: Position,
  Size: Position,
  Point: Position,
  MarkerImage: class {
    constructor(uri, size, options) {
      Object.assign(this, { uri, size, ...options });
    }
  },
  MarkerClusterer: class {
    clear() {
      state.markers = [];
    }

    addMarkers(markers) {
      state.markers = markers;
    }
  },
  LatLngBounds: class {
    points = [];

    extend(position) {
      this.points.push(position);
    }
  },
  Polyline: class {
    constructor() {
      throw new Error("발자취 지도에 동선을 만들면 안 됩니다.");
    }
  },
  event: {
    addListener(target, event, callback) {
      target.events[event] = callback;
    },
  },

  load(callback) {
    callback();
  },
};

const context = vm.createContext({
  kakao: { maps },
  window: { kakao: { maps }, ReactNativeWebView: { postMessage: (message) => {
    state.messages.push(JSON.parse(message));
  } } },
  document: { getElementById: () => {
    return {};
  },

  createElement: () => {
    return {};
  },

  head: { appendChild: (script) => {
    sdkScript = script;
  } } },

  setTimeout: () => {
    return 0;
  },

  clearTimeout() {},

  performance: { now: () => {
    return 0;
  } },

  requestAnimationFrame: (callback) => {
    callback();
  },
});

const html = buildKakaoMapHtml("test-public-key", { lat: 37.7794, lng: 128.8784, level: 4 });
vm.runInContext(html.match(/<script>([\s\S]*?)<\/script>/)[1], context);
sdkScript.onload();

const points = mockFootprints.map(({ id, lat, lng }) => {
  assert.ok(Number.isFinite(lat) && lat >= -90 && lat <= 90);
  assert.ok(Number.isFinite(lng) && lng >= -180 && lng <= 180);

  return { id, lat, lng };
});

assert.equal(new Set(points.map(({ id }) => {
  return id;
})).size, points.length);

vm.runInContext(buildSetMarkersScript(points.slice(0, 3), footprintPinImage, true), context);
assert.equal(state.markers.length, 3);
assert.equal(state.bounds[0].length, 3);
assert.equal(state.markers[0].options.image.offset.y, footprintPinImage.height / 2);
state.markers[1].events.click();
assert.deepEqual(state.messages.at(-1), { type: "point-pressed", id: "anmok" });
assert.deepEqual(parseKakaoMapMessage(JSON.stringify(state.messages.at(-1))), { type: "point-pressed", id: "anmok" });

vm.runInContext(buildSetMarkersScript([points[1]], footprintPinImage, true), context);
assert.equal(state.markers.length, 1);
assert.equal(state.center.x, points[1].lat);
assert.equal(state.center.y, points[1].lng);
vm.runInContext(buildSetMarkersScript([], footprintPinImage, true), context);
assert.equal(state.markers.length, 0);

vm.runInContext(buildSetMarkersScript([{ lat: 37.5, lng: 127 }], { uri: "data:image/png;base64,test", width: 36, height: 42 }), context);
assert.equal(state.bounds.length, 1);
assert.equal(state.markers[0].options.image.offset.y, 42);
assert.equal(state.markers[0].events.click, undefined);
assert.ok(!buildSetMarkersScript([{ id: "</script>", lat: 37.5, lng: 127 }]).includes("</script>"));
console.log("발자취 지도 검사 통과: 좌표·핀 선택·필터 갱신·단일 위치·빈 목록·기존 마커 호환");
