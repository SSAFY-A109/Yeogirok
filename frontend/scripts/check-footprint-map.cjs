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
const { createFootprintFixtures } = load("../api/mocks/fixtures/footprint.ts");
const state = { messages: [], markers: [], bounds: [], center: null, clusterer: null, boundsPadding: null, clusterContent: null };
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

  setBounds(bounds, ...padding) {
    state.bounds.push(bounds.points);
    state.boundsPadding = padding;
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

class ElementStub {
  style = {};
  attributes = {};
  children = [];

  set textContent(value) {
    this.text = value;
    this.children = [];
  }

  get textContent() {
    return this.text;
  }

  setAttribute(name, value) {
    this.attributes[name] = value;
  }

  appendChild(child) {
    this.children.push(child);
  }

  click() {
    this.onclick?.();
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
    constructor() {
      this.events = {};
      state.clusterer = this;
    }

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
    return new ElementStub();
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

const points = createFootprintFixtures().filter(({ ownerId }) => {
  return ownerId === 1;
}).map(({ id, latitude: lat, longitude: lng }) => {
  assert.ok(Number.isFinite(lat) && lat >= -90 && lat <= 90);
  assert.ok(Number.isFinite(lng) && lng >= -180 && lng <= 180);

  return { id: String(id), lat, lng };
});

assert.equal(new Set(points.map(({ id }) => {
  return id;
})).size, points.length);

vm.runInContext(buildSetMarkersScript(points.slice(0, 3), footprintPinImage, true), context);
assert.equal(state.markers.length, 3);
assert.equal(state.bounds[0].length, 3);
assert.equal(state.boundsPadding.join(","), "32,32,32,32");
assert.equal(state.markers[0].options.image.offset.y, footprintPinImage.height / 2);
state.markers[1].events.click();
assert.deepEqual(state.messages.at(-1), { type: "point-pressed", id: points[1].id });
assert.deepEqual(parseKakaoMapMessage(JSON.stringify(state.messages.at(-1))), { type: "point-pressed", id: points[1].id });

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

const clusterContent = new ElementStub();
let zoomClicks = 0;
let clusterCount = 2;

clusterContent.onclick = () => {
  zoomClicks += 1;
};

const clusters = [{ getSize: () => {
  return clusterCount;
},

getClusterMarker: () => {
  return { getContent: () => {
    return clusterContent;
  },

  setContent: (content) => {
    state.clusterContent = content;
  } };
} }];

state.clusterer.events.clustered(clusters);
assert.equal(clusterContent.children.length, 0);
const clusterBadge = { background: "badge-background", foreground: "badge-text", border: "badge-border" };
vm.runInContext(buildSetMarkersScript(points.slice(0, 3), footprintPinImage, true, clusterBadge), context);
assert.equal(state.boundsPadding.join(","), "48,48,48,48");
state.clusterer.events.clustered(clusters);
assert.equal(state.clusterContent, clusterContent);
assert.equal(clusterContent.children.length, 2);
assert.equal(clusterContent.children[0].src, footprintPinImage.uri);
assert.ok(clusterContent.style.cssText.includes("width:70px;height:80px"));
assert.ok(clusterContent.children[0].style.cssText.includes("width:55px;height:55px"));
assert.ok(clusterContent.children[1].style.cssText.includes("top:50px"));
assert.equal(clusterContent.children[1].textContent, "2");
assert.equal(clusterContent.attributes["aria-label"], "발자취 2개 확대");
clusterContent.click();
assert.equal(zoomClicks, 1);
let keyboardPrevented = false;

clusterContent.onkeydown({ key: "Enter",

  preventDefault: () => {
    keyboardPrevented = true;
  } });
assert.equal(keyboardPrevented, true);
assert.equal(zoomClicks, 2);
clusterCount = 1000;
state.clusterer.events.clustered(clusters);
assert.equal(clusterContent.children.length, 2);
assert.equal(clusterContent.children[1].textContent, "999+");
assert.equal(clusterContent.attributes["aria-label"], "발자취 1000개 확대");
console.log("발자취 지도 검사 통과: 핀 선택·필터 갱신·기존 호환·클러스터 모양·개수·확대 클릭 유지");
