import { Calendar, TriangleAlert } from "lucide-react-native";
import { useCallback, useRef, useState, type ReactElement } from "react";
import { ScrollView, SectionList, View, type ListViewToken } from "react-native";

import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useAllRoomPlaces } from "@/features/room/api/queries";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import type { RoomDetail } from "@/features/room/model";
import { useRoomSchedule } from "@/features/schedule/api/queries";
import { DayChips, type DayChipItem } from "@/features/schedule/components/day-chips";
import { DayPickerSheet } from "@/features/schedule/components/day-picker-sheet";
import { getScheduleErrorMessage } from "@/features/schedule/errors";
import {
  formatDuration,
  getPlaceName,
  getScheduleWarningMessage,
  toPlaceLookup,
  type RoomSchedule,
  type ScheduleDay,
  type ScheduleItem,
  type SchedulePlaceLookup,
  type ScheduleWarning,
} from "@/features/schedule/model";
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
 * 일정 응답에는 장소 이름이 없어 후보 목록(뺀 후보 포함)과 `roomPlaceId`로 묶는다.
 * 일정을 고치는 버튼("일정 조정하기")은 여행방 상세 하단에 있다.
 */
export function RoomDetailSchedule({ room, header }: RoomDetailScheduleProps) {
  const scheduleQuery = useRoomSchedule(room.id, { enabled: room.hasSchedule });
  const placesQuery = useAllRoomPlaces(room.id, { enabled: room.hasSchedule });
  const schedule = scheduleQuery.data;

  if (room.hasSchedule && schedule && placesQuery.data) {
    return <ScheduleTimeline schedule={schedule} places={toPlaceLookup(placesQuery.data)} header={header} />;
  }

  const renderState = () => {
    if (!room.hasSchedule || schedule === null) {
      return <ScheduleEmpty />;
    }

    if (scheduleQuery.isError || placesQuery.isError) {
      const message = scheduleQuery.isError ? getScheduleErrorMessage(scheduleQuery.error) : getRoomErrorMessage(placesQuery.error, ROOM_ERROR_FALLBACK.load);

      return (
        <View className="items-center gap-4 px-5 py-8">
          <Text accessibilityRole="alert" className="text-center text-body-sm text-foreground-secondary">{message}</Text>
          <Button
            variant="outline"
            loading={scheduleQuery.isFetching || placesQuery.isFetching}
            loadingLabel="불러오는 중"

            onPress={() => {
              if (scheduleQuery.isError) {
                void scheduleQuery.refetch();
              }

              if (placesQuery.isError) {
                void placesQuery.refetch();
              }
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

type DaySection = { key: LocalDate; day: ScheduleDay; dayNumber: number; data: ScheduleItem[] };

function toDayChipItems(days: readonly ScheduleDay[]): DayChipItem[] {
  return days.map((day, index) => {
    return { date: day.date, label: `${index + 1}일차`, caption: formatShortMonthDay(day.date, { parens: false }), hasWarning: day.warnings.length > 0 };
  });
}

/** 전체 일정을 위아래로 잇는다. 일차 머리글은 스크롤해도 위에 붙고, 날짜 칩은 목차처럼 그 날로 넘긴다. */
function ScheduleTimeline({ schedule, places, header }: { schedule: RoomSchedule; places: SchedulePlaceLookup; header: ReactElement }) {
  const listRef = useRef<SectionList<ScheduleItem, DaySection>>(null);
  const [selectedDate, setSelectedDate] = useState<LocalDate | undefined>(schedule.days[0]?.date);
  const [pickerOpen, setPickerOpen] = useState(false);

  const sections: DaySection[] = schedule.days.map((day, index) => {
    return { key: day.date, day, dayNumber: index + 1, data: day.items };
  });

  const chipItems = toDayChipItems(schedule.days);

  const rowCount = sections.reduce((sum, section) => {
    return sum + section.data.length + 2;
  }, 0);

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

        keyExtractor={(item) => {
          return String(item.id);
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
            {schedule.warnings.length === 0 ? null : (
              <View className="px-5 pb-2">
                <WarningBox warnings={schedule.warnings} />
              </View>
            )}
          </>
        }

        renderSectionHeader={({ section }) => {
          return <DayHeader section={section} />;
        }}

        renderItem={({ item, index, section }) => {
          return <ItemRow item={item} places={places} isFirst={index === 0} isLast={index === section.data.length - 1} />;
        }}

        renderSectionFooter={({ section }) => {
          return <DayFooter day={section.day} />;
        }}

        ListFooterComponent={schedule.unassignedPlaceIds.length === 0 ? undefined : <UnassignedPlaces roomPlaceIds={schedule.unassignedPlaceIds} places={places} />}
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

/** `10:00–18:00 · 3곳`. 그날 시각 조건이 없으면 장소 수만 보여 준다. */
function getDayCaption(day: ScheduleDay): string {
  const count = `${day.items.length}곳`;
  return day.startTime === null || day.endTime === null ? count : `${day.startTime}–${day.endTime} · ${count}`;
}

function DayHeader({ section }: { section: DaySection }) {
  const { day, dayNumber } = section;

  return (
    <View className="bg-background px-5 pt-3">
      <View className="flex-row flex-wrap items-baseline gap-x-2 border-b border-border pb-3">
        <Text accessibilityRole="header" className="text-label text-foreground">{`${dayNumber}일차 ${formatShortMonthDay(day.date)}`}</Text>
        <Text className="text-caption text-muted-foreground tabular-nums">{getDayCaption(day)}</Text>
      </View>
    </View>
  );
}

type ItemRowProps = { item: ScheduleItem; places: SchedulePlaceLookup; isFirst: boolean; isLast: boolean };

function ItemRow({ item, places, isFirst, isLast }: ItemRowProps) {
  const name = getPlaceName(places, item.roomPlaceId);
  const timeUnknown = item.arrivalTime === null;
  const detail = [places.get(item.roomPlaceId)?.category?.name, formatDuration(item.stayMinutes), timeUnknown ? "시각 정보 없음" : null].filter(Boolean).join(" · ");

  return (
    <View accessible accessibilityLabel={[item.arrivalTime, name, detail].filter(Boolean).join(", ")} className="flex-row gap-3 px-5">
      <Text className="w-12 pt-3 text-label text-primary tabular-nums">{item.arrivalTime ?? "–"}</Text>
      <View accessible={false} className="w-2.5 items-center">
        <View className={cn("h-4 w-0.5", isFirst ? "bg-transparent" : "bg-border-strong")} />
        <View className="h-2.5 w-2.5 rounded-full bg-primary" />
        <View className={cn("w-0.5 flex-1", isLast ? "bg-transparent" : "bg-border-strong")} />
      </View>
      <View className="flex-1 gap-1 py-3">
        <Text className="text-label text-foreground">{name}</Text>
        <Text className="text-caption text-foreground-secondary">{detail}</Text>
      </View>
    </View>
  );
}

/** 서버가 붙인 경고(이동 시간·도착 시각을 모름, 종료 시각을 넘김). */
function WarningBox({ warnings }: { warnings: readonly ScheduleWarning[] }) {
  return (
    <View className="gap-1 rounded-md bg-highlight-muted p-3">
      {warnings.map((warning) => {
        return (
          <View key={warning} className="flex-row gap-2">
            <Icon as={TriangleAlert} size={16} className="mt-0.5 text-highlight" />
            <Text className="flex-1 text-caption text-highlight">{getScheduleWarningMessage(warning)}</Text>
          </View>
        );
      })}
    </View>
  );
}

/** 그날 넣은 장소가 없거나 서버 경고가 있으면 알린다. */
function DayFooter({ day }: { day: ScheduleDay }) {
  return (
    <View className="gap-2 px-5 pb-2">
      {day.items.length === 0 ? <Text className="py-3 text-caption text-muted-foreground">이날은 아직 넣은 장소가 없어요</Text> : null}
      {day.warnings.length === 0 ? null : <WarningBox warnings={day.warnings} />}
    </View>
  );
}

/** 일정에 넣지 않은 후보. 서버는 넣지 않은 이유를 주지 않는다. */
function UnassignedPlaces({ roomPlaceIds, places }: { roomPlaceIds: readonly number[]; places: SchedulePlaceLookup }) {
  return (
    <View className="px-5 pt-2" testID="room-detail.schedule-unassigned">
      <View className="flex-row gap-2 rounded-md bg-highlight-muted p-3">
        <Icon as={TriangleAlert} size={16} className="mt-0.5 text-highlight" />
        <Text className="flex-1 text-caption text-highlight">
          {`일정에 넣지 않은 장소 ${roomPlaceIds.length}곳: ${roomPlaceIds.map((roomPlaceId) => {
            return getPlaceName(places, roomPlaceId);
          }).join(", ")}`}
        </Text>
      </View>
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
