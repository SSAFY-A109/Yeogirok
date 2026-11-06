import { FlatList, Pressable, View } from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import { ChevronRight, Footprints, Lock } from "lucide-react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { Badge } from "@/components/ui/badge";
import { Dropdown } from "@/components/ui/dropdown";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { footprintCategoryOptions, footprintPeriodOptions, mockPeriodStart } from "../mocks/my-data";
import { useMyMock } from "../mocks/my-provider";
import { MyFootprintMap } from "./my-footprint-map";
import { MyLoading, MyPage, MyUnavailable } from "./my-page";

export function MyFootprintsScreen() {
  const insets = useSafeAreaInsets();
  const { footprints } = useMyMock();
  const { category, period, preview } = useLocalSearchParams<{ category?: string; period?: string; preview?: string }>();

  const selectedCategory = footprintCategoryOptions.some((item) => {
    return item.value === category;
  }) ? category : "all";

  const selectedPeriod = period === "month" || period === "all" ? period : "recent";

  const filtered = footprints.filter((item) => {
    return preview !== "empty" && item.visitedOn >= mockPeriodStart[selectedPeriod] && (selectedCategory === "all" || item.categories.includes(selectedCategory ?? ""));
  });

  const openFootprint = (footprintId: string) => {
    router.push({ pathname: "/my/footprints/[footprintId]", params: { footprintId } });
  };

  return (
    <MyPage title="내 발자취" testID="my-footprints.screen">
      {preview === "loading" ? <MyLoading /> : preview === "error" ? <MyUnavailable title="발자취를 불러오지 못했어요" onRetry={() => {
        router.setParams({ preview: undefined });
      }} /> : <FlatList
        data={filtered}
        keyExtractor={(item) => {
          return item.id;
        }}
        contentContainerClassName="px-5"
        contentContainerStyle={{ paddingBottom: insets.bottom + 32 }}
        ListHeaderComponent={<View className="gap-4 pb-3"><View className="flex-row items-center gap-1"><Icon as={Lock} size={14} className="text-icon" /><Text className="flex-1 text-caption text-foreground-secondary">나만 볼 수 있어요 공개 분포 지도와 따로 보여요</Text></View><View className="flex-row flex-wrap gap-2"><Dropdown className="w-32" options={footprintPeriodOptions} value={selectedPeriod} accessibilityLabel="발자취 기간" testID="my-footprints.period" onValueChange={(value) => {
          router.setParams({ period: value });
        }} /><Dropdown className="w-40" options={footprintCategoryOptions} value={selectedCategory} accessibilityLabel="발자취 카테고리" testID="my-footprints.category" onValueChange={(value) => {
          router.setParams({ category: value });
        }} /></View><MyFootprintMap footprints={filtered} onSelect={openFootprint} /></View>}
        ListEmptyComponent={<View className="items-center gap-4 py-12" testID="my-footprints.empty"><Icon as={Footprints} size={32} className="text-highlight" /><Text className="text-center text-h3">아직 남긴 발자취가 없어요</Text><Text className="text-center text-body-sm text-muted-foreground">다른 기간이나 카테고리를 골라 보세요.</Text></View>}
        renderItem={({ item }) => {
          return <Pressable accessibilityRole="button" accessibilityLabel={`${item.visitedAt} ${item.place} 방명록 보기`} className="flex-row items-center gap-3 border-b border-border py-3 active:bg-muted" testID={`my-footprints.row-${item.id}`} onPress={() => {
            openFootprint(item.id);
          }}><View className="flex-1 gap-1"><Text className="text-caption text-muted-foreground">{item.visitedAt}</Text><View className="flex-row flex-wrap items-center gap-2"><Text className="text-label">{item.place}</Text>{item.categories.map((value) => {
              return <Badge key={value}><Text>{value}</Text></Badge>;
            })}</View><Text numberOfLines={1} className={item.body ? "text-caption text-foreground-secondary" : "text-caption text-muted-foreground"}>{item.body.replace(/\n/g, " ") || "방명록 없음"}</Text></View><Icon as={ChevronRight} size={18} className="text-icon" /></Pressable>;
        }}
      />}
    </MyPage>
  );
}
