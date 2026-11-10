import { Bus, Car, Hourglass, type LucideIcon } from "lucide-react-native";
import type { ReactNode } from "react";
import { View } from "react-native";

import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { TRANSPORT_MODE_LABEL, type TransportMode } from "@/features/room/model";
import {
  getItemTimeLabel,
  getPlaceName,
  getTravelLabel,
  type ScheduleDay,
  type ScheduleItem,
  type ScheduleLeg,
  type SchedulePlaceLookup,
  type SchedulePoint,
} from "@/features/schedule/model";
import { cn } from "@/lib/utils";

type ScheduleDayTimelineProps = {
  day: ScheduleDay;
  places: SchedulePlaceLookup;
  /** 첫날 출발지·마지막 날 도착지. 그날 구간에 출발지·도착지 구간이 있을 때 이름으로 쓴다. */
  startPoint: SchedulePoint | null;
  endPoint: SchedulePoint | null;
  /** 고친 뒤 다시 계산하지 않은 날. 시각을 보여 주지 않는다. */
  uncalculated?: boolean;
  /** 장소 카드 오른쪽 영역. 없으면 머무는 시간만 보여 준다. */
  renderItemActions?: (item: ScheduleItem, name: string) => ReactNode;
  testID: string;
};

function transportIconOf(mode: TransportMode): LucideIcon {
  return mode === "TRANSIT" ? Bus : Car;
}

function legLabelOf(leg: ScheduleLeg, prefix: string | null): string {
  const travel = getTravelLabel(TRANSPORT_MODE_LABEL[leg.transportMode], leg.travelMinutes);
  return prefix === null ? travel : `${prefix} ${travel}`;
}

/**
 * 하루 일정 타임라인(Figma W14). (이동) → 장소 카드 → (이동) → … 순서로 그린다.
 * 시각과 이동 시간은 서버 값을 그대로 쓰고, 서버가 모르면 "정보 없음"으로 보여 준다.
 */
export function ScheduleDayTimeline({ day, places, startPoint, endPoint, uncalculated = false, renderItemActions, testID }: ScheduleDayTimelineProps) {
  const endLeg = day.legs.find((leg) => {
    return leg.toItemId === null;
  });

  return (
    <View className="px-5" testID={testID}>
      {day.items.map((item, index) => {
        const name = getPlaceName(places, item.roomPlaceId);

        const leg = day.legs.find((candidate) => {
          return candidate.toItemId === item.id;
        });

        return (
          <View key={item.id}>
            {leg === undefined ? null : <LegRow leg={leg} prefix={leg.fromItemId === null ? `${startPoint?.name ?? "출발지"}에서` : null} />}
            <ItemRow
              name={name}
              order={index + 1}
              timeLabel={uncalculated ? null : getItemTimeLabel(item)}
              linkedAbove={leg !== undefined}
              linkedBelow={index < day.items.length - 1 || endLeg !== undefined}
              actions={renderItemActions?.(item, name) ?? <StayBadge minutes={item.stayMinutes} />}
              testID={`${testID}.stop-${item.id}`}
            />
          </View>
        );
      })}
      {endLeg === undefined ? null : <LegRow leg={endLeg} prefix={`${endPoint?.name ?? "도착지"}까지`} />}
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

function LegRow({ leg, prefix }: { leg: ScheduleLeg; prefix: string | null }) {
  const label = legLabelOf(leg, prefix);

  return (
    <View accessible accessibilityLabel={label} className="min-h-8 flex-row items-center gap-3">
      <Rail />
      <View className="flex-row items-center gap-1.5">
        <Icon as={transportIconOf(leg.transportMode)} size={14} className="text-muted-foreground" />
        <Text className="text-caption text-muted-foreground">{label}</Text>
      </View>
    </View>
  );
}

type ItemRowProps = {
  name: string;
  order: number;
  /** `10:00 – 11:00`. 보여 주지 않으면 `null`. */
  timeLabel: string | null;
  linkedAbove: boolean;
  linkedBelow: boolean;
  actions: ReactNode;
  testID: string;
};

function ItemRow({ name, order, timeLabel, linkedAbove, linkedBelow, actions, testID }: ItemRowProps) {
  return (
    <View className="flex-row items-center gap-3" testID={testID}>
      <Rail top={linkedAbove} bottom={linkedBelow}>
        <View className="h-6 w-6 items-center justify-center rounded-full bg-primary">
          <Text className="text-micro text-primary-foreground">{order}</Text>
        </View>
      </Rail>
      <View className="my-1 flex-1 flex-row items-center gap-2 rounded-md border border-border bg-card p-3">
        <View accessible accessibilityLabel={[`${order}번째 ${name}`, timeLabel].filter(Boolean).join(", ")} className="flex-1 gap-1">
          <Text className="text-label text-foreground">{name}</Text>
          {timeLabel === null ? null : <Text className="text-caption text-foreground-secondary tabular-nums">{timeLabel}</Text>}
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
