import { Calendar, Info, TriangleAlert } from "lucide-react-native";
import { useCallback, useRef, useState, type ReactElement } from "react";
import { ScrollView, SectionList, View, type ListViewToken } from "react-native";

import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { TRANSPORT_MODE_LABEL, type RoomDetail } from "@/features/room/model";
import { useRoomSchedule } from "@/features/schedule/api/queries";
import { DayChips, type DayChipItem } from "@/features/schedule/components/day-chips";
import { DayPickerSheet } from "@/features/schedule/components/day-picker-sheet";
import { getScheduleErrorMessage } from "@/features/schedule/errors";
import { formatDuration, hasUnplacedStops, type RoomSchedule, type ScheduleDay, type ScheduleStop } from "@/features/schedule/model";
import { formatShortMonthDay, type LocalDate } from "@/lib/date";
import { cn } from "@/lib/utils";

/** 짧은 목록이라 일차로 바로 넘길 때 그 칸이 그려져 있도록 처음부터 모두 그린다(상한). */
const MAX_INITIAL_ROWS = 80;
const VIEWABILITY_CONFIG = { itemVisiblePercentThreshold: 50 };

type RoomDetailScheduleProps = {
  room: RoomDetail;
  /** 목록 위에 함께 스크롤되는 여행방 정보·탭. */
  header: ReactElement;
};

/**
 * 여행방 상세의 "일정" 탭. 일정이 없으면 W09-S "일정 탭 빈 상태"를, 있으면 W09-B 전체 일정을 보여 준다.
 * 일정을 고치는 버튼("일정 조정하기")은 여행방 상세 하단에 있다.
 */
export function RoomDetailSchedule({ room, header }: RoomDetailScheduleProps) {
  const hasSchedule = room.scheduleCreatedAt !== null;
  const scheduleQuery = useRoomSchedule(room.id, { enabled: hasSchedule });
  const schedule = scheduleQuery.data;

  if (hasSchedule && schedule) {
    return <ScheduleTimeline room={room} schedule={schedule} header={header} />;
  }

  const renderState = () => {
    if (!hasSchedule || schedule === null) {
      return <ScheduleEmpty />;
    }

    if (scheduleQuery.isError) {
      return (
        <View className="items-center gap-4 px-5 py-8">
          <Text accessibilityRole="alert" className="text-center text-body-sm text-foreground-secondary">{getScheduleErrorMessage(scheduleQuery.error)}</Text>
          <Button
            variant="outline"
            loading={scheduleQuery.isFetching}
            loadingLabel="불러오는 중"

            onPress={() => {
              void scheduleQuery.refetch();
            }}

            testID="room-detail.schedule-retry"
          >
            <Text>다시 시도</Text>
          </Button>
        </View>
      );
    }

    return (
      <View accessibilityRole="progressbar" accessibilityLabel="일정을 불러오는 중" accessibilityState={{ busy: true }} className="gap-3 px-5">
        <Skeleton className="h-14 w-40 rounded-md" />
        <Skeleton className="h-5 w-48 rounded-xs" />
        <Skeleton className="h-4 w-40 rounded-xs" />
        <Skeleton className="h-4 w-32 rounded-xs" />
      </View>
    );
  };

  return (
    <ScrollView contentContainerClassName="pb-4">
      {header}
      {renderState()}
    </ScrollView>
  );
}

// #region 전체 일정(W09-B)

type DaySection = { key: LocalDate; day: ScheduleDay; dayNumber: number; data: ScheduleStop[] };

function toDayChipItems(days: readonly ScheduleDay[]): DayChipItem[] {
  return days.map((day, index) => {
    return { date: day.date, label: `${index + 1}일차`, caption: formatShortMonthDay(day.date, { parens: false }), hasWarning: hasUnplacedStops(day) };
  });
}

/** 전체 일정을 위아래로 잇는다. 일차 머리글은 스크롤해도 위에 붙고, 날짜 칩은 목차처럼 그 날로 넘긴다. */
function ScheduleTimeline({ room, schedule, header }: { room: RoomDetail; schedule: RoomSchedule; header: ReactElement }) {
  const listRef = useRef<SectionList<ScheduleStop, DaySection>>(null);
  const [selectedDate, setSelectedDate] = useState<LocalDate | undefined>(schedule.days[0]?.date);
  const [pickerOpen, setPickerOpen] = useState(false);

  const sections: DaySection[] = schedule.days.map((day, index) => {
    return { key: day.date, day, dayNumber: index + 1, data: day.stops };
  });

  const chipItems = toDayChipItems(schedule.days);

  const rowCount = sections.reduce((sum, section) => {
    return sum + section.data.length + 2;
  }, 0);

  // 이동 수단을 바꾸면 저장된 이동 시간이 낡는다(ScheduleTimeline 체크리스트).
  const transportChanged = schedule.transportMode !== "UNKNOWN" && room.transportMode !== "UNKNOWN" && schedule.transportMode !== room.transportMode;

  const scrollToDay = (date: LocalDate) => {
    const sectionIndex = sections.findIndex((section) => {
      return section.key === date;
    });

    if (sectionIndex === -1) {
      return;
    }

    setSelectedDate(date);
    listRef.current?.scrollToLocation({ sectionIndex, itemIndex: 0, animated: true });
  };

  // SectionList는 이 콜백이 바뀌면 안 된다. 상태 변경 함수만 쓰므로 한 번만 만든다.
  const handleViewableItemsChanged = useCallback(({ viewableItems }: { viewableItems: ListViewToken[] }) => {
    const firstSection = viewableItems.find((token) => {
      return token.section !== undefined;
    })?.section as DaySection | undefined;

    if (firstSection !== undefined) {
      setSelectedDate(firstSection.key);
    }
  }, []);

  return (
    <>
      <SectionList
        ref={listRef}
        sections={sections}
        stickySectionHeadersEnabled
        initialNumToRender={Math.min(rowCount, MAX_INITIAL_ROWS)}
        contentContainerClassName="pb-6"
        viewabilityConfig={VIEWABILITY_CONFIG}
        onViewableItemsChanged={handleViewableItemsChanged}

        keyExtractor={(stop) => {
          return String(stop.id);
        }}

        onScrollToIndexFailed={(info) => {
          listRef.current?.getScrollResponder()?.scrollTo({ y: info.averageItemLength * info.index, animated: true });
        }}

        ListHeaderComponent={
          <>
            {header}
            {selectedDate !== undefined ? (
              <DayChips items={chipItems} selectedDate={selectedDate} onSelect={scrollToDay} onOpenPicker={() => {
                setPickerOpen(true);
              }} testID="room-detail.schedule-days" />
            ) : null}
            {transportChanged ? (
              <View className="mx-5 mb-2 flex-row gap-2 rounded-md bg-muted p-3" testID="room-detail.schedule-transport-changed">
                <Icon as={Info} size={16} className="mt-0.5 text-icon" />
                <Text className="flex-1 text-caption text-foreground-secondary">
                  {`이동 수단을 ${TRANSPORT_MODE_LABEL[room.transportMode]}(으)로 바꿨어요. 일정 조정하기에서 다시 계산하면 이동 시간이 맞춰져요.`}
                </Text>
              </View>
            ) : null}
          </>
        }

        renderSectionHeader={({ section }) => {
          return <DayHeader section={section} />;
        }}

        renderItem={({ item, index, section }) => {
          return <StopRow stop={item} isFirst={index === 0} isLast={index === section.data.length - 1} />;
        }}

        renderSectionFooter={({ section }) => {
          return <DayFooter day={section.day} />;
        }}

        testID="room-detail.schedule"
      />
      {selectedDate !== undefined ? (
        <DayPickerSheet
          open={pickerOpen}
          onOpenChange={setPickerOpen}
          items={chipItems}
          selectedDate={selectedDate}
          onSelect={scrollToDay}
          testID="room-detail.schedule-day-picker"
        />
      ) : null}
    </>
  );
}

function DayHeader({ section }: { section: DaySection }) {
  const { day, dayNumber } = section;

  return (
    <View className="bg-background px-5 pt-3">
      <View className="flex-row flex-wrap items-baseline gap-x-2 border-b border-border pb-3">
        <Text accessibilityRole="header" className="text-label text-foreground">{`${dayNumber}일차 ${formatShortMonthDay(day.date)}`}</Text>
        <Text className="text-caption text-muted-foreground tabular-nums">{`${day.origin.time} 출발 → ${day.destination.time} 도착 ${day.stops.length}곳`}</Text>
      </View>
    </View>
  );
}

function StopRow({ stop, isFirst, isLast }: { stop: ScheduleStop; isFirst: boolean; isLast: boolean }) {
  const detail = [stop.category?.name, formatDuration(stop.stayMinutes)].filter(Boolean).join(" ");

  return (
    <View accessible accessibilityLabel={`${stop.arrivalTime} ${stop.name}, ${detail}`} className="flex-row gap-3 px-5">
      <Text className="w-12 pt-3 text-label text-primary tabular-nums">{stop.arrivalTime}</Text>
      <View accessible={false} className="w-2.5 items-center">
        <View className={cn("h-4 w-0.5", isFirst ? "bg-transparent" : "bg-border-strong")} />
        <View className="h-2.5 w-2.5 rounded-full bg-primary" />
        <View className={cn("w-0.5 flex-1", isLast ? "bg-transparent" : "bg-border-strong")} />
      </View>
      <View className="flex-1 gap-1 py-3">
        <Text className="text-label text-foreground">{stop.name}</Text>
        <Text className="text-caption text-foreground-secondary">{detail}</Text>
      </View>
    </View>
  );
}

/** 그날 넣은 장소가 없거나 넣지 못한 장소가 있으면 알린다. */
function DayFooter({ day }: { day: ScheduleDay }) {
  return (
    <View className="gap-2 px-5 pb-2">
      {day.stops.length === 0 ? <Text className="py-3 text-caption text-muted-foreground">이날은 아직 넣은 장소가 없어요</Text> : null}
      {hasUnplacedStops(day) ? (
        <View className="flex-row gap-2 rounded-md bg-highlight-muted p-3">
          <Icon as={TriangleAlert} size={16} className="mt-0.5 text-highlight" />
          <Text className="flex-1 text-caption text-highlight">
            {`넣지 못한 장소 ${day.unplaced.length}곳: ${day.unplaced.map((stop) => {
              return stop.name;
            }).join(", ")}`}
          </Text>
        </View>
      ) : null}
    </View>
  );
}

// #endregion

function ScheduleEmpty() {
  return (
    <View className="px-5">
      <View className="items-center gap-2 rounded-md bg-muted p-6" testID="room-detail.schedule-empty">
        <Icon as={Calendar} size={28} className="text-icon" />
        <Text className="text-label text-foreground">아직 일정이 없어요</Text>
        <Text className="text-center text-caption text-foreground-secondary">후보 장소로 AI 일정 초안을 만들어 보세요</Text>
      </View>
    </View>
  );
}
