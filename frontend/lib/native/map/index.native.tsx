import { useCallback, useEffect, useEffectEvent, useMemo, useRef } from "react";
import { WebView, type WebViewMessageEvent } from "react-native-webview";

import {
  buildKakaoMapHtml,
  buildSetMarkersScript,
  buildSetRouteScript,
  KAKAO_JS_SDK_DOMAIN,
  parseKakaoMapMessage,
  type KakaoMapFailure,
  type MapCamera,
  type MapMarker,
  type MapMarkerImage,
  type MapPerformanceReport,
  type MapRoute,
} from "./kakao-map-bridge";

export {
  KAKAO_JS_SDK_DOMAIN,
  type KakaoMapFailure,
  type MapCamera,
  type MapMarker,
  type MapMarkerImage,
  type MapPerformanceReport,
  type MapRoute,
  type MapRouteColors,
  type MapRoutePoint,
} from "./kakao-map-bridge";

export { placePinImage } from "./place-pin";

const NO_RESPONSE_TIMEOUT_MS = 20000;

type KakaoMapViewProps = {
  javascriptKey: string;
  initialCamera: MapCamera;
  markers?: readonly MapMarker[];
  markerImage?: MapMarkerImage;
  fitToMarkers?: boolean;
  route?: MapRoute;
  onPressPoint?: (id: string) => void;
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
  fitToMarkers = false,
  route,
  onPressPoint,
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
  const markersSignature = useRef("");
  const routeSignature = useRef("");

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

  const sendMarkers = useCallback((nextMarkers: readonly MapMarker[], image: MapMarkerImage | undefined) => {
    const signature = JSON.stringify(nextMarkers);
    markersSentAt.current = Date.now();
    webViewRef.current?.injectJavaScript(buildSetMarkersScript(nextMarkers, image, fitToMarkers && signature !== markersSignature.current));
    markersSignature.current = signature;
  }, [fitToMarkers]);

  useEffect(() => {
    if (isMapReady.current && markers) {
      sendMarkers(markers, markerImage);
    }
  }, [markers, markerImage, sendMarkers]);

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

      onReady();
    }

    if (message.type === "error") {
      onError(message.reason);
    }

    if (message.type === "point-pressed") {
      onPressPoint?.(message.id);
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
