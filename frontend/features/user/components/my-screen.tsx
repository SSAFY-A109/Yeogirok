import { useCallback, useState } from "react";
import { Pressable, ScrollView, StyleSheet, View } from "react-native";
import { router, useFocusEffect, useLocalSearchParams, type Href } from "expo-router";
import { ChevronRight, Footprints, LogOut, ScanEye, UserX, type LucideIcon } from "lucide-react-native";

import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Icon } from "@/components/ui/icon";
import { MediaImage } from "@/components/ui/media-photo";
import { Screen } from "@/components/ui/screen";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useRecords } from "@/features/record/api/queries";
import { RecordCard, RecordIssue, RecordLoading } from "@/features/record/components/record-list-screen";
import { cn } from "@/lib/utils";
import { useMe } from "../api/queries";
import { getUserErrorMessage, USER_ERROR_FALLBACK } from "../errors";
import { MyLoading, MyUnavailable } from "./my-page";

export function MyScreen() {
  const me = useMe();
  const { refetch } = me;
  const profile = me.data;
  const [logoutOpen, setLogoutOpen] = useState(false);
  const { preview } = useLocalSearchParams<{ preview?: string }>();

  useFocusEffect(useCallback(() => {
    void refetch({ cancelRefetch: false });
  }, [refetch]));

  return (
    <Screen testID="my.screen">
      <ScrollView contentContainerClassName="pb-6" keyboardShouldPersistTaps="handled">
        <Text accessibilityRole="header" className="px-5 pb-4 pt-2 text-h1">My</Text>
        {preview === "loading" ? <MyLoading /> : preview === "error" ? (
          <View className="min-h-80"><MyUnavailable title="내 정보를 불러오지 못했어요" onRetry={() => {
            router.setParams({ preview: undefined });
          }} /></View>
        ) : (
          <>
            {profile ? <View className="flex-row items-center gap-4 px-5 pb-6" testID="my.profile">
              <View className="h-14 w-14 items-center justify-center overflow-hidden rounded-full bg-brand-muted">
                <Text className="text-h3 text-primary">{profile.nickname.slice(0, 1)}</Text>
                {profile.profileImageUrl && <MediaImage uri={profile.profileImageUrl} contentFit="cover"
                  cachePolicy="none" style={StyleSheet.absoluteFill} />}
              </View>
              <View className="flex-1 gap-1">
                <Text className="text-h3">{profile.nickname}</Text>
                <Text className="text-caption text-foreground-secondary">{profile.loginProviders.map((provider) => {
                  return provider === "kakao" ? "카카오" : "구글";
                }).join(" · ")} 계정 연결</Text>
              </View>
            </View> : me.isPending ? <View className="flex-row items-center gap-4 px-5 pb-6" accessibilityLabel="내 정보 불러오는 중">
              <Skeleton className="h-14 w-14 rounded-full" />
              <Skeleton className="h-6 w-skeleton-heading" />
            </View> : <View className="min-h-44 pb-6">
              <MyUnavailable title={getUserErrorMessage(me.error, USER_ERROR_FALLBACK.load)} onRetry={() => {
                void refetch();
              }} />
            </View>}
            <View className="gap-3 px-5 pb-6">
              <View className="flex-row items-center justify-between">
                <Text accessibilityRole="header" className="text-h3">내 기록</Text>
                <Pressable accessibilityRole="button" accessibilityLabel="내 기록 전체 보기" hitSlop={8} className="h-7 flex-row items-center gap-1" testID="my.all-records" onPress={() => {
                  router.push("/memo" as Href);
                }}><Text className="text-label text-foreground-secondary">전체 보기</Text><Icon as={ChevronRight} size={16} className="text-icon" /></Pressable>
              </View>
              {preview === "empty" ? <Text className="py-6 text-center text-body-sm text-muted-foreground">아직 작성한 여행 기록이 없어요</Text> : <MyRecords />}
            </View>
            <View className="gap-3 px-5 pb-6">
              <Text accessibilityRole="header" className="text-h3">내 활동</Text>
              <View className="overflow-hidden rounded-md border border-border">
                <MenuRow icon={Footprints} label="내 발자취" count={profile ? `${profile.activityCounts.footprints}곳` : undefined} coral divider testID="my.footprints" onPress={() => {
                  router.push("/my/footprints");
                }} />
                <MenuRow icon={ScanEye} label="내 팁" count={profile ? `${profile.activityCounts.tips}개` : undefined} testID="my.tips" onPress={() => {
                  router.push("/my/tips");
                }} />
              </View>
            </View>
            <View className="gap-3 px-5">
              <Text accessibilityRole="header" className="text-h3">계정</Text>
              <View className="overflow-hidden rounded-md border border-border">
                <MenuRow icon={LogOut} label="로그아웃" divider testID="my.logout" onPress={() => {
                  setLogoutOpen(true);
                }} />
                <MenuRow icon={UserX} label="회원 탈퇴" destructive testID="my.withdraw" onPress={() => {
                  router.push("/my/withdrawal");
                }} />
              </View>
            </View>
          </>
        )}
      </ScrollView>
      <ConfirmDialog open={logoutOpen} onOpenChange={setLogoutOpen} title="로그아웃할까요?" description={"이 기기에서 로그아웃돼요.\n내 기록과 팁은 다시 로그인하면\n이어서 볼 수 있어요."} confirmLabel="로그아웃" testID="my.logout-dialog" onConfirm={() => {
        setLogoutOpen(false);
        router.push("/my/preview-login");
      }} />
    </Screen>
  );
}

function MyRecords() {
  const records = useRecords();

  if (records.isPending) {
    return <RecordLoading />;
  }

  if (records.isError && !records.data) {
    return <RecordIssue message="기록을 불러오지 못했어요." onRetry={() => {
      return void records.refetch();
    }} />;
  }

  const items = records.data?.pages[0]?.items.slice(0, 2) ?? [];

  if (!items.length) {
    return <Text className="py-6 text-center text-body-sm text-muted-foreground">아직 작성한 여행 기록이 없어요</Text>;
  }

  return <View className="gap-3">{items.map((item) => {
    return <RecordCard key={item.id} item={item} />;
  })}</View>;
}

function MenuRow({ icon, label, count, coral, destructive, divider, onPress, testID }: { icon: LucideIcon; label: string; count?: string; coral?: boolean; destructive?: boolean; divider?: boolean; onPress: () => void; testID: string }) {
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={`${label}${count ? ` ${count}` : ""}`} className={cn("min-h-button-lg flex-row items-center gap-3 p-4 active:bg-muted", divider && "border-b border-border")} onPress={onPress} testID={testID}>
      <Icon as={icon} size={20} className={destructive ? "text-destructive" : coral ? "text-map-footprint" : "text-icon"} />
      <Text className={cn("flex-1 text-label", destructive && "text-destructive")}>{label}</Text>
      {count && <><Text className="text-caption text-foreground-secondary">{count}</Text><Icon as={ChevronRight} size={18} className="text-icon" /></>}
    </Pressable>
  );
}
