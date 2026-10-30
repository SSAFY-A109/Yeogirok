import { isTokenPair, type TokenPair } from "./tokens";

let currentTokens: TokenPair | null = null;

export async function getTokens(): Promise<TokenPair | null> {
  return currentTokens ? { ...currentTokens } : null;
}

export async function setTokens(tokens: TokenPair): Promise<void> {
  if (!isTokenPair(tokens)) throw new Error("인증 토큰 형식이 올바르지 않습니다.");
  currentTokens = { accessToken: tokens.accessToken, refreshToken: tokens.refreshToken };
}

export async function clearTokens(): Promise<void> {
  currentTokens = null;
}
