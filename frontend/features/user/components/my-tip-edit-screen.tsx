import { useRef, useState } from "react";
import { Alert, Pressable, ScrollView, View } from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import { Camera, X } from "lucide-react-native";
import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { DesignAsset } from "@/components/ui/design-asset";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { Textarea } from "@/components/ui/textarea";
import { FilterChip } from "@/components/ui/filter-chip";
import { createIdempotencyKey } from "@/api/idempotency";
import { isApiError } from "@/api/errors";
import { useUploadMedia } from "@/features/media/api/queries";
import type { UploadFile } from "@/features/media/upload";
import { useMyTip, useReadMyTip, useUpdateTip } from "@/features/tip/api/queries";
import type { UpdateTipInput } from "@/features/tip/api/tip.api";
import { TipPublication } from "@/features/tip/components/tip-publication";
import { getTipErrorMessage, isTipUnavailable } from "@/features/tip/errors";
import { normalizeTipBody, TIP_BODY_MAX_LENGTH, TIP_TYPE_LABELS, TIP_TYPE_VALUES, type MyTipDetail, type TipTypeDto } from "@/features/tip/model";
import { addTipPhoto, arErrorMessage } from "@/features/tip/native/ar";
import type { TipPhoto } from "@/features/tip/native/ar/contracts";
import { prepareTipPhotoUpload } from "@/features/tip/native/ar/public";
import { MyLoading, MyPage, MyUnavailable } from "./my-page";

type Photo = { kind: "remote"; url: string; imageId: number } | { kind: "local"; value: TipPhoto } | null;

export function MyTipEditScreen() {
  const { tipId } = useLocalSearchParams<{ tipId?: string }>();
  const id = typeof tipId === "string" && /^\d+$/.test(tipId) ? Number(tipId) : 0;
  return <MyPage title="팁 수정" testID="my-tip-edit.screen">{Number.isSafeInteger(id) && id > 0 ? <TipEditor tipId={id} /> : <MyUnavailable />}</MyPage>;
}

function TipEditor({ tipId }: { tipId: number }) {
  const query = useMyTip(tipId);
  const [revision, setRevision] = useState(0);

  if (query.isPending) {
    return <MyLoading />;
  }

  if (isTipUnavailable(query.error)) {
    return <MyUnavailable title={getTipErrorMessage(query.error)} />;
  }

  if (!query.data) {
    return <MyUnavailable title={getTipErrorMessage(query.error)} onRetry={() => {
      void query.refetch();
    }} />;
  }

  return <TipForm key={`${tipId}:${revision}`} tip={query.data} reload={async () => {
    const latest = await query.refetch();

    if (latest.isSuccess) {
      setRevision((value) => {
        return value + 1;
      });
    }
  }} />;
}

function TipForm({ tip: initialTip, reload }: { tip: MyTipDetail; reload: () => Promise<void> }) {
  const [tip] = useState(initialTip);
  const [tipType, setTipType] = useState<TipTypeDto | null>(tip.tipType === "UNKNOWN" ? null : tip.tipType);
  const [body, setBody] = useState(tip.body);
  const [photo, setPhoto] = useState<Photo>(tip.photos[0] ? { kind: "remote", url: tip.photos[0].url, imageId: tip.photos[0].imageId } : null);
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);
  const [job, setJob] = useState<{ id?: string; recoveryStored?: boolean } | null>(null);
  const [uncertain, setUncertain] = useState(false);

  const [photoError, setPhotoError] = useState(() => {
    return (tip.photos[0]?.expiresAt.getTime() ?? Infinity) <= Date.now();
  });

  const photoRead = useReadMyTip();
  const file = useRef<{ name: string; file: UploadFile } | null>(null);
  const submission = useRef<UpdateTipInput | null>(null);
  const update = useUpdateTip();
  const upload = useUploadMedia();
  const changedPhoto = photo?.kind === "local" || (photo === null && tip.photos.length > 0);
  const changed = body !== tip.body || (tipType !== null && tipType !== tip.tipType) || changedPhoto;
  const bodyLength = body.length;
  const disabled = working || uncertain;
  const conflicted = isApiError(update.error) && update.error.status === 409 && update.error.code === "C004";

  function choosePhoto() {
    Alert.alert("팁 사진", "사진의 위치 정보를 지우고 첨부해요.", (["camera", "library"] as const).map((source) => {
      return { text: source === "camera" ? "사진 촬영" : "기존 사진 선택",

        onPress: () => {
          setWorking(true); setError(null);

          void addTipPhoto(source).then((value) => {
            if (value) {
              setPhoto({ kind: "local", value }); file.current = null;
            }
          }).catch((cause) => {
            setError(arErrorMessage(cause));
          }).finally(() => {
            setWorking(false);
          });
        } };
    }).concat([{ text: "취소",

      onPress: () => {} }]));
  }

  async function save() {
    if (working || conflicted || !changed || !body.trim() || bodyLength > TIP_BODY_MAX_LENGTH) {
      return;
    }

    setWorking(true); setError(null);

    try {
      if (!submission.current) {
        const request: UpdateTipInput["request"] = { version: tip.version };

        if (body !== tip.body) {
          request.body = normalizeTipBody(body);
        }

        if (tipType !== null && tipType !== tip.tipType) {
          request.category = tipType;
        }

        if (photo?.kind === "local") {
          if (file.current?.name !== photo.value.photoName) {
            file.current = { name: photo.value.photoName, file: await prepareTipPhotoUpload(photo.value) };
          }

          const ready = await upload.mutateAsync(file.current.file);
          request.uploadIds = [ready.uploadId];
        } else if (photo === null && tip.photos.length > 0) {
          request.uploadIds = [];
        }

        submission.current = { tipId: tip.tipId, request, idempotencyKey: createIdempotencyKey() };
      }

      const result = await update.mutateAsync(submission.current);
      setJob({ id: result.jobId, recoveryStored: result.recoveryStored });
    } catch (cause) {
      setError(getTipErrorMessage(cause));
      const unknown = !isApiError(cause) || cause.status === 0 || cause.status >= 500;
      setUncertain(unknown && submission.current !== null);

      if (!unknown) {
        submission.current = null;
      }
    } finally {
      setWorking(false);
    }
  }

  const unavailable = isTipUnavailable(update.error) ? update.error : isTipUnavailable(photoRead.error) ? photoRead.error : null;

  if (unavailable) {
    return <MyUnavailable title={getTipErrorMessage(unavailable)} />;
  }

  if (job) {
    return <TipPublication tipId={tip.tipId} jobId={job.id} recoveryStored={job.recoveryStored} source="my" onClose={() => {
      if (job.id) {
        router.back();
      } else {
        setJob(null);
      }
    }} />;
  }

  return <><ScrollView keyboardShouldPersistTaps="handled" contentContainerClassName="gap-6 px-5 py-4">
    <View className="gap-3"><Text className="text-label">종류 · 필수</Text><View className="flex-row flex-wrap gap-2">{TIP_TYPE_VALUES.map((type) => {
      return <FilterChip key={type} selected={tipType === type} disabled={disabled} onPress={() => {
        setTipType(type);
      }} testID={`my-tip-edit.category-${type}`}>{TIP_TYPE_LABELS[type]}</FilterChip>;
    })}</View></View>
    <View className="gap-3"><Text className="text-label">사진 · 선택</Text><View className="flex-row gap-3">
      {photo && <View className="h-24 w-24"><DesignAsset source={{ uri: photo.kind === "local" ? photo.value.photoUri : photo.url }} className="h-24 w-24 rounded-md" contentFit="cover" onError={() => {
        setPhotoError(true);
      }} accessibilityLabel="팁 사진" /><Pressable disabled={disabled} accessibilityRole="button" accessibilityLabel="사진 빼기" className="absolute right-0 top-0 h-touch w-touch items-center justify-center" testID="my-tip-edit.remove-photo" onPress={() => {
        setPhoto(null);
      }}><Icon as={X} size={20} className="text-foreground" /></Pressable></View>}
      <Pressable disabled={disabled} accessibilityRole="button" accessibilityLabel="사진 촬영 또는 선택" className="h-24 w-24 items-center justify-center gap-2 rounded-md border border-dashed border-border" testID="my-tip-edit.add-photo" onPress={choosePhoto}><Icon as={Camera} size={24} className="text-icon" /><Text className="text-caption">촬영 · 선택</Text></Pressable>
    </View>
    {photoError && photo?.kind === "remote" && <Button variant="outline" disabled={photoRead.isPending} onPress={() => {
      photoRead.mutate(tip.tipId, { onSuccess: (latest) => {
        const renewed = latest.photos.find((item) => {
          return item.imageId === photo.imageId;
        });

        if (renewed) {
          setPhoto((previous) => {
            return previous?.kind === "remote" && previous.imageId === renewed.imageId ? { kind: "remote", imageId: renewed.imageId, url: renewed.url } : previous;
          });

          setPhotoError(false);
        } else {
          setError("사진이 변경됐어요. 최신 내용으로 다시 작성해 주세요.");
        }
      },

      onError: (cause) => {
        setError(getTipErrorMessage(cause));
      } });
    }}><Text>사진 다시 불러오기</Text></Button>}
    </View>
    <View className="gap-3"><Text className="text-label">내용 · 필수</Text><Textarea value={body} editable={!disabled} onChangeText={(value) => {
      setBody(value.replace(/\r\n?/g, "\n"));
    }} accessibilityLabel="팁 내용 (필수, 최대 120자)" testID="my-tip-edit.body" /><Text className={bodyLength > TIP_BODY_MAX_LENGTH ? "text-right text-caption text-destructive" : "text-right text-caption text-muted-foreground"}>{bodyLength} / {TIP_BODY_MAX_LENGTH}</Text></View>
    <Text className="text-caption text-muted-foreground">수정이 완료될 때까지 이전 공개 내용이 유지돼요. 사진을 첨부하지 않아도 저장할 수 있어요.</Text>
    <Button variant="outline" disabled={working} onPress={() => {
      setJob({});
    }}><Text>게시·수정 상태 확인</Text></Button>
    <Button variant="outline" disabled={disabled || changed} testID="my-tip-edit.reposition" onPress={() => {
      router.push({ pathname: "/ar-tip", params: { repositionTipId: tip.tipId } });
    }}><Text>현장에서 AR 위치 다시 놓기</Text></Button>
    {error && <Text accessibilityRole="alert" className="text-body-sm text-destructive">{error}</Text>}
    {conflicted && <Button variant="outline" onPress={() => {
      void reload();
    }}><Text>최신 내용으로 다시 작성</Text></Button>}
    {uncertain && <Text className="text-body-sm text-muted-foreground">수정 결과를 확인하지 못했어요. 다시 시도하면 같은 수정 요청의 결과를 확인해요.</Text>}
  </ScrollView><BottomCta><Button size="lg" className="w-full" loading={working} disabled={working || conflicted || !changed || !body.trim() || bodyLength > TIP_BODY_MAX_LENGTH} testID="my-tip-edit.save" onPress={() => {
    void save();
  }}><Text>{uncertain ? "수정 결과 다시 확인" : "수정 저장"}</Text></Button></BottomCta></>;
}
