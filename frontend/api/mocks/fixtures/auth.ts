export const socialCredentialsFixture = {
  google: { provider: "google", idToken: "mock-google-id-token", nonce: "mock-google-nonce" },
  kakao: { provider: "kakao", idToken: "mock-kakao-id-token", nonce: "mock-kakao-nonce" },
} as const;

export const serviceTokensFixture = {
  accessToken: "mock-access-token",
  accessTokenExpiresIn: 60,
  refreshToken: "mock-refresh-token",
  refreshTokenExpiresIn: 120,
};

export const failedRefreshTokensFixture = {
  expired: "mock-expired-refresh-token",
  revoked: "mock-revoked-refresh-token",
} as const;
