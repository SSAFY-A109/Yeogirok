import { Lock, X } from "lucide-react-native";
import { Pressable, View } from "react-native";

import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useCategories } from "@/features/common/api/queries";
import { formatVisitPeriod, type CategoryCount, type VisitCell, type VisitStatistics } from "@/features/statistics/model";
import { formatTimeOfDay } from "@/lib/date";
import { getColors } from "@/lib/theme";

const FULL_BAR_PERCENT = 100;
const countFillColor = getColors("light")["map-footprint"];

type VisitAreaCardProps = {
  cell: VisitCell;
  statistics: Pick<VisitStatistics, "period" | "updatedAt">;
  /** 지도 위에서 고른 카테고리 이름. 고르면 서버가 그 카테고리를 고른 발자취만 센다. */
  categoryFilterName: string | null;
  onClose: () => void;
};

/**
 * 발바닥을 눌렀을 때 보여 주는 그 칸의 집계(Figma W15-A를 서버 값에 맞춤). 서버가 준 발자취 수와 카테고리별 수만 보여 주고,
 * 지역 이름·인원·비율은 서버가 주지 않아 만들지 않는다. 방명록은 보여 주지 않는다(규칙 80).
 */
export function VisitAreaCard({ cell, statistics, categoryFilterName, onClose }: VisitAreaCardProps) {
  const categories = useCategories();

  const rows = [...cell.categoryCounts].sort((a, b) => {
    return b.count - a.count;
  });

  const maxCategoryCount = rows[0]?.count ?? 0;
  const basis = `${formatVisitPeriod(statistics.period)} · ${formatTimeOfDay(statistics.updatedAt)} 기준`;

  const nameOf = (code: string) => {
    return categories.data?.find((category) => {
      return category.code === code;
    })?.name ?? code;
  };

  return (
    <View className="gap-4 rounded-lg border border-border bg-card p-4" testID="visit-map.area-card">
      <View className="flex-row items-start gap-2">
        <View className="flex-1 gap-1">
          <Text accessibilityRole="header" className="text-h3 tabular-nums text-foreground">
            {`이 일대에 남은 발자취 ${cell.count}개`}
          </Text>
          <Text className="text-caption text-foreground-secondary">
            {categoryFilterName === null ? basis : `${basis} · '${categoryFilterName}' 카테고리 발자취만 셌어요`}
          </Text>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="이 일대 정보 닫기"
          onPress={onClose}
          className="-mr-2 -mt-2 h-touch w-touch items-center justify-center"
          testID="visit-map.area-card-close"
        >
          <Icon as={X} size={20} className="text-icon" />
        </Pressable>
      </View>
      {rows.length > 0 ? (
        <View className="gap-2">
          {rows.map((row) => {
            return <CategoryCountRow key={row.code} name={nameOf(row.code)} row={row} maxCount={maxCategoryCount} />;
          })}
          {rows.length > 1 ? (
            <Text className="text-caption text-muted-foreground">발자취 하나에 카테고리를 여러 개 고를 수 있어, 더하면 전체보다 많을 수 있어요</Text>
          ) : null}
        </View>
      ) : null}
      <View className="flex-row items-center gap-1.5">
        <Icon as={Lock} size={14} className="text-muted-foreground" />
        <Text className="flex-1 text-caption text-muted-foreground">방명록 내용은 공개되지 않고 카테고리만 모아 보여 줘요</Text>
      </View>
    </View>
  );
}

/** 카테고리 한 줄. 막대는 이 칸에서 가장 많은 카테고리를 기준으로 한 길이이고, 숫자는 서버 값 그대로다. */
function CategoryCountRow({ name, row, maxCount }: { name: string; row: CategoryCount; maxCount: number }) {
  const barWidth = maxCount > 0 ? Math.min((row.count / maxCount) * FULL_BAR_PERCENT, FULL_BAR_PERCENT) : 0;

  return (
    <View accessible accessibilityLabel={`${name} 발자취 ${row.count}개`} className="flex-row items-center gap-3">
      <Text className="w-24 text-label-sm text-foreground" numberOfLines={1}>
        {name}
      </Text>
      <View className="h-2 flex-1 overflow-hidden rounded-full bg-border">
        <View className="h-full rounded-full" style={{ width: `${barWidth}%`, backgroundColor: countFillColor }} />
      </View>
      <Text className="w-12 text-right text-label-sm tabular-nums text-foreground-secondary">{`${row.count}개`}</Text>
    </View>
  );
}
