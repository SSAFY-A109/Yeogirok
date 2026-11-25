import { useEffect, useRef, useState } from "react";
import { View } from "react-native";
import { router } from "expo-router";
import { SafeAreaView } from "react-native-safe-area-context";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { PageLoading } from "@/components/ui/page-loading";
import { createIdempotencyKey } from "@/api/idempotency";
import { isApiError } from "@/api/errors";
import { useReadMyTip, useUpdateTip } from "../api/queries";
import type { UpdateTipInput } from "../api/tip.api";
import { getTipErrorMessage } from "../errors";
import { arErrorMessage, placeTip } from "../native/ar";
import type { CloudPlacement } from "../native/ar/contracts";
import { measureTipLocation, toTipSpatialRequest } from "../native/ar/public";
import { TipPublication } from "./tip-publication";

export function TipReposition({ tipId }: { tipId: number }) {
  const { mutateAsync: read } = useReadMyTip();
  const update = useUpdateTip();
  const [placement, setPlacement] = useState<CloudPlacement | null>(null);
  const [version, setVersion] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [accepted, setAccepted] = useState(false);
  const input = useRef<UpdateTipInput | null>(null);
  const started = useRef(false);

  async function place() {
    setBusy(true); setError(null);

    try {
      const tip = await read(tipId);
      const next = await placeTip(false);

      if (next) {
        setPlacement(next); setVersion(tip.version); input.current = null;
      }
    } catch (cause) {
      setError(isApiError(cause) ? getTipErrorMessage(cause) : arErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    if (!started.current) {
      started.current = true; void place();
    }
  });

  if (accepted) {
    return <SafeAreaView className="flex-1 bg-background"><TipPublication tipId={tipId} onClose={() => {
      router.replace({ pathname: "/my/tips/[tipId]", params: { tipId } });
    }} /></SafeAreaView>;
  }

  return <SafeAreaView className="flex-1 bg-background"><View className="flex-1 justify-center gap-6 px-5 py-6">
    <Text className="text-h2">AR 위치 다시 놓기</Text>
    <Text className="text-body-sm text-foreground-secondary">현재 장소에서 5m 이내의 표면을 선택해요. 본문과 사진은 유지돼요.</Text>
    {busy && <PageLoading type="spinner" title="AR 위치를 준비하고 있어요" />}
    {error && <Text accessibilityRole="alert" className="text-body-sm text-destructive">{error}</Text>}
    {placement && <Button disabled={busy || update.isPending} loading={update.isPending} onPress={() => {
      if (version === null) {
        return;
      }

      setBusy(true);

      void (async () => {
        try {
          if (!input.current) {
            input.current = { tipId, idempotencyKey: createIdempotencyKey(), request: { version, location: await measureTipLocation(), ...toTipSpatialRequest(placement) } };
          }

          await update.mutateAsync(input.current);
          setAccepted(true);
        } catch (cause) {
          setError(isApiError(cause) ? getTipErrorMessage(cause) : arErrorMessage(cause));

          if (isApiError(cause) && cause.status >= 400 && cause.status < 500) {
            input.current = null;
          }
        } finally {
          setBusy(false);
        }
      })();
    }}><Text>새 AR 위치 등록</Text></Button>}
    <Button variant="outline" disabled={busy || update.isPending} onPress={() => {
      void place();
    }}><Text>{placement ? "위치 다시 선택" : "현장에서 위치 선택"}</Text></Button>
    <Button variant="outline" disabled={busy || update.isPending} onPress={() => {
      router.replace({ pathname: "/my/tips/[tipId]", params: { tipId } });
    }}><Text>내 팁으로 돌아가기</Text></Button>
  </View></SafeAreaView>;
}
