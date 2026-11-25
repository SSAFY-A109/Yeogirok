import { useEffect, useRef } from "react";
import { BackHandler } from "react-native";
import { router, usePathname, useGlobalSearchParams, type Href } from "expo-router";

import { TabBar, tabBarIcons } from "@/components/ui/tab-bar";
import { useToast } from "@/components/ui/toast";

const items = [
  { key: "/rooms", label: "여행방", icon: tabBarIcons.users, testID: "tabs.rooms" },
  { key: "/ar-tip", label: "AR 팁", icon: tabBarIcons.scanEye, testID: "tabs.ar-tip" },
  { key: "/", label: "메인", icon: tabBarIcons.house },
  { key: "/my", label: "My", icon: tabBarIcons.user, testID: "tabs.my" },
  { key: "/map", label: "지도", icon: tabBarIcons.map, testID: "tabs.map" },
];

const TAB_PATHS = ["/", "/ar-tip", "/rooms", "/my", "/map", "/memo"];
const TAB_ROUTES: Record<string, Href> = { "/": "/", "/ar-tip": "/ar-tip", "/rooms": "/rooms", "/my": "/my", "/map": "/map" };
const EXIT_CONFIRM_WINDOW_MS = 2_000;

export function MainTabBar() {
  const pathname = usePathname();
  const { arMode } = useGlobalSearchParams<{ arMode?: string }>();
  const visible = TAB_PATHS.includes(pathname) && !(pathname === "/ar-tip" && arMode === "compose");

  useDoubleBackToExit(visible);

  if (!visible) {
    return null;
  }

  return <TabBar items={items} value={pathname === "/memo" ? "/my" : pathname} onValueChange={(value) => {
    const route = TAB_ROUTES[value];

    if (!route || value === pathname) {
      return;
    }

    switchTab(route);
  }} />;
}

/** 탭을 옮기면 쌓인 화면을 모두 닫고 그 탭 하나만 남긴다. */
function switchTab(route: Href) {
  if (router.canDismiss()) {
    router.dismissAll();
  }

  router.replace(route);
}

/** 되돌아갈 화면이 없는 탭에서 뒤로가기를 한 번 누르면 안내하고, 안내 중에 한 번 더 누르면 앱을 끝낸다. */
function useDoubleBackToExit(enabled: boolean) {
  const toast = useToast();
  const lastPressedAt = useRef(0);

  useEffect(() => {
    if (!enabled) {
      return;
    }

    lastPressedAt.current = 0;

    const subscription = BackHandler.addEventListener("hardwareBackPress", () => {
      if (router.canGoBack()) {
        return false;
      }

      const now = Date.now();

      if (now - lastPressedAt.current < EXIT_CONFIRM_WINDOW_MS) {
        toast.hide();
        BackHandler.exitApp();
        return true;
      }

      lastPressedAt.current = now;
      toast.show({ message: "한 번 더 뒤로 가면 앱이 꺼져요.", durationMs: EXIT_CONFIRM_WINDOW_MS });
      return true;
    });

    return () => {
      subscription.remove();
    };
  }, [enabled, toast]);
}
