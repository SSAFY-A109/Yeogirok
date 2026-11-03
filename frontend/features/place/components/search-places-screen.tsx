import { router } from "expo-router";
import { ChevronLeft } from "lucide-react-native";
import { useEffect, useMemo, useState } from "react";
import { FlatList, KeyboardAvoidingView, Platform, Pressable, View } from "react-native";

import { isApiError } from "@/api/errors";
import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { Screen } from "@/components/ui/screen";
import { HeaderIconButton } from "@/components/ui/screen-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import { TextField } from "@/components/ui/text-field";
import { useToast } from "@/components/ui/toast";
import { useSearchPlaces } from "@/features/place/api/queries";
import { PlaceSearchMap } from "@/features/place/components/place-search-map";
import { PlaceSearchRow, type PlaceSearchRowState } from "@/features/place/components/place-search-row";
import { getPlaceErrorMessage } from "@/features/place/errors";
import { useAddRoomPlaces, useRoomPlaces } from "@/features/room/api/queries";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";

/** 입력이 멈춘 뒤 검색을 보내기까지 기다리는 시간. */
const SEARCH_DEBOUNCE_MS = 300;
const SKELETON_ROW_COUNT = 4;
const HTTP_NOT_FOUND = 404;

function goBack() {
  router.back();
}

function isNotFoundError(error: unknown): boolean {
  return isApiError(error) && error.status === HTTP_NOT_FOUND;
}

/** 여행지 검색 담기(Figma W12). `roomId`가 `null`이면 여행방을 찾을 수 없다는 안내를 보여 준다. */
export function SearchPlacesScreen({ roomId }: { roomId: number | null }) {
  return <Screen>{roomId === null ? <SearchRoomNotFound /> : <SearchPlacesContent roomId={roomId} />}</Screen>;
}

function SearchPlacesContent({ roomId }: { roomId: number }) {
  const toast = useToast();
  const [input, setInput] = useState("");
  const [keyword, setKeyword] = useState("");

  const [selectedPlaceIds, setSelectedPlaceIds] = useState<ReadonlySet<number>>(() => {
    return new Set();
  });

  const search = useSearchPlaces(keyword);
  const roomPlaces = useRoomPlaces(roomId);
  const addRoomPlaces = useAddRoomPlaces();

  useEffect(() => {
    const timer = setTimeout(() => {
      setKeyword(input.trim());
    }, SEARCH_DEBOUNCE_MS);

    return () => {
      clearTimeout(timer);
    };
  }, [input]);

  const places = useMemo(() => {
    return search.data?.pages.flatMap((page) => {
      return page.places;
    }) ?? [];
  }, [search.data]);

  if (roomPlaces.isError && isNotFoundError(roomPlaces.error)) {
    return <SearchRoomNotFound />;
  }

  const addedPlaceIds = new Set(roomPlaces.data?.map((place) => {
    return place.placeId;
  }));

  const newPlaceIds = [...selectedPlaceIds].filter((placeId) => {
    return !addedPlaceIds.has(placeId);
  });

  function getRowState(placeId: number): PlaceSearchRowState {
    if (addedPlaceIds.has(placeId)) {
      return "added";
    }

    return selectedPlaceIds.has(placeId) ? "selected" : "unselected";
  }

  function togglePlace(placeId: number) {
    setSelectedPlaceIds((previous) => {
      const next = new Set(previous);

      if (next.has(placeId)) {
        next.delete(placeId);
      } else {
        next.add(placeId);
      }

      return next;
    });
  }

  function loadMore() {
    if (search.hasNextPage && !search.isFetchingNextPage && !search.isFetchNextPageError) {
      void search.fetchNextPage();
    }
  }

  function addSelectedPlaces() {
    addRoomPlaces.mutate(
      { roomId, request: { placeIds: newPlaceIds, source: "SEARCH", recommendationRequestId: null } },
      {
        onSuccess: (addedPlaces) => {
          toast.show({ message: `${addedPlaces.length}곳을 담았어요` });
          router.back();
        },
      },
    );
  }

  function renderBody() {
    if (keyword === "") {
      return <SearchMessage title="어떤 장소를 담을까요?" description="장소 이름이나 지역을 입력해 보세요." />;
    }

    if (search.data === undefined) {
      if (search.isError) {
        return (
          <SearchError
            message={getPlaceErrorMessage(search.error)}
            retrying={search.isFetching}
            onRetry={() => {
              void search.refetch();
            }}
          />
        );
      }

      return <SearchSkeleton />;
    }

    if (places.length === 0) {
      return <SearchMessage title="검색 결과가 없어요" description="다른 이름이나 지역으로 검색해 보세요." />;
    }

    return (
      <FlatList
        className="flex-1"
        contentContainerClassName="pb-4"
        data={places}
        extraData={{ selectedPlaceIds, addedPlaceIds }}
        keyExtractor={(place) => {
          return String(place.id);
        }}
        renderItem={({ item }) => {
          return <PlaceSearchRow place={item} state={getRowState(item.id)} onToggle={togglePlace} />;
        }}
        ItemSeparatorComponent={RowSeparator}
        ListHeaderComponent={
          <Text className="px-5 pb-1 pt-3 text-label text-foreground-secondary" testID="place-search.count">
            {`검색 결과 ${places.length}곳`}
          </Text>
        }
        ListFooterComponent={
          <>
            {search.isFetchingNextPage ? (
              <View className="items-center py-4">
                <Spinner />
              </View>
            ) : null}
            {search.isFetchNextPageError ? (
              <View className="items-center gap-2 px-5 py-4">
                <Text accessibilityRole="alert" className="text-center text-caption text-foreground-secondary">
                  {getPlaceErrorMessage(search.error)}
                </Text>
                <Button
                  variant="outline"
                  onPress={() => {
                    void search.fetchNextPage();
                  }}
                  testID="place-search.more-retry"
                >
                  <Text>다시 시도</Text>
                </Button>
              </View>
            ) : null}
            <PlaceSearchMap places={places} />
          </>
        }
        onEndReached={loadMore}
        onEndReachedThreshold={0.5}
        keyboardDismissMode="on-drag"
        keyboardShouldPersistTaps="handled"
        testID="place-search.results"
      />
    );
  }

  return (
    <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : "height"} className="flex-1">
      <View className="flex-row items-center gap-1 py-2 pl-1 pr-5">
        <HeaderIconButton icon={ChevronLeft} accessibilityLabel="뒤로 가기" onPress={goBack} testID="place-search.back" />
        <TextField
          containerClassName="min-w-0 flex-1"
          value={input}
          onChangeText={setInput}
          onSubmitEditing={() => {
            setKeyword(input.trim());
          }}
          placeholder="장소 이름이나 지역 검색"
          accessibilityLabel="여행지 검색"
          autoFocus
          autoCorrect={false}
          returnKeyType="search"
          testID="place-search.input"
        />
      </View>
      <View className="flex-1">{renderBody()}</View>
      <BottomCta>
        {roomPlaces.isError ? (
          <View className="w-full flex-row items-center gap-2">
            <Text accessibilityRole="alert" className="flex-1 text-caption text-destructive">
              {getRoomErrorMessage(roomPlaces.error, ROOM_ERROR_FALLBACK.load)}
            </Text>
            <Pressable
              accessibilityRole="button"
              className="min-h-touch justify-center px-2"
              onPress={() => {
                void roomPlaces.refetch();
              }}
              testID="place-search.room-retry"
            >
              <Text className="text-label text-primary">다시 시도</Text>
            </Pressable>
          </View>
        ) : null}
        {addRoomPlaces.isError ? (
          <Text accessibilityRole="alert" className="text-center text-caption text-destructive">
            {getRoomErrorMessage(addRoomPlaces.error, ROOM_ERROR_FALLBACK.addPlaces)}
          </Text>
        ) : null}
        <Button
          size="lg"
          className="w-full"
          disabled={newPlaceIds.length === 0 || !roomPlaces.isSuccess}
          loading={addRoomPlaces.isPending}
          loadingLabel="담는 중"
          onPress={addSelectedPlaces}
          testID="place-search.submit"
        >
          <Text>{`${newPlaceIds.length}곳 담기`}</Text>
        </Button>
      </BottomCta>
    </KeyboardAvoidingView>
  );
}

// #region 상태 화면

function RowSeparator() {
  return <View className="mx-5 h-px bg-border" />;
}

function SearchSkeleton() {
  return (
    <View accessibilityLabel="검색 결과를 불러오는 중" accessibilityState={{ busy: true }} className="pt-3" testID="place-search.loading">
      <Skeleton className="mx-5 mb-2 h-5 w-24" />
      {Array.from({ length: SKELETON_ROW_COUNT }, (_, index) => {
        return (
          <View key={index} className="mx-5 flex-row items-center gap-3 py-3">
            <Skeleton className="size-6 rounded-xs" />
            <Skeleton className="h-input w-input" />
            <View className="flex-1 gap-2">
              <Skeleton className="h-4 w-32" />
              <Skeleton className="h-3 w-44" />
            </View>
          </View>
        );
      })}
    </View>
  );
}

function SearchMessage({ title, description }: { title: string; description: string }) {
  return (
    <View className="items-center gap-2 px-5 pt-16" testID="place-search.message">
      <Text className="text-center text-h3 text-foreground">{title}</Text>
      <Text className="text-center text-body-sm text-foreground-secondary">{description}</Text>
    </View>
  );
}

function SearchError({ message, retrying, onRetry }: { message: string; retrying: boolean; onRetry: () => void }) {
  return (
    <View className="items-center gap-4 px-5 pt-16" testID="place-search.error">
      <Text accessibilityRole="alert" className="text-center text-body-sm text-foreground-secondary">
        {message}
      </Text>
      <Button variant="outline" loading={retrying} loadingLabel="불러오는 중" onPress={onRetry} testID="place-search.retry">
        <Text>다시 시도</Text>
      </Button>
    </View>
  );
}

function SearchRoomNotFound() {
  return (
    <>
      <View className="flex-row py-2 pl-1">
        <HeaderIconButton icon={ChevronLeft} accessibilityLabel="뒤로 가기" onPress={goBack} testID="place-search.back" />
      </View>
      <View className="flex-1 items-center justify-center gap-4 px-5" testID="place-search.not-found">
        <Text accessibilityRole="alert" className="text-center text-h3 text-foreground">
          여행방을 찾을 수 없어요
        </Text>
        <Button variant="outline" onPress={goBack} testID="place-search.not-found-back">
          <Text>돌아가기</Text>
        </Button>
      </View>
    </>
  );
}

// #endregion
