import { FlatList, Pressable, View } from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import { ChevronRight, Footprints, Lock } from "lucide-react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dropdown } from "@/components/ui/dropdown";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useCategories } from "@/features/common/api/queries";
import { useFootprints } from "@/features/footprint/api/queries";
import { getFootprintErrorMessage, isFootprintUnavailable } from "@/features/footprint/errors";
import { footprintPeriod, formatFootprintDate } from "@/features/footprint/model";
import { MyFootprintMap } from "./my-footprint-map";
import { MyLoading, MyPage, MyUnavailable } from "./my-page";

const periodOptions = [
  { value: "recent", label: "최근 3개월" },
  { value: "month", label: "최근 1개월" },
  { value: "all", label: "전체 기간" },
];

export function MyFootprintsScreen() {
  const insets = useSafeAreaInsets();
  const { category, period } = useLocalSearchParams<{ category?: string; period?: string }>();
  const categories = useCategories();

  const options = [{ value: "all", label: "카테고리 전체" }, ...categories.data?.map(({ code, name }) => {
    return { value: code, label: name };
  }) ?? []];

  const selectedCategory = options.some((item) => {
    return item.value === category;
  }) ? category : "all";

  const selectedPeriod = period === "month" || period === "all" ? period : "recent";
  const query = useFootprints({ ...footprintPeriod(selectedPeriod), category: selectedCategory === "all" ? undefined : selectedCategory });

  const footprints = query.data?.pages.flatMap((page) => {
    return page.items;
  }) ?? [];

  const openFootprint = (footprintId: string) => {
    router.push({ pathname: "/my/footprints/[footprintId]", params: { footprintId } });
  };

  return (
    <MyPage title="내 발자취" testID="my-footprints.screen">
      {query.isPending ? <MyLoading /> : isFootprintUnavailable(query.error) || query.isError && !query.data ? <MyUnavailable title={getFootprintErrorMessage(query.error)} onRetry={() => {
        void query.refetch();
      }} /> : <FlatList
        data={footprints}
        keyExtractor={(item) => {
          return String(item.id);
        }}
        onEndReached={() => {
          if (query.hasNextPage && !query.isFetching && !query.isFetchNextPageError) {
            void query.fetchNextPage({ cancelRefetch: false });
          }
        }}
        refreshing={query.isRefetching}
        onRefresh={() => {
          void query.refetch();
        }}
        contentContainerClassName="px-5"
        contentContainerStyle={{ paddingBottom: insets.bottom + 32 }}
        ListHeaderComponent={<View className="gap-4 pb-3"><View className="flex-row items-center gap-1"><Icon as={Lock} size={14} className="text-icon" /><Text className="flex-1 text-caption text-foreground-secondary">나만 볼 수 있어요 공개 분포 지도와 따로 보여요</Text></View><View className="flex-row flex-wrap gap-2"><Dropdown className="w-32" options={periodOptions} value={selectedPeriod} accessibilityLabel="발자취 기간" testID="my-footprints.period" onValueChange={(value) => {
          router.setParams({ period: value });
        }} /><Dropdown className="w-40" options={options} value={selectedCategory} accessibilityLabel="발자취 카테고리" testID="my-footprints.category" onValueChange={(value) => {
          router.setParams({ category: value });
        }} /></View>{categories.isError && <Button variant="outline" onPress={() => {
          void categories.refetch();
        }}><Text>카테고리 다시 불러오기</Text></Button>}<MyFootprintMap footprints={footprints} onSelect={openFootprint} /></View>}
        ListEmptyComponent={<View className="items-center gap-4 py-12" testID="my-footprints.empty"><Icon as={Footprints} size={32} className="text-highlight" /><Text className="text-center text-h3">아직 남긴 발자취가 없어요</Text><Text className="text-center text-body-sm text-muted-foreground">다른 기간이나 카테고리를 골라 보세요.</Text></View>}
        ListFooterComponent={query.isFetchNextPageError ? <Button variant="outline" onPress={() => {
          void query.fetchNextPage({ cancelRefetch: false });
        }}><Text>발자취 더 불러오기</Text></Button> : query.isFetchingNextPage ? <Text className="py-3 text-center text-caption">불러오는 중</Text> : undefined}
        renderItem={({ item }) => {
          const visitedAt = formatFootprintDate(item.visitedAt);

          return <Pressable accessibilityRole="button" accessibilityLabel={`${visitedAt} ${item.place ?? ""} 방명록 보기`} className="flex-row items-center gap-3 border-b border-border py-3 active:bg-muted" testID={`my-footprints.row-${item.id}`} onPress={() => {
            openFootprint(String(item.id));
          }}><View className="flex-1 gap-1"><Text className="text-caption text-muted-foreground">{visitedAt}</Text><View className="flex-row flex-wrap items-center gap-2">{item.place && <Text className="text-label">{item.place}</Text>}{item.categoryCodes.map((code) => {
              return <Badge key={code}><Text>{options.find((item) => {
                return item.value === code;
              })?.label ?? code}</Text></Badge>;
            })}</View><Text numberOfLines={1} className={item.body ? "text-caption text-foreground-secondary" : "text-caption text-muted-foreground"}>{item.body?.replace(/\n/g, " ") || "방명록 없음"}</Text></View><Icon as={ChevronRight} size={18} className="text-icon" /></Pressable>;
        }}
      />}
    </MyPage>
  );
}
