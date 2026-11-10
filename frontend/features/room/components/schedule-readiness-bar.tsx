import { Calendar, Check, Info, type LucideIcon } from "lucide-react-native";
import { Pressable, View } from "react-native";

import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { hasTripDates, TRANSPORT_MODE_LABEL, type RoomDetail, type ScheduleReadiness } from "@/features/room/model";
import { formatTripLength } from "@/lib/date";
import { cn } from "@/lib/utils";

/** 준비 줄 안 글자 링크(높이 16)의 터치 영역을 44로 넓히는 여백. */
const READINESS_LINK_HIT_SLOP = { top: 14, bottom: 14, left: 4, right: 4 };

type ReadinessContent = {
  icon: LucideIcon;
  /** `ready`: 초록 원 + 흰 체크. `muted`: 옅은 회색 원 + 회색 아이콘. */
  tone: "ready" | "muted";
  message: string;
  link: { label: string; action: "setDates" | "addPlace" } | null;
  button: { label: string; variant: "default" | "outline"; disabled: boolean };
};

/** 준비됨 줄. `후보 4곳 / 1박 2일 / 자차 — 일정을 만들 준비가 됐어요`. */
function getReadyMessage(room: RoomDetail, placeCount: number): string {
  const parts = [`후보 ${placeCount}곳`];

  if (hasTripDates(room)) {
    parts.push(formatTripLength(room.startDate, room.endDate));
  }

  parts.push(TRANSPORT_MODE_LABEL[room.transportMode]);
  return `${parts.join(" / ")} — 일정을 만들 준비가 됐어요`;
}

/** 상태별 아이콘·문구·버튼(Figma W09-S 상태 보드). */
function getReadinessContent(readiness: ScheduleReadiness, room: RoomDetail, placeCount: number): ReadinessContent {
  switch (readiness.kind) {
    case "HAS_SCHEDULE":
      return {
        icon: Info,
        tone: "muted",
        message: "이미 만든 일정이 있어요. 다시 만들면 순서가 바뀌어요",
        link: null,
        button: { label: "일정 다시 만들기", variant: "outline", disabled: false },
      };
    case "NO_DATES":
      return {
        icon: Calendar,
        tone: "muted",
        message: "여행 날짜를 정하면 일정을 만들 수 있어요",
        link: { label: "날짜 정하기", action: "setDates" },
        button: { label: "AI로 일정 만들기", variant: "default", disabled: true },
      };
    case "NOT_ENOUGH_PLACES":
      return {
        icon: Info,
        tone: "muted",
        message: `후보 장소를 ${readiness.minimum}곳 이상 담아 주세요`,
        link: { label: "장소 담기", action: "addPlace" },
        button: { label: "AI로 일정 만들기", variant: "default", disabled: true },
      };
    case "READY":
      return {
        icon: Check,
        tone: "ready",
        message: getReadyMessage(room, placeCount),
        link: null,
        button: { label: "AI로 일정 만들기", variant: "default", disabled: false },
      };
  }
}

type ScheduleReadinessBarProps = {
  readiness: ScheduleReadiness;
  room: RoomDetail;
  /** 화면에 보이는 후보 장소 수(빼기를 누른 장소 제외). */
  placeCount: number;
  onSetDates: () => void;
  onAddPlace: () => void;
  onCreateSchedule: () => void;
};

/** 여행방 상세 하단의 일정 만들기 영역(Figma W09 `Bottom CTA`, W09-S). 준비 상태 줄 + 주요 버튼. */
export function ScheduleReadinessBar({ readiness, room, placeCount, onSetDates, onAddPlace, onCreateSchedule }: ScheduleReadinessBarProps) {
  const content = getReadinessContent(readiness, room, placeCount);
  const { link } = content;

  return (
    <BottomCta>
      <View className="max-w-full flex-row items-center gap-2" testID="room-detail.readiness">
        <View
          accessible={false}
          className={cn("h-5 w-5 items-center justify-center rounded-full", content.tone === "ready" ? "bg-positive" : "bg-muted")}
        >
          <Icon as={content.icon} size={12} className={content.tone === "ready" ? "text-white" : "text-icon"} />
        </View>
        <Text className="shrink text-center text-caption text-foreground-secondary">{content.message}</Text>
        {link ? (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={link.label}
            hitSlop={READINESS_LINK_HIT_SLOP}
            onPress={link.action === "setDates" ? onSetDates : onAddPlace}
            testID="room-detail.readiness-action"
          >
            <Text className="text-label-sm text-primary">{link.label}</Text>
          </Pressable>
        ) : null}
      </View>
      <Button
        size="lg"
        variant={content.button.variant}
        className="w-full"
        disabled={content.button.disabled}
        onPress={onCreateSchedule}
        testID="room-detail.cta"
      >
        <Text>{content.button.label}</Text>
      </Button>
    </BottomCta>
  );
}
