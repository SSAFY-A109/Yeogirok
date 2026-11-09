import { DeviceEventEmitter, NativeModules, PermissionsAndroid, Platform } from "react-native";

import type { UploadFile } from "@/features/media/upload";
import { TIP_TYPE_LABELS, toSpatialReference, validateTipLocation, type LocationEvidenceDto, type TipSearchPage } from "@/features/tip/model";
import { ContractMismatchError } from "@/lib/contract";
import { createIdempotencyKey } from "@/api/idempotency";
import { getTipErrorMessage } from "../../errors";
import { parsePlacement, type CloudPlacement, type TipPhoto } from "./contracts";

type PublicBridge = {
  getCurrentLocation: () => Promise<string>;
  readPhoto: (name: string) => Promise<string>;
};
type ExplorerBridge = {
  openPublicTipExplorer: (raw: string, location: string, sessionId: string) => Promise<string | null>;
  updatePublicTips: (requestId: string, raw: string | null, error: string | null) => Promise<boolean>;
};

function bridge(): PublicBridge {
  const value = NativeModules.TipExperience;

  if (Platform.OS !== "android" || !value?.getCurrentLocation || !value?.readPhoto) {
    throw new Error("NATIVE_MODULE_MISSING");
  }

  return value;
}

export function toServerDateTime(value: string | Date): string {
  const date = new Date(value);

  if (!Number.isFinite(date.getTime())) {
    throw new ContractMismatchError("datetime");
  }

  return new Date(date.getTime() + 9 * 3600_000).toISOString().slice(0, 19).replace("T", " ") + "+09";
}

export async function measureTipLocation(): Promise<LocationEvidenceDto> {
  if (Platform.OS !== "android") {
    throw new Error("UNSUPPORTED_PLATFORM");
  }

  const native = bridge();

  const permission = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION, {
    title: "주변 AR 팁을 위한 현재 위치", message: "50m 안의 팁을 찾고 5m 이내 현장 조건을 확인하는 데 현재 위치를 사용해요.", buttonPositive: "위치 확인",
  });

  if (permission !== PermissionsAndroid.RESULTS.GRANTED) {
    throw new Error("LOCATION_PERMISSION");
  }

  const location = JSON.parse(await native.getCurrentLocation());
  return validateTipLocation({ ...location, capturedAt: toServerDateTime(location.capturedAt) });
}

export function toTipSpatialRequest(placement: CloudPlacement) {
  if (placement.anchorId.length > 255 || !placement.anchorId.trim() || /\s/.test(placement.anchorId)) {
    throw new ContractMismatchError("referenceId");
  }

  const spatialReference = { provider: "ARCORE_CLOUD_ANCHOR", referenceId: placement.anchorId, expiresAt: toServerDateTime(placement.expiresAtEstimate) };
  toSpatialReference(spatialReference);
  return { spatialReference, placement: { schemaVersion: 1, anchorType: "CLOUD", authorizationMode: "KEYLESS" } };
}

function publicTipRecords(page: TipSearchPage): string {
  return JSON.stringify(page.items.slice(0, 10).map((tip) => {
    if (tip.spatialReference.provider !== "ARCORE_CLOUD_ANCHOR" || !tip.spatialReference.expiresAt ||
      tip.placement.schemaVersion !== 1 || tip.placement.anchorType !== "CLOUD" || tip.placement.authorizationMode !== "KEYLESS") {
      throw new ContractMismatchError("placement");
    }

    return {
      id: String(tip.id), title: tip.summary, tipType: tip.tipType === "UNKNOWN" ? "팁" : TIP_TYPE_LABELS[tip.tipType],
      publicTip: true, distanceMeters: tip.distanceMeters, canInteract: tip.canInteract,
      cloudRecord: {
        schemaVersion: 1, kind: "yeogirok-public-cloud-anchor", anchorType: "CLOUD", anchorId: tip.spatialReference.referenceId,
        expiresAtEstimate: tip.spatialReference.expiresAt.toISOString(), ttlDays: 365,
        discoveryLocation: { latitude: tip.latitude, longitude: tip.longitude },
      },
    };
  }));
}

export async function openPublicTipExplorer(page: TipSearchPage, location: LocationEvidenceDto,
  discover: (location: LocationEvidenceDto) => Promise<TipSearchPage>): Promise<CloudPlacement | null> {
  const native: ExplorerBridge = NativeModules.GeoValidation;

  if (!native?.openPublicTipExplorer || !native.updatePublicTips) {
    throw new Error("NATIVE_MODULE_MISSING");
  }

  const sessionId = createIdempotencyKey();
  let active = true;

  const subscription = DeviceEventEmitter.addListener("ArTipNearbyRefresh", async (raw: string) => {
    let event;

    try {
      event = JSON.parse(raw);
    } catch {
      return;
    }

    if (!active || event?.sessionId !== sessionId || typeof event.requestId !== "string") {
      return;
    }

    try {
      const evidence = validateTipLocation({ ...event.location, capturedAt: toServerDateTime(event.location.capturedAt) });
      const next = await discover(evidence);

      if (active) {
        await native.updatePublicTips(event.requestId, publicTipRecords(next), null);
      }
    } catch (error) {
      if (active) {
        await native.updatePublicTips(event.requestId, null, getTipErrorMessage(error)).catch(() => {});
      }
    }
  });

  try {
    const result = await native.openPublicTipExplorer(publicTipRecords(page), JSON.stringify(validateTipLocation(location)), sessionId);
    return result === null ? null : parsePlacement(result);
  } finally {
    active = false;
    subscription.remove();
  }
}

export async function prepareTipPhotoUpload(photo: TipPhoto): Promise<UploadFile> {
  const { base64, sizeBytes } = JSON.parse(await bridge().readPhoto(photo.photoName));

  if (typeof base64 !== "string" || !Number.isSafeInteger(sizeBytes) || sizeBytes <= 0) {
    throw new ContractMismatchError("photo");
  }

  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  const bytes = new Uint8Array(sizeBytes);
  let bits = 0;
  let buffer = 0;
  let offset = 0;

  for (const char of base64.replace(/=+$/, "")) {
    const value = alphabet.indexOf(char);

    if (value < 0) {
      throw new ContractMismatchError("photo");
    }

    buffer = (buffer << 6) | value;
    bits += 6;

    if (bits >= 8) {
      bits -= 8;
      bytes[offset++] = (buffer >>> bits) & 255;
    }
  }

  if (offset !== sizeBytes) {
    throw new ContractMismatchError("photo.sizeBytes");
  }

  return { metadata: { purpose: "TIP_PHOTO", fileName: photo.photoName, contentType: "image/jpeg", sizeBytes }, body: bytes };
}
