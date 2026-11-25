import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { AccessibilityInfo, Pressable, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { Text } from "@/components/ui/text";

/**
 * 토스트를 띄우는 높이. 가장 높은 하단 CTA(여행방 상세: 위 여백 12 + 안내 줄 18 + 간격 12 + 버튼 52)보다 8 더 위다.
 * 아래 안전 영역은 `BottomCta`와 같은 규칙(최소 12)으로 더한다.
 */
const TOAST_BOTTOM_OFFSET = 102;
const MIN_BOTTOM_INSET = 12;
const DEFAULT_DURATION_MS = 4_000;

type ToastOptions = {
  message: string;
  /** 되돌리기처럼 토스트 안에서 바로 할 수 있는 동작. */
  action?: { label: string; onPress: () => void };
  durationMs?: number;
};

type ToastContextValue = { show: (options: ToastOptions) => void; hide: () => void };

const ToastContext = createContext<ToastContextValue | null>(null);

/**
 * 화면을 옮겨도 이어서 보이는 토스트. 루트 레이아웃에 한 번 둔다.
 * Figma에 시안이 없어 토큰은 임시로 정했다(DESIGN.md 5-3 `[확인]`).
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastOptions | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const insets = useSafeAreaInsets();

  const hide = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }

    setToast(null);
  }, []);

  const show = useCallback((options: ToastOptions) => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
    }

    setToast(options);
    AccessibilityInfo.announceForAccessibility(options.message);

    timerRef.current = setTimeout(() => {
      timerRef.current = null;
      setToast(null);
    }, options.durationMs ?? DEFAULT_DURATION_MS);
  }, []);

  useEffect(() => {
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
  }, []);

  const value = useMemo(() => {
    return { show, hide };
  }, [show, hide]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      {toast ? (
        <View
          pointerEvents="box-none"
          className="absolute inset-x-0 bottom-0 items-center px-5"
          style={{ paddingBottom: Math.max(insets.bottom, MIN_BOTTOM_INSET) + TOAST_BOTTOM_OFFSET }}
        >
          <View
            accessibilityLiveRegion="polite"
            className="min-h-button-md w-full flex-row items-center justify-between gap-3 rounded-md bg-foreground px-4"
            testID="toast"
          >
            <Text className="flex-1 text-body-sm text-primary-foreground">{toast.message}</Text>
            {toast.action ? (
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={toast.action.label}
                className="min-h-touch justify-center px-2"
                hitSlop={4}
                onPress={() => {
                  toast.action?.onPress();
                  hide();
                }}
                testID="toast.action"
              >
                <Text className="text-label text-secondary">{toast.action.label}</Text>
              </Pressable>
            ) : null}
          </View>
        </View>
      ) : null}
    </ToastContext.Provider>
  );
}

/** 토스트를 띄운다. `ToastProvider` 안에서만 쓴다. */
export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);

  if (!context) {
    throw new Error("useToast는 ToastProvider 안에서만 쓸 수 있습니다.");
  }

  return context;
}
