export type MapCamera = {
  lat: number;
  lng: number;
  level: number;
};

export type MapMarker = {
  lat: number;
  lng: number;
};

export type KakaoMapFailure = "sdk-load" | "map-init" | "timeout" | "network" | "no-response";

export type MapInteractionKind = "drag" | "zoom";

export type KakaoMapMessage =
  | { type: "ready" }
  | { type: "error"; reason: KakaoMapFailure }
  | { type: "markers-rendered"; count: number; renderMs: number }
  | {
      type: "interaction-perf";
      kind: MapInteractionKind;
      avgFps: number;
      worstFrameMs: number;
      durationMs: number;
      heapMb: number | null;
    };

export type MapPerformanceReport =
  | { type: "markers"; count: number; renderMs: number; totalMs: number }
  | {
      type: "interaction";
      kind: MapInteractionKind;
      avgFps: number;
      worstFrameMs: number;
      durationMs: number;
      heapMb: number | null;
    };

export const KAKAO_JS_SDK_DOMAIN = "http://localhost";

const TILES_TIMEOUT_MS = 15000;
const CLUSTER_MIN_LEVEL = 5;
const MIN_PERF_SAMPLE_FRAMES = 5;
const pageFailureReasons: readonly KakaoMapFailure[] = ["sdk-load", "map-init", "timeout"];

function toScriptLiteral(value: unknown): string {
  return JSON.stringify(value).replace(/</g, "\\u003c");
}

export function buildKakaoMapHtml(javascriptKey: string, camera: MapCamera): string {
  return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
<style>html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; }</style>
</head>
<body>
<div id="map"></div>
<script>
(function () {
  var appKey = ${toScriptLiteral(javascriptKey)};
  var camera = ${toScriptLiteral(camera)};
  var settled = false;
  var map = null;
  var clusterer = null;

  function post(message) {
    if (window.ReactNativeWebView) window.ReactNativeWebView.postMessage(JSON.stringify(message));
  }

  function settle(message) {
    if (settled || !window.ReactNativeWebView) return;
    settled = true;
    clearTimeout(timer);
    post(message);
  }

  var timer = setTimeout(function () {
    settle({ type: "error", reason: "timeout" });
  }, ${TILES_TIMEOUT_MS});

  function heapMb() {
    return performance.memory ? Math.round(performance.memory.usedJSHeapSize / 1048576) : null;
  }

  function afterNextPaint(callback) {
    requestAnimationFrame(function () {
      requestAnimationFrame(callback);
    });
  }

  function watchInteractionPerformance() {
    var sample = null;

    function tick(now) {
      if (!sample) return;
      var frameMs = now - sample.lastFrameAt;
      sample.lastFrameAt = now;
      sample.frames += 1;
      if (frameMs > sample.worstFrameMs) sample.worstFrameMs = frameMs;
      requestAnimationFrame(tick);
    }

    function start(kind) {
      if (sample) return;
      var now = performance.now();
      sample = { kind: kind, startedAt: now, lastFrameAt: now, frames: 0, worstFrameMs: 0 };
      requestAnimationFrame(tick);
    }

    function stop() {
      if (!sample) return;
      var durationMs = performance.now() - sample.startedAt;
      if (sample.frames < ${MIN_PERF_SAMPLE_FRAMES}) {
        sample = null;
        return;
      }
      post({
        type: "interaction-perf",
        kind: sample.kind,
        avgFps: durationMs > 0 ? Math.round((sample.frames * 1000) / durationMs) : 0,
        worstFrameMs: Math.round(sample.worstFrameMs),
        durationMs: Math.round(durationMs),
        heapMb: heapMb()
      });
      sample = null;
    }

    kakao.maps.event.addListener(map, "dragstart", function () { start("drag"); });
    kakao.maps.event.addListener(map, "zoom_start", function () { start("zoom"); });
    kakao.maps.event.addListener(map, "idle", stop);
  }

  window.yeogirokMap = {
    setMarkers: function (points) {
      if (!clusterer) return;
      var startedAt = performance.now();
      clusterer.clear();
      clusterer.addMarkers(points.map(function (point) {
        return new kakao.maps.Marker({ position: new kakao.maps.LatLng(point.lat, point.lng) });
      }));
      afterNextPaint(function () {
        post({ type: "markers-rendered", count: points.length, renderMs: Math.round(performance.now() - startedAt) });
      });
    }
  };

  function createMap() {
    try {
      map = new kakao.maps.Map(document.getElementById("map"), {
        center: new kakao.maps.LatLng(camera.lat, camera.lng),
        level: camera.level
      });
      clusterer = new kakao.maps.MarkerClusterer({ map: map, averageCenter: true, minLevel: ${CLUSTER_MIN_LEVEL} });
      kakao.maps.event.addListener(map, "tilesloaded", function () {
        settle({ type: "ready" });
      });
      watchInteractionPerformance();
    } catch (error) {
      settle({ type: "error", reason: "map-init" });
    }
  }

  var script = document.createElement("script");
  script.src = "https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&libraries=clusterer&appkey=" + encodeURIComponent(appKey);
  script.onerror = function () {
    settle({ type: "error", reason: "sdk-load" });
  };
  script.onload = function () {
    if (!window.kakao || !window.kakao.maps) {
      settle({ type: "error", reason: "sdk-load" });
      return;
    }
    kakao.maps.load(createMap);
  };
  document.head.appendChild(script);
})();
</script>
</body>
</html>`;
}

export function buildSetMarkersScript(markers: readonly MapMarker[]): string {
  const points = markers.map(({ lat, lng }) => ({ lat, lng }));
  return `window.yeogirokMap && window.yeogirokMap.setMarkers(${toScriptLiteral(points)}); true;`;
}

function isPageFailureReason(value: unknown): value is KakaoMapFailure {
  return pageFailureReasons.some((reason) => reason === value);
}

function isInteractionKind(value: unknown): value is MapInteractionKind {
  return value === "drag" || value === "zoom";
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

export function parseKakaoMapMessage(data: string): KakaoMapMessage | null {
  let value: unknown;
  try {
    value = JSON.parse(data);
  } catch {
    return null;
  }
  if (typeof value !== "object" || value === null || !("type" in value)) return null;

  if (value.type === "ready") return { type: "ready" };
  if (value.type === "error" && "reason" in value && isPageFailureReason(value.reason)) {
    return { type: "error", reason: value.reason };
  }
  if (
    value.type === "markers-rendered" &&
    "count" in value &&
    "renderMs" in value &&
    isFiniteNumber(value.count) &&
    isFiniteNumber(value.renderMs)
  ) {
    return { type: "markers-rendered", count: value.count, renderMs: value.renderMs };
  }
  if (
    value.type === "interaction-perf" &&
    "kind" in value &&
    "avgFps" in value &&
    "worstFrameMs" in value &&
    "durationMs" in value &&
    "heapMb" in value &&
    isInteractionKind(value.kind) &&
    isFiniteNumber(value.avgFps) &&
    isFiniteNumber(value.worstFrameMs) &&
    isFiniteNumber(value.durationMs) &&
    (value.heapMb === null || isFiniteNumber(value.heapMb))
  ) {
    return {
      type: "interaction-perf",
      kind: value.kind,
      avgFps: value.avgFps,
      worstFrameMs: value.worstFrameMs,
      durationMs: value.durationMs,
      heapMb: value.heapMb,
    };
  }
  return null;
}
