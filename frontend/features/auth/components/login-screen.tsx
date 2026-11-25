import { useState } from "react";
import { View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { DesignAsset } from "@/components/ui/design-asset";
import { GoogleLoginButton, KakaoLoginButton } from "@/components/ui/social-login-button";
import { Text } from "@/components/ui/text";
import type { SocialProvider } from "../model";

type LoginScreenProps = {
  onLogin: (provider: SocialProvider) => void;
  pendingProvider?: SocialProvider;
  error: string | null;
};

export function LoginScreen({ onLogin, pendingProvider, error }: LoginScreenProps) {
  const [bodySize, setBodySize] = useState({ width: 390, height: 540 });
  const [introHeight, setIntroHeight] = useState(52);
  const topPadding = Math.min(48, bodySize.height * 0.08);
  const artworkScale = Math.max(0, Math.min(
    1,
    (bodySize.width - 40) / 350,
    (bodySize.height - introHeight - topPadding - 48) / 376,
  ));

  return (
    <SafeAreaView className="flex-1 bg-background" testID="login.screen">
      <View className="mx-auto w-full max-w-md flex-1">
        <View
          className="min-h-0 flex-1 items-center gap-4 overflow-hidden px-5 pb-4"
          style={{ paddingTop: topPadding }}
          onLayout={({ nativeEvent: { layout } }) => {
            setBodySize({ width: layout.width, height: layout.height });
          }}
        >
          <DesignAsset
            source={require("@/assets/images/logo/logo1.webp")}
            contentFit="contain"
            style={{ width: 192 * artworkScale, height: 144 * artworkScale }}
            accessibilityLabel="여기록"
            testID="login.logo"
          />
          <Text className="w-full text-center text-body text-foreground-secondary" onLayout={({ nativeEvent: { layout } }) => {
            setIntroHeight(layout.height);
          }}>
            {"다른 사람의 발자취를 따라 여행하고,\n나의 발자취는 또 다른 사람의 여행이 돼요."}
          </Text>
          <DesignAsset
            source={require("@/assets/images/auth/login-map.png")}
            contentFit="contain"
            className="rounded-lg"
            style={{ width: 350 * artworkScale, height: 232 * artworkScale }}
            accessible={false}
            testID="login.map-preview"
          />
        </View>
        <View className="shrink-0 gap-3 px-5 pt-3">
          {error && (
            <Text className="text-center text-body-sm text-destructive" accessibilityLiveRegion="polite" testID="login.error">
              {error}
            </Text>
          )}
          <KakaoLoginButton testID="login.kakao" loading={pendingProvider === "kakao"} loadingLabel="카카오 로그인 중" disabled={pendingProvider !== undefined} onPress={() => {
            onLogin("kakao");
          }} />
          <GoogleLoginButton testID="login.google" loading={pendingProvider === "google"} loadingLabel="Google 로그인 중" disabled={pendingProvider !== undefined} onPress={() => {
            onLogin("google");
          }} />
          <Text className="text-center text-caption text-muted-foreground">
            계속하면 이용약관과 위치정보 이용에 동의하게 돼요
          </Text>
        </View>
      </View>
    </SafeAreaView>
  );
}
