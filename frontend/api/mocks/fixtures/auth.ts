export const socialCredentialsFixture = {
  google: { provider: "google", idToken: "mock-google-id-token", nonce: "mock-google-nonce" },
  kakao: { provider: "kakao", idToken: "mock-kakao-id-token", nonce: "mock-kakao-nonce" },
} as const;

/** 구글 탈퇴(API-009)에 쓰는 1회용 serverAuthCode 목 값. */
export const googleServerAuthCodeFixture = "mock-google-server-auth-code";

/** 개발 빌드에서 소셜 SDK 대신 쓰는 로그인 결과. */
export function mockSocialCredentials(provider: "google" | "kakao") {
  return {
    ...socialCredentialsFixture[provider],
    serverAuthCode: provider === "google" ? googleServerAuthCodeFixture : null,
  };
}

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

export const expiredAccessTokenFixture = "mock-expired-access-token";

export const meFixture = {
  nickname: "여행자",
  profileImageUrl: null,
  activityCounts: { footprints: 0, tips: 0 },
};
