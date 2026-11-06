import type { RequestHandler } from "msw";

import { authHandlers } from "./auth";
import { commonHandlers } from "./common";
import { courseHandlers } from "./course";
import { placeHandlers } from "./place";
import { recommendationHandlers } from "./recommendation";
import { roomHandlers } from "./room";
import { scheduleHandlers } from "./schedule";
import { tipHandlers } from "./tip";
import { mediaHandlers } from "./media";

export const handlers: RequestHandler[] = [
  ...authHandlers,
  ...commonHandlers,
  ...courseHandlers,
  ...placeHandlers,
  ...recommendationHandlers,
  ...roomHandlers,
  ...scheduleHandlers,
  ...tipHandlers,
  ...mediaHandlers,
];
