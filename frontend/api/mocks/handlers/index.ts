import type { RequestHandler } from "msw";

import { authHandlers } from "./auth";
import { commonHandlers } from "./common";
import { placeHandlers } from "./place";
import { recommendationHandlers } from "./recommendation";
import { roomHandlers } from "./room";
import { scheduleHandlers } from "./schedule";

export const handlers: RequestHandler[] = [
  ...authHandlers,
  ...commonHandlers,
  ...placeHandlers,
  ...recommendationHandlers,
  ...roomHandlers,
  ...scheduleHandlers,
];
