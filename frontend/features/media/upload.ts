import { completeUpload, createUpload, putUpload, type UploadBody } from "./api/media.api";
import type { CreateUploadRequestDto, ReadyUpload, UploadSession } from "./model";

export type UploadFile = { metadata: CreateUploadRequestDto; body: UploadBody };
export type UploadStage = "session" | "transfer" | "complete" | "ready";
export type UploadAttempt = {
  file: UploadFile;
  sessionKey: string;
  completeKey: string;
  session: UploadSession | null;
  transferred: boolean;
  ready: ReadyUpload | null;
};

export async function uploadMedia(task: UploadAttempt, onStage: (stage: UploadStage) => void, onProgress?: (fraction: number) => void) {
  if (task.ready) {
    return task.ready;
  }

  if (!task.session) {
    onStage("session");
    task.session = await createUpload({ request: task.file.metadata, idempotencyKey: task.sessionKey });
  }

  if (!task.transferred) {
    onStage("transfer");
    await putUpload(task.session, task.file.body, onProgress);
    task.transferred = true;
  }

  onStage("complete");
  task.ready = await completeUpload({ uploadId: task.session.uploadId, idempotencyKey: task.completeKey });
  onStage("ready");
  return task.ready;
}
