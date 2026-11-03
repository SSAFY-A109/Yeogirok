import type { ComponentProps } from "react";

import type { KakaoMapView as NativeKakaoMapView } from "./index.native";

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

export const KakaoMapView: (props: ComponentProps<typeof NativeKakaoMapView>) => null = () => {
  return null;
};
