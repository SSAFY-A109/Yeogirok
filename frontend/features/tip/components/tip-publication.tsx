import { useEffect } from "react";
import { View } from "react-native";
import { useQueryClient } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import { PageLoading } from "@/components/ui/page-loading";
import { Text } from "@/components/ui/text";
import { tipKeys, useTipStatus } from "../api/queries";
import { getTipErrorMessage } from "../errors";
import { isTipOperationPending } from "../model";

const STEP_LABELS = { media: "사진", spatial: "AR 공간", content: "내용" };
const STEP_STATES: Readonly<Record<string, string>> = { SKIPPED: "건너뜀", QUEUED: "대기 중", RUNNING: "처리 중", SUCCEEDED: "완료", FAILED: "실패" };

export function TipPublication({ tipId, jobId, onClose }: { tipId: number; jobId?: string; onClose: () => void }) {
  const client = useQueryClient();
  const query = useTipStatus(tipId, jobId);
  const status = !query.isError && (jobId === undefined || query.data?.jobId === jobId) ? query.data : undefined;
  const complete = status?.status === "PUBLISHED" && status.operationStatus === "SUCCEEDED";
  const failed = status?.operationStatus === "FAILED" || status?.status === "FAILED";
  const pending = isTipOperationPending(status);
  const action = status?.operation === "UPDATE" || jobId ? "수정" : "등록";

  useEffect(() => {
    if (complete) {
      void client.invalidateQueries({ queryKey: tipKeys.detail(tipId) });
      void client.invalidateQueries({ queryKey: tipKeys.lists });
    }
  }, [client, complete, tipId]);

  return (
    <View className="flex-1 justify-center gap-6 px-5 py-6" testID="my-tip-edit.publication">
      {query.isPending ? <PageLoading type="spinner" title="팁 상태를 확인하고 있어요" /> : (
        <>
          <Text className="text-h2">{complete ? `팁 ${action}을 완료했어요` : failed ? `팁 ${action}을 완료하지 못했어요` : pending ? `팁을 ${action}하고 있어요` : "팁 작업 상태를 확인해 주세요"}</Text>
          {status && (Object.keys(STEP_LABELS) as (keyof typeof STEP_LABELS)[]).map((step) => {
            return <Text key={step} className="text-body-sm">{STEP_LABELS[step]} · {STEP_STATES[status.steps[step]] ?? "확인 필요"}</Text>;
          })}
          {query.isError && <Text accessibilityRole="alert" className="text-body-sm text-destructive">{getTipErrorMessage(query.error)}</Text>}
          {failed && status?.status === "PUBLISHED" && <Text className="text-caption text-muted-foreground">이전 공개 내용이 유지돼요.</Text>}
          {!complete && <Button variant="outline" loading={query.isFetching} onPress={() => {
            void query.refetch();
          }}><Text>상태 다시 확인</Text></Button>}
        </>
      )}
      <Button variant={complete ? "default" : "outline"} onPress={onClose} testID="my-tip-edit.publication-close"><Text>{complete ? "확인" : "나중에"}</Text></Button>
    </View>
  );
}
