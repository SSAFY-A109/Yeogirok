import { Platform } from "react-native";

export async function loadKakaoMap(nativeAppKey: string) {
  const { KakaoMap, KakaoMapView } = await import("@react-native-kakao/map");
  await KakaoMap.initializeKakaoMapSDK(nativeAppKey);

  let keyHash: string | undefined;
  if (Platform.OS === "android") {
    const { getKeyHashAndroid } = await import("@react-native-kakao/core");
    const [result] = await Promise.allSettled([getKeyHashAndroid()]);
    if (result.status === "fulfilled") keyHash = result.value;
  }

  return { MapView: KakaoMapView, keyHash };
}
