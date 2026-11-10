import { router, usePathname, useGlobalSearchParams } from "expo-router";

import { TabBar, tabBarIcons } from "@/components/ui/tab-bar";

const items = [
  { key: "/rooms", label: "여행방", icon: tabBarIcons.users, testID: "tabs.rooms" },
  { key: "/ar-tip", label: "AR 팁", icon: tabBarIcons.scanEye, testID: "tabs.ar-tip" },
  { key: "/", label: "메인", icon: tabBarIcons.house },
  { key: "/my", label: "My", icon: tabBarIcons.user, testID: "tabs.my" },
  { key: "/map", label: "지도", icon: tabBarIcons.map, testID: "tabs.map" },
];

const TAB_PATHS = ["/", "/ar-tip", "/rooms", "/my", "/map", "/memo"];

export function MainTabBar() {
  const pathname = usePathname();
  const { arMode } = useGlobalSearchParams<{ arMode?: string }>();

  if (pathname === "/ar-tip" && arMode === "compose") {
    return null;
  }

  if (!TAB_PATHS.includes(pathname)) {
    return null;
  }

  return <TabBar items={items} value={pathname === "/memo" ? "/my" : pathname} onValueChange={(value) => {
    if (value === "/") {
      router.navigate("/");
    }

    if (value === "/ar-tip") {
      router.navigate("/ar-tip");
    }

    if (value === "/rooms") {
      router.navigate("/rooms");
    }

    if (value === "/my") {
      router.navigate("/my");
    }

    if (value === "/map") {
      router.navigate("/map");
    }
  }} />;
}
