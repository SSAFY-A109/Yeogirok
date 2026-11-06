import * as SecureStore from "expo-secure-store";

import type { SocialProvider } from "./model";
import { isTokenPair, type TokenPair } from "./tokens";

const TOKEN_KEY = "yeogirok.auth.tokens";
const PROVIDER_KEY = "yeogirok.auth.provider";

export async function getLoginProvider(): Promise<SocialProvider | null> {
  const provider = await SecureStore.getItemAsync(PROVIDER_KEY);
  return provider === "kakao" || provider === "google" ? provider : null;
}

export async function setLoginProvider(provider: SocialProvider): Promise<void> {
  await SecureStore.setItemAsync(PROVIDER_KEY, provider, { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY });
}

export async function getTokens(): Promise<TokenPair | null> {
  const stored = await SecureStore.getItemAsync(TOKEN_KEY);

  if (stored === null) {
    return null;
  }

  const tokens: unknown = JSON.parse(stored);

  if (!isTokenPair(tokens)) {
    throw new Error("저장된 인증 토큰 형식이 올바르지 않습니다.");
  }

  return tokens;
}

export async function setTokens(tokens: TokenPair): Promise<void> {
  if (!isTokenPair(tokens)) {
    throw new Error("인증 토큰 형식이 올바르지 않습니다.");
  }

  await SecureStore.setItemAsync(TOKEN_KEY, JSON.stringify({
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
  }), { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY });
}

export async function clearTokens(): Promise<void> {
  await SecureStore.deleteItemAsync(TOKEN_KEY);
  await SecureStore.deleteItemAsync(PROVIDER_KEY);
}
