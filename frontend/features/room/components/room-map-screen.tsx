import { router } from "expo-router";
import { useState, type ReactNode } from "react";
import { View } from "react-native";

import { Button } from "@/components/ui/button";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import { useAllRoomPlaces, useRoomPlaces } from "@/features/room/api/queries";
import { MapViewToggle } from "@/features/room/components/map-view-toggle";
import { PlaceRouteMap, type RoutePlace } from "@/features/room/components/place-route-map";
import type { PlaceTab } from "@/features/room/components/place-tabs";
import { toDayChipItems } from "@/features/room/components/room-detail-schedule";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import type { RoomPlace } from "@/features/room/model";
import { useRoomSchedule } from "@/features/schedule/api/queries";
import { DayChips } from "@/features/schedule/components/day-chips";
import { DayPickerSheet } from "@/features/schedule/components/day-picker-sheet";
import { getScheduleErrorMessage } from "@/features/schedule/errors";
import { toPlaceLookup, toScheduleMapStops, type RoomSchedule, type SchedulePlaceLookup } from "@/features/schedule/model";
import type { LocalDate } from "@/lib/date";

const TITLES: Record<PlaceTab, string> = { places: "후보 장소 지도", schedule: "일정 지도" };

/** 여행방 상세의 같은 탭으로 돌아간다. 돌아갈 화면이 없으면(딥링크 진입) 상세로 바꿔 이동하고, 여행방을 모르면 목록으로 간다. */
function goToList(roomId: number | null, view: PlaceTab) {
  if (router.canGoBack()) {
    router.back();
    return;
  }

  if (roomId === null) {
    router.replace("/rooms");
    return;
  }

  router.replace({ pathname: "/rooms/[roomId]", params: { roomId: String(roomId), tab: view } });
}

/** 후보 장소의 지도 표시. 보조 줄은 `카페 · 강문동`(카테고리 이름 · 지역)이다. */
function toCandidateRoutePlace(place: RoomPlace): RoutePlace {
  return {
    id: String(place.id),
    name: place.name,
    caption: [place.category?.name, place.areaName].filter(Boolean).join(" · "),
    coordinate: place.coordinate,
  };
}

type RoomMapScreenProps = {
  /** 검증한 라우트 파라미터. 잘못된 id면 `null`. */
  roomId: number | null;
  /** 지도로 볼 대상. 여행방 상세의 탭과 같다. */
  view: PlaceTab;
  /** 일정 지도를 열 때 처음 보여 줄 날(일정 탭에서 보고 있던 날). 없거나 일정에 없는 날이면 첫날이다. */
  initialDate: LocalDate | null;
};

/**
 * 여행방 "지도로 보기"(TRP-07). 여행방 상세의 장소 탭에서 오면 후보 장소를, 일정 탭에서 오면 고른 날의 일정을 방문 순서대로 지도에 보여 준다.
 * Figma에는 진입 버튼(`Map view toggle`)만 있고 이 화면의 시안은 없다.
 */
export function RoomMapScreen({ roomId, view, initialDate }: RoomMapScreenProps) {
  const back = () => {
    goToList(roomId, view);
  };

  const renderMap = () => {
    if (roomId === null) {
      return <MapMessage message="여행방을 찾을 수 없어요." onBack={back} />;
    }

    return view === "schedule" ? <ScheduleMap roomId={roomId} initialDate={initialDate} onBack={back} /> : <CandidatePlacesMap roomId={roomId} onBack={back} />;
  };

  return (
    <Screen>
      <ScreenHeader title={TITLES[view]} onBack={back} testID="room-map.header" />
      {renderMap()}
    </Screen>
  );
}

// #region 후보 장소

function CandidatePlacesMap({ roomId, onBack }: { roomId: number; onBack: () => void }) {
  const placesQuery = useRoomPlaces(roomId);

  if (placesQuery.data) {
    if (placesQuery.data.length === 0) {
      return <MapMessage message="아직 담은 장소가 없어요." onBack={onBack} />;
    }

    return (
      <PlaceRouteMap
        places={placesQuery.data.map(toCandidateRoutePlace)}
        connected={false}
        footer={<MapViewToggle target="list" onPress={onBack} testID="room-map.list-toggle" />}
        accessibilityLabel="후보 장소를 번호 핀으로 표시한 지도"
        testID="room-map.places"
      />
    );
  }

  if (placesQuery.isError) {
    return (
      <MapMessage
        message={getRoomErrorMessage(placesQuery.error, ROOM_ERROR_FALLBACK.load)}
        retrying={placesQuery.isFetching}

        onRetry={() => {
          void placesQuery.refetch();
        }}

        onBack={onBack}
      />
    );
  }

  return <MapLoading />;
}

// #endregion

// #region 일정

function ScheduleMap({ roomId, initialDate, onBack }: { roomId: number; initialDate: LocalDate | null; onBack: () => void }) {
  const scheduleQuery = useRoomSchedule(roomId);
  const placesQuery = useAllRoomPlaces(roomId);

  if (scheduleQuery.data !== undefined && placesQuery.data) {
    if (scheduleQuery.data === null || scheduleQuery.data.days.length === 0) {
      return <MapMessage message="아직 일정이 없어요." onBack={onBack} />;
    }

    return <ScheduleDayMap schedule={scheduleQuery.data} places={toPlaceLookup(placesQuery.data)} initialDate={initialDate} onBack={onBack} />;
  }

  if (scheduleQuery.isError || placesQuery.isError) {
    const message = scheduleQuery.isError ? getScheduleErrorMessage(scheduleQuery.error) : getRoomErrorMessage(placesQuery.error, ROOM_ERROR_FALLBACK.load);

    return (
      <MapMessage
        message={message}
        retrying={scheduleQuery.isFetching || placesQuery.isFetching}

        onRetry={() => {
          if (scheduleQuery.isError) {
            void scheduleQuery.refetch();
          }

          if (placesQuery.isError) {
            void placesQuery.refetch();
          }
        }}

        onBack={onBack}
      />
    );
  }

  return <MapLoading />;
}

type ScheduleDayMapProps = {
  /** 하루 이상 있는 일정. */
  schedule: RoomSchedule;
  /** 뺀 후보까지 포함한 후보 장소. 일정 항목의 이름과 좌표를 찾는다. */
  places: SchedulePlaceLookup;
  /** 처음 보여 줄 날. 일정에 없는 날이면 첫날을 보여 준다. */
  initialDate: LocalDate | null;
  onBack: () => void;
};

/** 날짜 칩으로 고른 날의 일정을 방문 순서대로 잇는다. 처음에는 일정 탭에서 보고 있던 날을, 없으면 첫날을 보여 준다. */
function ScheduleDayMap({ schedule, places, initialDate, onBack }: ScheduleDayMapProps) {
  const [pickedDate, setPickedDate] = useState<LocalDate | null>(initialDate);
  const [pickerOpen, setPickerOpen] = useState(false);

  const selectedDay = schedule.days.find((day) => {
    return day.date === pickedDate;
  }) ?? schedule.days[0];

  const chipItems = toDayChipItems(schedule.days);
  const routePlaces: RoutePlace[] = toScheduleMapStops(selectedDay.items, places);
  const listToggle = <MapViewToggle target="list" onPress={onBack} testID="room-map.list-toggle" />;

  return (
    <>
      <DayChips
        items={chipItems}
        selectedDate={selectedDay.date}
        onSelect={setPickedDate}

        onOpenPicker={() => {
          setPickerOpen(true);
        }}

        testID="room-map.days"
      />
      {routePlaces.length === 0 ? (
        <View className="flex-1 items-center justify-center gap-4 px-5" testID="room-map.empty-day">
          <Text className="text-center text-body-sm text-foreground-secondary">이날은 아직 넣은 장소가 없어요.</Text>
          {listToggle}
        </View>
      ) : (
        <PlaceRouteMap
          places={routePlaces}
          connected
          footer={listToggle}
          accessibilityLabel="고른 날의 일정을 방문 순서대로 이은 지도"
          testID="room-map.schedule"
        />
      )}
      <DayPickerSheet
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        items={chipItems}
        selectedDate={selectedDay.date}
        onSelect={setPickedDate}
        testID="room-map.day-picker"
      />
    </>
  );
}

// #endregion

// #region 상태

function MapLoading() {
  return (
    <View
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel="지도에 표시할 장소를 불러오는 중"
      accessibilityState={{ busy: true }}
      className="flex-1 items-center justify-center bg-brand-muted"
      testID="room-map.loading"
    >
      <Spinner tone="brand" accessible={false} />
    </View>
  );
}

type MapMessageProps = {
  message: string;
  /** 다시 시도해 풀릴 수 있는 오류일 때만 준다. */
  onRetry?: () => void;
  retrying?: boolean;
  onBack: () => void;
};

/** 지도 대신 보여 주는 안내(여행방 없음, 표시할 장소 없음, 불러오기 실패). 목록으로 돌아가는 버튼을 함께 둔다. */
function MapMessage({ message, onRetry, retrying = false, onBack }: MapMessageProps): ReactNode {
  return (
    <View className="flex-1 items-center justify-center gap-4 px-5 pb-12" testID="room-map.message">
      <Text accessibilityRole={onRetry === undefined ? undefined : "alert"} className="text-center text-body-sm text-foreground-secondary">
        {message}
      </Text>
      {onRetry === undefined ? null : (
        <Button variant="outline" loading={retrying} loadingLabel="불러오는 중" onPress={onRetry} testID="room-map.retry">
          <Text>다시 시도</Text>
        </Button>
      )}
      <MapViewToggle target="list" onPress={onBack} testID="room-map.back-to-list" />
    </View>
  );
}

// #endregion
