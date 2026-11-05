import { Lock, X } from "lucide-react-native";
import { Pressable, View } from "react-native";

import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import type { CategoryShare, VisitArea } from "@/features/statistics/model";
import { getColors } from "@/lib/theme";

const MAX_PERCENT = 100;
const shareFillColor = getColors("light")["map-footprint"];

type VisitAreaCardProps = {
  area: VisitArea;
  periodDays: number;
  onClose: () => void;
};

/** 발바닥을 눌렀을 때 보여 주는 그 일대 집계(Figma W15-A). 인원·방문 수와 카테고리 비율만 보여 주고 방명록은 보여 주지 않는다(규칙 80). */
export function VisitAreaCard({ area, periodDays, onClose }: VisitAreaCardProps) {
  return (
    <View className="gap-4 rounded-lg border border-border bg-card p-4" testID="visit-map.area-card">
      <View className="flex-row items-start gap-2">
        <View className="flex-1 gap-1">
          <Text accessibilityRole="header" className="text-h3 text-foreground">
            {area.name}
          </Text>
          <Text className="text-caption tabular-nums text-foreground-secondary">
            최근 {periodDays}일 다녀간 사람 {area.visitorCount}명 · 방문 {area.visitCount}회
          </Text>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`${area.name} 정보 닫기`}
          onPress={onClose}
          className="-mr-2 -mt-2 h-touch w-touch items-center justify-center"
          testID="visit-map.area-card-close"
        >
          <Icon as={X} size={20} className="text-icon" />
        </Pressable>
      </View>
      {area.categoryShares.length > 0 ? (
        <View className="gap-2">
          {area.categoryShares.map((share) => {
            return <CategoryShareRow key={share.code} share={share} />;
          })}
        </View>
      ) : null}
      <View className="flex-row items-center gap-1.5">
        <Icon as={Lock} size={14} className="text-muted-foreground" />
        <Text className="flex-1 text-caption text-muted-foreground">방명록 내용은 공개되지 않고 카테고리만 모아 보여 줘요</Text>
      </View>
    </View>
  );
}

function CategoryShareRow({ share }: { share: CategoryShare }) {
  const barWidth = Math.min(Math.max(share.percent, 0), MAX_PERCENT);

  return (
    <View accessible accessibilityLabel={`${share.name} ${share.percent}%`} className="flex-row items-center gap-3">
      <Text className="w-24 text-label-sm text-foreground" numberOfLines={1}>
        {share.name}
      </Text>
      <View className="h-2 flex-1 overflow-hidden rounded-full bg-border">
        <View className="h-full rounded-full" style={{ width: `${barWidth}%`, backgroundColor: shareFillColor }} />
      </View>
      <Text className="w-10 text-right text-label-sm tabular-nums text-foreground-secondary">{share.percent}%</Text>
    </View>
  );
}
