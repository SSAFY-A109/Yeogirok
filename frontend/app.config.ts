import type { ConfigContext, ExpoConfig } from "expo/config";

export default ({ config }: ConfigContext): ExpoConfig => {
  const plugins: NonNullable<ExpoConfig["plugins"]> = [
    ...(config.plugins ?? []),
    "expo-dev-client",
    "expo-secure-store",
    ["expo-notifications", { defaultChannel: "chat", enableRemoteNotifications: false }],
  ];
  const iosClientId = process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID;
  const nativeAppKey = process.env.EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY;

  if (iosClientId) {
    plugins.push(["react-native-nitro-google-signin", {
      iosUrlScheme: `com.googleusercontent.apps.${iosClientId.replace(/\.apps\.googleusercontent\.com$/, "")}`,
    }]);
  }

  if (nativeAppKey) {
    plugins.push(["@react-native-kakao/core", {
      nativeAppKey,
      android: { authCodeHandlerActivity: true },
      ios: { handleKakaoOpenUrl: true },
    }]);
    // Kakao SDK artifacts are published only to Kakao's Maven repository, not Maven Central.
    plugins.push(["expo-build-properties", {
      android: { extraMavenRepos: ["https://devrepo.kakao.com/nexus/content/groups/public/"] },
    }]);
  }

  return {
    ...config,
    name: config.name ?? "여기록",
    slug: config.slug ?? "yeogirok-fe",
    android: {
      ...config.android,
      googleServicesFile: process.env.GOOGLE_SERVICES_JSON ?? "./google-services.json",
    },
    plugins,
  };
};
