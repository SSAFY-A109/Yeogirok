import { router, usePreventRemove } from "expo-router";
import { useState } from "react";
import { View } from "react-native";

import { isApiError } from "@/api/errors";
import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { useCategories, useRegions } from "@/features/common/api/queries";
import type { Category, Region } from "@/features/common/model";
import { useRecommendationPreference, useRecommendPlaces } from "@/features/recommendation/api/queries";
import {
  getConditionLabels,
  getInitialConditions,
  getMissingConditionMessage,
  getSelectedCategoryNames,
  RecommendConditions,
  toRecommendationConditions,
  type RecommendConditionsValue,
} from "@/features/recommendation/components/recommend-conditions";
import { RecommendLoading } from "@/features/recommendation/components/recommend-loading";
import { RecommendResults } from "@/features/recommendation/components/recommend-results";
import { getRecommendationErrorMessage } from "@/features/recommendation/errors";
import type { PlaceRecommendations, RecommendationPreference } from "@/features/recommendation/model";
import { useAddRoomPlaces, useRoom, useRoomPlaces } from "@/features/room/api/queries";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import type { AddRoomPlaceItemDto, RoomDetail, RoomPlace } from "@/features/room/model";
import { useIdempotencyKeyFor } from "@/hooks/use-idempotency-key";

const CONDITIONS_TITLE = "AI 장소 추천";
const RESULTS_TITLE = "추천 장소";
const SKELETON_CHIP_COUNT = 6;
const HTTP_NOT_FOUND = 404;

/** `conditions`: 조건 입력(W10). `recommend`: 추천 요청 상태에 따라 처리 중·실패(W10-1) 또는 결과(W11). */
type RecommendStep = "conditions" | "recommend";

function goBack() {
  router.back();
}

/** AI 장소 추천(Figma W10 → W10-1 → W11). 한 라우트 안에서 단계를 바꾼다. */
export function RecommendScreen({ roomId }: { roomId: number | null }) {
  return roomId === null ? <RecommendRoomNotFound /> : <RecommendDataGate roomId={roomId} />;
}

/** 조건 화면에 필요한 여행방·기준 데이터·저장된 조건을 모두 받은 뒤 추천 흐름을 연다. */
function RecommendDataGate({ roomId }: { roomId: number }) {
  const room = useRoom(roomId);
  const roomPlaces = useRoomPlaces(roomId);
  const categories = useCategories();
  const regions = useRegions();
  const preference = useRecommendationPreference(roomId);

  if (room.isError && isApiError(room.error) && room.error.status === HTTP_NOT_FOUND) {
    return <RecommendRoomNotFound />;
  }

  const failedQueries = [room, roomPlaces, categories, regions, preference].filter((query) => {
    return query.isError && query.data === undefined;
  });

  const firstFailure = failedQueries[0];

  if (firstFailure !== undefined) {
    return (
      <Screen>
        <ScreenHeader title={CONDITIONS_TITLE} testID="recommend.header" />
        <View className="flex-1 items-center justify-center gap-4 px-5" testID="recommend.load-error">
          <Text accessibilityRole="alert" className="text-center text-body-sm text-foreground-secondary">
            {getRoomErrorMessage(firstFailure.error, ROOM_ERROR_FALLBACK.load)}
          </Text>
          <Button
            variant="outline"
            loading={failedQueries.some((query) => {
              return query.isFetching;
            })}
            loadingLabel="불러오는 중"
            onPress={() => {
              failedQueries.forEach((query) => {
                void query.refetch();
              });
            }}
            testID="recommend.load-retry"
          >
            <Text>다시 시도</Text>
          </Button>
        </View>
      </Screen>
    );
  }

  if (room.data === undefined || roomPlaces.data === undefined || categories.data === undefined || regions.data === undefined || preference.data === undefined) {
    return <RecommendSkeleton />;
  }

  return (
    <RecommendFlow
      roomId={roomId}
      room={room.data}
      roomPlaces={roomPlaces.data}
      categories={categories.data}
      regions={regions.data}
      preference={preference.data}
    />
  );
}

type RecommendFlowProps = {
  roomId: number;
  room: RoomDetail;
  roomPlaces: readonly RoomPlace[];
  categories: readonly Category[];
  regions: readonly Region[];
  preference: RecommendationPreference | null;
};

function RecommendFlow({ roomId, room, roomPlaces, categories, regions, preference }: RecommendFlowProps) {
  const toast = useToast();
  const [step, setStep] = useState<RecommendStep>("conditions");

  const [conditions, setConditions] = useState<RecommendConditionsValue>(() => {
    return getInitialConditions(preference, categories, regions);
  });

  const [excludedPlaceKeys, setExcludedPlaceKeys] = useState<ReadonlySet<string>>(() => {
    return new Set();
  });

  const recommendPlaces = useRecommendPlaces(roomId);
  const addRoomPlaces = useAddRoomPlaces();
  const getRecommendationKey = useIdempotencyKeyFor();
  const getAddPlacesKey = useIdempotencyKeyFor();
  const missingConditionMessage = getMissingConditionMessage(conditions);

  function returnToConditions() {
    if (addRoomPlaces.isPending) {
      return;
    }

    recommendPlaces.reset();
    addRoomPlaces.reset();
    setStep("conditions");
  }

  const disableBackPrevention = usePreventRemove(step === "recommend" && !addRoomPlaces.isSuccess, () => {
    returnToConditions();
  });

  const addedPlaceKeys = new Set(roomPlaces.map((place) => {
    return place.placeKey;
  }));

  function requestRecommendation() {
    const recommendationConditions = toRecommendationConditions(conditions);

    if (recommendationConditions === null) {
      return;
    }

    setExcludedPlaceKeys(new Set());
    addRoomPlaces.reset();
    setStep("recommend");

    recommendPlaces.mutate({
      conditions: recommendationConditions,
      idempotencyKey: getRecommendationKey(JSON.stringify(recommendationConditions)),
    });
  }

  function togglePlace(placeKey: string) {
    setExcludedPlaceKeys((previous) => {
      const next = new Set(previous);

      if (!next.delete(placeKey)) {
        next.add(placeKey);
      }

      return next;
    });
  }

  /** 고른 추천 장소를 담기 요청 항목으로 바꾼다. 이미 담긴 곳, 뺀 곳, 담을 수 없는 곳은 넣지 않는다. */
  function getSelectedItems(result: PlaceRecommendations): AddRoomPlaceItemDto[] {
    const items: AddRoomPlaceItemDto[] = [];

    for (const { place, rank } of result.places) {
      if (place.ref !== null && !addedPlaceKeys.has(place.key) && !excludedPlaceKeys.has(place.key)) {
        items.push({ placeRef: place.ref, sourceType: "AI", recommendationRequestId: result.requestId, rank });
      }
    }

    return items;
  }

  function addPlaces(items: AddRoomPlaceItemDto[]) {
    addRoomPlaces.mutate(
      { roomId, items, idempotencyKey: getAddPlacesKey(JSON.stringify(items)) },
      {
        onSuccess: ({ places: addedPlaces }) => {
          toast.show({ message: `${addedPlaces.length}곳을 담았어요` });
          disableBackPrevention();
          router.back();
        },
      },
    );
  }

  function renderStep() {
    if (step === "conditions") {
      return (
        <>
          <RecommendConditions
            room={room}
            placeCount={roomPlaces.length}
            categories={categories}
            regions={regions}
            value={conditions}
            onChange={setConditions}
          />
          <BottomCta>
            {missingConditionMessage !== null ? (
              <Text className="text-center text-caption text-foreground-secondary" testID="recommend.requirement">
                {missingConditionMessage}
              </Text>
            ) : null}
            <Button
              size="lg"
              className="w-full"
              disabled={missingConditionMessage !== null}
              onPress={requestRecommendation}
              testID="recommend.submit"
            >
              <Text>추천 받기</Text>
            </Button>
          </BottomCta>
        </>
      );
    }

    const result = recommendPlaces.data;

    if (result !== undefined) {
      const selectedItems = getSelectedItems(result);

      return (
        <>
          <RecommendResults
            result={result}
            categories={categories}
            criteria={getSelectedCategoryNames(conditions, categories)}
            addedPlaceKeys={addedPlaceKeys}
            excludedPlaceKeys={excludedPlaceKeys}
            onToggle={togglePlace}
            onReselect={returnToConditions}
          />
          {result.places.length > 0 ? (
            <BottomCta>
              {addRoomPlaces.isError ? (
                <Text accessibilityRole="alert" className="text-center text-caption text-destructive">
                  {getRoomErrorMessage(addRoomPlaces.error, ROOM_ERROR_FALLBACK.addPlaces)}
                </Text>
              ) : null}
              <Button
                size="lg"
                className="w-full"
                disabled={selectedItems.length === 0}
                loading={addRoomPlaces.isPending}
                loadingLabel="담는 중"
                onPress={() => {
                  addPlaces(selectedItems);
                }}
                testID="recommend.add"
              >
                <Text>{`${selectedItems.length}곳 담기`}</Text>
              </Button>
            </BottomCta>
          ) : null}
        </>
      );
    }

    const conditionLabels = getConditionLabels(conditions, categories, regions);

    if (recommendPlaces.isError) {
      return (
        <RecommendLoading
          status="error"
          placeCount={roomPlaces.length}
          conditionLabels={conditionLabels}
          errorMessage={getRecommendationErrorMessage(recommendPlaces.error)}
          onRetry={requestRecommendation}
        />
      );
    }

    return (
      <>
        <RecommendLoading
          status="pending"
          placeCount={roomPlaces.length}
          conditionLabels={conditionLabels}
          errorMessage=""
          onRetry={requestRecommendation}
        />
        <BottomCta>
          <Button size="lg" variant="outline" className="w-full" onPress={returnToConditions} testID="recommend.cancel">
            <Text>취소</Text>
          </Button>
        </BottomCta>
      </>
    );
  }

  return (
    <Screen>
      <ScreenHeader
        title={step === "recommend" && recommendPlaces.isSuccess ? RESULTS_TITLE : CONDITIONS_TITLE}
        onBack={step === "recommend" ? returnToConditions : goBack}
        testID="recommend.header"
      />
      {renderStep()}
    </Screen>
  );
}

// #region 상태 화면

function RecommendSkeleton() {
  return (
    <Screen>
      <ScreenHeader title={CONDITIONS_TITLE} testID="recommend.header" />
      <View accessibilityLabel="추천 조건을 불러오는 중" accessibilityState={{ busy: true }} className="flex-1 gap-6 px-5 pt-4" testID="recommend.skeleton">
        <View className="gap-2">
          <Skeleton className="h-8 w-56" />
          <Skeleton className="h-4 w-40" />
        </View>
        <View className="gap-3">
          <Skeleton className="h-5 w-36" />
          <View className="flex-row flex-wrap gap-2">
            {Array.from({ length: SKELETON_CHIP_COUNT }, (_, index) => {
              return <Skeleton key={index} className="h-9 w-20 rounded-sm" />;
            })}
          </View>
        </View>
        <View className="gap-2">
          <Skeleton className="h-5 w-10" />
          <View className="flex-row gap-2">
            <Skeleton className="h-input flex-1 rounded-sm" />
            <Skeleton className="h-input flex-1 rounded-sm" />
          </View>
        </View>
      </View>
      <BottomCta>
        <Button size="lg" className="w-full" disabled testID="recommend.submit">
          <Text>추천 받기</Text>
        </Button>
      </BottomCta>
    </Screen>
  );
}

function RecommendRoomNotFound() {
  return (
    <Screen>
      <ScreenHeader title={CONDITIONS_TITLE} testID="recommend.header" />
      <View className="flex-1 items-center justify-center gap-4 px-5" testID="recommend.not-found">
        <Text accessibilityRole="alert" className="text-center text-h3 text-foreground">
          여행방을 찾을 수 없어요
        </Text>
        <Button variant="outline" onPress={goBack} testID="recommend.not-found-back">
          <Text>돌아가기</Text>
        </Button>
      </View>
    </Screen>
  );
}

// #endregion
