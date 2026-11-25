import { Minus, Plus, type LucideIcon } from "lucide-react-native";
import { useState } from "react";
import { Pressable, View } from "react-native";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { FilterChip } from "@/components/ui/filter-chip";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { clampStayMinutes, formatDuration, STAY_MAX_MINUTES, STAY_MIN_MINUTES, STAY_QUICK_OPTIONS, STAY_STEP_MINUTES } from "@/features/schedule/model";
import { cn } from "@/lib/utils";

export type StayTarget = {
  /** 초안 항목 번호(itemId). */
  itemId: number;
  name: string;
  stayMinutes: number;
  /** 지금 자리. `지금 13:35 – 15:05`. 시각을 모르면 `null`. */
  detail: string | null;
};

type StayTimeSheetProps = {
  /** 머무는 시간을 바꿀 장소. 닫혀 있으면 `null`. */
  target: StayTarget | null;
  onApply: (target: StayTarget, stayMinutes: number) => void;
  onClose: () => void;
};

/** 머무는 시간 바꾸기(Figma W14-T). 10분씩 늘리고 줄인다. 적용해도 서버에 보내지 않고 화면의 배치만 바꾼다. */
export function StayTimeSheet({ target, onApply, onClose }: StayTimeSheetProps) {
  return (
    <BottomSheet visible={target !== null} onClose={onClose} closeLabel="머무는 시간 닫기">
      {target === null ? null : <StayTimeContent target={target} onApply={onApply} />}
    </BottomSheet>
  );
}

function StayTimeContent({ target, onApply }: { target: StayTarget; onApply: StayTimeSheetProps["onApply"] }) {
  const [minutes, setMinutes] = useState(target.stayMinutes);

  const change = (next: number) => {
    setMinutes(clampStayMinutes(next));
  };

  return (
    <View className="gap-5 px-5 pb-4 pt-2" testID="schedule-draft.stay-sheet">
      <View className="gap-1">
        <Text accessibilityRole="header" className="text-h3 text-foreground">{`${target.name}에 얼마나 머물까요?`}</Text>
        {target.detail === null ? null : <Text className="text-caption text-foreground-secondary tabular-nums">{target.detail}</Text>}
      </View>
      <View className="flex-row items-center justify-between px-6">
        <StepButton
          icon={Minus}
          label={`${STAY_STEP_MINUTES}분 줄이기`}
          disabled={minutes - STAY_STEP_MINUTES < STAY_MIN_MINUTES}

          onPress={() => {
            change(minutes - STAY_STEP_MINUTES);
          }}

          testID="schedule-draft.stay-sheet.decrease"
        />
        <View accessible accessibilityLabel={`머무는 시간 ${formatDuration(minutes)}`} accessibilityLiveRegion="polite" className="items-center gap-1">
          <Text className="text-h1 text-foreground tabular-nums" testID="schedule-draft.stay-sheet.value">{`${minutes}분`}</Text>
          <Text className="text-caption text-muted-foreground">{`${STAY_STEP_MINUTES}분씩 조절`}</Text>
        </View>
        <StepButton
          icon={Plus}
          label={`${STAY_STEP_MINUTES}분 늘리기`}
          disabled={minutes + STAY_STEP_MINUTES > STAY_MAX_MINUTES}

          onPress={() => {
            change(minutes + STAY_STEP_MINUTES);
          }}

          testID="schedule-draft.stay-sheet.increase"
        />
      </View>
      <View className="flex-row gap-2">
        {STAY_QUICK_OPTIONS.map((option) => {
          return (
            <FilterChip
              key={option}
              size="md"
              className="flex-1 px-1"
              selected={option === minutes}

              onPress={() => {
                change(option);
              }}

              testID={`schedule-draft.stay-sheet.quick-${option}`}
            >
              {formatDuration(option)}
            </FilterChip>
          );
        })}
      </View>
      <Button
        size="lg"
        className="w-full"
        disabled={minutes === target.stayMinutes}

        onPress={() => {
          onApply(target, minutes);
        }}

        testID="schedule-draft.stay-sheet.apply"
      >
        <Text>적용</Text>
      </Button>
    </View>
  );
}

type StepButtonProps = { icon: LucideIcon; label: string; disabled: boolean; onPress: () => void; testID: string };

function StepButton({ icon, label, disabled, onPress, testID }: StepButtonProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled }}
      disabled={disabled}
      className={cn("h-button-lg w-button-lg items-center justify-center rounded-full border", disabled ? "border-border bg-disabled" : "border-border-strong bg-card active:bg-muted")}
      onPress={onPress}
      testID={testID}
    >
      <Icon as={icon} size={20} className={disabled ? "text-disabled-foreground" : "text-foreground"} />
    </Pressable>
  );
}
