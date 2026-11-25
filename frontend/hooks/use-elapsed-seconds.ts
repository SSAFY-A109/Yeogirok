import { useEffect, useState } from "react";

const TICK_MS = 1_000;

/** 화면에 들어온 뒤 지난 초. `active`가 false면 세지 않는다(처리 중 경과 표시용). */
export function useElapsedSeconds(active: boolean): number {
  const [startedAt] = useState(() => {
    return Date.now();
  });

  const [now, setNow] = useState(startedAt);

  // 타이머(외부 시스템)와 화면 시각을 맞춘다.
  useEffect(() => {
    if (!active) {
      return undefined;
    }

    const timer = setInterval(() => {
      setNow(Date.now());
    }, TICK_MS);

    return () => {
      clearInterval(timer);
    };
  }, [active]);

  return Math.max(0, Math.floor((now - startedAt) / TICK_MS));
}
