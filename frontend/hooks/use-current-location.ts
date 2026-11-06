import { useEffect, useEffectEvent, useRef, useState } from "react";
import { AppState } from "react-native";

import {
  canRequestPreciseLocation,
  describePlace,
  getLocationAccess,
  LocationMeasureError,
  measureCurrentPosition,
  openAppSettings,
  requestLocationAccess,
  type LocationAccess,
  type LocationFailure,
  type LocationPermission,
  type MeasuredPosition,
} from "@/lib/native/location";

export type CurrentLocation = {
  /** 권한 상태를 아직 읽는 중이면 `null`. */
  permission: LocationPermission | null;
  /** 허용했지만 대략적인 위치만 허용해 수백 m 넘게 빗나갈 수 있는지. */
  isApproximate: boolean;
  /** 정확한 위치를 켜는 방법. 앱 안에서 다시 물을 수 없으면 설정 앱으로 보낸다. */
  preciseLocationAction: "request" | "settings";
  position: MeasuredPosition | null;
  /** 기기 지오코더로 찾은 근처 주소. 찾는 중이거나 못 찾으면 `null`. */
  placeLabel: string | null;
  isMeasuring: boolean;
  isRequestingPermission: boolean;
  /** 마지막 측정이 실패한 이유. 다시 재기 시작하면 지운다. */
  failure: LocationFailure | null;
  /** OS 권한 대화상자를 띄운다. 허용되면 바로 잰다. */
  requestPermission: () => Promise<void>;
  /** 대략적인 위치를 정확한 위치로 바꾸도록 요청한다. 바뀌면 다시 잰다. */
  enablePreciseLocation: () => Promise<void>;
  /** 위치를 다시 잰다. 이미 재는 중이면 그 결과를 기다리지 않고 `null`을 돌려준다. */
  measure: () => Promise<MeasuredPosition | null>;
};

/**
 * 앱 사용 중 위치 권한과, 필요한 순간에 잰 현재 위치를 다룬다(규칙 80·85).
 * 권한이 이미 있으면 화면에 들어올 때 한 번 잰다. OS 대화상자는 `requestPermission`을 부를 때만 띄운다.
 * 설정 앱에서 권한을 바꾸고 돌아오면 권한을 다시 읽고, 대략적인 위치가 정확한 위치로 바뀌었으면 다시 잰다.
 */
export function useCurrentLocation(): CurrentLocation {
  const [permission, setPermission] = useState<LocationPermission | null>(null);
  const [isApproximate, setIsApproximate] = useState(false);

  const [preciseLocationAction, setPreciseLocationAction] = useState<"request" | "settings">(() => {
    return canRequestPreciseLocation() ? "request" : "settings";
  });

  const [position, setPosition] = useState<MeasuredPosition | null>(null);
  const [placeLabel, setPlaceLabel] = useState<string | null>(null);
  const [isMeasuring, setIsMeasuring] = useState(false);
  const [isRequestingPermission, setIsRequestingPermission] = useState(false);
  const [failure, setFailure] = useState<LocationFailure | null>(null);
  const isMounted = useRef(true);
  const isMeasuringRef = useRef(false);
  const isRequestingRef = useRef(false);
  const latestPosition = useRef<MeasuredPosition | null>(null);
  const wasApproximate = useRef(false);

  const measure = async (): Promise<MeasuredPosition | null> => {
    if (isMeasuringRef.current) {
      return null;
    }

    isMeasuringRef.current = true;
    setIsMeasuring(true);
    setFailure(null);
    let next: MeasuredPosition;

    try {
      next = await measureCurrentPosition();
    } catch (error) {
      const reason = error instanceof LocationMeasureError ? error.reason : "unavailable";

      if (isMounted.current) {
        setFailure(reason);
      }

      if (reason === "permission") {
        applyAccess(await getLocationAccess());
      }

      return null;
    } finally {
      isMeasuringRef.current = false;

      if (isMounted.current) {
        setIsMeasuring(false);
      }
    }

    if (!isMounted.current) {
      return next;
    }

    latestPosition.current = next;
    setPosition(next);
    setPlaceLabel(null);

    void describePlace(next).then((label) => {
      if (isMounted.current && latestPosition.current === next) {
        setPlaceLabel(label);
      }
    });

    return next;
  };

  /** 권한 상태를 반영하고, 허용 상태에서 아직 안 쟀거나 대략적인 위치에서 정확한 위치로 바뀌었으면 잰다. */
  function applyAccess(access: LocationAccess) {
    if (!isMounted.current) {
      return;
    }

    const becamePrecise = wasApproximate.current && !access.isApproximate;
    wasApproximate.current = access.isApproximate;
    setPermission(access.permission);
    setIsApproximate(access.isApproximate);

    if (access.permission === "granted" && (latestPosition.current === null || becamePrecise)) {
      void measure();
    }
  }

  /** OS 권한 대화상자를 띄우고 결과를 반영한다. 이미 묻는 중이거나 실패하면 `null`. */
  const askAccess = async (): Promise<LocationAccess | null> => {
    if (isRequestingRef.current) {
      return null;
    }

    isRequestingRef.current = true;
    setIsRequestingPermission(true);

    try {
      const access = await requestLocationAccess();
      applyAccess(access);
      return access;
    } catch {
      if (isMounted.current) {
        setFailure("unavailable");
      }

      return null;
    } finally {
      isRequestingRef.current = false;

      if (isMounted.current) {
        setIsRequestingPermission(false);
      }
    }
  };

  const requestPermission = async () => {
    await askAccess();
  };

  /** Android는 앱 안에서 한 번 더 물을 수 있다. 그래도 대략적인 위치로 남으면 다음부터 설정 앱으로 보낸다. */
  const enablePreciseLocation = async () => {
    if (preciseLocationAction === "settings") {
      await openAppSettings();
      return;
    }

    const access = await askAccess();

    if (access?.isApproximate && isMounted.current) {
      setPreciseLocationAction("settings");
    }
  };

  const syncPermission = useEffectEvent(async () => {
    try {
      applyAccess(await getLocationAccess());
    } catch {
      applyAccess({ permission: "undetermined", isApproximate: false });
    }
  });

  useEffect(() => {
    isMounted.current = true;
    void syncPermission();

    const subscription = AppState.addEventListener("change", (state) => {
      if (state === "active") {
        void syncPermission();
      }
    });

    return () => {
      isMounted.current = false;
      subscription.remove();
    };
  }, []);

  return {
    permission,
    isApproximate,
    preciseLocationAction,
    position,
    placeLabel,
    isMeasuring,
    isRequestingPermission,
    failure,
    requestPermission,
    enablePreciseLocation,
    measure,
  };
}
