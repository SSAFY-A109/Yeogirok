import { randomUUID } from "expo-crypto";
import { router } from "expo-router";
import { Lock, MapPin, RotateCcw } from "lucide-react-native";
import { useRef, useState } from "react";
import { Pressable, ScrollView, View } from "react-native";
import Animated from "react-native-reanimated";

import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { FilterChip } from "@/components/ui/filter-chip";
import { Icon } from "@/components/ui/icon";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useCategories } from "@/features/common/api/queries";
import { categoryIconOf } from "@/features/explore/components/category-icons";
import { useCreateFootprint } from "@/features/footprint/api/queries";
import { canShowLocationMap, FootprintLocationMap, LocationMapPlaceholder } from "@/features/footprint/components/footprint-location-map";
import { FootprintPointPicker } from "@/features/footprint/components/footprint-point-picker";
import { LocationPermissionCard } from "@/features/footprint/components/location-permission-card";
import { PreciseLocationNotice } from "@/features/footprint/components/precise-location-notice";
import { useKeyboardFollowingScroll } from "@/features/footprint/components/use-keyboard-following-scroll";
import { getFootprintErrorMessage, getLocationFailureMessage, isLocationEvidenceError } from "@/features/footprint/errors";
import {
  countFootprintBody,
  FOOTPRINT_BODY_MAX_LENGTH,
  FOOTPRINT_CATEGORY_MAX_COUNT,
  FOOTPRINT_CATEGORY_MIN_COUNT,
  formatLocationEvidence,
  toCreateFootprintRequest,
  type CreateFootprintRequestDto,
  type FootprintPoint,
} from "@/features/footprint/model";
import { useCurrentLocation, type CurrentLocation } from "@/hooks/use-current-location";
import { describePlace } from "@/lib/native/location";
import { cn } from "@/lib/utils";

/** 카테고리를 불러오는 동안 보여 줄 칸 수. 공통 카테고리 8개가 4열 2행으로 놓이는 자리와 같다. */
const CATEGORY_SKELETON_COUNT = 8;
/** 격자 칸의 칩 터치 영역. 칸 사이 간격(8)의 절반씩만 넓혀 옆 칩과 겹치지 않게 하고, 높이 36 + 4 + 4 = 44를 맞춘다. */
const CATEGORY_CHIP_HIT_SLOP = { top: 4, bottom: 4, left: 4, right: 4 };

type PendingSubmission = { fingerprint: string; idempotencyKey: string };

/**
 * 같은 내용을 다시 보낼 때 같은 멱등키를 쓰게 한다(규칙 90). 위치를 다시 재거나 입력을 바꿔 내용이 달라지면 새 키를 만든다.
 * 저장에 성공하면 비운다.
 */
function useIdempotencyKey() {
  const pending = useRef<PendingSubmission | null>(null);

  const keyFor = (request: CreateFootprintRequestDto) => {
    const fingerprint = JSON.stringify(request);

    if (pending.current?.fingerprint !== fingerprint) {
      pending.current = { fingerprint, idempotencyKey: randomUUID() };
    }

    return pending.current.idempotencyKey;
  };

  const clear = () => {
    pending.current = null;
  };

  return { keyFor, clear };
}

/** 지도에서 직접 고른 위치와 그 근처 주소(기기 지오코더, 찾는 중이거나 못 찾으면 `null`). */
type ChosenPlace = { point: FootprintPoint; label: string | null };

/** 직접 고른 위치. 잰 위치를 다시 재도 사용자가 고른 곳은 그대로 둔다. */
function useChosenPlace() {
  const [chosen, setChosen] = useState<ChosenPlace | null>(null);
  const latestPoint = useRef<FootprintPoint | null>(null);

  const choose = (point: FootprintPoint) => {
    latestPoint.current = point;
    setChosen({ point, label: null });

    void describePlace(point).then((label) => {
      if (latestPoint.current === point) {
        setChosen({ point, label });
      }
    });
  };

  const clear = () => {
    latestPoint.current = null;
    setChosen(null);
  };

  return { chosen, choose, clear };
}

function leaveToMap() {
  if (router.canGoBack()) {
    router.back();
    return;
  }

  router.replace("/map");
}

/**
 * 발자취 남기기(Figma W16, FPT-02·03). 들어오면 현재 위치를 다시 재서 위치 증거로 쓰고,
 * 카테고리 1~3개(필수)와 나만 보는 방명록(선택)을 함께 저장한다(API-041). 저장 실패·위치 재측정 뒤에도 입력은 그대로 둔다.
 */
export function NewFootprintScreen() {
  const location = useCurrentLocation();
  const createFootprint = useCreateFootprint();
  const idempotency = useIdempotencyKey();
  const toast = useToast();
  const [categoryCodes, setCategoryCodes] = useState<string[]>([]);
  const [body, setBody] = useState("");
  const [isCategoryLimitShown, setIsCategoryLimitShown] = useState(false);
  const keyboard = useKeyboardFollowingScroll();
  const chosenPlace = useChosenPlace();
  const [isPickerOpen, setIsPickerOpen] = useState(false);

  const request = location.position === null ? null : toCreateFootprintRequest({
    position: location.position,
    chosenPoint: chosenPlace.chosen?.point ?? null,
    categoryCodes,
    body,
  });
  const saveError = createFootprint.error;
  const isSaving = createFootprint.isPending;

  const clearSaveError = () => {
    if (createFootprint.error !== null) {
      createFootprint.reset();
    }
  };

  const toggleCategory = (code: string) => {
    clearSaveError();

    if (categoryCodes.includes(code)) {
      setCategoryCodes(categoryCodes.filter((selected) => {
        return selected !== code;
      }));

      setIsCategoryLimitShown(false);
      return;
    }

    if (categoryCodes.length >= FOOTPRINT_CATEGORY_MAX_COUNT) {
      setIsCategoryLimitShown(true);
      return;
    }

    setCategoryCodes([...categoryCodes, code]);
  };

  const remeasure = async () => {
    const next = await location.measure();

    if (next !== null) {
      clearSaveError();
    }
  };

  const submit = () => {
    if (request === null || isSaving || location.isMeasuring) {
      return;
    }

    createFootprint.mutate(
      { request, idempotencyKey: idempotency.keyFor(request) },
      {
        onSuccess: () => {
          idempotency.clear();
          toast.show({ message: "발자취를 남겼어요. 방명록은 나만 볼 수 있어요." });
          leaveToMap();
        },
      },
    );
  };

  return (
    <Screen>
      <ScreenHeader title="발자취 남기기" testID="footprint.header" />
      <View className="flex-1">
        <ScrollView
          {...keyboard.scrollViewProps}
          className="flex-1"
          contentContainerClassName="pb-6"
          keyboardShouldPersistTaps="handled"
        >
          <LocationSection
            location={location}
            chosen={chosenPlace.chosen}
            onRemeasure={remeasure}
            onPick={() => {
              setIsPickerOpen(true);
            }}
            onResetToMeasured={() => {
              chosenPlace.clear();
              clearSaveError();
            }}
          />
          <View className="gap-6 px-5 pt-4">
            <CategoryField
              selectedCodes={categoryCodes}
              isLimitShown={isCategoryLimitShown}
              disabled={isSaving}
              onToggle={toggleCategory}
            />
            <GuestbookField
              value={body}
              editable={!isSaving}
              onChangeText={(value) => {
                setBody(value);
                clearSaveError();
              }}
              onFocus={keyboard.followInput}
              onBlur={keyboard.stopFollowing}
            />
          </View>
        </ScrollView>
        <BottomCta>
          {saveError !== null ? (
            <View className="w-full flex-row items-center gap-3" testID="footprint.error">
              <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="flex-1 text-caption text-destructive">
                {getFootprintErrorMessage(saveError)}
              </Text>
              {isLocationEvidenceError(saveError) ? (
                <Button
                  variant="outline"
                  loading={location.isMeasuring}
                  loadingLabel="확인 중"
                  onPress={() => {
                    void remeasure();
                  }}
                  testID="footprint.remeasure"
                >
                  <Text>위치 다시 확인</Text>
                </Button>
              ) : null}
            </View>
          ) : null}
          <Button
            size="lg"
            className="w-full"
            disabled={request === null || location.isMeasuring}
            loading={isSaving}
            loadingLabel="남기는 중"
            onPress={submit}
            testID="footprint.submit"
          >
            <Text>발자취 남기기</Text>
          </Button>
        </BottomCta>
        <Animated.View style={keyboard.keyboardSpacerStyle} />
      </View>
      {location.position !== null ? (
        <FootprintPointPicker
          visible={isPickerOpen}
          measured={location.position}
          initialPoint={chosenPlace.chosen?.point ?? location.position}
          onConfirm={(point) => {
            chosenPlace.choose(point);
            clearSaveError();
            setIsPickerOpen(false);
          }}
          onClose={() => {
            setIsPickerOpen(false);
          }}
        />
      ) : null}
    </Screen>
  );
}

// --- 위치

/** 직접 수정한 위치라는 표시. 주소를 못 찾으면 이 문구를 제목으로 쓴다. */
const CHOSEN_PLACE_TEXT = "직접 수정한 위치";

function toPlaceTitle(location: CurrentLocation, chosen: ChosenPlace | null): string {
  if (chosen !== null) {
    return chosen.label === null ? CHOSEN_PLACE_TEXT : `${chosen.label} 부근`;
  }

  return location.placeLabel === null ? "지금 있는 곳" : `${location.placeLabel} 부근`;
}

type LocationSectionProps = {
  location: CurrentLocation;
  chosen: ChosenPlace | null;
  onRemeasure: () => Promise<void>;
  onPick: () => void;
  onResetToMeasured: () => void;
};

/**
 * 발자취를 남길 곳(Figma W16 `Location map`·`Location`). 권한·측정 상태에 따라 안내를 바꾼다.
 * 잰 위치가 빗나가면 지도에서 직접 고를 수 있고(거리 제한 없음), 언제든 잰 위치로 되돌릴 수 있다.
 */
function LocationSection({ location, chosen, onRemeasure, onPick, onResetToMeasured }: LocationSectionProps) {
  const remeasure = () => {
    void onRemeasure();
  };

  if (location.permission === null) {
    return (
      <LocationMapPlaceholder testID="footprint.location-checking">
        <Spinner tone="brand" accessible={false} />
        <Text className="text-caption text-foreground-secondary">위치 권한을 확인하고 있어요</Text>
      </LocationMapPlaceholder>
    );
  }

  if (location.permission !== "granted") {
    return (
      <View className="px-5 pt-4">
        <LocationPermissionCard
          permission={location.permission}
          isRequesting={location.isRequestingPermission}
          onRequest={() => {
            void location.requestPermission();
          }}
          testID="footprint.location-permission"
        />
      </View>
    );
  }

  if (location.position === null) {
    return location.failure !== null && !location.isMeasuring ? (
      <LocationMapPlaceholder testID="footprint.location-error">
        <Text accessibilityRole="alert" className="text-center text-caption text-foreground-secondary">
          {getLocationFailureMessage(location.failure)}
        </Text>
        <Button variant="outline" className="h-9 px-3" onPress={remeasure} testID="footprint.location-retry">
          <Text>다시 확인</Text>
        </Button>
      </LocationMapPlaceholder>
    ) : (
      <LocationMapPlaceholder testID="footprint.location-measuring">
        <Spinner tone="brand" accessible={false} />
        <Text accessibilityLiveRegion="polite" className="text-caption text-foreground-secondary">지금 있는 곳을 확인하고 있어요</Text>
      </LocationMapPlaceholder>
    );
  }

  const measured = location.position;

  return (
    <View>
      <FootprintLocationMap measured={measured} point={chosen?.point ?? measured} />
      <View className="flex-row items-start gap-2 px-5 pt-4" testID="footprint.location">
        <Icon as={MapPin} size={20} className="text-icon" />
        <View className="flex-1 gap-1">
          <Text className="text-label text-foreground">{toPlaceTitle(location, chosen)}</Text>
          {chosen === null ? (
            <Text className="text-caption text-foreground-secondary">{formatLocationEvidence(measured)}</Text>
          ) : null}
          {chosen !== null && chosen.label !== null ? (
            <Text className="text-caption text-foreground-secondary">{CHOSEN_PLACE_TEXT}</Text>
          ) : null}
          {location.failure !== null ? (
            <Text accessibilityRole="alert" className="text-caption text-destructive">{getLocationFailureMessage(location.failure)}</Text>
          ) : null}
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="위치 다시 확인"
          accessibilityState={{ disabled: location.isMeasuring, busy: location.isMeasuring }}
          className="-mt-3 min-h-touch justify-center px-1"
          disabled={location.isMeasuring}
          onPress={remeasure}
          testID="footprint.location-recheck"
        >
          <Text className={cn("text-label", location.isMeasuring ? "text-muted-foreground" : "text-primary")}>
            {location.isMeasuring ? "확인 중" : "다시 확인"}
          </Text>
        </Pressable>
      </View>
      {canShowLocationMap ? (
        <View className="flex-row gap-2 px-5 pt-3">
          <Button variant="outline" onPress={onPick} testID="footprint.pick-location">
            <Text>위치 수정</Text>
          </Button>
          {chosen !== null ? (
            <Button
              variant="outline"
              className="w-touch px-0"
              accessibilityLabel="잰 위치로 되돌리기"
              onPress={onResetToMeasured}
              testID="footprint.reset-location"
            >
              <Icon as={RotateCcw} size={20} className="text-icon-active" />
            </Button>
          ) : null}
        </View>
      ) : null}
      {location.isApproximate ? (
        <View className="px-5 pt-3">
          <PreciseLocationNotice location={location} testID="footprint.precise-location" />
        </View>
      ) : null}
    </View>
  );
}

// --- 카테고리

type CategoryFieldProps = {
  selectedCodes: readonly string[];
  isLimitShown: boolean;
  disabled: boolean;
  onToggle: (code: string) => void;
};

/**
 * 카테고리 1~3개(Figma W16 `Category (필수)`). 목록은 서버 기준 데이터(API-079)다.
 * 사용자 요청(2026-11-06)으로 4열 격자에 놓는다(공통 카테고리 8개 → 2행). 칸마다 위에 카테고리 아이콘(Figma `카테고리 기준 (v3)`),
 * 아래에 이름을 두고, 이름이 길면 칸 안에서 두 줄로 가운데 정렬한다.
 */
function CategoryField({ selectedCodes, isLimitShown, disabled, onToggle }: CategoryFieldProps) {
  const categories = useCategories();

  return (
    <View className="gap-3" testID="footprint.categories">
      <View className="flex-row items-center gap-1">
        <Text className="text-label text-foreground">카테고리</Text>
        <Text className="text-caption text-highlight">{`${FOOTPRINT_CATEGORY_MIN_COUNT}~${FOOTPRINT_CATEGORY_MAX_COUNT}개`}</Text>
      </View>
      {categories.isPending ? (
        <View className="-m-1 flex-row flex-wrap" accessibilityLabel="카테고리 불러오는 중">
          {Array.from({ length: CATEGORY_SKELETON_COUNT }, (_, index) => {
            return (
              <View key={index} className="w-1/4 p-1">
                <Skeleton className="h-16 w-full rounded-sm" />
              </View>
            );
          })}
        </View>
      ) : null}
      {categories.isError ? (
        <View className="flex-row items-center gap-3">
          <Text className="flex-1 text-caption text-foreground-secondary">카테고리를 불러오지 못했어요.</Text>
          <Button
            variant="outline"
            onPress={() => {
              void categories.refetch();
            }}
            testID="footprint.categories-retry"
          >
            <Text>다시 시도</Text>
          </Button>
        </View>
      ) : null}
      {categories.data ? (
        <View className="-m-1 flex-row flex-wrap">
          {categories.data.map((category) => {
            return (
              <View key={category.code} className="w-1/4 p-1">
                <FilterChip
                  size="md"
                  className="h-auto min-h-button-sm flex-1 gap-1 px-1 py-2"
                  textClassName="text-center"
                  icon={categoryIconOf(category.code)}
                  hitSlop={CATEGORY_CHIP_HIT_SLOP}
                  selected={selectedCodes.includes(category.code)}
                  disabled={disabled}
                  onPress={() => {
                    onToggle(category.code);
                  }}
                  testID={`footprint.category-${category.code}`}
                >
                  {category.name}
                </FilterChip>
              </View>
            );
          })}
        </View>
      ) : null}
      <Text
        accessibilityLiveRegion="polite"
        className={cn("text-caption", isLimitShown ? "text-highlight" : "text-muted-foreground")}
      >
        {isLimitShown ? `카테고리는 ${FOOTPRINT_CATEGORY_MAX_COUNT}개까지 고를 수 있어요` : "카테고리는 지도 분포에 함께 집계돼요"}
      </Text>
    </View>
  );
}

// --- 방명록

type GuestbookFieldProps = {
  value: string;
  editable: boolean;
  onChangeText: (value: string) => void;
  onFocus: () => void;
  onBlur: () => void;
};

/** 나만 보는 방명록(Figma W16 `Guestbook (선택 나만 봄)`). 글자 수는 서버와 같은 기준으로 센다. */
function GuestbookField({ value, editable, onChangeText, onFocus, onBlur }: GuestbookFieldProps) {
  const length = countFootprintBody(value);
  const isTooLong = length > FOOTPRINT_BODY_MAX_LENGTH;

  return (
    <View className="gap-3" testID="footprint.guestbook">
      <View className="flex-row items-center gap-2">
        <Text className="text-label text-foreground">방명록</Text>
        <View className="flex-row items-center gap-1 rounded-full bg-muted px-2 py-1">
          <Icon as={Lock} size={12} className="text-foreground-secondary" />
          <Text className="text-micro text-foreground-secondary">나만 볼 수 있어요</Text>
        </View>
        <Text className="text-caption text-muted-foreground">선택</Text>
      </View>
      <Textarea
        value={value}
        onChangeText={onChangeText}
        onFocus={onFocus}
        onBlur={onBlur}
        editable={editable}
        placeholder="여행에 대한 메모를 남겨 주세요."
        accessibilityLabel="방명록, 나만 볼 수 있어요"
        error={isTooLong ? `방명록은 ${FOOTPRINT_BODY_MAX_LENGTH}자까지 쓸 수 있어요.` : undefined}
        testID="footprint.body"
      />
      <Text className={cn("text-right text-caption tabular-nums", isTooLong ? "text-destructive" : "text-muted-foreground")}>
        {`${length} / ${FOOTPRINT_BODY_MAX_LENGTH}`}
      </Text>
    </View>
  );
}
