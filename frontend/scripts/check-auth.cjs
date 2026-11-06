const assert = require("node:assert/strict");
const Module = require("node:module");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const { http, HttpResponse } = require("msw");

const stored = new Map();
const load = Module._load;
const sdkState = { googleResponse: { type: "success", data: { idToken: "sdk-google-id-token", serverAuthCode: "sdk-google-auth-code" } }, kakaoResponse: { idToken: "sdk-kakao-id-token" }, googleError: null, kakaoError: null, googleConfig: null, kakaoLogin: null };

Module._load = function (id, ...args) {
  if (id === "react-native") {
    return { Platform: { OS: "android" } };
  }

  if (id === "react-native-nitro-google-signin") {
    return {
      GoogleOneTapSignIn: {
        configure: (params) => {
          sdkState.googleConfig = params;
        },

        checkPlayServices: async () => {},

        presentExplicitSignIn: async () => {
          if (sdkState.googleError) {
            throw sdkState.googleError;
          }

          return sdkState.googleResponse;
        },
      },

      isSuccessResponse: (response) => {
        return response.type === "success";
      },

      isCancelledResponse: (response) => {
        return response.type === "cancelled";
      },

      isErrorWithCode: (error) => {
        return typeof error?.code === "string";
      },

      statusCodes: { SIGN_IN_CANCELLED: "SIGN_IN_CANCELLED", PLAY_SERVICES_NOT_AVAILABLE: "PLAY_SERVICES_NOT_AVAILABLE" },
    };
  }

  if (id === "expo-crypto") {
    return {
      getRandomBytes: (count) => {
        return require("node:crypto").randomBytes(count);
      },
    };
  }

  if (id === "@react-native-kakao/core") {
    return { initializeKakaoSDK: async () => {} };
  }

  if (id === "@react-native-kakao/user") {
    return {
      login: async (params) => {
        sdkState.kakaoLogin = params;

        if (sdkState.kakaoError) {
          throw sdkState.kakaoError;
        }

        return sdkState.kakaoResponse;
      },
    };
  }

  if (id === "expo-secure-store") {
    return {
      getItemAsync: async (key) => {
        return stored.get(key) ?? null;
      },

      setItemAsync: async (key, value) => {
        stored.set(key, value);
      },

      deleteItemAsync: async (key) => {
        stored.delete(key);
      },
    };
  }

  return load.call(this, id, ...args);
};

process.env.EXPO_PUBLIC_API_URL = "https://api.yeogirok.test";
global.__DEV__ = true;
const { api } = require("../.expo/auth-check/api/client");
const { isApiError } = require("../.expo/auth-check/api/errors");
const { exchangeSocialToken, refreshServiceTokens } = require("../.expo/auth-check/features/auth/api/auth.api");
const { toLoginResult, toServiceTokens } = require("../.expo/auth-check/features/auth/model");
const { getTokens, setTokens, getLoginProvider, clearTokens } = require("../.expo/auth-check/features/auth/token-store");
const { server } = require("../.expo/auth-check/api/mocks/server");
const { failedRefreshTokensFixture, googleServerAuthCodeFixture, serviceTokensFixture, socialCredentialsFixture } = require("../.expo/auth-check/api/mocks/fixtures/auth");
const { createNonce } = require("../.expo/auth-check/features/auth/nonce");
const { signOutThisDevice } = require("../.expo/auth-check/features/auth/sign-out");
const { deleteMe, getMe } = require("../.expo/auth-check/features/user/api/user.api");
const { withdrawWithReauthentication } = require("../.expo/auth-check/features/user/withdraw");
const { performSocialLogin } = require("../.expo/auth-check/features/auth/sign-in");
const { signInWithGoogle } = loadNativeAdapter("google");
const { signInWithKakao } = loadNativeAdapter("kakao");
const { getSocialCredentials } = require("../.expo/auth-check/features/auth/native/social-login");
const configureApp = require("../.expo/auth-check/app.config").default;
Module._load = load;

function loadNativeAdapter(name) {
  const filename = path.resolve(__dirname, `../.expo/auth-check/features/auth/native/social-login/${name}.js`);
  const source = fs.readFileSync(path.resolve(__dirname, `../features/auth/native/social-login/${name}.ts`), "utf8");
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  });
  const adapter = new Module(filename, module);
  adapter.filename = filename;
  adapter.paths = module.paths;
  adapter._compile(compiled.outputText, filename);
  return adapter.exports;
}

const hasCode = (code, status) => {
  return (error) => {
    return isApiError(error) && error.code === code && error.status === status;
  };
};

async function check() {
  assert.equal(await getLoginProvider(), null);
  stored.set("yeogirok.auth.provider", "unsupported");
  assert.equal(await getLoginProvider(), null, "모르는 로그인 제공자를 추측하면 안 됩니다.");
  stored.delete("yeogirok.auth.provider");

  for (const key of ["EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID", "EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID", "EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY"]) {
    delete process.env[key];
  }

  const socialCode = (code) => {
    return (error) => {
      return error.code === code;
    };
  };

  const nonce = createNonce();
  assert.match(nonce, /^[0-9a-f]{64}$/);
  assert.notEqual(createNonce(), nonce, "nonce는 로그인마다 새로 만들어야 합니다.");
  await assert.rejects(signInWithGoogle(nonce), socialCode("AUTH_NOT_CONFIGURED"));
  await assert.rejects(signInWithKakao(nonce), socialCode("AUTH_NOT_CONFIGURED"));
  assert.equal(configureApp({ config: {} }).plugins.length, 2);
  process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID = "example-web.apps.googleusercontent.com";
  process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID = "example-ios.apps.googleusercontent.com";
  process.env.EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY = "example-native-app-key";
  assert.deepEqual(await signInWithGoogle(nonce), { provider: "google", idToken: "sdk-google-id-token", nonce, serverAuthCode: "sdk-google-auth-code" });
  assert.deepEqual(sdkState.googleConfig, { webClientId: "example-web.apps.googleusercontent.com", nonce, offlineAccess: true });
  assert.deepEqual(await signInWithKakao(nonce), { provider: "kakao", idToken: "sdk-kakao-id-token", nonce, serverAuthCode: null });
  assert.deepEqual(sdkState.kakaoLogin, { nonce });
  const plugins = configureApp({ config: {} }).plugins;
  assert.equal(plugins[2][1].iosUrlScheme, "com.googleusercontent.apps.example-ios");
  assert.equal(plugins[3][1].android.authCodeHandlerActivity, true);
  assert.equal(plugins[3][1].ios.handleKakaoOpenUrl, true);
  sdkState.googleResponse = { type: "cancelled" };
  await assert.rejects(signInWithGoogle(nonce), socialCode("AUTH_CANCELLED"));
  sdkState.googleResponse = { type: "success", data: { idToken: "", serverAuthCode: null } };
  await assert.rejects(signInWithGoogle(nonce), socialCode("AUTH_NOT_CONFIGURED"));
  sdkState.googleError = { code: "SIGN_IN_CANCELLED" };
  await assert.rejects(signInWithGoogle(nonce), socialCode("AUTH_CANCELLED"));
  sdkState.googleError = { code: "PLAY_SERVICES_NOT_AVAILABLE" };
  await assert.rejects(signInWithGoogle(nonce), socialCode("GOOGLE_PLAY_SERVICES"));
  sdkState.kakaoError = { code: "Cancelled" };
  await assert.rejects(signInWithKakao(nonce), socialCode("AUTH_CANCELLED"));
  sdkState.kakaoError = null;
  sdkState.kakaoResponse = {};
  await assert.rejects(signInWithKakao(nonce), socialCode("AUTH_NOT_CONFIGURED"));
  global.__DEV__ = false;
  await assert.rejects(getSocialCredentials("google"), socialCode("AUTH_UNSUPPORTED"));
  global.__DEV__ = true;

  assert.deepEqual(toServiceTokens(serviceTokensFixture), serviceTokensFixture);
  assert.equal(toLoginResult({ ...serviceTokensFixture, isNewUser: false }).isNewUser, false);

  for (const value of [null, {}, { ...serviceTokensFixture, isNewUser: "true" }, serviceTokensFixture]) {
    assert.throws(() => {
      toLoginResult(value);
    }, hasCode("CONTRACT_MISMATCH", 200));
  }

  for (const field of ["accessTokenExpiresIn", "refreshTokenExpiresIn"]) {
    for (const value of [undefined, null, "60", 0, -1, NaN, Infinity]) {
      assert.throws(() => {
        toServiceTokens({ ...serviceTokensFixture, [field]: value });
      }, hasCode("CONTRACT_MISMATCH", 200));
    }
  }

  for (const field of ["accessToken", "refreshToken"]) {
    assert.throws(() => {
      toServiceTokens({ ...serviceTokensFixture, [field]: "" });
    }, hasCode("CONTRACT_MISMATCH", 200));
  }

  const authorizationHeaders = [];

  server.events.on("request:start", ({ request }) => {
    if (new URL(request.url).pathname.startsWith("/api/v1/auth/token")) {
      authorizationHeaders.push(request.headers.get("Authorization"));
    }
  });

  server.listen({ onUnhandledRequest: "error" });

  try {
    for (const provider of ["google", "kakao"]) {
      const result = await performSocialLogin(provider);
      assert.equal(await getLoginProvider(), provider, "성공한 로그인 제공자를 기억해야 합니다.");
      assert.equal(result, undefined, "서비스 토큰을 mutation 결과 캐시에 남기면 안 됩니다.");
      const tokens = await getTokens();
      assert.ok(tokens.accessToken.startsWith("mock-access-token-"));
      assert.ok(tokens.refreshToken.startsWith("mock-refresh-token-"));
    }

    await setTokens(serviceTokensFixture);
    assert.equal(await getLoginProvider(), "kakao", "토큰 갱신은 로그인 제공자를 유지해야 합니다.");
    const google = await exchangeSocialToken(socialCredentialsFixture.google);
    const kakao = await exchangeSocialToken(socialCredentialsFixture.kakao);
    assert.equal(google.isNewUser, true);
    assert.notEqual(google.refreshToken, kakao.refreshToken);

    for (const [body, code, status] of [
      [{ provider: "google" }, "C001", 400],
      [{ provider: "google", idToken: socialCredentialsFixture.google.idToken }, "C001", 400],
      [{ provider: "google", idToken: "" }, "C001", 400],
      [{ provider: "unsupported", idToken: "example", nonce: "example" }, "C003", 400],
      [{ provider: "google", idToken: "invalid", nonce: "example" }, "A005", 401],
      [{ ...socialCredentialsFixture.google, nonce: "wrong" }, "A006", 401],
    ]) {
      await assert.rejects(exchangeSocialToken(body), hasCode(code, status));
    }

    const rotated = await refreshServiceTokens(google.refreshToken);
    assert.notEqual(rotated.refreshToken, google.refreshToken);
    assert.equal("isNewUser" in rotated, false);
    await assert.rejects(refreshServiceTokens(google.refreshToken), hasCode("A010", 401));
    await refreshServiceTokens(kakao.refreshToken);

    for (const [token, code, status] of [
      ["", "C001", 400],
      ["invalid", "A007", 401],
      [failedRefreshTokensFixture.expired, "A008", 401],
      [failedRefreshTokensFixture.revoked, "A009", 401],
    ]) {
      await assert.rejects(refreshServiceTokens(token), hasCode(code, status));
    }

    await setTokens(rotated);

    server.use(http.get("*/api/v1/__auth_check__", ({ request }) => {
      return request.headers.get("Authorization") === `Bearer ${rotated.accessToken}`
        ? HttpResponse.json({ success: false, code: "C005", message: "expired" }, { status: 401 })
        : HttpResponse.json({ success: true, data: { authenticated: true } });
    }));

    assert.deepEqual((await api.get("/__auth_check__")).data, { authenticated: true });
    assert.notEqual((await getTokens()).refreshToken, rotated.refreshToken);
    await assert.rejects(refreshServiceTokens(rotated.refreshToken), hasCode("A010", 401));

    server.use(http.post("*/api/v1/auth/token", () => {
      return HttpResponse.json({ success: true, data: serviceTokensFixture });
    }));

    await assert.rejects(exchangeSocialToken(socialCredentialsFixture.google), hasCode("CONTRACT_MISMATCH", 200));
    await assert.rejects(performSocialLogin("google"), hasCode("CONTRACT_MISMATCH", 200));
    assert.equal(await getLoginProvider(), "kakao", "실패한 로그인은 기존 제공자를 바꾸면 안 됩니다.");
    assert.ok(authorizationHeaders.length > 0);

    assert.ok(authorizationHeaders.every((header) => {
      return header === null;
    }), "로그인·갱신 요청에 서비스 토큰을 보내면 안 됩니다.");

    server.resetHandlers();

    // 로그아웃(API-007): 이 기기 리프레시 토큰 폐기, 요청이 실패해도 기기 토큰 삭제
    await performSocialLogin("kakao");
    const beforeLogout = await getTokens();
    await signOutThisDevice();
    assert.equal(await getTokens(), null);
    await assert.rejects(refreshServiceTokens(beforeLogout.refreshToken), hasCode("A007", 401));
    await setTokens(serviceTokensFixture);

    server.use(http.post("*/api/v1/auth/logout", () => {
      return HttpResponse.json({ success: false, code: "C009", message: "fail" }, { status: 500 });
    }));

    await assert.rejects(signOutThisDevice(), hasCode("C009", 500));
    assert.equal(await getTokens(), null, "로그아웃 요청이 실패해도 기기 토큰은 지워야 합니다.");
    server.resetHandlers();

    // 내 정보(API-008)
    await performSocialLogin("kakao");
    const me = await getMe();
    assert.ok(me.nickname.length > 0);
    assert.deepEqual(me.loginProviders, ["kakao"]);

    // 탈퇴(API-009): 재발급 토큰이면 A011, 구글 코드 누락·불일치는 C001·A029
    await setTokens(await refreshServiceTokens((await getTokens()).refreshToken));
    await assert.rejects(deleteMe(null), hasCode("A011", 403));
    await performSocialLogin("google");
    await assert.rejects(deleteMe(null), hasCode("C001", 400));
    await performSocialLogin("google");
    await assert.rejects(deleteMe("wrong-code"), hasCode("A029", 400));
    await performSocialLogin("google");
    assert.deepEqual(await deleteMe(googleServerAuthCodeFixture), { withdrawn: true, dataCleanupStatus: "QUEUED" });

    for (const provider of ["google", "kakao"]) {
      await setTokens(serviceTokensFixture);
      await withdrawWithReauthentication(provider);
      assert.equal(await getTokens(), null, "탈퇴 후 기기 토큰을 지워야 합니다.");
    }

    await clearTokens();
    assert.equal(await getTokens(), null);
    assert.equal(await getLoginProvider(), null, "세션 삭제 시 로그인 제공자도 지워야 합니다.");
    console.info("Auth: 개발 MSW 로그인·nonce·토큰 저장·제공자 저장/유지/삭제·SDK 어댑터·키 누락/취소·Expo 설정·오류 8종·회전·자동 갱신·로그아웃·내 정보·재인증 탈퇴 확인");
  } finally {
    server.close();
  }
}

check().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
