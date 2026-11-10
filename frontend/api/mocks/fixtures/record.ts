import type { CourseDayDto } from "../../../features/course/model";
import type { Material, RecordDetailDto, RecordDraft, RecordPlace } from "../../../features/record/model";
import type { MockRoom } from "./room";

export type MockRecord = {
  detail: RecordDetailDto;
  materials: Map<string, Material>;
  draft: (RecordDraft & { readyAt: number; jobId: string; applied: boolean }) | null;
  publication: { description: string; publishedAt: string; imageIds: number[] } | null;
};

export function makeMockRecord(id: number, room: MockRoom, title = room.detail.name): MockRecord {
  const { roomId, startDate, endDate, members } = room.detail;
  return {
    detail: {
      id, roomId, title, startDate, endDate, status: "DRAFT", version: 1,

      members: members.map(({ userId, nickname, me }) => {
        return { userId, nickname, isCreator: me };
      }),

      progress: { stage: "UPLOAD", draftKey: null, jobId: null },
      plannedSchedule: room.schedule && {
        roomId, version: room.schedule.version,

        days: room.schedule.days.map((day) => {
          return { ...day,

            stops: day.stops.map((stop) => {
              return { ...stop };
            }) };
        }),
      },
      uploads: [], photoPool: [], actualVisits: [], materials: [], publishedCourseId: null,
    },
    materials: new Map(),
    draft: null,
    publication: null,
  };
}

export const mockVisitPlace: RecordPlace = {
  provider: "MANUAL", providerPlaceId: null, name: "흰여울문화마을", latitude: 35.0788, longitude: 129.0442,
  address: "부산 영도구 흰여울길", categoryCode: null, imageUrl: null, imageAttribution: null,
};

export function createRecordFixtures(rooms: MockRoom[]) {
  const room = rooms.find((item) => {
    return item.detail.roomId === 3;
  })!;

  const record = makeMockRecord(1, room);
  record.detail.status = "COMPLETED";
  record.detail.progress.stage = "COMPLETED";
  record.detail.actualVisits.push({
    id: 701, place: mockVisitPlace, plannedItemId: 3001, visitDate: "2026-10-03", visitedAt: "2026-10-03 10:30:00+09",
    order: 1, photos: [], stories: [{ id: 1001, authorId: 1, body: "바닷길을 따라 천천히 걸었어요." }],
  });
  record.detail.actualVisits.push({
    id: 702,
    place: { ...mockVisitPlace, name: "태종대", latitude: 35.0513, longitude: 129.0877, address: "부산 영도구 전망로" },
    plannedItemId: 3002, visitDate: "2026-10-04", visitedAt: "2026-10-04 11:20:00+09",
    order: 1, photos: [], stories: [],
  });
  return new Map([[record.detail.id, record]]);
}

export function publicRecordDays(record: MockRecord, includePhoto: (imageId: number) => boolean): CourseDayDto[] {
  const visits = [...record.detail.actualVisits].sort((a, b) => {
    return a.visitDate.localeCompare(b.visitDate) || a.order - b.order;
  });

  const dates = [...new Set(visits.map((visit) => {
    return visit.visitDate;
  }))];

  return dates.map((date, index) => {
    return {
      day: index + 1,

      items: visits.filter((visit) => {
        return visit.visitDate === date;
      }).map(({ id, order, place, photos }) => {
        return {
          visitId: id, order, place: { ...place, categoryName: null },

          photos: photos.filter((photo) => {
            return includePhoto(photo.imageId);
          }).map(({ imageId, url, attribution }) => {
            return { imageId, url, attribution };
          }),
        };
      }),
    };
  });
}

export function publicRecordAuthors(room: MockRoom) {
  return room.detail.members.map(({ userId, profileImageUrl }) => {
    return { id: userId, profileImageUrl };
  }).sort((a, b) => {
    return a.id - b.id;
  });
}
