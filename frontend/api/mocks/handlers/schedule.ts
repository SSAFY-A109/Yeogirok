import { delay, http } from "msw";

import { mockDb } from "../db";
import { MOCK_DELAY_MS, ok, readId, roomNotFound } from "./respond";

export const scheduleHandlers = [
  http.get("*/api/v1/rooms/:roomId/schedule", async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const roomId = readId(params.roomId);
    const room = roomId === null ? undefined : mockDb.rooms.get(roomId);
    return room === undefined ? roomNotFound() : ok(room.schedule);
  }),
];
