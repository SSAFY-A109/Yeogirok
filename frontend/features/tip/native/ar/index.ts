import { NativeModules, Platform } from "react-native";

import { isObject, isPlacementExpired, isTipType, parseLocalTip, parseLocalTips, parsePlacement, parsePhoto, parseTipDetail, parseTipReaction, type CloudPlacement, type TipPhoto, type TipType } from "./contracts";

type GeoBridge = {
  getStatus: () => Promise<unknown>;
  openTipPlacement: () => Promise<unknown>;
  openTipExplorer: (selectedId: string | null) => Promise<unknown>;
  openCloudMap: (raw: string) => Promise<unknown>;
};
type TipBridge = {
  listTips: () => Promise<unknown>;
  saveTip: (raw: string) => Promise<unknown>;
  openTipAR: (id: string) => Promise<unknown>;
  pickPhoto: () => Promise<unknown>;
  capturePhoto: () => Promise<unknown>;
  getTipDetail?: (id: string) => Promise<unknown>;
  toggleTipReaction?: (id: string, reaction: string) => Promise<unknown>;
  closeTipDetail?: () => Promise<unknown>;
};

function isGeoBridge(value: unknown): value is GeoBridge {
  return isObject(value) && typeof value.getStatus === "function" &&
    typeof value.openTipPlacement === "function" && typeof value.openTipExplorer === "function" && typeof value.openCloudMap === "function";
}

function isTipBridge(value: unknown): value is TipBridge {
  return isObject(value) && typeof value.listTips === "function" &&
    typeof value.saveTip === "function" && typeof value.openTipAR === "function";
}

function bridges() {
  if (Platform.OS !== "android") {
    throw new Error("UNSUPPORTED_PLATFORM");
  }

  const geo: unknown = NativeModules.GeoValidation;
  const tips: unknown = NativeModules.TipExperience;

  if (!isGeoBridge(geo) || !isTipBridge(tips)) {
    throw new Error("NATIVE_MODULE_MISSING");
  }

  return { geo, tips };
}

export async function loadArTips() {
  const { geo, tips } = bridges();
  const status = await geo.getStatus();

  if (!isObject(status) || typeof status.arcoreAuthMode !== "string" || typeof status.kakaoKeyConfigured !== "boolean") {
    throw new Error("INVALID_RESULT");
  }

  return {
    arcoreConfigured: status.arcoreAuthMode === "KEYLESS",
    mapConfigured: status.kakaoKeyConfigured,
    tips: parseLocalTips(await tips.listTips()),
  };
}

export async function loadTipDetail(id: string) {
  const { tips } = bridges();

  if (!tips.getTipDetail) {
    throw new Error("NATIVE_MODULE_MISSING");
  }

  const detail = parseTipDetail(await tips.getTipDetail(id));

  if (detail.tip.id !== id) {
    throw new Error("INVALID_RESULT");
  }

  return detail;
}

export async function toggleTipReaction(id: string, reaction: "LIKE" | "DISLIKE") {
  const { tips } = bridges();

  if (!tips.toggleTipReaction) {
    throw new Error("NATIVE_MODULE_MISSING");
  }

  return parseTipReaction(await tips.toggleTipReaction(id, reaction));
}

export async function closeTipDetail() {
  const { tips } = bridges();

  if (!tips.closeTipDetail) {
    throw new Error("NATIVE_MODULE_MISSING");
  }

  await tips.closeTipDetail();
}

export async function placeTip(persistMarker = true): Promise<CloudPlacement | null> {
  const result = await bridges().geo.openTipPlacement();
  return keepLocalPlacement(result, persistMarker);
}

export async function exploreTips(selectedId: string | null = null, persistMarker = true): Promise<CloudPlacement | null> {
  const result = await bridges().geo.openTipExplorer(selectedId);
  return keepLocalPlacement(result, persistMarker);
}

async function keepLocalPlacement(result: unknown, persistMarker: boolean): Promise<CloudPlacement | null> {
  if (result === null) {
    return null;
  }

  const placement = parsePlacement(result);

  if (isPlacementExpired(placement)) {
    throw new Error("ANCHOR_EXPIRED");
  }

  if (!persistMarker) {
    return placement;
  }

  const { tips } = bridges();
  const existing = parseLocalTips(await tips.listTips());

  if (!existing.some((tip) => {
    return tip.placement.anchorId === placement.anchorId;
  })) {
    const saved = parseLocalTip(await tips.saveTip(JSON.stringify({
      title: "임시 AR 마커",
      body: "위치 복원 테스트용 임시 마커",
      cloudRecord: JSON.parse(placement.raw),
    })));

    if (saved.placement.anchorId !== placement.anchorId) {
      throw new Error("INVALID_RESULT");
    }
  }

  return placement;
}

export async function chooseDiscoveryLocation(placement: CloudPlacement): Promise<CloudPlacement | null> {
  const result = await bridges().geo.openCloudMap(placement.raw);
  return result === null ? null : parsePlacement(result);
}

export async function addTipPhoto(source: "camera" | "library"): Promise<TipPhoto | null> {
  const { tips } = bridges();
  const action = source === "camera" ? tips.capturePhoto : tips.pickPhoto;

  if (typeof action !== "function") {
    throw new Error("NATIVE_MODULE_MISSING");
  }

  const result = await action.call(tips);
  return result === null ? null : parsePhoto(result);
}

export async function saveLocalTip(body: string, placement: CloudPlacement, photo?: TipPhoto | null) {
  if (!body.trim()) {
    throw new Error("BODY_REQUIRED");
  }

  if (body.trim().length > 2000) {
    throw new Error("BODY_TOO_LONG");
  }

  if (!placement.hasDiscoveryLocation && placement.visitAccuracyMeters == null) {
    throw new Error("LOCATION_REQUIRED");
  }

  if (isPlacementExpired(placement)) {
    throw new Error("ANCHOR_EXPIRED");
  }

  const cloudRecord: unknown = JSON.parse(placement.raw);
  return parseLocalTip(await bridges().tips.saveTip(JSON.stringify({ body: body.trim(), cloudRecord, ...(photo !== undefined ? { photoName: photo?.photoName ?? null } : {}) })));
}

export async function saveTipDraft(body: string, placement: CloudPlacement, photo: TipPhoto | null, tipType: TipType | null) {
  if (!isTipType(tipType)) {
    throw new Error("TYPE_REQUIRED");
  }

  if (!photo) {
    throw new Error("PHOTO_REQUIRED");
  }

  if (body.length > 120) {
    throw new Error("DRAFT_BODY_TOO_LONG");
  }

  if (isPlacementExpired(placement)) {
    throw new Error("ANCHOR_EXPIRED");
  }

  return parseLocalTip(await bridges().tips.saveTip(JSON.stringify({
    draft: true, tipType, title: tipType, body: body.trim(), photoName: photo.photoName,
    cloudRecord: JSON.parse(placement.raw),
  })));
}

export function arErrorMessage(error: unknown): string {
  const code = isObject(error) && typeof error.code === "string" ? error.code : error instanceof Error ? error.message : "";

  switch (code) {
    case "UNSUPPORTED_PLATFORM": return "AR 팁은 현재 Android 앱에서 사용할 수 있어요. 다른 탭에서 여행을 계속해 주세요.";
    case "NATIVE_MODULE_MISSING": return "AR 모듈이 아직 연결되지 않았어요. Kotlin 모듈을 포함한 Android 개발 빌드가 필요해요.";
    case "BUSY": return "다른 AR 작업이 진행 중이에요. 화면을 닫은 뒤 다시 시도해 주세요.";
    case "KAKAO_KEY_MISSING": return "지도 설정이 필요해요. 카카오 JavaScript 키와 웹 플랫폼의 localhost 등록을 확인하고 개발 앱을 다시 빌드해 주세요.";
    case "BODY_REQUIRED": return "팁 내용을 입력해 주세요.";
    case "TYPE_REQUIRED": return "팁 종류를 선택해 주세요.";
    case "PHOTO_REQUIRED": return "팁 사진을 첨부해 주세요.";
    case "DRAFT_BODY_TOO_LONG": return "팁 내용은 120자까지 입력할 수 있어요.";
    case "BODY_TOO_LONG": return "이번 PoC는 팁 내용을 2,000자까지 저장할 수 있어요.";
    case "PHOTO_FAILED": return "사진을 불러오지 못했어요. 20MB 이하의 다른 사진을 선택해 주세요.";
    case "CAPTURE_FAILED": return "카메라를 열지 못했어요. 사진 선택으로 다시 시도해 주세요.";
    case "LOCATION_REQUIRED": return "지도에 탐색 위치를 먼저 찍어 주세요.";
    case "ANCHOR_EXPIRED": return "공간 정보의 예상 유효 시간이 지났어요. 현장에서 자리를 다시 저장해 주세요.";
    case "TIP_UNAVAILABLE": return "이 팁을 불러올 수 없어요. 목록을 다시 확인해 주세요.";
    case "INVALID_RESULT":
    case "INVALID_RECORD": return "공간 정보를 확인하지 못했어요. 다시 불러오거나 자리를 다시 저장해 주세요.";
    case "SAVE_FAILED": return "팁을 저장하지 못했어요. 입력한 내용은 유지되니 다시 시도해 주세요.";
    default: return "AR 작업을 완료하지 못했어요. 권한과 네트워크를 확인한 뒤 다시 시도해 주세요.";
  }
}
