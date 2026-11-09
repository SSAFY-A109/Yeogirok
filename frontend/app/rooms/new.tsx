import { Stack, useLocalSearchParams } from "expo-router";

import { NewRoomScreen, type CarryOverPlaces } from "@/features/room/components/new-room-screen";
import { parseIds, parsePlaceIds, parseRoomId, readRouteText } from "@/features/room/route-params";

export default function NewRoomRoute() {
  const params = useLocalSearchParams<{ placeIds?: string; placeNames?: string; recordId?: string; visitIds?: string }>();
  const placeNames = readRouteText(params.placeNames);
  const recordId = parseRoomId(params.recordId);
  const visitIds = parseIds(params.visitIds);
  const isCourseImport = params.recordId !== undefined || params.visitIds !== undefined;
  const placeIds = parsePlaceIds(params.placeIds);
  let carryOver: CarryOverPlaces | null = null;

  if (isCourseImport && recordId !== null && visitIds.length > 0) {
    carryOver = { kind: "course", recordId, visitIds, placeNames };
  } else if (!isCourseImport && placeIds.length > 0) {
    carryOver = { kind: "search", placeIds, placeNames };
  }

  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <NewRoomScreen carryOver={carryOver} invalidCourseImport={isCourseImport && carryOver === null} />
    </>
  );
}
