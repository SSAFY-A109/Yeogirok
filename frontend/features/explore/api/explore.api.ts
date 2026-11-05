// 여행방 담기 임시 구현: 코스·인기 장소를 여행방에 가져오는 API(API-030)의 명세를 받기 전까지 JS mock을 비동기로 감싼다(TBD).
// 명세가 나오면 room 도메인의 API·MSW로 옮기고 이 파일을 지운다(규칙 90).
import { rooms } from "../mocks/explore.mock";
import type { AddPlacesInput, AddPlacesResult, Room } from "../model";

const MOCK_DELAY_MS = 350;

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => {
    setTimeout(() => {
      return resolve(value);
    }, MOCK_DELAY_MS);
  });
}

export function fetchRooms(): Promise<Room[]> {
  return delay(rooms);
}

export async function addPlacesToRoom({ roomId, placeIds }: AddPlacesInput): Promise<AddPlacesResult> {
  const room = rooms.find((item) => {
    return item.id === roomId;
  });

  if (!room || placeIds.length === 0) {
    throw new Error("add-places-failed");
  }

  return delay({ roomName: room.name, addedCount: placeIds.length });
}
