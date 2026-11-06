import type { RequestHandler } from "msw";

import { authHandlers } from "./auth";
import { commonHandlers } from "./common";
import { courseHandlers } from "./course";
import { footprintHandlers } from "./footprint";
import { placeHandlers } from "./place";
import { recommendationHandlers } from "./recommendation";
import { roomHandlers } from "./room";
import { scheduleHandlers } from "./schedule";
import { statisticsHandlers } from "./statistics";

export const handlers: RequestHandler[] = [
  ...authHandlers,
  ...commonHandlers,
  ...courseHandlers,
  ...footprintHandlers,
  ...placeHandlers,
  ...recommendationHandlers,
  ...roomHandlers,
  ...scheduleHandlers,
  ...statisticsHandlers,
];
