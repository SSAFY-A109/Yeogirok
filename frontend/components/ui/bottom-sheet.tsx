import { useEffect, useRef, useState, type ReactNode } from "react";
import { Animated, Easing, Modal, PanResponder, Pressable, ScrollView, View, useWindowDimensions } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

const HANDLE_ZONE_HEIGHT = 28;
const MOTION_MS = 220;
const TAP_SLOP = 6;
const FLING_VELOCITY = 0.6;
const DISMISS_DISTANCE = 80;

type BottomSheetProps = {
  visible: boolean;
  onClose: () => void;
  closeLabel?: string;
  /** false면 끌어올려 화면을 채우는 동작을 막고 내용 높이로만 보여 준다. */
  expandable?: boolean;
  children: ReactNode;
};

// 기본은 내용 높이로 열리고, 맨 윗부분(핸들)을 끌어올리면 화면을 채운다. 아래로 끌면 줄어들고, 더 끌면 닫힌다.
export function BottomSheet({ visible, ...props }: BottomSheetProps) {
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={props.onClose} statusBarTranslucent>
      {visible && <SheetBody {...props} />}
    </Modal>
  );
}

function SheetBody({ onClose, closeLabel = "닫기", expandable = true, children }: Omit<BottomSheetProps, "visible">) {
  const insets = useSafeAreaInsets();
  const { height: windowHeight } = useWindowDimensions();
  const [contentHeight, setContentHeight] = useState(0);
  const [expanded, setExpanded] = useState(false);

  const [sheetHeight] = useState(() => {
    return new Animated.Value(0);
  });

  const [translateY] = useState(() => {
    return new Animated.Value(windowHeight);
  });

  const heightRef = useRef(0);
  const overshootRef = useRef(0);
  const dragStartHeightRef = useRef(0);

  const expandedHeight = windowHeight - insets.top;
  const measured = contentHeight > 0;
  const collapsedHeight = Math.min(HANDLE_ZONE_HEIGHT + contentHeight + insets.bottom, expandedHeight);

  const latest = useRef({ collapsedHeight, expandedHeight, expandable, windowHeight, onClose, expanded });

  // 제스처 핸들러는 이벤트 시점에만 ref를 읽는다(렌더 중 접근 아님).
  // eslint-disable-next-line react-hooks/refs
  const [panResponder] = useState(() => {
    function animateHeight(to: number) {
      Animated.timing(sheetHeight, { toValue: to, duration: MOTION_MS, easing: Easing.out(Easing.cubic), useNativeDriver: false }).start();
    }

    function dismiss() {
      Animated.timing(translateY, { toValue: latest.current.windowHeight, duration: MOTION_MS, easing: Easing.out(Easing.cubic), useNativeDriver: false }).start(({ finished }) => {
        if (finished) {
          latest.current.onClose();
        }
      });
    }

    function settle(shouldExpand: boolean) {
      const { collapsedHeight: low, expandedHeight: high, expandable: canExpand } = latest.current;
      const next = shouldExpand && canExpand;

      setExpanded(next);
      animateHeight(next ? high : low);
    }

    return PanResponder.create({
      onStartShouldSetPanResponder: () => {
        return true;
      },

      onPanResponderGrant: () => {
        sheetHeight.stopAnimation();
        dragStartHeightRef.current = heightRef.current;
      },

      onPanResponderMove: (_, gesture) => {
        const { collapsedHeight: low, expandedHeight: high, expandable: canExpand } = latest.current;
        const target = dragStartHeightRef.current - gesture.dy;

        sheetHeight.setValue(Math.min(Math.max(target, low), canExpand ? high : low));
        overshootRef.current = Math.max(0, low - target);
        translateY.setValue(overshootRef.current);
      },

      onPanResponderRelease: (_, gesture) => {
        const { collapsedHeight: low, expandedHeight: high, expanded: isExpanded } = latest.current;

        if (Math.abs(gesture.dx) < TAP_SLOP && Math.abs(gesture.dy) < TAP_SLOP) {
          settle(!isExpanded);

          return;
        }

        if (overshootRef.current > 0) {
          const shouldDismiss = overshootRef.current > DISMISS_DISTANCE || gesture.vy > FLING_VELOCITY;

          overshootRef.current = 0;

          if (shouldDismiss) {
            dismiss();
          } else {
            Animated.timing(translateY, { toValue: 0, duration: MOTION_MS, easing: Easing.out(Easing.cubic), useNativeDriver: false }).start();
          }

          return;
        }

        const passedMiddle = heightRef.current > (low + high) / 2;

        settle(gesture.vy < -FLING_VELOCITY || (gesture.vy < FLING_VELOCITY && passedMiddle));
      },
    });
  });

  useEffect(() => {
    latest.current = { collapsedHeight, expandedHeight, expandable, windowHeight, onClose, expanded };
  });

  useEffect(() => {
    const id = sheetHeight.addListener(({ value }) => {
      heightRef.current = value;
    });

    return () => {
      sheetHeight.removeListener(id);
    };
  }, [sheetHeight]);

  const openedRef = useRef(false);

  useEffect(() => {
    if (!measured || openedRef.current) {
      return;
    }

    openedRef.current = true;
    sheetHeight.setValue(collapsedHeight);
    Animated.timing(translateY, { toValue: 0, duration: MOTION_MS, easing: Easing.out(Easing.cubic), useNativeDriver: false }).start();
  }, [measured, collapsedHeight, sheetHeight, translateY]);

  useEffect(() => {
    if (openedRef.current) {
      Animated.timing(sheetHeight, { toValue: expanded ? expandedHeight : collapsedHeight, duration: MOTION_MS, easing: Easing.out(Easing.cubic), useNativeDriver: false }).start();
    }
  }, [expanded, collapsedHeight, expandedHeight, sheetHeight]);

  function dismissFromScrim() {
    Animated.timing(translateY, { toValue: windowHeight, duration: MOTION_MS, easing: Easing.out(Easing.cubic), useNativeDriver: false }).start(({ finished }) => {
      if (finished) {
        onClose();
      }
    });
  }

  return (
    <View className="flex-1 justify-end">
      <Pressable accessibilityRole="button" accessibilityLabel={closeLabel} onPress={dismissFromScrim} className="absolute inset-0 bg-black/45" testID="bottom-sheet.scrim" />
      <Animated.View
        className="overflow-hidden rounded-t-xl bg-popover"
        style={{ height: sheetHeight, opacity: measured ? 1 : 0, transform: [{ translateY }] }}
        testID="bottom-sheet"
      >
        <View
          {...panResponder.panHandlers}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="시트 크기 조절"
          accessibilityValue={{ text: expanded ? "전체 화면" : "기본 크기" }}
          accessibilityActions={[{ name: "increment", label: "전체 화면으로 펼치기" }, { name: "decrement", label: "기본 크기로 줄이기" }]}
          onAccessibilityAction={(event) => {
            setExpanded(event.nativeEvent.actionName === "increment" && expandable);
          }}
          className="items-center justify-center"
          style={{ height: HANDLE_ZONE_HEIGHT }}
          testID="bottom-sheet.handle"
        >
          <View className="h-1 w-9 rounded-full bg-border-strong" />
        </View>
        <ScrollView bounces={false} showsVerticalScrollIndicator={false} keyboardShouldPersistTaps="handled" scrollEnabled={expanded}>
          <View
            onLayout={({ nativeEvent: { layout } }) => {
              setContentHeight(layout.height);
            }}
          >
            {children}
          </View>
          <View style={{ height: insets.bottom }} />
        </ScrollView>
      </Animated.View>
    </View>
  );
}
