import { Plus, Trash2 } from "lucide-react-native";
import type { ReactElement } from "react";
import { FlatList, Pressable, View } from "react-native";

import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { MediaPhoto } from "@/components/ui/media-photo";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useRoomPlaces } from "@/features/room/api/queries";
import { AddPlaceHeading, AddPlaceOptions, type AddPlaceSource } from "@/features/room/components/add-place-options";
import { MemberAvatar } from "@/features/room/components/member-avatar";
import { getVisibleRoomPlaces } from "@/features/room/components/room-place-delete";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import { getRoomPlaceSourceLabel, type RoomMember, type RoomPlace } from "@/features/room/model";
import { cn } from "@/lib/utils";

/** 32 폭 삭제 버튼의 터치 폭을 44로 넓히는 여백. */
const DELETE_BUTTON_HIT_SLOP = { left: 6, right: 6 };
const LOADING_ROW_COUNT = 3;

/** 장소 행 보조 줄. `카페 강문동 해솔님 코스`(카테고리 이름 + 동네 + 담은 경로). */
function getPlaceMeta(place: RoomPlace): string {
  return [place.category?.name, place.areaName, getRoomPlaceSourceLabel(place)].filter(Boolean).join(" ");
}

/** 담은 사람 아바타 색. 일행 목록 순서를 따라 화면마다 같은 색이 되게 한다. */
function getAddedByToneIndex(members: RoomMember[], userId: number): number {
  const index = members.findIndex((member) => {
    return member.id === userId;
  });

  return index === -1 ? userId : index;
}

type RoomPlaceListProps = {
  roomId: number;
  members: RoomMember[];
  hiddenIds: ReadonlySet<number>;
  /** 목록 위에 함께 스크롤되는 여행방 정보·탭. */
  header: ReactElement;
  onDeletePlace: (place: RoomPlace) => void;
  onAddPlace: () => void;
  onSelectSource: (source: AddPlaceSource) => void;
};

/** 여행방 상세의 "장소" 탭(Figma W09). 후보 장소가 없으면 담는 경로 세 가지를 목록 자리에 보여 준다. */
export function RoomPlaceList({ roomId, members, hiddenIds, header, onDeletePlace, onAddPlace, onSelectSource }: RoomPlaceListProps) {
  const placesQuery = useRoomPlaces(roomId);
  const places = placesQuery.data ? getVisibleRoomPlaces(placesQuery.data, hiddenIds) : [];

  const renderEmpty = () => {
    if (placesQuery.data) {
      return (
        <View className="gap-2 px-5 pt-2">
          <AddPlaceHeading />
          <AddPlaceOptions onSelect={onSelectSource} testIDPrefix="room-detail.empty-" />
        </View>
      );
    }

    if (placesQuery.isError) {
      return (
        <View className="items-center gap-4 px-5 py-8">
          <Text className="text-center text-body-sm text-foreground-secondary">
            {getRoomErrorMessage(placesQuery.error, ROOM_ERROR_FALLBACK.load)}
          </Text>
          <Button
            variant="outline"
            loading={placesQuery.isFetching}
            loadingLabel="불러오는 중"

            onPress={() => {
              void placesQuery.refetch();
            }}

            testID="room-detail.places-retry"
          >
            <Text>다시 시도</Text>
          </Button>
        </View>
      );
    }

    return <RoomPlaceListLoading />;
  };

  return (
    <FlatList
      data={places}
      keyExtractor={(place) => {
        return String(place.id);
      }}
      contentContainerClassName="pb-4"
      ListHeaderComponent={(
        <>
          {header}
          {places.length > 0 ? <RoomPlaceListHeader count={places.length} onAddPlace={onAddPlace} /> : null}
        </>
      )}
      ListEmptyComponent={renderEmpty()}

      renderItem={({ item, index }) => {
        return (
          <RoomPlaceRow
            place={item}
            toneIndex={item.addedBy ? getAddedByToneIndex(members, item.addedBy.id) : 0}
            showDivider={index < places.length - 1}

            onDelete={() => {
              onDeletePlace(item);
            }}
          />
        );
      }}
    />
  );
}

// #region 목록 머리·행·로딩

function RoomPlaceListHeader({ count, onAddPlace }: { count: number; onAddPlace: () => void }) {
  return (
    <View className="flex-row items-center justify-between pb-1 pl-5 pr-3">
      <Text accessibilityRole="header" className="text-label text-foreground">
        {`후보 장소 ${count}곳`}
      </Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="장소 담기"
        className="h-touch flex-row items-center gap-1 rounded-sm px-2 active:bg-accent"
        onPress={onAddPlace}
        testID="room-detail.add-place"
      >
        <Icon as={Plus} size={16} className="text-primary" />
        <Text className="text-label text-primary">장소 담기</Text>
      </Pressable>
    </View>
  );
}

type RoomPlaceRowProps = {
  place: RoomPlace;
  toneIndex: number;
  showDivider: boolean;
  onDelete: () => void;
};

function RoomPlaceRow({ place, toneIndex, showDivider, onDelete }: RoomPlaceRowProps) {
  const meta = getPlaceMeta(place);
  const addedByLabel = place.addedBy ? `${place.addedBy.nickname}님이 담음` : null;

  return (
    <View className="px-5">
      <View className={cn("flex-row items-center gap-2 py-3", showDivider && "border-b border-border")}>
        <MediaPhoto uri={place.photoUrl} className="h-input w-input" />
        <View
          accessible
          accessibilityLabel={[place.name, meta, addedByLabel].filter(Boolean).join(", ")}
          className="flex-1 flex-row items-center gap-2"
        >
          <View className="flex-1 gap-1">
            <Text className="text-label text-foreground" numberOfLines={1}>
              {place.name}
            </Text>
            {meta ? (
              <Text className="text-caption text-foreground-secondary" numberOfLines={1}>
                {meta}
              </Text>
            ) : null}
          </View>
          {place.addedBy ? <MemberAvatar initial={place.addedBy.initial} toneIndex={toneIndex} size="sm" /> : null}
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`${place.name} 후보에서 빼기`}
          className="h-touch w-8 items-center justify-center rounded-full active:bg-accent"
          hitSlop={DELETE_BUTTON_HIT_SLOP}
          onPress={onDelete}
          testID={`room-detail.place-delete-${place.id}`}
        >
          <Icon as={Trash2} size={20} className="text-icon" />
        </Pressable>
      </View>
    </View>
  );
}

function RoomPlaceListLoading() {
  return (
    <View
      accessibilityRole="progressbar"
      accessibilityLabel="후보 장소를 불러오는 중"
      accessibilityState={{ busy: true }}
      className="px-5"
    >
      {Array.from({ length: LOADING_ROW_COUNT }, (_, index) => {
        return (
          <View key={index} className="flex-row items-center gap-2 py-3">
            <Skeleton className="h-input w-input" />
            <View className="flex-1 gap-2">
              <Skeleton className="h-4 w-32 rounded-xs" />
              <Skeleton className="h-3 w-44 rounded-xs" />
            </View>
          </View>
        );
      })}
    </View>
  );
}

// #endregion
