import { useAnimatedKeyboard } from "react-native-reanimated";

/**
 * Reanimated `useAnimatedKeyboard`의 키보드 상태가 낡지 않게 앱이 떠 있는 동안 구독 하나를 유지한다. 그리는 것은 없다.
 * Android에서 Reanimated는 앱 전체에서 키보드 상태를 하나만 두고, 마지막 구독이 끊기면 관찰을 멈춘 채 마지막 높이·상태를 남긴다.
 * 방명록을 쓰다 키보드가 열린 채 W16을 나가면 "열림"이 남아, 다시 들어올 때 키보드 높이만큼 아래 버튼이 떠 있었다.
 * W16 키보드 처리를 `react-native-keyboard-controller`로 옮기면(개발 빌드 전환 때, TBD) 이 컴포넌트를 지운다.
 */
export function KeyboardStateKeeper() {
  useAnimatedKeyboard();
  return null;
}
