import { DateTimePicker } from "@expo/ui/community/datetime-picker";
import { useState } from "react";
import { Platform, View } from "react-native";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { toLocalDate } from "@/lib/date";
import { getColors } from "@/lib/theme";

type ReceiptDateTimePickerProps = {
  value: Date;
  onSelect: (value: Date) => void;
  onClose: () => void;
};

export function ReceiptDateTimePicker({ value, onSelect, onClose }: ReceiptDateTimePickerProps) {
  const [draft, setDraft] = useState(value);
  const accentColor = getColors("light").primary;

  if (Platform.OS === "android") {
    return <DateTimePicker mode="date" value={value} presentation="dialog" accentColor={accentColor}
      positiveButton={{ label: "선택 완료" }} negativeButton={{ label: "취소" }}
      onValueChange={(_, next) => {
        onSelect(next);
        onClose();
      }} onDismiss={onClose} />;
  }

  return <BottomSheet visible onClose={onClose} closeLabel="결제 날짜 선택 닫기" expandable={false}>
    <View className="gap-4 px-5 pb-4 pt-2">
      <Text accessibilityRole="header" className="text-h3">결제 날짜</Text>
      {Platform.OS === "web" ? (
        <input type="date" aria-label="결제 날짜" value={toLocalDate(draft)}
          className="h-input w-full rounded-sm border border-input bg-card px-4 font-sans text-body-sm text-foreground"
          onChange={(event) => {
            const next = new Date(`${event.currentTarget.value}T12:00:00`);

            if (!Number.isNaN(next.getTime())) {
              setDraft(next);
            }
          }} />
      ) : (
        <DateTimePicker mode="date" value={draft} display="inline" locale="ko_KR"
          themeVariant="light" accentColor={accentColor} onValueChange={(_, next) => {
            setDraft(next);
          }} />
      )}
      <Button size="lg" onPress={() => {
        onSelect(draft);
        onClose();
      }}><Text>선택 완료</Text></Button>
    </View>
  </BottomSheet>;
}
