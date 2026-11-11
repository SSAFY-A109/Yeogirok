import { getErrorMessage } from "@/api/errors";

/** 회원(U·A) 도메인 code별 문구. */
const USER_ERROR_MESSAGES: Readonly<Record<string, string>> = {
  A011: "보안을 위해 다시 로그인한 뒤 탈퇴해 주세요.",
  A029: "지금 로그인한 구글 계정으로 다시 로그인해 주세요.",
  WITHDRAW_ACCOUNT_MISMATCH: "현재 로그인한 소셜 계정으로 다시 시도해 주세요.",
};

export const USER_ERROR_FALLBACK = {
  load: "내 정보를 불러오지 못했어요.",
  withdraw: "다시 시도해 주세요.",
} as const;

export function getUserErrorMessage(error: unknown, fallback: string): string {
  return getErrorMessage(error, USER_ERROR_MESSAGES, fallback);
}
