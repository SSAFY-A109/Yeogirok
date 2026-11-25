export type TokenPair = { accessToken: string; refreshToken: string };

export type ServiceTokenPair = TokenPair & {
  accessTokenExpiresIn: number;
  refreshTokenExpiresIn: number;
};

export function isTokenPair(value: unknown): value is TokenPair {
  return (
    typeof value === "object" && value !== null &&
    "accessToken" in value && typeof value.accessToken === "string" && value.accessToken.length > 0 &&
    "refreshToken" in value && typeof value.refreshToken === "string" && value.refreshToken.length > 0
  );
}

export function isServiceTokenPair(value: unknown): value is ServiceTokenPair {
  return (
    isTokenPair(value) &&
    "accessTokenExpiresIn" in value && typeof value.accessTokenExpiresIn === "number" &&
    Number.isFinite(value.accessTokenExpiresIn) && value.accessTokenExpiresIn > 0 &&
    "refreshTokenExpiresIn" in value && typeof value.refreshTokenExpiresIn === "number" &&
    Number.isFinite(value.refreshTokenExpiresIn) && value.refreshTokenExpiresIn > 0
  );
}
