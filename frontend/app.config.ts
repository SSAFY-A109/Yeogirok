import type { ConfigContext, ExpoConfig } from "expo/config";

export default ({ config }: ConfigContext): ExpoConfig => {
  const nativeAppKey = process.env.EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY?.trim();

  return {
    ...config,
    name: config.name ?? "여기록",
    slug: config.slug ?? "yeogirok-fe",
    plugins: [
      ...(config.plugins ?? []),
      "expo-dev-client",
      [
        "expo-build-properties",
        {
          android: {
            extraMavenRepos: [
              "https://devrepo.kakao.com/nexus/content/groups/public/",
            ],
          },
        },
      ],
      ...(nativeAppKey
        ? [["@react-native-kakao/core", { nativeAppKey }]] satisfies NonNullable<ExpoConfig["plugins"]>
        : []),
    ],
  };
};
