import { MapPin } from "lucide-react-native";

import { PermissionGate } from "@/components/ui/permission-gate";
import { openAppSettings, type LocationPermission } from "@/lib/native/location";

type LocationPermissionCopy = { title: string; description: string; action: string };

/** 권한 상태별 문구. 무엇에 쓰는지와, 위치 없이도 지도 분포는 볼 수 있다는 다음 행동을 함께 적는다(PermissionGate 패턴). */
const COPY: Readonly<Record<Exclude<LocationPermission, "granted">, LocationPermissionCopy>> = {
  undetermined: {
    title: "위치를 켜면 발자취를 남길 수 있어요",
    description: "지금 있는 곳을 확인할 때만 위치를 써요. 지도 분포는 위치 없이도 볼 수 있어요.",
    action: "위치 사용 허용",
  },
  denied: {
    title: "위치 권한이 꺼져 있어요",
    description: "발자취를 남기려면 위치 권한이 필요해요. 지도 분포는 위치 없이도 볼 수 있어요.",
    action: "위치 권한 다시 요청",
  },
  blocked: {
    title: "위치 권한이 꺼져 있어요",
    description: "설정에서 여기록의 위치 권한을 켜면 발자취를 남길 수 있어요. 지도 분포는 위치 없이도 볼 수 있어요.",
    action: "설정 열기",
  },
};

type LocationPermissionCardProps = {
  permission: Exclude<LocationPermission, "granted">;
  isRequesting: boolean;
  onRequest: () => void;
  /** 권한 없이 지도를 계속 보는 "나중에". 없으면 버튼을 그리지 않는다. */
  onLater?: () => void;
  testID: string;
};

/** 위치 권한 사전 안내와 거부 상태(Figma W15-P, COM-03). 다시 물을 수 없게 거부했으면 설정 앱으로 보낸다. */
export function LocationPermissionCard({ permission, isRequesting, onRequest, onLater, testID }: LocationPermissionCardProps) {
  const copy = COPY[permission];

  return (
    <PermissionGate
      icon={MapPin}
      title={copy.title}
      description={copy.description}
      action={{
        label: copy.action,
        loading: isRequesting,
        testID: `${testID}.allow`,

        onPress: () => {
          if (permission === "blocked") {
            void openAppSettings();
            return;
          }

          onRequest();
        },
      }}
      secondaryAction={onLater ? { label: "나중에", onPress: onLater, testID: `${testID}.later` } : undefined}
      testID={testID}
    />
  );
}
