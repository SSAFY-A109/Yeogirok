/** 서버 응답이 계약(명세)과 다를 때 던지는 오류. 화면에서 우회하지 않고 드러낸다. */
export class ContractMismatchError extends Error {
  constructor(public readonly field: string) {
    super(`응답 필드가 계약과 다릅니다: ${field}`);
    this.name = "ContractMismatchError";
  }
}

/** 계약상 숫자 id인지 검증하고 그대로 반환한다. */
export function assertId(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value)) {
    throw new ContractMismatchError(field);
  }

  return value;
}

/** 열거형 값을 알려진 값의 유니온으로 좁히고, 모르는 값은 `UNKNOWN`으로 흡수한다. */
export function toKnownEnum<T extends string>(value: unknown, known: readonly T[]): T | "UNKNOWN" {
  return typeof value === "string" && (known as readonly string[]).includes(value) ? (value as T) : "UNKNOWN";
}
