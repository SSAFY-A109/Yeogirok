import { Stack, useLocalSearchParams } from "expo-router";

import { NewRoomScreen, type CarryOverPlaces } from "@/features/room/components/new-room-screen";
import { parseIds, parsePlaceRefs, parseRoomId, readRouteText } from "@/features/room/route-params";

export default function NewRoomRoute() {
  const params = useLocalSearchParams<{ placeRefs?: string; placeNames?: string; recordId?: string; visitIds?: string }>();
  const placeNames = readRouteText(params.placeNames);
  const recordId = parseRoomId(params.recordId);
  const visitIds = parseIds(params.visitIds);
  const isCourseImport = params.recordId !== undefined || params.visitIds !== undefined;
  const placeRefs = parsePlaceRefs(params.placeRefs);
  let carryOver: CarryOverPlaces | null = null;

  if (isCourseImport && recordId !== null && visitIds.length > 0) {
    carryOver = { kind: "course", recordId, visitIds, placeNames };
  } else if (!isCourseImport && placeRefs.length > 0) {
    carryOver = { kind: "search", placeRefs, placeNames };
  }

  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <NewRoomScreen carryOver={carryOver} invalidCourseImport={isCourseImport && carryOver === null} />
    </>
  );
}
