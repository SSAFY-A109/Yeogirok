import { useRef } from "react";

import { createIdempotencyKey } from "@/api/idempotency";

/**
 * 제출할 때 붙일 멱등키를 돌려주는 함수를 만든다. 이벤트 핸들러에서만 부른다.
 * 같은 내용(`signature`)을 다시 보내면(재시도) 같은 키를, 내용이 바뀌면 새 키를 준다.
 * 같은 키로 다른 내용을 보내면 서버가 거절하기 때문이다(C009).
 */
export function useIdempotencyKeyFor(): (signature: string) => string {
  const lastRef = useRef<{ signature: string; key: string } | null>(null);

  return (signature) => {
    if (lastRef.current?.signature !== signature) {
      lastRef.current = { signature, key: createIdempotencyKey() };
    }

    return lastRef.current.key;
  };
}
