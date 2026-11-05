import { Bus, Car, Hourglass, type LucideIcon } from "lucide-react-native";
import type { ReactNode } from "react";
import { View } from "react-native";

import { Badge } from "@/components/ui/badge";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { TRANSPORT_MODE_LABEL, type TransportMode } from "@/features/room/model";
import { getParkingLabel, getTravelLabel, type ScheduleDay, type ScheduleStop } from "@/features/schedule/model";
import { cn } from "@/lib/utils";

type ScheduleDayTimelineProps = {
  day: ScheduleDay;
  transportMode: TransportMode;
  /** 장소 카드 오른쪽 영역. 없으면 머무는 시간만 보여 준다. */
  renderStopActions?: (stop: ScheduleStop) => ReactNode;
  testID: string;
};

function transportIconOf(mode: TransportMode): LucideIcon {
  return mode === "PUBLIC_TRANSIT" ? Bus : Car;
}

/**
 * 하루 일정 타임라인(Figma W14). 출발 → (이동) → 장소 카드 → … → (이동) → 도착 순서로 그린다.
 * 이동 시간과 주차 정보는 서버 값을 그대로 쓰고, 없으면 "정보 없음"으로 보여 준다.
 */
export function ScheduleDayTimeline({ day, transportMode, renderStopActions, testID }: ScheduleDayTimelineProps) {
  const modeLabel = TRANSPORT_MODE_LABEL[transportMode];
  const travelIcon = transportIconOf(transportMode);
  const showParking = transportMode === "CAR";

  return (
    <View className="px-5" testID={testID}>
      <EndpointRow time={day.origin.time} label={`${day.origin.name} 출발`} position="first" />
      {day.stops.map((stop, index) => {
        return (
          <View key={stop.id}>
            <LegRow icon={travelIcon} label={getTravelLabel(modeLabel, stop.travelMinutes)} />
            <StopRow
              stop={stop}
              order={index + 1}
              showParking={showParking}
              actions={renderStopActions?.(stop) ?? <StayBadge minutes={stop.stayMinutes} />}
              testID={`${testID}.stop-${stop.id}`}
            />
          </View>
        );
      })}
      <LegRow icon={travelIcon} label={getTravelLabel(modeLabel, day.destination.travelMinutes)} />
      <EndpointRow time={day.destination.time} label={`${day.destination.name} 도착`} position="last" />
    </View>
  );
}

// #region 행

function Rail({ children, top = true, bottom = true }: { children?: ReactNode; top?: boolean; bottom?: boolean }) {
  return (
    <View accessible={false} className="w-6 items-center self-stretch">
      <View className={cn("w-0.5 flex-1", top ? "bg-border-strong" : "bg-transparent")} />
      {children}
      <View className={cn("w-0.5 flex-1", bottom ? "bg-border-strong" : "bg-transparent")} />
    </View>
  );
}

function EndpointRow({ time, label, position }: { time: string; label: string; position: "first" | "last" }) {
  return (
    <View accessible accessibilityLabel={`${time} ${label}`} className="min-h-10 flex-row items-center gap-3">
      <Rail top={position === "last"} bottom={position === "first"}>
        <View className="h-3 w-3 rounded-full border-2 border-ring bg-card" />
      </Rail>
      <Text className="text-label text-foreground tabular-nums">{time}</Text>
      <Text className="flex-1 text-body-sm text-foreground-secondary">{label}</Text>
    </View>
  );
}

function LegRow({ icon, label }: { icon: LucideIcon; label: string }) {
  return (
    <View accessible accessibilityLabel={label} className="min-h-8 flex-row items-center gap-3">
      <Rail />
      <View className="flex-row items-center gap-1.5">
        <Icon as={icon} size={14} className="text-muted-foreground" />
        <Text className="text-caption text-muted-foreground">{label}</Text>
      </View>
    </View>
  );
}

type StopRowProps = { stop: ScheduleStop; order: number; showParking: boolean; actions: ReactNode; testID: string };

function StopRow({ stop, order, showParking, actions, testID }: StopRowProps) {
  const timeRange = `${stop.arrivalTime} – ${stop.departureTime}`;

  return (
    <View className="flex-row items-center gap-3" testID={testID}>
      <Rail>
        <View className="h-6 w-6 items-center justify-center rounded-full bg-primary">
          <Text className="text-micro text-primary-foreground">{order}</Text>
        </View>
      </Rail>
      <View className="my-1 flex-1 flex-row items-center gap-2 rounded-md border border-border bg-card p-3">
        <View
          accessible
          accessibilityLabel={[`${order}번째 ${stop.name}`, timeRange, stop.tag, showParking ? getParkingLabel(stop.parking) : null].filter(Boolean).join(", ")}
          className="flex-1 gap-1"
        >
          <View className="flex-row flex-wrap items-center gap-1.5">
            <Text className="text-label text-foreground">{stop.name}</Text>
            {stop.tag === null ? null : (
              <Badge className="self-center">
                <Text>{stop.tag}</Text>
              </Badge>
            )}
          </View>
          <Text className="text-caption text-foreground-secondary tabular-nums">{timeRange}</Text>
          {showParking ? <Text className="text-caption text-muted-foreground">{getParkingLabel(stop.parking)}</Text> : null}
        </View>
        {actions}
      </View>
    </View>
  );
}

function StayBadge({ minutes }: { minutes: number }) {
  return (
    <View accessible accessibilityLabel={`머무는 시간 ${minutes}분`} className="h-8 flex-row items-center gap-1 rounded-sm bg-muted px-2">
      <Icon as={Hourglass} size={14} className="text-icon" />
      <Text className="text-label-sm text-foreground tabular-nums">{`${minutes}분`}</Text>
    </View>
  );
}

// #endregion
