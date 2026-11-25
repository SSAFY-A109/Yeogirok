import { setStringAsync } from "expo-clipboard";

/** 글자를 클립보드에 복사한다. 네이티브 모듈은 이 어댑터에서만 불러온다(규칙 85). */
export async function copyToClipboard(text: string): Promise<void> {
  await setStringAsync(text);
}
