import { useEffect, useRef } from "react";
import { View } from "react-native";
import { useQueryClient } from "@tanstack/react-query";
import { router } from "expo-router";

import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { PageLoading } from "@/components/ui/page-loading";
import { createIdempotencyKey } from "@/api/idempotency";
import { tipKeys, useRetryTip, useTipPublicationRefresh, useTipStatus, TIP_STATUS_MAX_WAIT_MS, TIP_STATUS_POLL_MS } from "../api/queries";
import { getTipErrorMessage } from "../errors";
import { isTipOperationPending } from "../model";

export function TipPublication({ tipId, jobId, onClose, onComplete, recoveryStored = true, source = "ar" }: { tipId: number; jobId?: string; onClose: () => void; onComplete?: () => void; recoveryStored?: boolean; source?: "ar" | "my" }) {
  const retry = useRetryTip();
  const expectedJobId = retry.data?.completed === false ? retry.data.jobId : jobId;
  const query = useTipStatus(tipId, expectedJobId);
  const refresh = useTipPublicationRefresh();
  const retryKey = useRef(createIdempotencyKey());
  const completionHandled = useRef(false);
  const awaitingJob = expectedJobId !== undefined && query.data?.jobId !== expectedJobId && query.data?.status !== "DELETED";
  const status = awaitingJob ? undefined : query.data;
  const complete = status?.operationStatus === "SUCCEEDED" && status.status === "PUBLISHED" || retry.data?.completed === true;
  const pending = awaitingJob || isTipOperationPending(status);
  const failed = status?.operationStatus === "FAILED" || status?.status === "FAILED";
  const action = status?.operation === "UPDATE" || jobId !== undefined ? "수정" : "등록";
  const state = useQueryClient().getQueryState(tipKeys.status(tipId, expectedJobId));
  const timedOut = (state?.dataUpdateCount ?? 0) + (state?.errorUpdateCount ?? 0) >= TIP_STATUS_MAX_WAIT_MS / TIP_STATUS_POLL_MS;

  useEffect(() => {
    if (status) {
      refresh(status);
    }
  }, [status, refresh]);

  useEffect(() => {
    retryKey.current = createIdempotencyKey();
  }, [status?.jobId]);

  useEffect(() => {
    if (complete && onComplete && !completionHandled.current) {
      completionHandled.current = true;
      onComplete();
    }
  }, [complete, onComplete]);

  return <View className="flex-1 justify-center gap-6 px-5 py-6" testID={source === "my" ? "my-tip-edit.publication" : "ar-tip.publication"}>
    {!recoveryStored && <Text className="text-body-sm text-muted-foreground">등록 상태는 내 팁 화면에서도 다시 확인할 수 있어요.</Text>}
    {query.isPending ? <PageLoading type="spinner" title="팁 상태를 확인하고 있어요" /> : <>
      <Text className="text-h2">{complete ? `팁 ${action}을 완료했어요` : failed ? `팁 ${action}을 완료하지 못했어요` : pending ? `팁을 ${action}하고 있어요` : "팁 상태를 확인해 주세요"}</Text>
      {status && <View className="gap-3">{(["media", "spatial", "content"] as const).map((step) => {
        const value = status.steps[step];
        return <Text key={step} className="text-body-sm">{{ media: "사진", spatial: "AR 공간", content: "내용" }[step]} · {value === "SKIPPED" ? "미첨부 (정상)" : value === "SUCCEEDED" ? "완료" : value === "FAILED" ? "실패" : value === "RUNNING" ? "처리 중" : value === "QUEUED" ? "대기 중" : "확인 필요"}</Text>;
      })}</View>}
      {(query.isError || retry.isError) && <Text accessibilityRole="alert" className="text-body-sm text-destructive">{getTipErrorMessage(retry.error ?? query.error)}</Text>}
      {failed && status?.status === "PUBLISHED" && <Text className="text-caption text-muted-foreground">이전 공개 내용이 유지돼요.</Text>}
      {pending && timedOut && <Text className="text-body-sm text-muted-foreground">아직 처리 중이에요. 나중에 다시 확인할 수 있어요.</Text>}
      {status?.retryable && <Button loading={retry.isPending} disabled={retry.isPending} testID="ar-tip.retry-publication" onPress={() => {
        retry.mutate({ tipId, request: {}, idempotencyKey: retryKey.current });
      }}><Text>실패 단계 다시 시도</Text></Button>}
      {source === "ar" && failed && <Button variant="outline" disabled={retry.isPending} onPress={() => {
        router.push({ pathname: "/my/tips/[tipId]", params: { tipId } });
      }}><Text>본문·사진·AR 위치 확인</Text></Button>}
      {(query.isError || timedOut || !pending && !complete) && <Button variant="outline" onPress={() => {
        void query.refetch();
      }}><Text>상태 다시 확인</Text></Button>}
    </>}
    <Button variant={complete ? "default" : "outline"} disabled={retry.isPending} onPress={onClose} testID={source === "my" ? "my-tip-edit.publication-close" : "ar-tip.publication-later"}><Text>{complete ? "확인" : "나중에"}</Text></Button>
  </View>;
}
