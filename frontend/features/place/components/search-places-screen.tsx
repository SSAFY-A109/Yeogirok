import { router } from "expo-router";
import { ChevronLeft } from "lucide-react-native";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { FlatList, KeyboardAvoidingView, Platform, Pressable, View } from "react-native";

import { isApiError } from "@/api/errors";
import { createIdempotencyKey } from "@/api/idempotency";
import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { Screen } from "@/components/ui/screen";
import { HeaderIconButton } from "@/components/ui/screen-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import { TextField } from "@/components/ui/text-field";
import { useToast } from "@/components/ui/toast";
import { useCategories } from "@/features/common/api/queries";
import { useSearchPlaces } from "@/features/place/api/queries";
import { MergedSearchResults, type MergedSearchState } from "@/features/place/components/merged-search-results";
import { PlaceSearchMap } from "@/features/place/components/place-search-map";
import { PlaceSearchRow, type PlaceSearchRowState } from "@/features/place/components/place-search-row";
import { getPlaceErrorMessage } from "@/features/place/errors";
import {
  findCategoryName, isMapPlaceAdded, mergeSearchResults, toCustomPlaceRequest,
  type MapPlace, type MergedSearchItem, type Place, type PlaceRef,
} from "@/features/place/model";
import { useAddCustomPlace, useAddRoomPlaces, useRoomPlaces } from "@/features/room/api/queries";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import type { AddRoomPlaceItemDto } from "@/features/room/model";
import { useIdempotencyKeyFor } from "@/hooks/use-idempotency-key";
import { KakaoPlaceSearch, type KakaoPlace } from "@/lib/native/kakao-place-search";

/** 입력이 멈춘 뒤 검색을 보내기까지 기다리는 시간. */
const SEARCH_DEBOUNCE_MS = 300;
const SKELETON_ROW_COUNT = 4;
const HTTP_NOT_FOUND = 404;
const PARTIAL_ADD_ERROR = "일부 장소를 담지 못했어요. 다시 시도해 주세요.";
const kakaoJavascriptKey = process.env.EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY?.trim();

/** 카카오 장소 검색 한 번의 결과. 검색어·다시 시도 횟수가 지금과 같을 때만 쓴다. 실패하면 `results`가 `null`이다. */
type MapSearchResult = { keyword: string; attempt: number; results: KakaoPlace[] | null };

/** `off`: 카카오 키가 없거나 검색어가 없어 지도 검색을 하지 않는다. */
type MapSearchState = { status: "off" } | MergedSearchState;

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

  const [selectedRefs, setSelectedRefs] = useState<ReadonlyMap<string, PlaceRef>>(() => {
    return new Map();
  });

  const [selectedMapPlaces, setSelectedMapPlaces] = useState<ReadonlyMap<string, MapPlace>>(() => {
    return new Map();
  });

  const [mapSearch, setMapSearch] = useState<MapSearchResult | null>(null);
  const [mapAttempt, setMapAttempt] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const customPlaceKeys = useRef(new Map<string, string>());

  const search = useSearchPlaces(keyword);
  const categories = useCategories();
  const roomPlaces = useRoomPlaces(roomId);
  const addRoomPlaces = useAddRoomPlaces();
  const addCustomPlace = useAddCustomPlace();
  const getIdempotencyKey = useIdempotencyKeyFor();

  useEffect(() => {
    const timer = setTimeout(() => {
      setKeyword(input.trim());
    }, SEARCH_DEBOUNCE_MS);

    return () => {
      clearTimeout(timer);
    };
  }, [input]);

  const handleMapResult = useCallback((results: KakaoPlace[] | null) => {
    setMapSearch({ keyword, attempt: mapAttempt, results });
  }, [keyword, mapAttempt]);

  const places = useMemo(() => {
    return search.data?.pages.flatMap((page) => {
      return page.places;
    }) ?? [];
  }, [search.data]);

  if (roomPlaces.isError && isNotFoundError(roomPlaces.error)) {
    return <SearchRoomNotFound />;
  }

  const totalCount = search.data?.pages[0]?.totalCount ?? places.length;
  const roomPlaceList = roomPlaces.data ?? [];

  const addedPlaceKeys = new Set(roomPlaceList.map((place) => {
    return place.placeKey;
  }));

  const newItems: AddRoomPlaceItemDto[] = [...selectedRefs]
    .filter(([placeKey]) => {
      return !addedPlaceKeys.has(placeKey);
    })
    .map(([, placeRef]) => {
      return { placeRef, sourceType: "SEARCH" };
    });

  const newMapPlaces = [...selectedMapPlaces.values()].filter((place) => {
    return !isMapPlaceAdded(place, roomPlaceList);
  });

  const newCount = newItems.length + newMapPlaces.length;
  const isMapSearchCurrent = mapSearch?.keyword === keyword && mapSearch.attempt === mapAttempt;
  // 지도 결과는 투어API 첫 쪽과만 맞춘다. 다음 쪽을 받을 때 이미 보인 줄이 바뀌지 않게 하려는 것이다.
  const firstPagePlaces = search.data?.pages[0]?.places ?? [];
  const mapState = getMapSearchState();
  const mergedItems = mapState.status === "ready" ? mapState.items : [];
  const mapHasNoPlaces = mapState.status === "off" || mapState.status === "failed" || (mapState.status === "ready" && mergedItems.length === 0);

  const mergedTourKeys = new Set(mergedItems.flatMap((item) => {
    return item.kind === "tour" ? [item.place.key] : [];
  }));

  const listTourPlaces = places.filter((place) => {
    return !mergedTourKeys.has(place.key);
  });

  function getMapSearchState(): MapSearchState {
    if (!kakaoJavascriptKey || keyword === "") {
      return { status: "off" };
    }

    if (mapSearch === null || !isMapSearchCurrent) {
      return { status: "searching" };
    }

    return mapSearch.results === null ? { status: "failed" } : { status: "ready", items: mergeSearchResults(mapSearch.results, firstPagePlaces) };
  }

  function getRowState(place: Place): PlaceSearchRowState {
    if (addedPlaceKeys.has(place.key)) {
      return "added";
    }

    if (place.ref === null) {
      return "unavailable";
    }

    return selectedRefs.has(place.key) ? "selected" : "unselected";
  }

  function getMapRowState(place: MapPlace): PlaceSearchRowState {
    if (isMapPlaceAdded(place, roomPlaceList)) {
      return "added";
    }

    return selectedMapPlaces.has(place.key) ? "selected" : "unselected";
  }

  function togglePlace(place: Place) {
    const { key, ref } = place;

    if (ref === null) {
      return;
    }

    setSelectedRefs((previous) => {
      const next = new Map(previous);

      if (!next.delete(key)) {
        next.set(key, ref);
      }

      return next;
    });
  }

  function toggleMapPlace(place: MapPlace) {
    setSelectedMapPlaces((previous) => {
      const next = new Map(previous);

      if (!next.delete(place.key)) {
        next.set(place.key, place);
      }

      return next;
    });
  }

  function retryMapSearch() {
    setMapAttempt((attempt) => {
      return attempt + 1;
    });
  }

  function loadMore() {
    if (search.hasNextPage && !search.isFetchingNextPage && !search.isFetchNextPageError) {
      void search.fetchNextPage();
    }
  }

  /** 같은 장소를 다시 보내면(재시도) 같은 멱등키를 써서 서버가 두 번 담지 않게 한다. */
  function getCustomPlaceKey(place: MapPlace): string {
    const signature = JSON.stringify(toCustomPlaceRequest(place));
    const saved = customPlaceKeys.current.get(signature);

    if (saved !== undefined) {
      return saved;
    }

    const key = createIdempotencyKey();
    customPlaceKeys.current.set(signature, key);
    return key;
  }

  /** 투어API 장소는 한 번에(API-029), 지도에서 찾은 장소는 하나씩(API-031) 담는다. 하나라도 실패하면 화면에 남는다. */
  async function addSelectedPlaces() {
    let addedCount = 0;
    setSubmitting(true);
    setSubmitError(null);

    try {
      if (newItems.length > 0) {
        const { places: addedPlaces } = await addRoomPlaces.mutateAsync({
          roomId, items: newItems, idempotencyKey: getIdempotencyKey(JSON.stringify(newItems)),
        });

        addedCount += addedPlaces.length;
        setSelectedRefs(new Map());
      }

      for (const place of newMapPlaces) {
        await addCustomPlace.mutateAsync({ roomId, request: toCustomPlaceRequest(place), idempotencyKey: getCustomPlaceKey(place) });
        addedCount += 1;

        setSelectedMapPlaces((previous) => {
          const next = new Map(previous);
          next.delete(place.key);
          return next;
        });
      }
    } catch (error) {
      setSubmitError(addedCount > 0 ? PARTIAL_ADD_ERROR : getRoomErrorMessage(error, ROOM_ERROR_FALLBACK.addPlaces));
      setSubmitting(false);
      return;
    }

    toast.show({ message: `${addedCount}곳을 담았어요` });
    router.back();
  }

  function renderMergedItem(item: MergedSearchItem, index: number) {
    if (item.kind === "tour") {
      return (
        <PlaceSearchRow
          place={item.place}
          categoryName={findCategoryName(categories.data, item.place.categoryCode)}
          state={getRowState(item.place)}
          onToggle={togglePlace}
        />
      );
    }

    return (
      <PlaceSearchRow
        place={item.place}
        categoryName={null}
        state={getMapRowState(item.place)}
        onToggle={toggleMapPlace}
        testID={`place-search.map-row-${index}`}
      />
    );
  }

  /** 투어API 목록 위의 제목. 지도 결과가 있으면 그와 겹치지 않은 나머지라는 뜻으로 부른다. */
  function getTourListTitle(): string | null {
    if (listTourPlaces.length === 0) {
      return null;
    }

    if (mapState.status === "off") {
      return `검색 결과 ${totalCount}곳`;
    }

    return mapState.status === "ready" && mergedItems.length > 0 ? "관광 정보에서 더 찾은 곳" : `관광 정보 ${totalCount}곳`;
  }

  function renderTourHeader() {
    if (search.isError && search.data === undefined) {
      return (
        <View className="items-start gap-2 px-5 pt-3" testID="place-search.tour-error">
          <Text accessibilityRole="alert" className="text-caption text-foreground-secondary">
            {getPlaceErrorMessage(search.error)}
          </Text>
          <Button
            variant="outline"
            loading={search.isFetching}
            loadingLabel="불러오는 중"
            onPress={() => {
              void search.refetch();
            }}
            testID="place-search.retry"
          >
            <Text>다시 시도</Text>
          </Button>
        </View>
      );
    }

    const title = getTourListTitle();

    if (title === null) {
      return null;
    }

    return (
      <Text className="px-5 pb-1 pt-5 text-label text-foreground-secondary" testID="place-search.count">
        {title}
      </Text>
    );
  }

  function renderBody() {
    if (keyword === "") {
      return <SearchMessage title="어떤 장소를 담을까요?" description="장소 이름이나 지역을 입력해 보세요." />;
    }

    const tourFailed = search.data === undefined && search.isError;

    if (search.data === undefined && !tourFailed) {
      return <SearchSkeleton />;
    }

    if (tourFailed && mapHasNoPlaces) {
      return (
        <SearchError
          message={getPlaceErrorMessage(search.error)}
          retrying={search.isFetching}
          onRetry={() => {
            void search.refetch();

            if (mapState.status === "failed") {
              retryMapSearch();
            }
          }}
        />
      );
    }

    if (!tourFailed && places.length === 0 && (mapState.status === "off" || (mapState.status === "ready" && mergedItems.length === 0))) {
      return <SearchMessage title="검색 결과가 없어요" description="다른 이름이나 지역으로 검색해 보세요." />;
    }

    return (
      <FlatList
        className="flex-1"
        contentContainerClassName="pb-4"
        data={listTourPlaces}
        extraData={{ selectedRefs, selectedMapPlaces, addedPlaceKeys, categories: categories.data }}
        keyExtractor={(place) => {
          return place.key;
        }}
        renderItem={({ item }) => {
          return (
            <PlaceSearchRow
              place={item}
              categoryName={findCategoryName(categories.data, item.categoryCode)}
              state={getRowState(item)}
              onToggle={togglePlace}
            />
          );
        }}
        ItemSeparatorComponent={RowSeparator}
        ListHeaderComponent={
          <>
            {mapState.status === "off" ? null : (
              <MergedSearchResults state={mapState} renderItem={renderMergedItem} onRetry={retryMapSearch} />
            )}
            {renderTourHeader()}
          </>
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
      {kakaoJavascriptKey && mapState.status === "searching" ? (
        <KakaoPlaceSearch key={`${keyword}:${mapAttempt}`} javascriptKey={kakaoJavascriptKey} keyword={keyword} onResult={handleMapResult} />
      ) : null}
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
        {submitError === null ? null : (
          <Text accessibilityRole="alert" className="text-center text-caption text-destructive">
            {submitError}
          </Text>
        )}
        <Button
          size="lg"
          className="w-full"
          disabled={newCount === 0 || !roomPlaces.isSuccess}
          loading={submitting}
          loadingLabel="담는 중"
          onPress={() => {
            void addSelectedPlaces();
          }}
          testID="place-search.submit"
        >
          <Text>{`${newCount}곳 담기`}</Text>
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
