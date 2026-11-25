import { QueryClient } from "@tanstack/react-query";

import { isApiError } from "@/api/errors";

/** 쿼리 재시도 횟수. 4xx는 재시도로 해결되지 않으므로 재시도하지 않는다. */
const QUERY_RETRY_COUNT = 1;

function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  if (isApiError(error) && error.status >= 400 && error.status < 500) {
    return false;
  }

  return failureCount < QUERY_RETRY_COUNT;
}

/** 앱 전체에서 하나만 쓰는 QueryClient. 재시도 정책은 여기에만 둔다(쿼리 1회, 뮤테이션 0회). */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: shouldRetryQuery },
    mutations: { retry: 0 },
  },
});
