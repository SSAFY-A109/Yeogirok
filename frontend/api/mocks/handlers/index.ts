import type { RequestHandler } from "msw";

import { authHandlers } from "./auth";
import { chatHandlers } from "./chat";
import { commonHandlers } from "./common";
import { courseHandlers } from "./course";
import { footprintHandlers } from "./footprint";
import { placeHandlers } from "./place";
import { recommendationHandlers } from "./recommendation";
import { roomHandlers } from "./room";
import { scheduleHandlers } from "./schedule";
import { tipHandlers } from "./tip";
import { mediaHandlers } from "./media";
import { statisticsHandlers } from "./statistics";

export const handlers: RequestHandler[] = [
  ...authHandlers,
  ...commonHandlers,
  ...courseHandlers,
  ...footprintHandlers,
  ...placeHandlers,
  ...recommendationHandlers,
  ...roomHandlers,
  ...chatHandlers,
  ...scheduleHandlers,
  ...tipHandlers,
  ...mediaHandlers,
  ...statisticsHandlers,
];
