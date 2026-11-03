import type { ReactNode } from "react";
import { Modal, Pressable, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

type BottomSheetProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  children: ReactNode;
  /** 보조 기술이 읽는 시트 이름. */
  accessibilityLabel: string;
  testID?: string;
};

/**
 * 아래에서 올라오는 시트(Figma `BottomSheet`). 바깥(어두운 영역)을 누르거나 뒤로가기를 하면 닫힌다.
 * 내용 여백은 쓰는 쪽에서 정한다(Figma 기준 `px-5 pt-2 pb-4`).
 */
export function BottomSheet({ open, onOpenChange, children, accessibilityLabel, testID }: BottomSheetProps) {
  const insets = useSafeAreaInsets();

  const close = () => {
    onOpenChange(false);
  };

  return (
    <Modal visible={open} transparent animationType="fade" statusBarTranslucent onRequestClose={close}>
      <View className="flex-1 justify-end">
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="시트 닫기"
          className="absolute inset-0 bg-black/50"
          onPress={close}
          testID={testID ? `${testID}.scrim` : undefined}
        />
        <View
          accessibilityViewIsModal
          accessibilityLabel={accessibilityLabel}
          className="rounded-t-xl bg-popover"
          style={{ paddingBottom: insets.bottom }}
          testID={testID}
        >
          <View className="items-center py-2">
            <View className="h-1 w-9 rounded-full bg-border-strong" />
          </View>
          {children}
        </View>
      </View>
    </Modal>
  );
}
