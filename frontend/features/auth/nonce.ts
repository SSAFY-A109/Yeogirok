import { getRandomBytes } from "expo-crypto";

const NONCE_BYTES = 32;

/** 소셜 로그인 1회용 nonce. 보안 난수 32바이트를 hex 문자열로 만든다. */
export function createNonce(): string {
  return Array.from(getRandomBytes(NONCE_BYTES), (byte) => {
    return byte.toString(16).padStart(2, "0");
  }).join("");
}
