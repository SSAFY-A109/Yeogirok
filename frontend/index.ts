import { AppRegistry } from "react-native";

AppRegistry.registerComponent("ArTipDetail", () => {
  const detail = require("./features/tip/components/tip-detail-sheet") as typeof import("./features/tip/components/tip-detail-sheet");
  return detail.ArTipDetail;
});

if (!process.env.EXPO_PUBLIC_API_URL) {
  throw new Error("EXPO_PUBLIC_API_URL을 .env.local에 설정해야 합니다.");
}

function startRouter() {
  require("expo-router/entry");
}

// 네이티브는 번들 실행 직후 "main"을 실행한다. 등록을 await 뒤로 미루면
// "App entry not found"가 나므로 MSW는 동기 require로 불러온다.
if (__DEV__) {
  const { startMocking } = require("./api/mocks/start") as typeof import("./api/mocks/start");
  const ready = startMocking();

  if (ready) {
    void ready.then(startRouter);
  } else {
    startRouter();
  }
} else {
  startRouter();
}
