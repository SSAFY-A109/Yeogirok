import { useState } from "react";
import { Pressable, ScrollView, View } from "react-native";
import { router } from "expo-router";

import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { SocialLoginButton } from "@/components/ui/social-login-button";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { useLoginProvider } from "@/features/auth/api/queries";
import { useWithdraw } from "../api/queries";
import { getUserErrorMessage, USER_ERROR_FALLBACK } from "../errors";
import { MyPage } from "./my-page";

export function MyWithdrawalScreen() {
  const [consent, setConsent] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const loginProvider = useLoginProvider().data?.provider;
  const withdraw = useWithdraw();
  const toast = useToast();
  const providerLabel = loginProvider === "kakao" ? "카카오" : "구글";

  return (
    <MyPage title="회원 탈퇴" testID="my-withdrawal.screen">
      <ScrollView contentContainerClassName="flex-grow gap-6 px-5 pb-6 pt-6">
        <Text className="text-h1">여기록을 떠나시나요?</Text>
        <Text className="text-body-sm text-foreground-secondary">탈퇴 전에 아래 내용을 확인해 주세요.</Text>
        <View className="gap-6 rounded-md bg-muted p-4">
          <View className="gap-3"><Text className="text-label">계정을 복구할 수 없어요</Text><Text className="text-body-sm text-foreground-secondary">{"탈퇴 후 다시 이용하려면\n새로운 계정으로 가입해야 해요."}</Text></View>
          <View className="gap-3"><Text className="text-label">필요한 기록을 미리 보관해 주세요</Text><Text className="text-body-sm text-foreground-secondary">{"여행 기록과 팁에 필요한 내용이 있다면\n탈퇴 전에 따로 저장해 주세요."}</Text></View>
        </View>
        <View className="gap-3"><Text className="text-label">본인 확인 후 탈퇴할 수 있어요</Text><Text className="text-body-sm text-foreground-secondary">{loginProvider ? `가입할 때 사용한 ${providerLabel} 계정으로\n본인 확인을 진행해요.` : "로그인 정보를 확인할 수 없어요. 다시 로그인해 주세요."}</Text></View>
        <View className="flex-1" />
        <View className="min-h-touch flex-row items-center gap-3 pt-6">
          <Checkbox checked={consent} onCheckedChange={setConsent} accessibilityLabel="안내를 확인했고, 회원 탈퇴에 동의해요" testID="my-withdrawal.consent" />
          <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: consent }} accessibilityLabel="회원 탈퇴 안내 동의" className="min-h-touch flex-1 justify-center" testID="my-withdrawal.consent-label" onPress={() => {
            setConsent(!consent);
          }}><Text className="text-body-sm">안내를 확인했고, 회원 탈퇴에 동의해요.</Text></Pressable>
        </View>
      </ScrollView>
      <BottomCta>
        {loginProvider ? <SocialLoginButton provider={loginProvider} label={`${providerLabel}로 본인 확인`} disabled={!consent} testID="my-withdrawal.verify" onPress={() => {
          setConfirmOpen(true);
        }} /> : <Button disabled size="lg" className="w-full" testID="my-withdrawal.verify"><Text>로그인 정보 확인 필요</Text></Button>}
        <Pressable accessibilityRole="button" accessibilityLabel="탈퇴 취소하고 My로 돌아가기" className="min-h-touch w-full items-center justify-center" testID="my-withdrawal.cancel" onPress={() => {
          router.replace("/my");
        }}><Text className="text-label text-foreground-secondary">계속 이용할게요</Text></Pressable>
      </BottomCta>
      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title="정말 탈퇴할까요?"
        description={"소셜 계정으로 본인 확인 후 탈퇴해요.\n탈퇴하면 계정을 복구할 수 없어요."}
        confirmLabel="탈퇴하기"
        confirmTone="destructive"
        loading={withdraw.isPending}
        loadingLabel="탈퇴 처리 중"
        testID="my-withdrawal.dialog"
        onConfirm={() => {
          if (!loginProvider) {
            return;
          }

          withdraw.mutate(loginProvider, {
            onSuccess: () => {
              toast.show({ message: "탈퇴가 완료됐어요." });
            },

            onError: (error) => {
              setConfirmOpen(false);
              const detail = getUserErrorMessage(error, USER_ERROR_FALLBACK.withdraw);
              toast.show({ message: `탈퇴하지 못했어요. ${detail}` });
            },
          });
        }}
      />
    </MyPage>
  );
}
