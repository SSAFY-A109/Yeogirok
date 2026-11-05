export type CloudPlacement = {
  raw: string;
  anchorId: string;
  expiresAtEstimate: string;
  hasDiscoveryLocation: boolean;
  visitAccuracyMeters: number | null;
};

export type TipPhoto = { photoName: string; photoUri: string };
export type TipReaction = "LIKE" | "DISLIKE" | null;

export const TIP_TYPES = ["포토", "맛집", "카페", "꿀팁", "TMI"] as const;
export type TipType = typeof TIP_TYPES[number];

export function isTipType(value: unknown): value is TipType {
  return TIP_TYPES.some((type) => {
    return type === value;
  });
}

export type LocalTip = {
  id: string;
  body: string;
  createdAt: string;
  placement: CloudPlacement;
  photo: TipPhoto | null;
  tipType: TipType | null;
  draft: boolean;
};

export function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function parseJson(raw: unknown): unknown {
  if (typeof raw !== "string") {
    throw new Error("INVALID_RESULT");
  }

  try {
    return JSON.parse(raw);
  } catch {
    throw new Error("INVALID_RESULT");
  }
}

export function parsePlacement(raw: unknown): CloudPlacement {
  const value = parseJson(raw);

  if (
    !isObject(value) || value.schemaVersion !== 1 || value.kind !== "yeogirok-cloud-anchor" ||
    typeof value.anchorId !== "string" || !value.anchorId.trim() ||
    typeof value.ttlDays !== "number" || !Number.isInteger(value.ttlDays) || value.ttlDays < 1 || value.ttlDays > 365 ||
    typeof value.hostedAt !== "string" || !Number.isFinite(Date.parse(value.hostedAt)) ||
    typeof value.expiresAtEstimate !== "string" || !Number.isFinite(Date.parse(value.expiresAtEstimate))
  ) {
    throw new Error("INVALID_RESULT");
  }

  if (Date.parse(value.expiresAtEstimate) !== Date.parse(value.hostedAt) + value.ttlDays * 86400000 ||
    (value.authorizationMode !== undefined && value.authorizationMode !== "KEYLESS")) {
    throw new Error("INVALID_RESULT");
  }

  const location = value.discoveryLocation;

  if (location != null && (
    !isObject(location) || location.source !== "MAP_SELECTED" ||
    typeof location.latitude !== "number" || !Number.isFinite(location.latitude) || Math.abs(location.latitude) > 90 ||
    typeof location.longitude !== "number" || !Number.isFinite(location.longitude) || Math.abs(location.longitude) > 180
  )) {
    throw new Error("INVALID_RESULT");
  }

  const visit = value.visitLocation;

  if (visit != null && (!isObject(visit) || visit.source !== "DEVICE_GPS" ||
    typeof visit.latitude !== "number" || !Number.isFinite(visit.latitude) || Math.abs(visit.latitude) > 90 ||
    typeof visit.longitude !== "number" || !Number.isFinite(visit.longitude) || Math.abs(visit.longitude) > 180 ||
    typeof visit.accuracyMeters !== "number" || !Number.isFinite(visit.accuracyMeters) || visit.accuracyMeters < 0 ||
    typeof visit.capturedAt !== "string" || !Number.isFinite(Date.parse(visit.capturedAt)))) {
    throw new Error("INVALID_RESULT");
  }

  return {
    raw: JSON.stringify(value),
    anchorId: value.anchorId,
    expiresAtEstimate: value.expiresAtEstimate,
    hasDiscoveryLocation: location != null,
    visitAccuracyMeters: isObject(visit) && typeof visit.accuracyMeters === "number" ? visit.accuracyMeters : null,
  };
}

function parseTipValue(value: unknown): LocalTip {
  if (
    !isObject(value) || value.schemaVersion !== 1 || typeof value.id !== "string" || !value.id ||
    typeof value.body !== "string" || typeof value.createdAt !== "string" ||
    !Number.isFinite(Date.parse(value.createdAt))
  ) {
    throw new Error("INVALID_RESULT");
  }

  if (value.draft === true && (!isTipType(value.tipType) || value.body.length > 120 ||
    typeof value.photoName !== "string" || !/^[a-f0-9-]{36}\.jpg$/.test(value.photoName))) {
    throw new Error("INVALID_RESULT");
  }

  return {
    id: value.id,
    body: value.body,
    createdAt: value.createdAt,
    placement: parsePlacement(JSON.stringify(value.cloudRecord)),
    photo: typeof value.photoName === "string" && typeof value.photoUri === "string" ? parsePhoto(JSON.stringify(value)) : null,
    tipType: isTipType(value.tipType) ? value.tipType : null,
    draft: value.draft === true,
  };
}

export function parsePhoto(raw: unknown): TipPhoto {
  const value = parseJson(raw);

  if (!isObject(value) || typeof value.photoName !== "string" || !/^[a-f0-9-]{36}\.jpg$/.test(value.photoName) ||
    typeof value.photoUri !== "string" || !value.photoUri.startsWith("file://")) {
    throw new Error("INVALID_RESULT");
  }

  return { photoName: value.photoName, photoUri: value.photoUri };
}

export function parseLocalTip(raw: unknown): LocalTip {
  return parseTipValue(parseJson(raw));
}

export function parseTipReaction(value: unknown): TipReaction {
  if (value !== null && value !== "LIKE" && value !== "DISLIKE") {
    throw new Error("INVALID_RESULT");
  }

  return value;
}

export function parseTipDetail(raw: unknown) {
  const value = parseJson(raw);
  const tip = parseTipValue(value);

  if (!isObject(value)) {
    throw new Error("INVALID_RESULT");
  }

  return { tip, reaction: parseTipReaction(value.reaction) };
}

export function parseLocalTips(raw: unknown): LocalTip[] {
  const value = parseJson(raw);

  if (!Array.isArray(value)) {
    throw new Error("INVALID_RESULT");
  }

  return value.map(parseTipValue);
}

export function isPlacementExpired(placement: CloudPlacement, now = Date.now()): boolean {
  return Date.parse(placement.expiresAtEstimate) <= now;
}
