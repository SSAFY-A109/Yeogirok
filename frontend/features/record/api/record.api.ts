import { api, idempotentRequestConfig, TIMEOUT_MUTATION_MS } from "@/api/client";
import { toCoursePreview } from "@/features/course/model";
import { ContractMismatchError } from "@/lib/contract";
import { parseServerDateTime } from "@/lib/date";
import {
  toRecordDetail, toRecordListItem, type Material, type PlaceInput, type RecordDetailDto,
  type PublicationInput, type PublicationPreview, type PublicationResult,
  type RecordDraft, type RecordJob, type RecordListDto, type VisitInput,
} from "../model";

export async function getRecords(cursor?: string, signal?: AbortSignal, roomId?: number) {
  const { data } = await api.get<RecordListDto>("/records", { params: { cursor, roomId }, signal });
  return { items: data.items.map(toRecordListItem), nextCursor: data.nextCursor };
}

export async function getRecord(id: number, signal?: AbortSignal) {
  const { data } = await api.get<RecordDetailDto>(`/records/${id}`, { signal });
  return toRecordDetail(data);
}

export async function createRecord(roomId: number, key: string) {
  const { data } = await api.post<{ recordId: number }>(`/rooms/${roomId}/records`, {}, idempotentRequestConfig(key));
  return data;
}

export async function previewPublication(id: number, body: PublicationInput) {
  const { data } = await api.post<PublicationPreview>(`/records/${id}/publication-preview`, body, { timeout: TIMEOUT_MUTATION_MS });
  const preview = toCoursePreview(data.preview);

  if (!body.includePhotos && preview.places.some((place) => {
    return place.photoUrls.length > 0;
  })) {
    throw new ContractMismatchError("preview.days.items.photos");
  }

  return { ...data, preview };
}

export async function publishRecord(id: number, body: PublicationInput & { selectionHash: string }, key: string) {
  const { data } = await api.post<PublicationResult>(`/records/${id}/publications`, body, idempotentRequestConfig(key));
  return data;
}

export async function getMaterials(id: number, signal?: AbortSignal) {
  const { data } = await api.get<{ items: Material[] }>(`/records/${id}/materials`, { signal });
  return data.items;
}

export async function getRecordFileAccess(id: number, kind: "images" | "materials", fileId: number | string, signal?: AbortSignal) {
  const { data } = await api.get<{ url: string; expiresAt: string; contentType: string }>(
    `/records/${id}/${kind}/${fileId}/access`, { signal },
  );
  return { ...data, expiresAt: parseServerDateTime(data.expiresAt, "expiresAt") };
}

export async function connectReceipt(id: number, uploadId: string, key: string) {
  await api.post(`/records/${id}/materials`, { type: "RECEIPT", uploadId }, idempotentRequestConfig(key));
}

export async function reviewReceipt(id: number, materialId: string, body: PlaceInput & {
  version: number; merchantName: string; paidAt: string;
}, key: string) {
  await api.patch(`/records/${id}/materials/${materialId}/review`, body, idempotentRequestConfig(key));
}

export async function retryReceipt(id: number, materialId: string, version: number, key: string) {
  await api.post(`/records/${id}/materials/${materialId}/ocr`, { version }, idempotentRequestConfig(key));
}

export async function generateDraft(id: number, body: {
  baseVersion: number; imageIds: number[]; materials: { materialId: string; version: number }[];
}, key: string) {
  const { data } = await api.post<{ draftKey: string; jobId: string; status: "QUEUED" }>(
    `/records/${id}/drafts`, body, idempotentRequestConfig(key),
  );
  return data;
}

export async function getRecordJob(jobId: string, signal?: AbortSignal) {
  const { data } = await api.get<RecordJob>(`/jobs/${jobId}`, { signal });
  return data;
}

export async function getRecordDraft(id: number, draftKey: string, signal?: AbortSignal) {
  const { data } = await api.get<RecordDraft>(`/records/${id}/drafts/${draftKey}`, { signal });
  return data;
}

export async function addVisits(id: number, body: {
  version: number; draftKey?: string; draftVersion?: number; items: VisitInput[];
}, key: string) {
  await api.post(`/records/${id}/visits`, body, idempotentRequestConfig(key));
}

export async function updateVisits(id: number, body: {
  version: number; items: (Omit<VisitInput, keyof PlaceInput> & Partial<PlaceInput> & { visitId: number })[];
  removeVisitIds?: number[]; confirmDeleteStories?: boolean;
}) {
  await api.patch(`/records/${id}/visits`, body, { timeout: TIMEOUT_MUTATION_MS });
}

export async function connectPhotos(id: number, visitId: number, imageIds: number[], key: string) {
  await api.post(`/records/${id}/visits/${visitId}/photos`, { imageIds }, idempotentRequestConfig(key));
}

export async function deleteRecordPhoto(id: number, imageId: number, version: number, key: string) {
  await api.delete(`/records/${id}/images/${imageId}`, { ...idempotentRequestConfig(key), params: { version } });
}

export async function saveStory(id: number, visitId: number, body: string, key: string, storyId?: number) {
  const path = `/records/${id}/visits/${visitId}/stories`;

  if (storyId === undefined) {
    await api.post(path, { body }, idempotentRequestConfig(key));
  } else {
    await api.patch(`${path}/${storyId}`, { body }, { timeout: TIMEOUT_MUTATION_MS });
  }
}

export async function completeRecord(id: number, version: number, key: string) {
  await api.post(`/records/${id}/complete`, { version }, idempotentRequestConfig(key));
}
