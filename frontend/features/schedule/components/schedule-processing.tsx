import { ScrollView, View } from "react-native";

import { Button } from "@/components/ui/button";
import { PageLoading } from "@/components/ui/page-loading";
import { Text } from "@/components/ui/text";
import { SCHEDULE_DRAFT_MAX_WAIT_MS } from "@/features/schedule/api/queries";
import { useElapsedSeconds } from "@/hooks/use-elapsed-seconds";

const MS_PER_SECOND = 1_000;

type ScheduleProcessingProps = {
  /** 담은 장소 수. 여행방을 아직 못 받았으면 `null`. */
  placeCount: number | null;
  /** 진행 확인 요청 중인지(다시 확인 버튼). */
  checking: boolean;
  /** 진행 확인 요청이 실패했는지. 초안 실패가 아니므로 안내만 한다. */
  connectionTrouble: boolean;
  onCheckAgain: () => void;
};

/**
 * 일정 초안 처리 중(JobProgress, COM-05). 단계가 없는 작업이라 "만들고 있어요"와 지난 시간을 보여 준다.
 * 최대 대기를 넘기면 "아직 만들고 있어요"로 바꾸고 직접 다시 확인하게 한다. 화면을 나가도 작업은 서버에서 계속된다.
 */
export function ScheduleProcessing({ placeCount, checking, connectionTrouble, onCheckAgain }: ScheduleProcessingProps) {
  const elapsedSeconds = useElapsedSeconds(true);
  const waitedTooLong = elapsedSeconds * MS_PER_SECOND >= SCHEDULE_DRAFT_MAX_WAIT_MS;

  return (
    <ScrollView className="flex-1" contentContainerClassName="items-center gap-5 px-5 pb-6 pt-10" testID="schedule-draft.processing">
      {waitedTooLong ? (
        <View
          accessibilityLiveRegion="polite"
          className="min-h-56 w-full items-center justify-center gap-4 rounded-sm border border-border bg-card p-6"
          testID="schedule-draft.waiting-long"
        >
          <Text accessibilityRole="header" className="text-center text-h3 text-foreground">아직 만들고 있어요</Text>
          <Text className="text-center text-body-sm text-foreground-secondary">평소보다 오래 걸리고 있어요. 화면을 나가도 일정 초안은 계속 만들어져요.</Text>
          <Button variant="outline" loading={checking} loadingLabel="확인하는 중" onPress={onCheckAgain} testID="schedule-draft.check-again">
            <Text>다시 확인</Text>
          </Button>
        </View>
      ) : (
        <PageLoading
          type="spinner"
          title="일정 초안을 만들고 있어요"
          description={placeCount === null ? "담은 장소의 순서와 이동 시간을 계산하고 있어요" : `담은 ${placeCount}곳의 순서와 이동 시간을 계산하고 있어요`}
        />
      )}
      <Text className="text-caption text-muted-foreground tabular-nums">{`${elapsedSeconds}초 지났어요`}</Text>
      {connectionTrouble ? (
        <Text accessibilityLiveRegion="polite" className="text-center text-caption text-foreground-secondary" testID="schedule-draft.connection">
          연결이 불안정해 진행 확인이 늦어지고 있어요. 다시 연결되면 이어서 확인해요.
        </Text>
      ) : null}
    </ScrollView>
  );
}
