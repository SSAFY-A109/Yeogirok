import { useMutation } from "@tanstack/react-query";
import { View } from "react-native";

import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { searchPlaces } from "@/features/place/api/place.api";
import { useRoomPlaces } from "@/features/room/api/queries";
import type { RoomPlace } from "@/features/room/model";
import type { RecordPlace } from "../model";
import { RecordIssue, RecordLoading } from "./record-list-screen";

async function getCandidateLocation(candidate: RoomPlace): Promise<RecordPlace | null> {
  const seen = new Set<string>();
  let cursor: string | null = null;

  do {
    const page = await searchPlaces({ query: candidate.name, cursor, limit: 20 });

    const place = page.places.find((item) => {
      return item.key === candidate.placeKey;
    });

    if (place) {
      return { ...place.coordinate, name: place.name, address: place.address, categoryCode: place.categoryCode,
        provider: "MANUAL", providerPlaceId: null, imageUrl: place.photoUrl, imageAttribution: null };
    }

    cursor = page.nextCursor;

    if (!cursor || seen.has(cursor)) {
      break;
    }

    seen.add(cursor);
  } while (cursor);

  return null;
}

export function DraftRoomPlaces({ roomId, includedIds, includedPlaces, onSelect, testIdPrefix = "memo.draft-room-place" }: {
  roomId: number; includedIds: ReadonlySet<number | null | undefined>; includedPlaces: RecordPlace[];
  onSelect: (place: RecordPlace, roomPlaceId: number) => void; testIdPrefix?: string;
}) {
  const query = useRoomPlaces(roomId);
  const location = useMutation({ mutationFn: getCandidateLocation });
  const candidates = query.data ?? [];

  const missing = candidates.filter((candidate) => {
    if (includedIds.has(candidate.id)) {
      return false;
    }

    const uniqueName = candidates.filter(({ name }) => {
      return name === candidate.name;
    }).length === 1;

    return !uniqueName || !includedPlaces.some(({ name, address }) => {
      return name === candidate.name && candidate.areaName !== null && address?.split(/\s+/).includes(candidate.areaName);
    });
  });

  return <View className="gap-3">
    <Text accessibilityRole="header" className="text-h3">여행방에 담은 장소</Text>
    {query.isPending ? <RecordLoading /> : query.isError && !query.data ? (
      <RecordIssue message="여행방에 담은 장소를 불러오지 못했어요." onRetry={() => {
        void query.refetch();
      }} />
    ) : missing.length === 0 ? <Text className="text-body-sm text-muted-foreground">추가할 여행방 장소가 없어요.</Text> : missing.map((candidate) => {
      return <Button key={candidate.id} variant="outline" className="justify-start" disabled={location.isPending}
        loading={location.isPending && location.variables.id === candidate.id} testID={`${testIdPrefix}-${candidate.id}`}
        onPress={() => {
          location.mutate(candidate, { onSuccess: (place) => {
            if (place) {
              onSelect(place, candidate.id);
            }
          } });
        }}>
        <View className="min-w-0 flex-1 gap-1">
          <Text numberOfLines={1}>{candidate.name}</Text>
          {candidate.areaName && <Text className="text-caption text-foreground-secondary">{candidate.areaName}</Text>}
        </View>
      </Button>;
    })}
    {location.isError && <RecordIssue message="장소 정보를 불러오지 못했어요. 다시 선택해 주세요." />}
    {location.data === null && <RecordIssue message="이 장소의 위치를 확인하지 못했어요. 장소 검색에서 직접 골라 주세요." />}
  </View>;
}
