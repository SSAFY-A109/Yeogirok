const { getDefaultConfig } = require("expo/metro-config");
const { withNativeWind } = require("nativewind/metro");

const config = getDefaultConfig(__dirname);

// auth:check(tsc node16)는 상대 경로 import에 `.js` 확장자를 요구한다. Metro에서는 같은 경로를 `.ts` 소스로 찾는다.
config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (moduleName.startsWith(".") && moduleName.endsWith(".js")) {
    try {
      return context.resolveRequest(context, moduleName, platform);
    } catch {
      return context.resolveRequest(context, moduleName.slice(0, -".js".length), platform);
    }
  }

  return context.resolveRequest(context, moduleName, platform);
};

module.exports = withNativeWind(config, {
  input: "./global.css",
  inlineRem: 16,
  // CSS 생성물을 디스크에 저장해 Metro의 가상 모듈 패치를 건너뛴다.
  forceWriteFileSystem: true,
});
