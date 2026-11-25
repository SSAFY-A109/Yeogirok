import { assertId, ContractMismatchError } from "../../lib/contract";
import type { SocialProvider } from "../auth/model";

// 원천: 팀 Notion "API 명세" API-008(GET /users/me)·API-009(DELETE /users/me).
// 2026-11-04 실기기 + 로컬 백엔드(backend/feat/S15P31A109-32-member-api)로 확인한 정리본 기준.

// #region DTO

export type MeDto = {
  id: number;
  nickname: string;
  profileImageUrl: string | null;
  activityCounts: { footprints: number; tips: number };
  loginProviders: SocialProvider[];
};

/** 구글 회원만 재로그인 응답의 serverAuthCode를 보낸다. 카카오는 본문이 없다. */
export type WithdrawRequestDto = { googleAuthCode: string };

export type WithdrawResponseDto = { withdrawn: boolean; dataCleanupStatus: string };

// #endregion

// #region 화면 모델

export type Me = MeDto;

// #endregion

function isSocialProvider(value: unknown): value is SocialProvider {
  return value === "google" || value === "kakao";
}

function assertCount(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) {
    throw new ContractMismatchError(field);
  }

  return value;
}

export function toMe(value: unknown): Me {
  if (typeof value !== "object" || value === null) {
    throw new ContractMismatchError("me");
  }

  const { id, nickname, profileImageUrl, activityCounts, loginProviders } = value as Record<string, unknown>;

  if (typeof nickname !== "string") {
    throw new ContractMismatchError("nickname");
  }

  if (profileImageUrl !== null && typeof profileImageUrl !== "string") {
    throw new ContractMismatchError("profileImageUrl");
  }

  if (typeof activityCounts !== "object" || activityCounts === null) {
    throw new ContractMismatchError("activityCounts");
  }

  if (!Array.isArray(loginProviders) || loginProviders.length === 0 || !loginProviders.every(isSocialProvider)) {
    throw new ContractMismatchError("loginProviders");
  }

  const counts = activityCounts as Record<string, unknown>;

  return {
    id: assertId(id, "id"),
    nickname,
    profileImageUrl,
    activityCounts: {
      footprints: assertCount(counts.footprints, "activityCounts.footprints"),
      tips: assertCount(counts.tips, "activityCounts.tips"),
    },
    loginProviders,
  };
}

export function toWithdrawResult(value: unknown): WithdrawResponseDto {
  if (
    typeof value !== "object" || value === null ||
    !("withdrawn" in value) || value.withdrawn !== true ||
    !("dataCleanupStatus" in value) || typeof value.dataCleanupStatus !== "string"
  ) {
    throw new ContractMismatchError("withdraw");
  }

  return { withdrawn: value.withdrawn, dataCleanupStatus: value.dataCleanupStatus };
}
