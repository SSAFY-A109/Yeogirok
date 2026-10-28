import type { ComponentProps } from "react";

import type { KakaoMapView as NativeKakaoMapView } from "./index.native";

export {
  KAKAO_JS_SDK_DOMAIN,
  type KakaoMapFailure,
  type MapCamera,
  type MapMarker,
  type MapPerformanceReport,
} from "./kakao-map-bridge";

export const KakaoMapView: (props: ComponentProps<typeof NativeKakaoMapView>) => null = () => null;
