import type { RequestHandler } from "msw";

import { commonHandlers } from "./common";
import { placeHandlers } from "./place";
import { recommendationHandlers } from "./recommendation";
import { roomHandlers } from "./room";
import { scheduleHandlers } from "./schedule";

export const handlers: RequestHandler[] = [
  ...commonHandlers,
  ...placeHandlers,
  ...recommendationHandlers,
  ...roomHandlers,
  ...scheduleHandlers,
];
