import { useEffect, useEffectEvent, useMemo, useRef } from "react";
import { WebView, type WebViewMessageEvent } from "react-native-webview";

import {
  buildKakaoMapHtml,
  buildSetMarkersScript,
  KAKAO_JS_SDK_DOMAIN,
  parseKakaoMapMessage,
  type KakaoMapFailure,
  type MapCamera,
  type MapMarker,
  type MapPerformanceReport,
} from "./kakao-map-bridge";

export {
  KAKAO_JS_SDK_DOMAIN,
  type KakaoMapFailure,
  type MapCamera,
  type MapMarker,
  type MapPerformanceReport,
} from "./kakao-map-bridge";

const NO_RESPONSE_TIMEOUT_MS = 20000;

type KakaoMapViewProps = {
  javascriptKey: string;
  initialCamera: MapCamera;
  markers?: readonly MapMarker[];
  onReady: () => void;
  onError: (reason: KakaoMapFailure) => void;
  onPerformance?: (report: MapPerformanceReport) => void;
  accessibilityLabel?: string;
};

export function KakaoMapView({
  javascriptKey,
  initialCamera,
  markers,
  onReady,
  onError,
  onPerformance,
  accessibilityLabel,
}: KakaoMapViewProps) {
  const source = useMemo(
    () => ({ html: buildKakaoMapHtml(javascriptKey, initialCamera), baseUrl: KAKAO_JS_SDK_DOMAIN }),
    [javascriptKey, initialCamera],
  );
  const webViewRef = useRef<WebView<object>>(null);
  const hasResponded = useRef(false);
  const isMapReady = useRef(false);
  const markersSentAt = useRef(0);

  const reportNoResponse = useEffectEvent(() => {
    if (!hasResponded.current) onError("no-response");
  });
  useEffect(() => {
    const timer = setTimeout(reportNoResponse, NO_RESPONSE_TIMEOUT_MS);
    return () => clearTimeout(timer);
  }, []);

  function sendMarkers(nextMarkers: readonly MapMarker[]) {
    markersSentAt.current = Date.now();
    webViewRef.current?.injectJavaScript(buildSetMarkersScript(nextMarkers));
  }

  useEffect(() => {
    if (isMapReady.current && markers) sendMarkers(markers);
  }, [markers]);

  function handleMessage(event: WebViewMessageEvent) {
    const message = parseKakaoMapMessage(event.nativeEvent.data);
    if (!message) return;
    hasResponded.current = true;

    if (message.type === "ready") {
      isMapReady.current = true;
      if (markers) sendMarkers(markers);
      onReady();
    }
    if (message.type === "error") onError(message.reason);
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
