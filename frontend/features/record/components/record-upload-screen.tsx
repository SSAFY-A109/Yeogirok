import { useQuery } from "@tanstack/react-query";
import { CalendarDays, CircleCheck, LockKeyhole, ReceiptText, Trash2 } from "lucide-react-native";
import { useEffect, useRef, useState } from "react";
import { Keyboard, ScrollView, View } from "react-native";

import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Icon } from "@/components/ui/icon";
import { MediaImage } from "@/components/ui/media-photo";
import { HeaderIconButton } from "@/components/ui/screen-header";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import { isApiError } from "@/api/errors";
import { completeUpload } from "@/features/media/api/media.api";
import { useUploadMedia } from "@/features/media/api/queries";
import type { UploadFile } from "@/features/media/upload";
import { formatServerDateTime, parseServerDateTime, toLocalDate } from "@/lib/date";
import { useRecordWrite } from "../api/queries";
import { connectReceipt, deleteRecordPhoto, generateDraft, getMaterials, getRecordFileAccess, getRecordJob, retryReceipt, reviewReceipt } from "../api/record.api";
import { recordError } from "../errors";
import { normalizeReceiptDate, normalizeReceiptTime, recordDateTime, toPlaceInput, type Material, type RecordDetail, type RecordPlace } from "../model";
import { pickRecordFile } from "../native/pick-record-file";
import { ReceiptDateTimePicker } from "../native/receipt-date-time-picker";
import { RecordField, RecordPlaceField } from "./record-fields";
import { RecordFrame } from "./record-frame";
import { RecordIssue, RecordLoading } from "./record-list-screen";
import { RecordPhoto } from "./record-photo";

const WAITING = ["UPLOADED", "EXTRACTING"];
const ACTIVE_JOB = ["QUEUED", "RUNNING"];

export function RecordUploadScreen({ record, onBack, onReview, onRefresh }: {
  record: RecordDetail; onBack: () => void; onReview: (key: string) => void; onRefresh: () => void;
}) {
  const [editing, setEditing] = useState<Material | null>(null);
  const [selected, setSelected] = useState<UploadFile | null>(null);
  const [pickerError, setPickerError] = useState<string | null>(null);
  const [picking, setPicking] = useState(false);
  const [submittedJob, setSubmittedJob] = useState<{ draftKey: string; jobId: string } | null>(null);
  const [photoToDelete, setPhotoToDelete] = useState<{ imageId: number; version: number } | null>(null);
  const pickerActive = useRef(false);
  const upload = useUploadMedia();
  const write = useRecordWrite(record.id);
  const { stage } = record.progress;
  const jobId = submittedJob?.jobId ?? record.progress.jobId;
  const generationPending = stage === "PROCESSING" || submittedJob !== null;
  const materials = useQuery({
    queryKey: ["record", record.id, "materials"],

    queryFn: ({ signal }) => {
      return getMaterials(record.id, signal);
    },

    refetchInterval: (query) => {
      return query.state.data?.some((item) => {
        return WAITING.includes(item.status);
      }) ? 2500 : false;
    },
  });
  const job = useQuery({
    queryKey: ["record", record.id, "job", jobId],
    enabled: generationPending && Boolean(jobId),

    queryFn: ({ signal }) => {
      return getRecordJob(jobId!, signal);
    },

    refetchInterval: (query) => {
      return !query.state.data || ACTIVE_JOB.includes(query.state.data.status) ? 2500 : false;
    },
  });
  const jobDone = stage === "REVIEW" || job.data?.status === "SUCCEEDED";
  const jobFailed = generationPending && job.data?.status === "FAILED";
  const processing = generationPending && !jobDone && !jobFailed;
  const draftKey = job.data?.result?.type === "RECORD_DRAFT" ? job.data.result.draftKey : submittedJob?.draftKey ?? record.progress.draftKey;
  const items = materials.isError ? [] : materials.data ?? [];

  const ready = items.filter((item) => {
    return item.canUseForDraft;
  });

  const canGenerate = record.photoPool.length > 0 && ready.length > 0 &&
    items.every((item) => {
      return item.canUseForDraft || item.status === "CANCELLED" || item.status === "FAILED";
    }) && !materials.isError;

  const busy = picking || upload.isPending || write.isPending;
  const locked = processing || jobDone || busy;

  async function linkReceipt(uploadId: string) {
    const linked = await write.run(`connect-receipt:${uploadId}`, (key) => {
      return connectReceipt(record.id, uploadId, key);
    });

    void materials.refetch();
    return linked;
  }

  async function sendFile(file: UploadFile) {
    try {
      const result = await upload.mutateAsync(file);
      onRefresh();

      if (file.metadata.purpose === "RECEIPT" && !await linkReceipt(result.uploadId)) {
        return;
      }

      setSelected(null);
    } catch {
      onRefresh();
    }
  }

  async function selectFile(purpose: "RECORD_PHOTO" | "RECEIPT") {
    if (pickerActive.current || busy) {
      return;
    }

    pickerActive.current = true;
    setPicking(true);
    setPickerError(null);
    write.reset();

    try {
      const file = await pickRecordFile(record.id, purpose);

      if (!file) {
        return;
      }

      setSelected(file);
      await sendFile(file);
    } catch (error) {
      setPickerError(recordError(error, "파일을 열지 못했어요. 다른 파일을 골라 주세요."));
    } finally {
      pickerActive.current = false;
      setPicking(false);
    }
  }

  if (editing) {
    return <ReceiptReview key={`${editing.materialId}:${editing.version}`} recordId={record.id} material={editing} tripEndDate={record.endDate}
      onBack={() => {
        return setEditing(null);
      }} onReload={async () => {
        const result = await materials.refetch();

        const latest = result.data?.find((item) => {
          return item.materialId === editing.materialId;
        });

        if (latest) {
          setEditing(latest);
        }
      }} onSaved={() => {
        setEditing(null); void materials.refetch();
      }} />;
  }

  return <RecordFrame centered title={`${record.title} 기록`} onBack={onBack} footer={
    <Button className="w-full" size="lg" testID="memo.generate" loading={write.isPending}
      disabled={processing || busy || (jobDone ? !draftKey : !canGenerate || (jobFailed && !job.data?.retryable))}
      onPress={() => {
        if (jobDone && draftKey) {
          onReview(draftKey); return;
        }

        const body = {
          baseVersion: record.version,

          imageIds: record.photoPool.map((photo) => {
            return photo.imageId;
          }),

          materials: ready.map(({ materialId, version }) => {
            return { materialId, version };
          }),
        };

        void write.run(`generate:${JSON.stringify(body)}`, async (key) => {
          const result = await generateDraft(record.id, body, key);
          setSubmittedJob(result);
        });
      }}><Text>{jobDone ? "초안 확인" : jobFailed ? "초안 다시 생성하기" : processing ? "초안 생성 중" : "초안 생성하기"}</Text></Button>
  }>
    <View className="gap-2">
      <Text accessibilityRole="header" className="text-h2">사진과 영수증 올리기</Text>
      <Text className="text-body-sm text-foreground-secondary">여행 사진을 먼저 올려 주세요. 그다음 영수증을 올리면 사진과 영수증, 참고 계획을 모아 방문 초안을 만들 수 있어요.</Text>
    </View>
    <View className="gap-3">
      <Text className="text-label">사진 {record.photoPool.length}장</Text>
      <ScrollView horizontal contentContainerClassName="gap-2">
        {record.photoPool.map((photo) => {
          return <View key={photo.imageId} className="items-center gap-1">
            <RecordPhoto record={record} photo={photo} />
            {!locked && <HeaderIconButton icon={Trash2} accessibilityLabel="기록 사진 삭제" onPress={() => {
              setPhotoToDelete({ imageId: photo.imageId, version: record.version });
            }} />}
          </View>;
        })}
      </ScrollView>
      <Button variant="outline" disabled={locked} testID="memo.add-photo" onPress={() => {
        return void selectFile("RECORD_PHOTO");
      }}><Text>사진 올리기</Text></Button>
    </View>
    <View className="gap-3">
      <Text className="text-label">영수증 {items.length}장</Text>
      {materials.isPending && <RecordLoading />}
      {materials.isError && <RecordIssue message="영수증 정보를 불러오지 못했어요." onRetry={() => {
        return void materials.refetch();
      }} />}
      {items.map((item) => {
        return (
          <View key={item.materialId} className="flex-row items-center gap-3 rounded-md bg-muted p-3">
            <Icon as={ReceiptText} size={20} className="text-icon" />
            <View className="flex-1">
              <Text className="text-label">{item.values?.merchantName ?? "영수증"}</Text>
              <Text className="text-caption text-muted-foreground">
                {item.canUseForDraft ? "확인 완료" : WAITING.includes(item.status) ? "읽는 중" : item.status === "FAILED" ? "읽기 실패" : item.status === "CANCELLED" ? "취소됨" : "확인 필요"}
              </Text>
            </View>
            {item.status === "READY" && (
              <Button variant="secondary" disabled={locked} onPress={() => {
                return setEditing(item);
              }}><Text>{item.needsReview ? "보정하기" : "확인하기"}</Text></Button>
            )}
            {item.status === "FAILED" && item.originalAvailable && (
              <Button variant="secondary" disabled={locked} onPress={async () => {
                await write.run(`retry:${item.materialId}:${item.version}`, (key) => {
                  return retryReceipt(record.id, item.materialId, item.version, key);
                });

                void materials.refetch();
              }}><Text>다시 읽기</Text></Button>
            )}
            {item.status === "FAILED" && !item.originalAvailable && <Text className="text-caption text-muted-foreground">다시 올려 주세요</Text>}
          </View>
        );
      })}
      {record.uploads.filter((item) => {
        return item.status !== "READY" || (item.purpose === "RECEIPT" &&
        !items.some((receipt) => {
          return receipt.materialId === item.materialId;
        }));
      }).map((item) => {
        return (
          <View key={item.uploadId} className="flex-row items-center justify-between gap-2 rounded-md bg-muted p-3">
            <Text className="flex-1 text-caption text-foreground-secondary">
              {item.purpose === "RECEIPT" ? "영수증" : "사진"} {item.status === "READY" ? "연결 대기" : item.status === "UPLOADING" ? "업로드 확인 대기" : "업로드 실패"}
            </Text>
            {item.status === "READY" && <Button variant="secondary" disabled={locked || !record.photoPool.length}
              onPress={() => {
                return void linkReceipt(item.uploadId);
              }}><Text>연결하기</Text></Button>}
            {item.status === "UPLOADING" && item.retryable && <Button variant="secondary" disabled={locked}
              onPress={async () => {
                const verified = await write.run(`finish-upload:${item.uploadId}`, (key) => {
                  return completeUpload({ uploadId: item.uploadId, idempotencyKey: key });
                });

                if (verified && item.purpose === "RECEIPT") {
                  await linkReceipt(item.uploadId);
                }
              }}><Text>완료 확인</Text></Button>}
          </View>
        );
      })}
      <Button variant="outline" testID="memo.add-receipt" disabled={locked || record.photoPool.length === 0}
        onPress={() => {
          return void selectFile("RECEIPT");
        }}><Text>영수증 올리기</Text></Button>
      <View className="flex-row items-center gap-2">
        <Icon as={LockKeyhole} size={14} className="text-icon" />
        <Text className="flex-1 text-caption text-muted-foreground">영수증 원본은 올린 사람만 볼 수 있어요.</Text>
      </View>
    </View>
    {upload.isPending && <Text accessibilityLiveRegion="polite" className="text-body-sm text-primary">파일 업로드 중 {Math.round(upload.progress * 100)}%</Text>}
    {pickerError && <RecordIssue message={pickerError} />}
    {(upload.isError || write.isError) && <RecordIssue message={recordError(upload.error ?? write.error)}
      onRetry={selected && !locked && !(isApiError(upload.error) && ["M001", "M002", "M003"].includes(upload.error.code))
        ? () => {
          return void sendFile(selected);
        } : undefined} />}
    {(processing || jobDone || jobFailed) && <View className="gap-3 rounded-md border border-border p-4">
      <Text className="text-label">{jobDone ? "방문 일정 정리 완료" : jobFailed ? "방문 일정을 정리하지 못했어요" : "방문 일정 정리 중"}</Text>
      <Text className="text-caption text-foreground-secondary">사진 {record.photoPool.length}장 · 확인한 영수증 {ready.length}장</Text>
      {processing && !job.isError && <Spinner tone="brand" accessibilityLabel="방문 일정 초안 생성 중" />}
      {jobDone && <Icon as={CircleCheck} className="text-positive" size={20} />}
      {jobFailed && <Text className="text-caption text-foreground-secondary">
        {job.data?.retryable ? "사진과 영수증을 확인한 뒤 다시 시도해 주세요." : "이 작업은 다시 시도할 수 없어요."}
      </Text>}
      {job.isError && <RecordIssue message="초안 진행 상태를 불러오지 못했어요." onRetry={() => {
        return void job.refetch();
      }} />}
    </View>}
    <ConfirmDialog open={photoToDelete !== null} onOpenChange={(open) => {
      if (!open) {
        setPhotoToDelete(null);
      }
    }} title="기록 사진을 삭제할까요?" description="보관함과 연결된 방문에서 이 사진이 삭제돼요."
    confirmLabel="삭제" confirmTone="destructive" loading={write.isPending} onConfirm={async () => {
      if (!photoToDelete) {
        return;
      }

      const { imageId, version } = photoToDelete;

      await write.run(`photo-delete:${imageId}:${version}`, (key) => {
        return deleteRecordPhoto(record.id, imageId, version, key);
      });

      setPhotoToDelete(null);
    }} />
  </RecordFrame>;
}

function ReceiptReview({ recordId, material, tripEndDate, onBack, onReload, onSaved }: {
  recordId: number; material: Material; tripEndDate: string | null; onBack: () => void; onReload: () => Promise<void>; onSaved: () => void;
}) {
  const [merchantName, setMerchantName] = useState(material.values?.merchantName ?? "");
  const [date, setDate] = useState(material.values?.paidDate ?? material.values?.paidAt?.slice(0, 10) ?? "");
  const [time, setTime] = useState(material.values?.paidTime?.slice(0, 5) ?? material.values?.paidAt?.slice(11, 16) ?? "");
  const [pickerDate, setPickerDate] = useState<Date | null>(null);
  const [place, setPlace] = useState<RecordPlace | null>(material.values?.place ?? null);
  const [imageFailed, setImageFailed] = useState(false);
  const [expiredAt, setExpiredAt] = useState(0);
  const write = useRecordWrite(recordId);
  const original = useQuery({
    queryKey: ["record", recordId, "original", material.materialId],
    enabled: material.originalAvailable, gcTime: 0, retry: false,

    queryFn: ({ signal }) => {
      return getRecordFileAccess(recordId, "materials", material.materialId, signal);
    },

    refetchInterval: (query) => {
      if (!query.state.data || query.state.error) {
        return false;
      }

      const originalDeadline = material.originalDeleteAfter ? parseServerDateTime(material.originalDeleteAfter, "originalDeleteAfter").getTime() : Infinity;
      return Math.max(1000, Math.min(query.state.data.expiresAt.getTime(), originalDeadline) - Date.now());
    },
  });
  const placeInput = toPlaceInput(place);
  const paidDate = normalizeReceiptDate(date);
  const paidTime = normalizeReceiptTime(time);
  const paidAt = paidDate && paidTime
    ? recordDateTime(paidDate, paidTime, material.values?.paidAt) ?? recordDateTime(paidDate, paidTime) : null;
  const missing = [
    !merchantName.trim() && "가게 이름",
    !paidDate && "결제 날짜",
    !paidTime && "결제 시각",
    !placeInput && "장소 선택",
  ].filter(Boolean);
  const expiresAt = original.data?.expiresAt.getTime() ?? 0;

  function openDatePicker() {
    Keyboard.dismiss();

    const initialDate = paidDate ?? normalizeReceiptDate(tripEndDate ?? "") ?? formatServerDateTime(new Date()).slice(0, 10);
    setPickerDate(new Date(`${initialDate}T12:00:00`));
  }

  useEffect(() => {
    if (!expiresAt) {
      return;
    }

    const timer = setTimeout(() => {
      return setExpiredAt(expiresAt);
    }, Math.max(0, expiresAt - Date.now()));

    return () => {
      return clearTimeout(timer);
    };
  }, [expiresAt]);

  return <RecordFrame centered title="영수증 확인" onBack={onBack} footer={<>
    {missing.length > 0 && <Text accessibilityLiveRegion="polite" className="w-full text-caption text-foreground-secondary">
      확인 필요 · {missing.join(", ")}
    </Text>}
    <Button className="w-full" size="lg" disabled={missing.length > 0 || !paidAt} loading={write.isPending}
      testID="memo.receipt-confirm"
      onPress={async () => {
        if (!placeInput || !paidAt) {
          return;
        }

        const body = { ...placeInput, version: material.version, merchantName: merchantName.trim(), paidAt };

        if (await write.run(`receipt:${material.materialId}:${JSON.stringify(body)}`, (key) => {
          return reviewReceipt(recordId, material.materialId, body, key);
        })) {
          onSaved();
        }
      }}><Text>확인 완료</Text></Button>
  </>}>
    <Text accessibilityRole="header" className="text-h2">읽은 내용이 맞는지 확인해 주세요</Text>
    {__DEV__ && <Text className="text-caption text-muted-foreground">
      개발 환경에서는 영수증을 자동으로 판독하지 않아요. 가게 이름과 결제 시각을 입력하고 날짜와 장소를 선택해 주세요.
    </Text>}
    {original.data && !original.isError && !imageFailed && material.originalAvailable && expiresAt !== expiredAt ? (
      <View className="h-44 rounded-md bg-muted p-3">
        <MediaImage uri={original.data.url} contentFit="contain" cachePolicy="none" accessibilityLabel="내 영수증 원본"
          onError={() => {
            return setImageFailed(true);
          }} style={{ width: "100%", height: "100%" }} />
      </View>
    ) : material.originalAvailable && !original.isError && original.isFetching ? <Spinner tone="brand" /> : (
      <RecordIssue message="영수증 원본을 표시할 수 없어요. 남아 있는 값은 직접 확인할 수 있어요."
        onRetry={material.originalAvailable ? () => {
          setImageFailed(false); void original.refetch();
        } : undefined} />
    )}
    <RecordField label="가게 이름" value={merchantName} onChange={setMerchantName} invalid={material.missingFields.includes("merchantName") && !merchantName.trim()} />
    <View className="gap-2">
      <Text className="text-label">결제 날짜</Text>
      <Button variant="outline" className="min-h-input justify-between border-input" testID="memo.receipt-date"
        accessibilityLabel={paidDate ? `결제 날짜 ${paidDate}, 변경` : "결제 날짜 선택"} onPress={() => {
          openDatePicker();
        }}>
        <Text className={paidDate ? "text-body-sm text-foreground" : "text-body-sm text-muted-foreground"}>{paidDate ?? "날짜 선택"}</Text>
        <Icon as={CalendarDays} size={20} className="text-icon" />
      </Button>
    </View>
    <RecordField label="결제 시각 (한국 시간)" value={time} onChange={setTime} placeholder="예: 14:30"
      invalid={material.missingFields.includes("paidTime") && !paidTime} />
    {pickerDate && <ReceiptDateTimePicker value={pickerDate}
      onClose={() => {
        setPickerDate(null);
      }} onSelect={(next) => {
        setDate(toLocalDate(next));
      }} />}
    <RecordPlaceField mode="receipt" place={place}
      candidates={material.extracted?.placeCandidates ?? []} onChange={setPlace} />
    {write.isError && <RecordIssue message={recordError(write.error)} onRetry={isApiError(write.error) && write.error.code === "C004" ? () => {
      return void onReload();
    } : undefined} />}
  </RecordFrame>;
}
