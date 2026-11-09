import type { LayoutChangeEvent, NativeScrollEvent, NativeSyntheticEvent, ScrollViewInstance } from "react-native";
import {
  KeyboardState,
  scrollTo,
  useAnimatedKeyboard,
  useAnimatedReaction,
  useAnimatedRef,
  useAnimatedStyle,
  useSharedValue,
} from "react-native-reanimated";

/** 처음 여는 키보드의 높이를 모를 때 스크롤을 맞춰 올릴 기준(pt). 한 번 열린 뒤에는 실제 높이를 쓴다. */
const FALLBACK_KEYBOARD_HEIGHT = 300;
/** 스크롤 위치를 받는 간격(ms). 키보드가 열리기 시작한 위치를 알기 위해 쓴다. */
const SCROLL_EVENT_THROTTLE_MS = 16;

/**
 * 키보드가 올라오는 움직임을 매 프레임 따라가는 화면 아래 여백과 스크롤.
 * 아래 입력(방명록)을 쓰는 동안에는 키보드가 올라오는 만큼 스크롤도 함께 올려, 입력이 키보드 위에 붙어 따라 올라오게 한다.
 * Expo Go에서도 돌아가도록 Reanimated `useAnimatedKeyboard`를 쓴다. 개발 빌드로 옮기면 Expo·Reanimated 문서가
 * 권하는 `react-native-keyboard-controller`로 바꾼다(TBD, 설치 승인 필요).
 */
export function useKeyboardFollowingScroll() {
  const keyboard = useAnimatedKeyboard();
  const scrollRef = useAnimatedRef<ScrollViewInstance>();
  const scrollOffset = useSharedValue(0);
  const contentHeight = useSharedValue(0);
  const closedViewportHeight = useSharedValue(0);
  const isFollowing = useSharedValue(false);
  const openingFrom = useSharedValue(0);
  const expectedKeyboardHeight = useSharedValue(FALLBACK_KEYBOARD_HEIGHT);

  const keyboardSpacerStyle = useAnimatedStyle(() => {
    return { height: keyboard.height.get() };
  });

  useAnimatedReaction(
    () => {
      return keyboard.height.get();
    },
    (height, previous) => {
      if ((previous === null || previous === 0) && height > 0) {
        openingFrom.set(scrollOffset.get());
      }

      if (!isFollowing.get() || previous === null || height <= previous) {
        return;
      }

      const from = openingFrom.get();
      const endOffset = Math.max(0, contentHeight.get() - (closedViewportHeight.get() - height));

      if (endOffset <= from) {
        return;
      }

      const progress = Math.min(1, height / expectedKeyboardHeight.get());
      scrollTo(scrollRef, 0, from + (endOffset - from) * progress, false);
    },
  );

  useAnimatedReaction(
    () => {
      return keyboard.state.get();
    },
    (state) => {
      if (state !== KeyboardState.OPEN) {
        return;
      }

      const height = keyboard.height.get();

      if (height > 0) {
        expectedKeyboardHeight.set(height);
      }

      const endOffset = Math.max(0, contentHeight.get() - (closedViewportHeight.get() - height));

      if (isFollowing.get() && scrollOffset.get() < endOffset - 1) {
        scrollTo(scrollRef, 0, endOffset, true);
      }
    },
  );

  /** 아래 입력에 포커스가 가면 부른다. 키보드가 이미 떠 있으면 바로 끝까지 부드럽게 내린다. */
  const followInput = () => {
    isFollowing.set(true);

    if (keyboard.state.get() === KeyboardState.OPEN) {
      scrollRef.current?.scrollToEnd({ animated: true });
    }
  };

  const stopFollowing = () => {
    isFollowing.set(false);
  };

  const scrollViewProps = {
    ref: scrollRef,
    scrollEventThrottle: SCROLL_EVENT_THROTTLE_MS,

    onScroll: (event: NativeSyntheticEvent<NativeScrollEvent>) => {
      scrollOffset.set(event.nativeEvent.contentOffset.y);
    },

    onLayout: (event: LayoutChangeEvent) => {
      if (keyboard.height.get() === 0) {
        closedViewportHeight.set(event.nativeEvent.layout.height);
      }
    },

    onContentSizeChange: (_width: number, height: number) => {
      contentHeight.set(height);
    },
  };

  return { scrollViewProps, keyboardSpacerStyle, followInput, stopFollowing };
}
