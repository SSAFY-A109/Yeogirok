import { useState } from "react";
import { Pressable, ScrollView, View } from "react-native";
import { router, useLocalSearchParams, type Href } from "expo-router";
import { ChevronRight, Footprints, Image, LogOut, ScanEye, UserX, type LucideIcon } from "lucide-react-native";

import { Badge } from "@/components/ui/badge";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Icon } from "@/components/ui/icon";
import { Screen } from "@/components/ui/screen";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { cn } from "@/lib/utils";
import { useMe } from "@/features/user/api/queries";
import { mockProfile, mockRecords } from "../mocks/my-data";
import { useMyMock } from "../mocks/my-provider";
import { MyLoading, MyUnavailable } from "./my-page";

export function MyScreen() {
  const { loginProvider } = useMyMock();
  const { data: me } = useMe();
  const [logoutOpen, setLogoutOpen] = useState(false);
  const { preview } = useLocalSearchParams<{ preview?: string }>();

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
            <View className="flex-row items-center gap-4 px-5 pb-6">
              <View className="h-14 w-14 items-center justify-center rounded-full bg-brand-muted"><Text className="text-h3 text-primary">{mockProfile.name.slice(0, 1)}</Text></View>
              <View className="gap-1"><Text className="text-h3">{mockProfile.name}</Text><Text className="text-caption text-foreground-secondary">{loginProvider ? `${loginProvider === "kakao" ? "카카오" : "구글"}로 로그인` : "로그인 정보 확인 필요"}</Text></View>
            </View>
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
                <MenuRow icon={Footprints} label="내 발자취" count={me ? `${me.activityCounts.footprints}곳` : undefined} coral divider chevron testID="my.footprints" onPress={() => {
                  router.push("/my/footprints");
                }} />
                <MenuRow icon={ScanEye} label="내 팁" count={me ? `${me.activityCounts.tips}개` : undefined} chevron testID="my.tips" onPress={() => {
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
  const { published, setPublished } = useMyMock();
  const toast = useToast();

  return (
    <View className="gap-3">
      {mockRecords.map((record) => {
        return (
          <View key={record.id} className="flex-row items-center gap-4 rounded-md border border-border bg-card p-4">
            {!record.draft && <View className="h-16 w-16 items-center justify-center rounded-md bg-highlight-muted"><Icon as={Image} size={24} className="text-muted-foreground" /></View>}
            <View className={cn("flex-1", record.draft ? "gap-3" : "gap-2")}>
              <View className={cn("flex-row flex-wrap items-center gap-2", record.draft && "justify-between")}>
                <Text className="flex-shrink text-label">{record.title}</Text>
                <Badge><Text className={!record.draft && published ? "text-positive" : undefined}>{record.draft ? "초안 확인 필요" : published ? "공개 중" : "비공개"}</Text></Badge>
              </View>
              <View className={cn("gap-2", record.draft && "flex-row items-center justify-between")}>
                <Text className="text-caption text-foreground-secondary">{record.description}</Text>
                {record.draft ? (
                  <Pressable accessibilityRole="button" accessibilityLabel={`${record.title} 이어서 작성`} hitSlop={{ top: 12, bottom: 12 }} className="h-5 flex-row items-center gap-1" testID="my.continue-record" onPress={() => {
                    toast.show({ message: "여행 기록 편집 화면은 준비 중이에요" });
                  }}><Text className="text-label text-primary">이어서 작성</Text><Icon as={ChevronRight} size={16} className="text-primary" /></Pressable>
                ) : (
                  <View className="flex-row flex-wrap gap-2">
                    <SmallAction label="수정" testID="my.edit-record" onPress={() => {
                      toast.show({ message: "여행 기록 편집 화면은 준비 중이에요" });
                    }} />
                    <SmallAction label={published ? "공개 해제" : "공개하기"} testID="my.toggle-publish" onPress={() => {
                      setPublished(!published);
                      toast.show({ message: published ? "공개를 해제했어요" : "코스를 공개했어요" });
                    }} />
                  </View>
                )}
              </View>
            </View>
          </View>
        );
      })}
    </View>
  );
}

function SmallAction({ label, onPress, testID }: { label: string; onPress: () => void; testID: string }) {
  return <Pressable accessibilityRole="button" accessibilityLabel={label} hitSlop={{ top: 6, bottom: 6 }} className="h-8 justify-center rounded-sm border border-border px-3 active:bg-muted" onPress={onPress} testID={testID}><Text className="text-label-sm">{label}</Text></Pressable>;
}

function MenuRow({ icon, label, count, coral, destructive, divider, chevron, onPress, testID }: { icon: LucideIcon; label: string; count?: string; coral?: boolean; destructive?: boolean; divider?: boolean; chevron?: boolean; onPress: () => void; testID: string }) {
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={`${label}${count ? ` ${count}` : ""}`} className={cn("min-h-button-lg flex-row items-center gap-3 p-4 active:bg-muted", divider && "border-b border-border")} onPress={onPress} testID={testID}>
      <Icon as={icon} size={20} className={destructive ? "text-destructive" : coral ? "text-map-footprint" : "text-icon"} />
      <Text className={cn("flex-1 text-label", destructive && "text-destructive")}>{label}</Text>
      {count && <Text className="text-caption text-foreground-secondary">{count}</Text>}
      {chevron && <Icon as={ChevronRight} size={18} className="text-icon" />}
    </Pressable>
  );
}
