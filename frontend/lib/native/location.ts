import {
  getForegroundPermissionsAsync,
  getLastKnownPositionAsync,
  hasServicesEnabledAsync,
  LocationAccuracy,
  requestForegroundPermissionsAsync,
  reverseGeocodeAsync,
  watchPositionAsync,
  type LocationGeocodedAddress,
  type LocationObject,
  type LocationPermissionResponse,
  type LocationSubscription,
} from "expo-location";
import { Linking, Platform } from "react-native";

/**
 * 현재 위치 어댑터(규칙 85 모듈 경계). `expo-location`은 이 파일에서만 import한다.
 * 앱 사용 중(foreground) 권한만 다루고, 위치는 필요한 순간에만 잰다.
 */

/** 위치 권한 상태. `blocked`는 거부한 뒤 다시 물을 수 없어 설정에서만 켤 수 있는 상태다. */
export type LocationPermission = "undetermined" | "granted" | "denied" | "blocked";

/** 권한 상태와, 허용했더라도 대략적인 위치(Android coarse·iOS reduced)만 허용했는지. */
export type LocationAccess = {
  permission: LocationPermission;
  isApproximate: boolean;
};

/** 잰 현재 위치. 정확도(m)와 측정 시각은 위치 증거로 함께 쓴다(규칙 80). */
export type MeasuredPosition = {
  latitude: number;
  longitude: number;
  /** 기기가 알려 준 수평 정확도(m). 알 수 없으면 `null`. */
  accuracyMeters: number | null;
  measuredAt: Date;
};

/** 위치를 재지 못한 이유. 화면은 이유별로 다음 행동을 안내한다. */
export type LocationFailure = "services-off" | "permission" | "timeout" | "unavailable";

export class LocationMeasureError extends Error {
  constructor(public readonly reason: LocationFailure) {
    super(`현재 위치를 측정하지 못했습니다(${reason}).`);
    this.name = "LocationMeasureError";
  }
}

/** 이 시간 안에 위치를 하나도 못 받으면 실패로 본다. GPS가 잡히지 않는 곳에서 무한히 기다리지 않게 한다. */
const MEASURE_TIMEOUT_MS = 20_000;
/** 첫 위치를 받은 뒤 더 정확한 위치를 기다리는 시간. 첫 값은 보통 기지국·Wi-Fi 기반이라 덜 정확하다. */
const REFINE_WINDOW_MS = 6_000;
/** 이만큼 정확하면(m) 더 기다리지 않는다. 서버의 인정 기준(DEC-01)이 아니라 기다리는 시간을 줄이는 값이다. */
const SETTLED_ACCURACY_METERS = 15;
/** 재는 동안 새 위치를 받는 간격. */
const WATCH_INTERVAL_MS = 1_000;

/** 권한이 없어 측정을 거절할 때 Android·iOS 모듈이 주는 오류 code. */
const PERMISSION_ERROR_CODES = ["ERR_LOCATION_UNAUTHORIZED", "E_LOCATION_UNAUTHORIZED", "ERR_LOCATION_PERMISSIONS"];

function toPermission(response: LocationPermissionResponse): LocationPermission {
  if (response.granted) {
    return "granted";
  }

  if (response.status === "undetermined") {
    return "undetermined";
  }

  return response.canAskAgain ? "denied" : "blocked";
}

function toAccess(response: LocationPermissionResponse): LocationAccess {
  const isApproximate = response.android?.accuracy === "coarse" || response.ios?.accuracy === "reduced";
  return { permission: toPermission(response), isApproximate: response.granted && isApproximate };
}

/** 지금 권한 상태를 읽는다. OS 대화상자는 띄우지 않는다. */
export async function getLocationAccess(): Promise<LocationAccess> {
  return toAccess(await getForegroundPermissionsAsync());
}

/**
 * OS 위치 권한 대화상자를 띄운다. 사전 안내(COM-03)를 보여 준 뒤에만 부른다.
 * Android에서 대략적인 위치만 허용한 상태로 다시 부르면 정확한 위치로 바꾸는 대화상자가 뜬다.
 */
export async function requestLocationAccess(): Promise<LocationAccess> {
  return toAccess(await requestForegroundPermissionsAsync());
}

/** 앱 안에서 정확한 위치를 다시 요청할 수 있는지. iOS는 설정 앱에서만 바꿀 수 있다. */
export function canRequestPreciseLocation(): boolean {
  return Platform.OS === "android";
}

/** 거부한 권한이나 대략적인 위치는 앱 설정에서만 바꿀 수 있다. */
export async function openAppSettings(): Promise<void> {
  await Linking.openSettings();
}

function readErrorCode(error: unknown): string | null {
  return typeof error === "object" && error !== null && "code" in error && typeof error.code === "string" ? error.code : null;
}

function toFailure(error: unknown): LocationFailure {
  const code = readErrorCode(error);
  return code !== null && PERMISSION_ERROR_CODES.includes(code) ? "permission" : "unavailable";
}

function toMeasuredPosition(location: LocationObject): MeasuredPosition {
  return {
    latitude: location.coords.latitude,
    longitude: location.coords.longitude,
    accuracyMeters: location.coords.accuracy,
    measuredAt: new Date(location.timestamp),
  };
}

/** 정확도 값이 더 작은 쪽(정확도를 모르면 가장 나쁜 값으로 본다). 같으면 새 값을 고른다. */
function moreAccurate(current: MeasuredPosition | null, next: MeasuredPosition): MeasuredPosition {
  if (current === null) {
    return next;
  }

  return (next.accuracyMeters ?? Infinity) <= (current.accuracyMeters ?? Infinity) ? next : current;
}

/**
 * 현재 위치를 잰다. 짧게 위치를 받아 가며 가장 정확한 값을 고르고, 다 재면 바로 구독을 끊는다(규칙 85).
 * 충분히 정확해지거나, 첫 값을 받고 `REFINE_WINDOW_MS`가 지나면 끝낸다.
 * 위치 서비스가 꺼져 있거나, 시간 안에 하나도 못 받거나, 권한이 없으면 `LocationMeasureError`를 던진다.
 * 좌표는 로그·오류 리포트에 넣지 않는다(규칙 80).
 */
export async function measureCurrentPosition(): Promise<MeasuredPosition> {
  if (!(await hasServicesEnabledAsync())) {
    throw new LocationMeasureError("services-off");
  }

  return new Promise<MeasuredPosition>((resolve, reject) => {
    let best: MeasuredPosition | null = null;
    let subscription: LocationSubscription | null = null;
    let refineTimer: ReturnType<typeof setTimeout> | undefined;
    let isDone = false;

    const finish = (failure: LocationFailure) => {
      if (isDone) {
        return;
      }

      isDone = true;
      clearTimeout(refineTimer);
      clearTimeout(timeoutTimer);
      subscription?.remove();

      if (best === null) {
        reject(new LocationMeasureError(failure));
      } else {
        resolve(best);
      }
    };

    const timeoutTimer = setTimeout(() => {
      finish("timeout");
    }, MEASURE_TIMEOUT_MS);

    const handleLocation = (location: LocationObject) => {
      best = moreAccurate(best, toMeasuredPosition(location));

      if (best.accuracyMeters !== null && best.accuracyMeters <= SETTLED_ACCURACY_METERS) {
        finish("unavailable");
        return;
      }

      refineTimer ??= setTimeout(() => {
        finish("unavailable");
      }, REFINE_WINDOW_MS);
    };

    watchPositionAsync(
      { accuracy: LocationAccuracy.Highest, timeInterval: WATCH_INTERVAL_MS, distanceInterval: 0 },
      handleLocation,
      () => {
        finish("unavailable");
      },
    ).then((started) => {
      if (isDone) {
        started.remove();
        return;
      }

      subscription = started;
    }, (error: unknown) => {
      finish(toFailure(error));
    });
  });
}

/**
 * 기기가 마지막으로 알던 위치. 새로 재지 않아 바로 돌아오지만 오래된 값일 수 있어, 지도를 처음 어디에서 열지 정하는 데만 쓴다.
 * 위치 증거로는 쓰지 않는다(규칙 80). 권한이 없거나 기기가 아는 위치가 없거나 읽지 못하면 `null`이고, OS 대화상자는 띄우지 않는다.
 */
export async function getLastKnownPosition(): Promise<MeasuredPosition | null> {
  try {
    const access = await getForegroundPermissionsAsync();

    if (!access.granted) {
      return null;
    }

    const location = await getLastKnownPositionAsync();
    return location === null ? null : toMeasuredPosition(location);
  } catch {
    return null;
  }
}

function toPlaceLabel(address: LocationGeocodedAddress): string | null {
  const parts: string[] = [];

  for (const part of [address.city ?? address.subregion, address.district, address.street]) {
    const value = part?.trim();

    if (value && !parts.includes(value)) {
      parts.push(value);
    }
  }

  if (parts.length > 0) {
    return parts.join(" ");
  }

  return address.name?.trim() || null;
}

/**
 * 좌표 근처의 주소를 "강릉시 창해로"처럼 짧게 만든다. 기기 지오코더를 쓰며, 못 찾거나 실패하면 `null`이다.
 * 화면 표시용 참고 값이라 확정 주소처럼 쓰지 않는다. 저장되는 장소 이름은 서버가 정한다.
 */
export async function describePlace({ latitude, longitude }: Pick<MeasuredPosition, "latitude" | "longitude">): Promise<string | null> {
  try {
    const [address] = await reverseGeocodeAsync({ latitude, longitude });
    return address ? toPlaceLabel(address) : null;
  } catch {
    return null;
  }
}
