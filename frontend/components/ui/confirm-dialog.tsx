import type { ReactNode } from "react";
import { Modal, View } from "react-native";

import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";

type ConfirmDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  /** 설명 아래에 두는 추가 내용(예: 영향 받는 장소 목록). */
  children?: ReactNode;
  cancelLabel?: string;
  confirmLabel: string;
  onConfirm: () => void;
  /** 되돌릴 수 없는 동작(삭제 등)은 `destructive`. */
  confirmTone?: "primary" | "destructive";
  loading?: boolean;
  loadingLabel?: string;
  testID?: string;
};

/**
 * 확인 다이얼로그(Figma `Dialog`, 모서리 20, 여백 24·20). 취소는 Secondary, 확인은 Primary 버튼이다.
 * 처리 중에는 두 버튼과 바깥 닫기를 막는다.
 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  children,
  cancelLabel = "취소",
  confirmLabel,
  onConfirm,
  confirmTone = "primary",
  loading = false,
  loadingLabel,
  testID,
}: ConfirmDialogProps) {
  const close = () => {
    if (!loading) {
      onOpenChange(false);
    }
  };

  return (
    <Modal visible={open} transparent animationType="fade" statusBarTranslucent onRequestClose={close}>
      <View className="flex-1 items-center justify-center bg-black/50 px-7">
        <View accessibilityViewIsModal accessibilityRole="alert" className="w-full gap-4 rounded-xl bg-popover px-5 py-6" testID={testID}>
          <View className="gap-2">
            <Text accessibilityRole="header" className="text-h3 text-foreground">{title}</Text>
            <Text className="text-body-sm text-foreground-secondary">{description}</Text>
          </View>
          {children}
          <View className="flex-row gap-2">
            <Button
              variant="secondary"
              className="h-button-md flex-1"
              disabled={loading}
              onPress={close}
              testID={testID ? `${testID}.cancel` : undefined}
            >
              <Text>{cancelLabel}</Text>
            </Button>
            <Button
              className={confirmTone === "destructive" ? "h-button-md flex-1 bg-destructive active:bg-destructive" : "h-button-md flex-1"}
              loading={loading}
              loadingLabel={loadingLabel}
              onPress={onConfirm}
              testID={testID ? `${testID}.confirm` : undefined}
            >
              <Text>{confirmLabel}</Text>
            </Button>
          </View>
        </View>
      </View>
    </Modal>
  );
}
