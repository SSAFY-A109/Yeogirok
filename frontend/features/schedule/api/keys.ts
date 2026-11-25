/** 일정 쿼리 키. 여행방 쿼리(`room/api/queries.ts`)도 무효화에 쓰므로 순환 참조가 생기지 않게 따로 둔다. */
export const scheduleKeys = {
  all: ["schedule"] as const,

  room: (roomId: number) => {
    return [...scheduleKeys.all, "room", roomId] as const;
  },

  draft: (roomId: number, draftKey: string) => {
    return [...scheduleKeys.all, "draft", roomId, draftKey] as const;
  },

  /** 저장된 일정을 이어받아 만든 초안("일정 조정하기"). */
  savedDraft: (roomId: number) => {
    return [...scheduleKeys.all, "saved-draft", roomId] as const;
  },
};
