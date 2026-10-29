import type { SharedOptions } from "msw";

export const onUnhandledRequest: SharedOptions["onUnhandledRequest"] = (request, print) => {
  const { pathname } = new URL(request.url);

  if (pathname === "/api/v1" || pathname.startsWith("/api/v1/")) {
    print.error();
  }
};
