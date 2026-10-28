import type { ConfigContext, ExpoConfig } from "expo/config";

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: config.name ?? "여기록",
  slug: config.slug ?? "yeogirok-fe",
  plugins: [...(config.plugins ?? []), "expo-dev-client"],
});
