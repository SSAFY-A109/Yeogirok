import type { RequestHandler } from "msw";

import { authHandlers } from "./auth";
import { commonHandlers } from "./common";
import { courseHandlers } from "./course";
import { footprintHandlers } from "./footprint";
import { mediaHandlers } from "./media";
import { placeHandlers } from "./place";
import { recommendationHandlers } from "./recommendation";
import { roomHandlers } from "./room";
import { scheduleHandlers } from "./schedule";
import { myTipHandlers } from "./tip";

export const handlers: RequestHandler[] = [
  ...authHandlers,
  ...commonHandlers,
  ...courseHandlers,
  ...footprintHandlers,
  ...mediaHandlers,
  ...placeHandlers,
  ...recommendationHandlers,
  ...roomHandlers,
  ...scheduleHandlers,
  ...myTipHandlers,
];
