import { useRef, useState } from "react";
import { useMutation } from "@tanstack/react-query";

import { isApiError } from "@/api/errors";
import { createIdempotencyKey } from "@/api/idempotency";
import { uploadMedia, type UploadAttempt, type UploadFile, type UploadStage } from "@/features/media/upload";

/** 파일 하나당 훅 하나. 같은 파일 객체로 재시도하면 완료된 PUT과 세션 발급을 반복하지 않는다. */
export function useUploadMedia() {
  const attempt = useRef<UploadAttempt | null>(null);
  const [progress, setProgress] = useState(0);
  const [stage, setStage] = useState<UploadStage | null>(null);
  const mutation = useMutation({
    mutationFn: async (file: UploadFile) => {
      if (!attempt.current || attempt.current.file !== file) {
        attempt.current = { file, sessionKey: createIdempotencyKey(), completeKey: createIdempotencyKey(), session: null, transferred: false, ready: null };
        setProgress(0);
      }

      const task = attempt.current;

      try {
        return await uploadMedia(task, setStage, setProgress);
      } catch (error) {
        if (isApiError(error) && ["M001", "M002", "M003", "M011"].includes(error.code)) {
          attempt.current = null;
        } else if (!task.transferred && task.session && task.session.uploadExpiresAt.getTime() <= Date.now()) {
          attempt.current = null;
        }

        throw error;
      }
    },

    retry: false,
  });

  return { ...mutation, progress, stage };
}
