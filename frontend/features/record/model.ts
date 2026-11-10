import { assertId, toKnownEnum } from "@/lib/contract";
import { formatDateRange, formatMonthDay, formatServerDateTime, parseLocalDate, parseNullableLocalDate, parseServerDateTime } from "@/lib/date";
import { parseLocalTime, type ScheduleDayDto } from "@/features/schedule/model";
import type { CoursePreviewDto } from "@/features/course/model";

const stages = ["UPLOAD", "RECEIPT_REVIEW", "READY_TO_GENERATE", "PROCESSING", "REVIEW", "EDIT", "COMPLETED"] as const;
const statuses = ["DRAFT", "COMPLETED"] as const;
type ProgressDto = { stage: string; draftKey: string | null; jobId: string | null };
type Member = { userId: number; nickname: string };
type RecordBaseDto = {
  id: number;
  title: string;
  roomId: number | null;
  status: string;
  startDate: string | null;
  endDate: string | null;
  progress: ProgressDto;
};

export type RecordListItemDto = RecordBaseDto & {
  regionName: string | null;
  memberCount: number;
  members: (Member & { profileImageUrl: string | null })[];
  placeCount: number;
  publication: { courseId: number; publishedAt: string; version: number } | null;
};
export type RecordListDto = { items: RecordListItemDto[]; nextCursor: string | null };

export type RecordPlace = {
  provider: string;
  providerPlaceId: string | null;
  name: string;
  latitude: number;
  longitude: number;
  address: string | null;
  categoryCode: string | null;
  imageUrl: string | null;
  imageAttribution: string | null;
};
export type RecordPhoto = {
  imageId: number;
  url: string;
  attribution: string | null;
  uploadedBy: number | null;
  visitId: number | null;
};
export type RecordStory = { id: number; authorId: number | null; body: string };
export type ActualVisit = {
  id: number;
  place: RecordPlace;
  plannedItemId: number | null;
  visitDate: string;
  visitedAt: string;
  order: number;
  photos: RecordPhoto[];
  stories: RecordStory[];
};
export type MaterialSummary = {
  materialId: string;
  version: number;
  type: "RECEIPT" | "PAYMENT";
  status: string;
  reviewStatus: string;
  needsReview: boolean;
  missingFields: string[];
  canUseForDraft: boolean;
  error: { code: string; message: string } | null;
};
export type Material = MaterialSummary & {
  jobId: string | null;
  extracted: { placeCandidates: RecordPlace[] } | null;
  values: { merchantName: string | null; paidAt: string | null; paidDate: string | null; paidTime: string | null; place: RecordPlace | null };
  originalAvailable: boolean;
  originalDeleteAfter: string | null;
  uploadAcceptedAt: string;
};
export type RecordUpload = {
  uploadId: string;
  imageId: number | null;
  materialId: string | null;
  purpose: "RECORD_PHOTO" | "RECEIPT";
  status: "UPLOADING" | "READY" | "FAILED";
  uploadedBy: number;
  error: { code: string; message: string } | null;
  retryable: boolean;
};
export type RecordDetailDto = RecordBaseDto & {
  members: (Member & { isCreator: boolean })[];
  plannedSchedule: { roomId: number; version: number; days: ScheduleDayDto[] } | null;
  uploads: RecordUpload[];
  photoPool: RecordPhoto[];
  actualVisits: ActualVisit[];
  materials: MaterialSummary[];
  publishedCourseId: number | null;
  version: number;
};
export type DraftSource = "MANUAL" | "RECEIPT" | "PHOTO" | "PLAN";
export type DraftItem = {
  itemId: number;
  sourceMaterialIds: string[];
  sourcePhotoIds: number[];
  plannedItemId: number | null;
  placeCandidates: RecordPlace[];
  proposedPlace: RecordPlace | null;
  visitDate: string | null;
  proposedVisitedAt: string | null;
  needsReview: boolean;
  evidence: { sourceTypes: DraftSource[]; fieldSources: Record<string, DraftSource | null>; warnings: string[] };
};
export type RecordDraft = {
  draftKey: string;
  status: string;
  baseVersion: number;
  draftVersion: number;
  items: DraftItem[];
  warnings: string[];
  error: { code: string; message: string } | null;
  sourcePriority: DraftSource[];
};

export type RecordJob = {
  jobId: string;
  type: string;
  status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";
  result: { type: "RECORD_DRAFT"; draftKey: string } | { type: "RECEIPT_OCR"; materialId: string; materialVersion: number } | null;
  error: { code: string; message: string } | null;
  retryable: boolean;
};

export type PublicationInput = {
  version: number;
  title: string;
  description?: string;
  includePhotos: boolean;
};

export type PublicationPreview = {
  preview: CoursePreviewDto;
  selectionHash: string;
  excluded: { id: number; reason: string }[];
  warnings: string[];
};

export type PublicationResult = { courseId: number; publishedAt: string; version: number };

export type PlaceInput =
  | { placeRef: { provider: "TOUR_API"; providerPlaceId: string }; customPlace?: never }
  | { customPlace: { name: string; latitude: number; longitude: number; address?: string | null }; placeRef?: never };
export type VisitInput = PlaceInput & { visitDate: string; visitedAt: string; order: number; plannedItemId?: number | null; itemId?: number };

export function toPlaceInput(place: RecordPlace | null): PlaceInput | null {
  if (place?.provider === "TOUR_API" && place.providerPlaceId) {
    return { placeRef: { provider: "TOUR_API", providerPlaceId: place.providerPlaceId } };
  }

  if (place?.provider === "MANUAL" && place.name.trim() &&
    Number.isFinite(place.latitude) && Number.isFinite(place.longitude) &&
    Math.abs(place.latitude) <= 90 && Math.abs(place.longitude) <= 180) {
    return { customPlace: { name: place.name.trim(), latitude: place.latitude, longitude: place.longitude, address: place.address } };
  }

  return null;
}

/** 사용자가 입력한 한국 시각을 요청 형식으로 바꾼다. 기존 방문의 오프셋은 수정하지 않으면 유지한다. */
export function recordDateTime(date: string, time: string, original?: string | null): string | null {
  try {
    const day = parseLocalDate(date, "date");
    const clock = parseLocalTime(time, "time");

    if (original?.slice(0, 10) === day && original.slice(11, 16) === clock) {
      parseServerDateTime(original, "visitedAt");
      return original;
    }

    return `${day} ${clock}:00+09`;
  } catch {
    return null;
  }
}

export function normalizeReceiptDate(value: string): string | null {
  const text = value.trim();
  const parts = /^(\d{4})[-./](\d{1,2})[-./](\d{1,2})$/.exec(text) ?? /^(\d{4})(\d{2})(\d{2})$/.exec(text);

  if (!parts || Number(parts[2]) < 1 || Number(parts[2]) > 12 || Number(parts[3]) < 1) {
    return null;
  }

  const date = `${parts[1]}-${parts[2].padStart(2, "0")}-${parts[3].padStart(2, "0")}`;

  try {
    return parseLocalDate(date, "paidDate");
  } catch {
    return null;
  }
}

export function normalizeReceiptTime(value: string): string | null {
  const parts = /^(\d{1,2}):?(\d{2})$/.exec(value.trim());

  if (!parts) {
    return null;
  }

  try {
    return parseLocalTime(`${parts[1].padStart(2, "0")}:${parts[2]}`, "paidTime");
  } catch {
    return null;
  }
}

export function canCreateRecord(endDate: string | null, now = new Date()): boolean {
  const todayInSeoul = formatServerDateTime(now).slice(0, 10);
  return endDate !== null && endDate < todayInSeoul;
}

export function recordDateLabel(startDate: string | null, endDate: string | null): string {
  if (startDate && endDate) {
    return formatDateRange(startDate, endDate, { weekday: false });
  }

  const date = startDate ?? endDate;
  return date ? formatMonthDay(date, { weekday: false }) : "날짜 미정";
}

export function plannedDelayMinutes(date: string, time: string, actual: string): number | null {
  const planned = recordDateTime(date, time.slice(0, 5));

  if (!planned || !actual.startsWith(date)) {
    return null;
  }

  try {
    const difference = parseServerDateTime(actual, "visitedAt").getTime() - parseServerDateTime(planned, "arrivalTime").getTime();
    const minutes = Math.floor(difference / 60_000);
    return minutes > 0 ? minutes : null;
  } catch {
    return null;
  }
}

function toBase(dto: RecordBaseDto) {
  return {
    ...dto,
    id: assertId(dto.id, "recordId"),
    startDate: parseNullableLocalDate(dto.startDate, "startDate"),
    endDate: parseNullableLocalDate(dto.endDate, "endDate"),
    status: toKnownEnum(dto.status, statuses),
    progress: { ...dto.progress, stage: toKnownEnum(dto.progress.stage, stages) },
  };
}

export function recordMembers(members: Member[]) {
  return members.map(({ userId, nickname }) => {
    return { id: userId, nickname, initial: nickname.slice(0, 1) };
  });
}

export function toRecordListItem(dto: RecordListItemDto) {
  return {
    ...dto, ...toBase(dto), members: recordMembers(dto.members),
    publication: dto.publication && { ...dto.publication, publishedAt: parseServerDateTime(dto.publication.publishedAt, "publishedAt") },
  };
}

export function toRecordDetail(dto: RecordDetailDto) {
  const plan = dto.plannedSchedule;
  return {
    ...dto, ...toBase(dto), members: recordMembers(dto.members),
    plannedSchedule: plan && { ...plan,

      days: plan.days.map((day) => {
        return {
          date: day.date,

          stops: day.stops.map((stop) => {
            return { id: stop.roomPlaceId, arrivalTime: stop.arrivalTime || null };
          }),
        };
      }) },

    actualVisits: [...dto.actualVisits].sort((a, b) => {
      return a.visitDate.localeCompare(b.visitDate) || a.order - b.order;
    }),
  };
}

export type RecordListItem = ReturnType<typeof toRecordListItem>;
export type RecordDetail = ReturnType<typeof toRecordDetail>;
