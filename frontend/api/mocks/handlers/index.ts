import type { RequestHandler } from "msw";

import { authHandlers } from "./auth";

export const handlers: RequestHandler[] = [...authHandlers];
