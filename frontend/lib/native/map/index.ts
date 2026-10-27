import type { loadKakaoMap as loadNativeKakaoMap } from "./index.native";

export const loadKakaoMap: typeof loadNativeKakaoMap = async () => {
  throw new Error("네이티브 카카오맵은 Android 또는 iOS 개발 빌드에서 실행해주세요.");
};
