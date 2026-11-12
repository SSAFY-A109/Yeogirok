import { Bus, Calendar, Car, type LucideIcon } from "lucide-react-native";
import { useState, type ReactNode } from "react";
import { Pressable, View } from "react-native";

import { Badge } from "@/components/ui/badge";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { TextField } from "@/components/ui/text-field";
import { TripDateSheet, type TripDateRange } from "@/features/room/components/trip-date-sheet";
import { hasTripDates, TRANSPORT_MODE_LABEL, TRANSPORT_MODES, type RoomSummary, type TransportMode, type TransportModeDto } from "@/features/room/model";
import { formatDateRange, formatTripLength } from "@/lib/date";
import { cn } from "@/lib/utils";

/** 날짜를 정하지 않았을 때 날짜 필드에 보이는 문구. */
const EMPTY_TRIP_DATE_TEXT = "날짜 미정";

const TRANSPORT_ICON: Readonly<Record<TransportModeDto, LucideIcon>> = { CAR: Car, TRANSIT: Bus };
const TRANSPORT_TEST_ID: Readonly<Record<TransportModeDto, string>> = { CAR: "transport-car", TRANSIT: "transport-public-transit" };

/** 여행방의 현재 기간. 날짜가 없으면 `null`. */
export function toTripDateRange(room: Pick<RoomSummary, "startDate" | "endDate">): TripDateRange | null {
  return hasTripDates(room) ? { startDate: room.startDate, endDate: room.endDate } : null;
}

/** 여행방 이름은 앞뒤 공백을 빼고 한 글자 이상이어야 한다. 그 밖의 필수 항목·글자 수 제한은 정해지지 않았다(TBD). */
export function isValidRoomName(name: string): boolean {
  return name.trim().length > 0;
}

type FormFieldProps = {
  label: string;
  help: string;
  error?: string;
  children: ReactNode;
};

/** 라벨·입력·도움말로 이루어진 폼 한 칸(Figma `Field / *`). 오류는 입력 바로 아래에 둔다. */
function FormField({ label, help, error, children }: FormFieldProps) {
  return (
    <View className="gap-2">
      <Text className="text-label text-foreground">{label}</Text>
      {children}
      {error ? (
        <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="text-caption text-destructive">{error}</Text>
      ) : null}
      <Text className="text-caption text-muted-foreground">{help}</Text>
    </View>
  );
}

type RoomNameFieldProps = {
  value: string;
  onChangeText: (value: string) => void;
  testID: string;
};

export function RoomNameField({ value, onChangeText, testID }: RoomNameFieldProps) {
  return (
    <FormField label="여행방 이름" help="일행에게 보이는 이름이에요">
      <TextField
        accessibilityLabel="여행방 이름"
        returnKeyType="done"
        value={value}
        onChangeText={onChangeText}
        testID={`${testID}.name-input`}
      />
    </FormField>
  );
}

type TripDateFieldProps = {
  value: TripDateRange | null;
  onChange: (value: TripDateRange | null) => void;
  help: string;
  error?: string;
  testID: string;
};

/** 누르면 캘린더 시트(W06-C)를 여는 날짜 필드. */
export function TripDateField({ value, onChange, help, error, testID }: TripDateFieldProps) {
  const [sheetOpen, setSheetOpen] = useState(false);
  const [sheetSession, setSheetSession] = useState(0);
  const rangeText = value === null ? null : formatDateRange(value.startDate, value.endDate, { repeatMonth: true });
  const lengthText = value === null ? null : formatTripLength(value.startDate, value.endDate);

  return (
    <FormField label="여행 날짜" help={help} error={error}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="여행 날짜"
        accessibilityValue={{ text: rangeText === null ? EMPTY_TRIP_DATE_TEXT : `${rangeText} ${lengthText}` }}
        accessibilityHint={error}
        className={cn(
          "min-h-input flex-row items-center gap-3 rounded-md border border-border bg-card px-4 py-3 active:bg-muted",
          error && "border-destructive",
        )}
        onPress={() => {
          setSheetSession(sheetSession + 1);
          setSheetOpen(true);
        }}
        testID={`${testID}.date-field`}
      >
        <Icon as={Calendar} size={20} className="text-icon" />
        <Text className={cn("flex-1 text-caption", rangeText === null ? "text-muted-foreground" : "text-foreground")}>
          {rangeText ?? EMPTY_TRIP_DATE_TEXT}
        </Text>
        {lengthText === null ? null : (
          <Badge className="self-center">
            <Text className="text-primary">{lengthText}</Text>
          </Badge>
        )}
      </Pressable>
      <TripDateSheet key={sheetSession} open={sheetOpen} onOpenChange={setSheetOpen} value={value} onConfirm={onChange} />
    </FormField>
  );
}

type TransportModeFieldProps = {
  /** 서버가 모르는 값(`UNKNOWN`)이면 아무것도 선택하지 않은 상태로 보인다. */
  value: TransportMode;
  onChange: (value: TransportModeDto) => void;
  help: string;
  testID: string;
};

export function TransportModeField({ value, onChange, help, testID }: TransportModeFieldProps) {
  return (
    <FormField label="이동 수단" help={help}>
      <View accessibilityRole="radiogroup" accessibilityLabel="이동 수단" className="flex-row gap-3">
        {TRANSPORT_MODES.map((mode) => {
          const selected = value === mode;

          return (
            <Pressable
              key={mode}
              accessibilityRole="radio"
              accessibilityLabel={TRANSPORT_MODE_LABEL[mode]}
              accessibilityState={{ checked: selected }}
              className={cn(
                "flex-1 flex-row items-center justify-center gap-2 rounded-md p-4",
                selected ? "border-2 border-ring bg-accent" : "border border-border bg-card active:bg-muted",
              )}
              onPress={() => {
                onChange(mode);
              }}
              testID={`${testID}.${TRANSPORT_TEST_ID[mode]}`}
            >
              <Icon as={TRANSPORT_ICON[mode]} size={20} className={selected ? "text-icon-active" : "text-icon"} />
              <Text className={cn("text-label", selected ? "text-primary" : "text-foreground")}>{TRANSPORT_MODE_LABEL[mode]}</Text>
            </Pressable>
          );
        })}
      </View>
    </FormField>
  );
}
