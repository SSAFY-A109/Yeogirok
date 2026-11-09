import type { LucideIcon } from "lucide-react-native";
import { Pressable, View } from "react-native";

import { cn } from "@/lib/utils";
import { Button } from "./button";
import { Icon } from "./icon";
import { Text } from "./text";

type PermissionGateAction = {
  label: string;
  onPress: () => void;
  loading?: boolean;
  testID?: string;
};

type PermissionGateProps = {
  icon: LucideIcon;
  title: string;
  /** 무엇에 쓰는지, 거부해도 무엇을 할 수 있는지 한두 문장으로 쓴다. */
  description: string;
  action: PermissionGateAction;
  /** "나중에"처럼 권한 없이 하던 일을 이어 가는 동작. */
  secondaryAction?: PermissionGateAction;
  className?: string;
  testID?: string;
};

/**
 * 위치·카메라·사진 권한의 사전 안내와 거부 상태 카드(Figma W15-P `Permission card`, DESIGN.md 5-3).
 * OS 대화상자는 이 카드의 버튼을 누른 뒤에만 띄운다(규칙 80).
 */
export function PermissionGate({ icon, title, description, action, secondaryAction, className, testID }: PermissionGateProps) {
  return (
    <View className={cn("gap-4 rounded-lg border border-border bg-card p-4", className)} testID={testID}>
      <View className="flex-row items-start gap-3">
        <View className="size-10 items-center justify-center rounded-md bg-brand-muted">
          <Icon as={icon} size={22} className="text-icon-active" />
        </View>
        <View className="flex-1 gap-1">
          <Text accessibilityRole="header" className="text-label text-foreground">{title}</Text>
          <Text className="text-caption text-foreground-secondary">{description}</Text>
        </View>
      </View>
      <Button
        size="lg"
        className="w-full"
        loading={action.loading}
        onPress={action.onPress}
        testID={action.testID}
      >
        <Text>{action.label}</Text>
      </Button>
      {secondaryAction ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={secondaryAction.label}
          className="min-h-touch items-center justify-center rounded-sm active:bg-accent"
          onPress={secondaryAction.onPress}
          testID={secondaryAction.testID}
        >
          <Text className="text-label text-foreground-secondary">{secondaryAction.label}</Text>
        </Pressable>
      ) : null}
    </View>
  );
}
