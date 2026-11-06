import { router, useFocusEffect, useLocalSearchParams } from "expo-router";
import { useCallback, useRef, useState } from "react";
import { Alert, KeyboardAvoidingView, Linking, Platform, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Button } from "@/components/ui/button";
import { PageLoading } from "@/components/ui/page-loading";
import { Text } from "@/components/ui/text";
import { isApiError } from "@/api/errors";
import { createIdempotencyKey } from "@/api/idempotency";
import { useUploadMedia } from "@/features/media/api/queries";
import type { UploadFile } from "@/features/media/upload";
import { useCreateTip, useDiscoverTips } from "../api/queries";
import type { CreateTipInput } from "../api/tip.api";
import { getTipErrorMessage } from "../errors";
import { normalizeTipBody, type TipTypeDto } from "../model";
import { addTipPhoto, arErrorMessage, placeTip } from "../native/ar";
import type { CloudPlacement, TipPhoto } from "../native/ar/contracts";
import { measureTipLocation, openPublicTipExplorer, prepareTipPhotoUpload, toTipSpatialRequest } from "../native/ar/public";
import { TipComposer } from "./tip-composer";
import { TipPublication } from "./tip-publication";
import { TipReposition } from "./tip-reposition";

export function ArTipScreen() {
  const { repositionTipId } = useLocalSearchParams<{ repositionTipId?: string }>();
  const id = Number(repositionTipId);
  return repositionTipId !== undefined && Number.isSafeInteger(id) && id > 0 ? <TipReposition tipId={id} /> : <PublicArTipScreen />;
}

function PublicArTipScreen() {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>("주변 팁을 확인하고 있어요");
  const [placement, setPlacement] = useState<CloudPlacement | null>(null);
  const [body, setBody] = useState("");
  const [tipType, setTipType] = useState<TipTypeDto | null>(null);
  const [photo, setPhoto] = useState<TipPhoto | null>(null);
  const [failed, setFailed] = useState(false);
  const [pendingId, setPendingId] = useState<number | null>(null);
  const [uncertain, setUncertain] = useState(false);
  const [recoveryStored, setRecoveryStored] = useState(true);
  const locked = useRef(false);
  const focused = useRef(false);
  const draftPlacement = useRef<CloudPlacement | null>(null);
  const submission = useRef<{ signature: string; input: CreateTipInput } | null>(null);
  const uploadFile = useRef<{ name: string; file: UploadFile } | null>(null);
  const { mutateAsync: discover } = useDiscoverTips();
  const create = useCreateTip();
  const upload = useUploadMedia();

  const acceptPlacement = useCallback((next: CloudPlacement | null) => {
    if (!next || !focused.current) {
      return;
    }

    draftPlacement.current = next;
    submission.current = null;
    setPlacement(next);
    setFailed(false);
    setUncertain(false);
    router.setParams({ arMode: "compose" });
  }, []);

  const run = useCallback(async (label: string, action: () => Promise<void>) => {
    if (locked.current) {
      return;
    }

    locked.current = true;
    setBusy(label);
    setError(null);

    try {
      await action();
    } catch (cause) {
      if (focused.current) {
        setError(isApiError(cause) ? getTipErrorMessage(cause) : arErrorMessage(cause));
      }
    } finally {
      locked.current = false;

      if (focused.current) {
        setBusy(null);
      }
    }
  }, []);

  const explore = useCallback(async () => {
    const location = await measureTipLocation();
    const page = await discover(location);

    if (!focused.current) {
      return;
    }

    router.setParams({ arMode: "explore" });
    acceptPlacement(await openPublicTipExplorer(page, location, discover));
  }, [discover, acceptPlacement]);

  useFocusEffect(useCallback(() => {
    focused.current = true;
    setPendingId(null);

    if (!draftPlacement.current) {
      void run("주변 팁을 확인하고 있어요", explore);
    }

    return () => {
      focused.current = false;
    };
  }, [run, explore]));

  const returnToCamera = useCallback(() => {
    if (!focused.current || locked.current) {
      return;
    }

    setPendingId(null);
    void run("주변 팁을 확인하고 있어요", explore);
  }, [run, explore]);

  function closeComposer() {
    if (locked.current) {
      return;
    }

    draftPlacement.current = null;
    setPlacement(null);
    setFailed(false);
    setUncertain(false);
    submission.current = null;
    router.setParams({ arMode: "explore" });
    returnToCamera();
  }

  function selectPhoto() {
    Alert.alert("팁 사진", "첨부할 사진의 위치 정보를 지워요.", [
      { text: "사진 촬영",

        onPress: () => {
          select("camera");
        } },
      { text: "기존 사진 선택",

        onPress: () => {
          select("library");
        } },
      { text: "취소", style: "cancel" },
    ]);

    function select(source: "camera" | "library") {
      void run("사진을 준비하고 있어요", async () => {
        const next = await addTipPhoto(source);

        if (next && focused.current) {
          setPhoto(next);
          uploadFile.current = null;
        }
      });
    }
  }

  function save() {
    if (!placement || !tipType) {
      return;
    }

    void run(photo ? "사진 확인 후 팁을 등록하고 있어요" : "팁을 등록하고 있어요", async () => {
      try {
        const normalized = normalizeTipBody(body);
        const signature = JSON.stringify([normalized, tipType, placement.anchorId, photo?.photoName]);

        if (!submission.current || submission.current.signature !== signature) {
          let uploadIds: string[] = [];

          if (photo) {
            if (uploadFile.current?.name !== photo.photoName) {
              uploadFile.current = { name: photo.photoName, file: await prepareTipPhotoUpload(photo) };
            }

            const ready = await upload.mutateAsync(uploadFile.current.file);
            uploadIds = [ready.uploadId];
          }

          const location = await measureTipLocation();
          submission.current = { signature, input: { idempotencyKey: createIdempotencyKey(), request: {
            body: normalized, category: tipType, uploadIds, location, ...toTipSpatialRequest(placement),
          } } };
        }

        const result = await create.mutateAsync(submission.current.input);
        setRecoveryStored(result.recoveryStored);
        setPendingId(result.tipId);
        draftPlacement.current = null;
        setPlacement(null);
        setBody("");
        setPhoto(null);
        setTipType(null);
        setFailed(false);
        setUncertain(false);
        submission.current = null;
      } catch (cause) {
        if (focused.current) {
          setFailed(false);
          const unknownResult = !isApiError(cause) || cause.status === 0 || cause.status >= 500;
          setUncertain(unknownResult && submission.current !== null);

          if (isApiError(cause) && cause.status >= 400 && cause.status < 500) {
            submission.current = null;
          }
        }

        throw cause;
      }
    });
  }

  return <SafeAreaView edges={placement ? ["top", "bottom"] : ["top"]} className="flex-1 bg-background" testID="ar-tip.screen">
    <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : "height"} className="flex-1">
      {pendingId !== null ? <TipPublication tipId={pendingId} recoveryStored={recoveryStored} onClose={returnToCamera} onComplete={returnToCamera} /> : placement ? <TipComposer placement={placement} body={body} tipType={tipType} photo={photo} busy={busy} error={error} failed={failed} editingLocked={uncertain}
        onBody={(value) => {
          setBody(value.replace(/\r\n?/g, "\n"));
        }} onType={setTipType} onPhoto={selectPhoto}
        onRemovePhoto={() => {
          setPhoto(null); uploadFile.current = null;
        }} onReplace={() => {
          void run("현장에서 위치를 다시 놓아 주세요", async () => {
            acceptPlacement(await placeTip(false));
          });
        }} onClose={closeComposer} onSave={save} /> : <View className="flex-1 justify-center gap-6 px-5 py-6">
        {busy ? <PageLoading type="spinner" title="AR 팁을 준비하고 있어요" description={busy} /> : <>
          <Text className="text-h2">{error ? "AR 카메라를 열지 못했어요" : "AR 카메라가 닫혔어요"}</Text>
          {error && <Text accessibilityRole="alert" className="text-body-sm text-destructive">{error}</Text>}
          <Button size="lg" testID="ar-tip.explore" onPress={() => {
            void run("주변 팁을 확인하고 있어요", explore);
          }}><Text>{error ? "다시 시도" : "AR 카메라 다시 열기"}</Text></Button>
          {error && Platform.OS !== "web" && <Button variant="outline" onPress={() => {
            void Linking.openSettings();
          }}><Text>앱 권한 설정</Text></Button>}
        </>}
      </View>}
    </KeyboardAvoidingView>
  </SafeAreaView>;
}
