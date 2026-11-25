import type { CreateUploadRequestDto, ReadyUploadDto, UploadSessionDto } from "../../../features/media/model";

export type MockUpload = {
  request: CreateUploadRequestDto;
  session: UploadSessionDto;
  transferredBytes: number | null;
  bytes?: ArrayBuffer;
  acceptedAt?: number;
  result: ReadyUploadDto | null;
};

export type MockIdempotentResponse = { request: string; data: unknown; status: number };
