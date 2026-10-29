import "../global.css";

import { Stack } from "expo-router";
import { PortalHost } from "@rn-primitives/portal";

export default function RootLayout() {
  return (
    <>
      <Stack />
      <PortalHost />
    </>
  );
}
