async function startApp() {
  if (!process.env.EXPO_PUBLIC_API_URL) {
    throw new Error("EXPO_PUBLIC_API_URL을 .env.local에 설정해야 합니다.");
  }

  if (__DEV__) {
    const { startMocking } = await import("./api/mocks/start");
    await startMocking();
  }

  require("expo-router/entry");
}

void startApp();
