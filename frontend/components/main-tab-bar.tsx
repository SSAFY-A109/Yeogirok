import { router, usePathname, useGlobalSearchParams } from "expo-router";

import { TabBar, tabBarIcons } from "@/components/ui/tab-bar";

const items = [
  { key: "rooms", label: "여행방", icon: tabBarIcons.users, disabled: true },
  { key: "/ar-tip", label: "AR 팁", icon: tabBarIcons.scanEye, testID: "tabs.ar-tip" },
  { key: "/", label: "메인", icon: tabBarIcons.house },
  { key: "records", label: "My", icon: tabBarIcons.user, disabled: true },
  { key: "map", label: "지도", icon: tabBarIcons.map, disabled: true },
];

export function MainTabBar() {
  const pathname = usePathname();
  const { arMode } = useGlobalSearchParams<{ arMode?: string }>();

  if (pathname === "/ar-tip" && arMode === "compose") {
    return null;
  }

  if (pathname !== "/" && pathname !== "/ar-tip") {
    return null;
  }

  return <TabBar items={items} value={pathname} onValueChange={(value) => {
    if (value === "/") {
      router.navigate("/");
    }

    if (value === "/ar-tip") {
      router.navigate("./ar-tip");
    }
  }} />;
}
