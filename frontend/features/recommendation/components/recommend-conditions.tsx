import { Bus, Calendar, Car, Sparkles, Users, type LucideIcon } from "lucide-react-native";
import { ScrollView, View } from "react-native";

import { Dropdown } from "@/components/ui/dropdown";
import { FilterChip } from "@/components/ui/filter-chip";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import type { Category, Region } from "@/features/common/model";
import type { RecommendationPreference, RecommendationPreferenceDto } from "@/features/recommendation/model";
import { hasTripDates, TRANSPORT_MODE_LABEL, type RoomDetail } from "@/features/room/model";
import { formatDateRange } from "@/lib/date";

/** 추천 조건 입력값. 지역은 시/도 → 시/군/구 2단으로 고른다(시/도를 고르기 전에는 시/군/구를 고를 수 없다). */
export type RecommendConditionsValue = {
  categoryCodes: string[];
  provinceCode: string | null;
  districtCode: string | null;
};

const EMPTY_CONDITIONS: RecommendConditionsValue = { categoryCodes: [], provinceCode: null, districtCode: null };

// #region 조건 계산

/** 저장된 지역 코드(시/도 또는 시/군/구)를 2단 선택값으로 바꾼다. 목록에 없으면 비운다. */
function findRegionSelection(regions: readonly Region[], regionCode: string | null): Pick<RecommendConditionsValue, "provinceCode" | "districtCode"> {
  if (regionCode !== null) {
    for (const province of regions) {
      if (province.code === regionCode) {
        return { provinceCode: province.code, districtCode: null };
      }

      const hasDistrict = province.districts.some((district) => {
        return district.code === regionCode;
      });

      if (hasDistrict) {
        return { provinceCode: province.code, districtCode: regionCode };
      }
    }
  }

  return { provinceCode: null, districtCode: null };
}

/** 저장된 조건이 있으면 그 값으로, 없으면 비운 채로 시작한다. 지금 기준 데이터에 없는 코드는 버린다. */
export function getInitialConditions(
  preference: RecommendationPreference | null,
  categories: readonly Category[],
  regions: readonly Region[],
): RecommendConditionsValue {
  if (preference === null) {
    return EMPTY_CONDITIONS;
  }

  const knownCategoryCodes = new Set(categories.map((category) => {
    return category.code;
  }));

  return {
    categoryCodes: preference.categoryCodes.filter((code) => {
      return knownCategoryCodes.has(code);
    }),

    ...findRegionSelection(regions, preference.regionCode),
  };
}

/** 시/군/구를 골랐으면 그 코드, 시/도만 골랐으면 시/도 코드, 아니면 `null`을 보낸다. */
export function toRecommendationRequest(conditions: RecommendConditionsValue): RecommendationPreferenceDto {
  return {
    categoryCodes: conditions.categoryCodes,
    regionCode: conditions.districtCode ?? conditions.provinceCode ?? null,
  };
}

/** 처리 중 화면의 "고른 조건" 칩 문구(카테고리 이름, 지역 이름). */
export function getConditionLabels(
  conditions: RecommendConditionsValue,
  categories: readonly Category[],
  regions: readonly Region[],
): string[] {
  const categoryLabels = categories
    .filter((category) => {
      return conditions.categoryCodes.includes(category.code);
    })
    .map((category) => {
      return category.name;
    });

  const province = regions.find((region) => {
    return region.code === conditions.provinceCode;
  });

  const district = province?.districts.find((item) => {
    return item.code === conditions.districtCode;
  });

  const regionLabel = district?.name ?? province?.name;
  return regionLabel === undefined ? categoryLabels : [...categoryLabels, regionLabel];
}

// #endregion

type RecommendConditionsProps = {
  room: RoomDetail;
  placeCount: number;
  categories: readonly Category[];
  regions: readonly Region[];
  value: RecommendConditionsValue;
  onChange: (value: RecommendConditionsValue) => void;
};

/** AI 추천 조건 입력(Figma W10). 지역·기간·이동 수단은 여행방 값을 보여 준다. */
export function RecommendConditions({ room, placeCount, categories, regions, value, onChange }: RecommendConditionsProps) {
  const dateLabel = hasTripDates(room) ? formatDateRange(room.startDate, room.endDate, { weekday: false }) : "날짜 미정";
  const transportIcon = room.transportMode === "PUBLIC_TRANSIT" ? Bus : Car;

  const selectedProvince = regions.find((region) => {
    return region.code === value.provinceCode;
  });

  const provinceOptions = regions.map((region) => {
    return { value: region.code, label: region.name };
  });

  const districtOptions = (selectedProvince?.districts ?? []).map((district) => {
    return { value: district.code, label: district.name };
  });

  function toggleCategory(code: string) {
    const categoryCodes = value.categoryCodes.includes(code)
      ? value.categoryCodes.filter((item) => {
        return item !== code;
      })
      : [...value.categoryCodes, code];

    onChange({ ...value, categoryCodes });
  }

  function selectProvince(provinceCode: string) {
    if (provinceCode === value.provinceCode) {
      return;
    }

    onChange({ ...value, provinceCode, districtCode: null });
  }

  function selectDistrict(districtCode: string) {
    onChange({ ...value, districtCode });
  }

  return (
    <ScrollView className="flex-1" contentContainerClassName="gap-6 px-5 pb-6 pt-4" testID="recommend.conditions">
      <View className="gap-2">
        <Text accessibilityRole="header" className="text-h1 text-foreground">
          어떤 여행을 원하세요?
        </Text>
        <View className="flex-row flex-wrap items-center gap-2">
          <RoomContextItem icon={Calendar} label={dateLabel} />
          <RoomContextItem icon={transportIcon} label={TRANSPORT_MODE_LABEL[room.transportMode]} />
          <RoomContextItem icon={Users} label={`${room.memberCount}명`} />
        </View>
      </View>

      <View className="gap-3">
        <Text className="text-label text-foreground">좋아하는 곳 (여러 개 선택)</Text>
        <View className="flex-row flex-wrap gap-2">
          {categories.map((category) => {
            return (
              <FilterChip
                key={category.code}
                size="md"
                selected={value.categoryCodes.includes(category.code)}
                onSelectedChange={() => {
                  toggleCategory(category.code);
                }}
                testID={`recommend.category-${category.code}`}
              >
                {category.name}
              </FilterChip>
            );
          })}
        </View>
      </View>

      <View className="gap-2">
        <Text className="text-label text-foreground">지역</Text>
        <View className="flex-row gap-2">
          <View className="min-w-0 flex-1">
            <Dropdown
              accessibilityLabel="시/도"
              placeholder="시/도"
              options={provinceOptions}
              value={value.provinceCode ?? undefined}
              onValueChange={selectProvince}
              testID="recommend.region"
            />
          </View>
          <View className="min-w-0 flex-1">
            <Dropdown
              key={value.provinceCode ?? "none"}
              accessibilityLabel="시/군/구"
              placeholder="시/군/구"
              options={districtOptions}
              value={value.districtCode ?? undefined}
              onValueChange={selectDistrict}
              disabled={districtOptions.length === 0}
              testID="recommend.district"
            />
          </View>
        </View>
      </View>

      {placeCount > 0 ? (
        <View className="flex-row items-center gap-3 rounded-md bg-brand-muted p-4">
          <Icon as={Sparkles} size={20} className="text-icon-active" />
          <Text className="flex-1 text-caption text-primary">{`이미 담은 ${placeCount}곳과 동선이 맞는 곳을 먼저 추천해요`}</Text>
        </View>
      ) : null}
    </ScrollView>
  );
}

function RoomContextItem({ icon, label }: { icon: LucideIcon; label: string }) {
  return (
    <View className="flex-row items-center gap-1">
      <Icon as={icon} size={16} className="text-icon" />
      <Text className="text-caption text-foreground-secondary">{label}</Text>
    </View>
  );
}
