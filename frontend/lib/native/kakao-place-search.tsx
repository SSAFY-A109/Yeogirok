import { useCallback, useEffect, useMemo, useRef } from "react";
import { Platform, View } from "react-native";
import { WebView } from "react-native-webview";

import { KAKAO_JS_SDK_DOMAIN } from "@/lib/native/map";

export type KakaoPlace = {
  name: string;
  address: string | null;
  latitude: number;
  longitude: number;
};

const SEARCH_TIMEOUT_MS = 15000;

export function buildKakaoPlaceSearchHtml(javascriptKey: string, keyword: string): string {
  const encodedKeyword = JSON.stringify(encodeURIComponent(keyword));
  const sdkUrl = `https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&libraries=services&appkey=${encodeURIComponent(javascriptKey)}`;

  return `<!doctype html><meta charset="utf-8"><script>
const keyword = decodeURIComponent(${encodedKeyword});
function post(items) {
  const message = JSON.stringify({ type: "kakao-place-search", items });
  if (window.ReactNativeWebView) window.ReactNativeWebView.postMessage(message);
  else window.parent.postMessage(message, "*");
}
function search() {
  const { Geocoder, Places, Status } = kakao.maps.services;
  const geocoder = new Geocoder();
  const places = new Places();
  function query(find, toPlace) {
    return new Promise((resolve) => {
      find(keyword, (rows, status) => {
        if (status === Status.OK && Array.isArray(rows)) resolve(rows.map(toPlace));
        else resolve(status === Status.ZERO_RESULT ? [] : null);
      }, { size: 10 });
    });
  }
  Promise.all([
    query(geocoder.addressSearch.bind(geocoder), ({ address_name, road_address, x, y }) => {
      const address = road_address?.address_name || address_name;
      return { name: address, address, latitude: Number(y || NaN), longitude: Number(x || NaN) };
    }),
    query(places.keywordSearch.bind(places), ({ place_name, address_name, road_address_name, x, y }) => ({
      name: place_name, address: road_address_name || address_name || null,
      latitude: Number(y || NaN), longitude: Number(x || NaN),
    })),
  ]).then(([addresses, matches]) => {
    const items = [...(addresses ?? []), ...(matches ?? [])];
    post(items.length || (addresses && matches) ? items : null);
  }).catch(() => post(null));
}
const script = document.createElement("script");
script.src = ${JSON.stringify(sdkUrl)};
script.onerror = () => post(null);
script.onload = () => {
  if (!window.kakao?.maps) { post(null); return; }
  kakao.maps.load(() => {
    try { search(); } catch { post(null); }
  });
};
document.head.appendChild(script);
</script>`;
}

export function parseKakaoPlaceSearchMessage(data: string): KakaoPlace[] | null | undefined {
  try {
    const message: { type?: string; items?: unknown } | null = JSON.parse(data);

    if (message?.type !== "kakao-place-search") {
      return undefined;
    }

    if (!Array.isArray(message.items)) {
      return null;
    }

    const places = message.items.filter((item: KakaoPlace | null) => {
      return item && typeof item.name === "string" && Boolean(item.name.trim()) &&
        (item.address === null || typeof item.address === "string") &&
        Number.isFinite(item.latitude) && Math.abs(item.latitude) <= 90 &&
        Number.isFinite(item.longitude) && Math.abs(item.longitude) <= 180;
    });

    return places.length > 0 || message.items.length === 0 ? places : null;
  } catch {
    return undefined;
  }
}

export function KakaoPlaceSearch({ javascriptKey, keyword, onResult }: {
  javascriptKey: string; keyword: string; onResult: (places: KakaoPlace[] | null) => void;
}) {
  const frame = useRef<HTMLIFrameElement>(null);
  const completed = useRef(false);

  const source = useMemo(() => {
    return { html: buildKakaoPlaceSearchHtml(javascriptKey, keyword), baseUrl: KAKAO_JS_SDK_DOMAIN };
  }, [javascriptKey, keyword]);

  const settle = useCallback((result: KakaoPlace[] | null) => {
    if (!completed.current) {
      completed.current = true;
      onResult(result);
    }
  }, [onResult]);

  useEffect(() => {
    const timer = setTimeout(() => {
      settle(null);
    }, SEARCH_TIMEOUT_MS);

    function onMessage(event: MessageEvent) {
      if (event.source === frame.current?.contentWindow && typeof event.data === "string") {
        const result = parseKakaoPlaceSearchMessage(event.data);

        if (result !== undefined) {
          settle(result);
        }
      }
    }

    if (Platform.OS === "web") {
      window.addEventListener("message", onMessage);
    }

    return () => {
      clearTimeout(timer);

      if (Platform.OS === "web") {
        window.removeEventListener("message", onMessage);
      }
    };
  }, [settle]);

  if (Platform.OS === "web") {
    return <iframe ref={frame} title="카카오 장소 검색" srcDoc={source.html} hidden aria-hidden tabIndex={-1} />;
  }

  return <View className="absolute overflow-hidden" style={{ width: 1, height: 1, opacity: 0 }}
    pointerEvents="none" importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
    <WebView source={source} originWhitelist={["*"]} onShouldStartLoadWithRequest={({ url }) => {
      return url === "about:blank" || url === `${KAKAO_JS_SDK_DOMAIN}/` || url === KAKAO_JS_SDK_DOMAIN;
    }} onMessage={({ nativeEvent }) => {
      const result = parseKakaoPlaceSearchMessage(nativeEvent.data);

      if (result !== undefined) {
        settle(result);
      }
    }} onError={() => {
      settle(null);
    }} />
  </View>;
}
