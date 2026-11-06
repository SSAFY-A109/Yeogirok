import { useState } from "react";
import { Pressable, ScrollView, View } from "react-native";
import { router } from "expo-router";
import { CircleCheck } from "lucide-react-native";

import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { assets, DesignAsset } from "@/components/ui/design-asset";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { cn } from "@/lib/utils";
import { useMyMock } from "../mocks/my-provider";
import { MyPage } from "./my-page";

export function MyWithdrawalScreen() {
  const [consent, setConsent] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [complete, setComplete] = useState(false);
  const { reset } = useMyMock();

  return (
    <MyPage title="회원 탈퇴" testID="my-withdrawal.screen">
      {complete ? (
        <View className="flex-1 justify-center gap-6 px-5 pb-24" testID="my-withdrawal.complete">
          <Icon as={CircleCheck} size={36} className="self-center text-positive" />
          <Text className="text-center text-h3">탈퇴가 완료됐어요</Text>
          <Text className="text-center text-body-sm text-foreground-secondary">그동안 여기록과 함께해 주셔서 고마워요.</Text>
          <Button size="lg" className="w-full" testID="my-withdrawal.start" onPress={() => {
            router.replace("/my/preview-login");
          }}><Text>시작 화면으로</Text></Button>
        </View>
      ) : (
        <>
          <ScrollView contentContainerClassName="flex-grow gap-6 px-5 pb-6 pt-6">
            <Text className="text-h1">여기록을 떠나시나요?</Text>
            <Text className="text-body-sm text-foreground-secondary">탈퇴 전에 아래 내용을 확인해 주세요.</Text>
            <View className="gap-6 rounded-md bg-muted p-4">
              <View className="gap-3"><Text className="text-label">계정을 복구할 수 없어요</Text><Text className="text-body-sm text-foreground-secondary">{"탈퇴 후 다시 이용하려면\n새로운 계정으로 가입해야 해요."}</Text></View>
              <View className="gap-3"><Text className="text-label">필요한 기록을 미리 보관해 주세요</Text><Text className="text-body-sm text-foreground-secondary">{"여행 기록과 팁에 필요한 내용이 있다면\n탈퇴 전에 따로 저장해 주세요."}</Text></View>
            </View>
            <View className="gap-3"><Text className="text-label">본인 확인 후 탈퇴할 수 있어요</Text><Text className="text-body-sm text-foreground-secondary">{"가입할 때 사용한 카카오 계정으로\n본인 확인을 진행해요."}</Text></View>
            <View className="flex-1" />
            <View className="min-h-touch flex-row items-center gap-3 pt-6">
              <Checkbox checked={consent} onCheckedChange={setConsent} accessibilityLabel="안내를 확인했고, 회원 탈퇴에 동의해요" testID="my-withdrawal.consent" />
              <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: consent }} accessibilityLabel="회원 탈퇴 안내 동의" className="min-h-touch flex-1 justify-center" testID="my-withdrawal.consent-label" onPress={() => {
                setConsent(!consent);
              }}><Text className="text-body-sm">안내를 확인했고, 회원 탈퇴에 동의해요.</Text></Pressable>
            </View>
          </ScrollView>
          <BottomCta>
            <Pressable accessibilityRole="button" accessibilityLabel="카카오로 본인 확인 UI 미리보기" accessibilityState={{ disabled: !consent }} disabled={!consent} className={cn("min-h-button-lg w-full flex-row items-center justify-center gap-2 rounded-full bg-auth-kakao px-5", !consent && "opacity-40")} testID="my-withdrawal.verify" onPress={() => {
              setConfirmOpen(true);
            }}><DesignAsset source={assets.kakao} className="h-5 w-5" accessible={false} /><Text className="text-button-lg text-black/85">카카오로 본인 확인</Text></Pressable>
            <Pressable accessibilityRole="button" accessibilityLabel="탈퇴 취소하고 My로 돌아가기" className="min-h-touch w-full items-center justify-center" testID="my-withdrawal.cancel" onPress={() => {
              router.replace("/my");
            }}><Text className="text-label text-foreground-secondary">계속 이용할게요</Text></Pressable>
          </BottomCta>
        </>
      )}
      <ConfirmDialog open={confirmOpen} onOpenChange={setConfirmOpen} title="정말 탈퇴할까요?" description={"본인 확인이 완료됐어요.\n탈퇴하면 계정을 복구할 수 없어요."} confirmLabel="탈퇴하기" confirmTone="destructive" testID="my-withdrawal.dialog" onConfirm={() => {
        setConfirmOpen(false);
        reset();
        setComplete(true);
      }} />
    </MyPage>
  );
}
