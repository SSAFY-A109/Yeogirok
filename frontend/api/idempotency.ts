/** 멱등성 헤더 이름. 백엔드 공통 멱등키 처리(`backend/dev`의 `IdempotencyKey`, 2026-11-04 확인)와 같다. */
export const IDEMPOTENCY_HEADER = "Idempotency-Key";

const UUID_TEMPLATE = "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx";
const HEX_RADIX = 16;
const UUID_VARIANT_BASE = 8;
const UUID_VARIANT_RANGE = 4;

/**
 * 요청 하나를 가리키는 멱등키(UUID v4 형식). 보안 값이 아니라 같은 요청이 두 번 처리되지 않게 구분하는 값이라
 * `Math.random`으로 만든다. Hermes에는 `crypto.randomUUID`가 없다.
 */
export function createIdempotencyKey(): string {
  return UUID_TEMPLATE.replace(/[xy]/g, (char) => {
    const random = Math.floor(Math.random() * HEX_RADIX);
    const value = char === "x" ? random : (random % UUID_VARIANT_RANGE) + UUID_VARIANT_BASE;
    return value.toString(HEX_RADIX);
  });
}
