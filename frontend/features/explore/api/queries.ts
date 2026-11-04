import { useMutation, useQuery } from "@tanstack/react-query";

import { addPlacesToRoom, fetchRooms } from "./explore.api";

export function useRooms(enabled: boolean) {
  return useQuery({ queryKey: ["explore", "rooms"] as const, queryFn: fetchRooms, enabled });
}

export function useAddPlacesToRoom() {
  return useMutation({ mutationFn: addPlacesToRoom });
}
