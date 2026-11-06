import { useEffect, useEffectEvent, useMemo, useRef } from "react";
import { WebView, type WebViewMessageEvent } from "react-native-webview";

import {
  buildFocusScript,
  buildKakaoMapHtml,
  buildSetCurrentLocationScript,
  buildSetFootprintsScript,
  buildSetMarkersScript,
  buildSetRouteScript,
  KAKAO_JS_SDK_DOMAIN,
  parseKakaoMapMessage,
  type KakaoMapFailure,
  type MapCamera,
  type MapCurrentLocation,
  type MapCurrentLocationStyle,
  type MapFocusRequest,
  type MapFootprint,
  type MapFootprintStyle,
  type MapMarker,
  type MapMarkerImage,
  type MapPerformanceReport,
  type MapRegion,
  type MapRoute,
} from "./kakao-map-bridge";

export {
  KAKAO_JS_SDK_DOMAIN,
  type KakaoMapFailure,
  type MapCamera,
  type MapCurrentLocation,
  type MapCurrentLocationStyle,
  type MapFocusRequest,
  type MapFootprint,
  type MapFootprintStyle,
  type MapMarker,
  type MapMarkerImage,
  type MapPerformanceReport,
  type MapRegion,
  type MapRoute,
  type MapRouteColors,
  type MapRoutePoint,
} from "./kakao-map-bridge";

export { footprintPinImage } from "./footprint-pin";
export { placePinImage } from "./place-pin";

const NO_RESPONSE_TIMEOUT_MS = 20000;

type KakaoMapViewProps = {
  javascriptKey: string;
  initialCamera: MapCamera;
  markers?: readonly MapMarker[];
  markerImage?: MapMarkerImage;
  route?: MapRoute;
  footprints?: readonly MapFootprint[];
  footprintStyle?: MapFootprintStyle;
  currentLocation?: MapCurrentLocation | null;
  currentLocationStyle?: MapCurrentLocationStyle;
  focusRequest?: MapFocusRequest | null;
  onPressPoint?: (id: string) => void;
  onPressMap?: () => void;
  onRegionChange?: (region: MapRegion) => void;
  onReady: () => void;
  onError: (reason: KakaoMapFailure) => void;
  onPerformance?: (report: MapPerformanceReport) => void;
  accessibilityLabel?: string;
};

export function KakaoMapView({
  javascriptKey,
  initialCamera,
  markers,
  markerImage,
  route,
  footprints,
  footprintStyle,
  currentLocation,
  currentLocationStyle,
  focusRequest,
  onPressPoint,
  onPressMap,
  onRegionChange,
  onReady,
  onError,
  onPerformance,
  accessibilityLabel,
}: KakaoMapViewProps) {
  const source = useMemo(
    () => {
      return { html: buildKakaoMapHtml(javascriptKey, initialCamera), baseUrl: KAKAO_JS_SDK_DOMAIN };
    },
    [javascriptKey, initialCamera],
  );

  const webViewRef = useRef<WebView<object>>(null);
  const hasResponded = useRef(false);
  const isMapReady = useRef(false);
  const markersSentAt = useRef(0);
  const routeSignature = useRef("");
  const appliedFocusId = useRef<number | null>(null);

  const reportNoResponse = useEffectEvent(() => {
    if (!hasResponded.current) {
      onError("no-response");
    }
  });

  useEffect(() => {
    const timer = setTimeout(reportNoResponse, NO_RESPONSE_TIMEOUT_MS);

    return () => {
      return clearTimeout(timer);
    };
  }, []);

  function sendMarkers(nextMarkers: readonly MapMarker[], image: MapMarkerImage | undefined) {
    markersSentAt.current = Date.now();
    webViewRef.current?.injectJavaScript(buildSetMarkersScript(nextMarkers, image));
  }

  useEffect(() => {
    if (isMapReady.current && markers) {
      sendMarkers(markers, markerImage);
    }
  }, [markers, markerImage]);

  function sendRoute(nextRoute: MapRoute) {
    const signature = nextRoute.points.map(({ id }) => {
      return id;
    }).join(",");

    webViewRef.current?.injectJavaScript(buildSetRouteScript(nextRoute, signature !== routeSignature.current));
    routeSignature.current = signature;
  }

  useEffect(() => {
    if (isMapReady.current && route) {
      sendRoute(route);
    }
  }, [route]);

  function sendFootprints(nextFootprints: readonly MapFootprint[], style: MapFootprintStyle) {
    webViewRef.current?.injectJavaScript(buildSetFootprintsScript(nextFootprints, style));
  }

  useEffect(() => {
    if (isMapReady.current && footprints && footprintStyle) {
      sendFootprints(footprints, footprintStyle);
    }
  }, [footprints, footprintStyle]);

  function sendCurrentLocation(location: MapCurrentLocation | null, style: MapCurrentLocationStyle) {
    webViewRef.current?.injectJavaScript(buildSetCurrentLocationScript(location, style));
  }

  useEffect(() => {
    if (isMapReady.current && currentLocationStyle && currentLocation !== undefined) {
      sendCurrentLocation(currentLocation, currentLocationStyle);
    }
  }, [currentLocation, currentLocationStyle]);

  function sendFocus(request: MapFocusRequest) {
    if (appliedFocusId.current === request.id) {
      return;
    }

    appliedFocusId.current = request.id;
    webViewRef.current?.injectJavaScript(buildFocusScript(request));
  }

  useEffect(() => {
    if (isMapReady.current && focusRequest) {
      sendFocus(focusRequest);
    }
  }, [focusRequest]);

  function handleMessage(event: WebViewMessageEvent) {
    const message = parseKakaoMapMessage(event.nativeEvent.data);

    if (!message) {
      return;
    }

    hasResponded.current = true;

    if (message.type === "ready") {
      isMapReady.current = true;

      if (markers) {
        sendMarkers(markers, markerImage);
      }

      if (route) {
        sendRoute(route);
      }

      if (footprints && footprintStyle) {
        sendFootprints(footprints, footprintStyle);
      }

      if (currentLocation && currentLocationStyle) {
        sendCurrentLocation(currentLocation, currentLocationStyle);
      }

      if (focusRequest) {
        sendFocus(focusRequest);
      }

      onReady();
    }

    if (message.type === "error") {
      onError(message.reason);
    }

    if (message.type === "point-pressed") {
      onPressPoint?.(message.id);
    }

    if (message.type === "map-pressed") {
      onPressMap?.();
    }

    if (message.type === "region-changed") {
      onRegionChange?.(message.region);
    }

    if (message.type === "markers-rendered") {
      onPerformance?.({
        type: "markers",
        count: message.count,
        renderMs: message.renderMs,
        totalMs: Date.now() - markersSentAt.current,
      });
    }

    if (message.type === "interaction-perf") {
      onPerformance?.({
        type: "interaction",
        kind: message.kind,
        avgFps: message.avgFps,
        worstFrameMs: message.worstFrameMs,
        durationMs: message.durationMs,
        heapMb: message.heapMb,
      });
    }
  }

  return (
    <WebView
      ref={webViewRef}
      accessibilityLabel={accessibilityLabel}
      bounces={false}
      nestedScrollEnabled
      onError={() => {
        hasResponded.current = true;
        onError("network");
      }}
      onMessage={handleMessage}
      scrollEnabled={false}
      source={source}
      webviewDebuggingEnabled={__DEV__}
    />
  );
}
