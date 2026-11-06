export type MapCamera = {
  lat: number;
  lng: number;
  level: number;
};

export type MapMarker = {
  id?: string;
  lat: number;
  lng: number;
};

/** WebView에서 읽을 data URI 이미지. 기본은 아래 가운데를 좌표에 맞추며 offset으로 기준점을 바꿀 수 있다. */
export type MapMarkerImage = {
  uri: string;
  width: number;
  height: number;
  offset?: { x: number; y: number };
};

export type MapClusterBadge = {
  background: string;
  foreground: string;
  border: string;
};

/** 코스 경로의 한 지점. order는 코스 안 순서(1부터)이고, selected는 핀 강조 여부다. */
export type MapRoutePoint = {
  id: string;
  lat: number;
  lng: number;
  order: number;
  selected: boolean;
};

export type MapRouteColors = {
  line: string;
  pinSelected: string;
  pinSelectedText: string;
  pinDefault: string;
  pinDefaultText: string;
  pinBorder: string;
  pinSelectedBorder: string;
};

export type MapRoute = {
  points: readonly MapRoutePoint[];
  colors: MapRouteColors;
};

export type KakaoMapFailure = "sdk-load" | "map-init" | "timeout" | "network" | "no-response";

export type MapInteractionKind = "drag" | "zoom";

export type KakaoMapMessage =
  | { type: "ready" }
  | { type: "error"; reason: KakaoMapFailure }
  | { type: "markers-rendered"; count: number; renderMs: number }
  | { type: "point-pressed"; id: string }
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
  var clusterImage = null;
  var clusterBadge = null;
  var routeLine = null;
  var routeOverlays = [];
  var anchor = null;

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
    setMarkers: function (points, image, fit, badge) {
      if (!clusterer) return;
      clusterImage = image;
      clusterBadge = badge;
      var startedAt = performance.now();
      var markerImage = image
        ? new kakao.maps.MarkerImage(image.uri, new kakao.maps.Size(image.width, image.height), { offset: new kakao.maps.Point(image.offset ? image.offset.x : image.width / 2, image.offset ? image.offset.y : image.height) })
        : null;
      clusterer.clear();
      clusterer.addMarkers(points.map(function (point) {
        var options = { position: new kakao.maps.LatLng(point.lat, point.lng) };
        if (markerImage) options.image = markerImage;
        var marker = new kakao.maps.Marker(options);
        if (typeof point.id === "string") {
          kakao.maps.event.addListener(marker, "click", function () {
            post({ type: "point-pressed", id: point.id });
          });
        }
        return marker;
      }));
      if (fit && points.length === 1) {
        map.setCenter(new kakao.maps.LatLng(points[0].lat, points[0].lng));
      } else if (fit && points.length > 1) {
        var bounds = new kakao.maps.LatLngBounds();
        points.forEach(function (point) { bounds.extend(new kakao.maps.LatLng(point.lat, point.lng)); });
        var padding = badge ? 48 : 32;
        map.setBounds(bounds, padding, padding, padding, padding);
      }
      afterNextPaint(function () {
        post({ type: "markers-rendered", count: points.length, renderMs: Math.round(performance.now() - startedAt) });
      });
    }
  };

  function styleFootprintClusters(clusters) {
    if (!clusterImage || !clusterBadge) return;
    clusters.forEach(function (cluster) {
      var count = cluster.getSize();
      if (count < 2) return;
      var marker = cluster.getClusterMarker();
      var content = marker.getContent();
      content.style.cssText = "position:relative;width:70px;height:80px;box-sizing:border-box;background:transparent;cursor:pointer;";
      content.setAttribute("role", "button");
      content.setAttribute("aria-label", "발자취 " + count + "개 확대");
      content.tabIndex = 0;
      content.onkeydown = function (event) {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          content.click();
        }
      };
      var paw = document.createElement("img");
      paw.src = clusterImage.uri;
      paw.alt = "";
      paw.style.cssText = "position:absolute;left:7.5px;top:1.25px;width:55px;height:55px;object-fit:contain;pointer-events:none;";
      var badge = document.createElement("span");
      badge.textContent = count > 999 ? "999+" : String(count);
      badge.style.cssText = "position:absolute;left:7.5px;top:50px;width:55px;height:27.5px;box-sizing:border-box;" +
        "display:flex;align-items:center;justify-content:center;border:2px solid " + clusterBadge.border +
        ";border-radius:9999px;background:" + clusterBadge.background + ";color:" + clusterBadge.foreground +
        ";font:500 12px/16px 'Noto Sans KR',sans-serif;pointer-events:none;";
      content.textContent = "";
      content.appendChild(paw);
      content.appendChild(badge);
      marker.setContent(content);
    });
  }

  function pinElement(point, colors) {
    var wrapper = document.createElement("div");
    wrapper.style.cssText = "width:44px;height:44px;display:flex;align-items:center;justify-content:center;";
    var pin = document.createElement("div");
    pin.textContent = String(point.order);
    pin.style.cssText = "width:26px;height:26px;border-radius:13px;box-sizing:border-box;border:2px solid " +
      (point.selected ? colors.pinSelectedBorder : colors.pinBorder) +
      ";background:" + (point.selected ? colors.pinSelected : colors.pinDefault) +
      ";color:" + (point.selected ? colors.pinSelectedText : colors.pinDefaultText) +
      ";font:500 12px/22px sans-serif;text-align:center;";
    wrapper.appendChild(pin);
    wrapper.addEventListener("click", function () {
      post({ type: "point-pressed", id: point.id });
    });
    return wrapper;
  }

  window.yeogirokMap.setRoute = function (route, fit) {
    if (!map) return;
    if (routeLine) routeLine.setMap(null);
    routeOverlays.forEach(function (overlay) { overlay.setMap(null); });
    routeOverlays = [];
    var path = route.points.map(function (point) { return new kakao.maps.LatLng(point.lat, point.lng); });
    routeLine = new kakao.maps.Polyline({
      path: path,
      strokeWeight: 4,
      strokeColor: route.colors.line,
      strokeOpacity: 0.95,
      strokeStyle: "solid"
    });
    routeLine.setMap(map);
    route.points.forEach(function (point, index) {
      var overlay = new kakao.maps.CustomOverlay({
        position: path[index],
        content: pinElement(point, route.colors),
        xAnchor: 0.5,
        yAnchor: 0.5,
        zIndex: point.selected ? 100 + point.order : point.order
      });
      overlay.setMap(map);
      routeOverlays.push(overlay);
    });
    if (fit && path.length > 0) {
      var bounds = new kakao.maps.LatLngBounds();
      path.forEach(function (position) { bounds.extend(position); });
      map.setBounds(bounds, 48, 48, 48, 48);
      anchor = map.getCenter();
    }
  };

  function recenterOnAnchor() {
    map.relayout();
    map.setCenter(anchor);
  }

  function keepCenterOnResize(container) {
    var lastWidth = container.clientWidth;
    var lastHeight = container.clientHeight;
    var userMoving = false;

    kakao.maps.event.addListener(map, "dragstart", function () { userMoving = true; });
    kakao.maps.event.addListener(map, "zoom_start", function () { userMoving = true; });
    kakao.maps.event.addListener(map, "idle", function () {
      if (!userMoving) return;
      userMoving = false;
      anchor = map.getCenter();
    });

    function handleResize() {
      var width = container.clientWidth;
      var height = container.clientHeight;
      if (width === 0 || height === 0 || (width === lastWidth && height === lastHeight)) return;
      lastWidth = width;
      lastHeight = height;
      recenterOnAnchor();
    }

    if (window.ResizeObserver) {
      new ResizeObserver(handleResize).observe(container);
    } else {
      window.addEventListener("resize", handleResize);
    }
  }

  function createMap() {
    try {
      var container = document.getElementById("map");
      anchor = new kakao.maps.LatLng(camera.lat, camera.lng);
      map = new kakao.maps.Map(container, { center: anchor, level: camera.level });
      clusterer = new kakao.maps.MarkerClusterer({ map: map, averageCenter: true, minLevel: ${CLUSTER_MIN_LEVEL} });
      kakao.maps.event.addListener(clusterer, "clustered", styleFootprintClusters);
      kakao.maps.event.addListener(map, "tilesloaded", function () {
        if (!settled) recenterOnAnchor();
        settle({ type: "ready" });
      });
      keepCenterOnResize(container);
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

export function buildSetRouteScript(route: MapRoute, fit: boolean): string {
  const points = route.points.map(({ id, lat, lng, order, selected }) => {
    return { id, lat, lng, order, selected };
  });

  return `window.yeogirokMap && window.yeogirokMap.setRoute(${toScriptLiteral({ points, colors: route.colors })}, ${fit ? "true" : "false"}); true;`;
}

export function buildSetMarkersScript(markers: readonly MapMarker[], image?: MapMarkerImage, fit = false, clusterBadge?: MapClusterBadge): string {
  const points = markers.map(({ id, lat, lng }) => {
    return { id, lat, lng };
  });

  return `window.yeogirokMap && window.yeogirokMap.setMarkers(${toScriptLiteral(points)}, ${toScriptLiteral(image ?? null)}, ${fit ? "true" : "false"}, ${toScriptLiteral(clusterBadge ?? null)}); true;`;
}

function isPageFailureReason(value: unknown): value is KakaoMapFailure {
  return pageFailureReasons.some((reason) => {
    return reason === value;
  });
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

  if (typeof value !== "object" || value === null || !("type" in value)) {
    return null;
  }

  if (value.type === "ready") {
    return { type: "ready" };
  }

  if (value.type === "point-pressed" && "id" in value && typeof value.id === "string") {
    return { type: "point-pressed", id: value.id };
  }

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
