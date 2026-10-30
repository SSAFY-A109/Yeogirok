export type TokenPair = { accessToken: string; refreshToken: string };

export function isTokenPair(value: unknown): value is TokenPair {
  return (
    typeof value === "object" && value !== null &&
    "accessToken" in value && typeof value.accessToken === "string" && value.accessToken.length > 0 &&
    "refreshToken" in value && typeof value.refreshToken === "string" && value.refreshToken.length > 0
  );
}
