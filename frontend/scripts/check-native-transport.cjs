const assert = require("node:assert/strict");
const Module = require("node:module");
const { createRequire } = Module;

const requireNative = createRequire(require.resolve("react-native/package.json"));
const nativeFetch = requireNative("whatwg-fetch");

class NativeXMLHttpRequest {
  constructor() {
    this.listeners = new Map();
    this.upload = {
      addEventListener() {},

      removeEventListener() {},
    };
    this.readyState = 0;
    this.responseType = "";
    this.onloadend = null;
  }

  addEventListener(type, listener) {
    const listeners = this.listeners.get(type) ?? new Set();
    listeners.add(listener);
    this.listeners.set(type, listeners);
  }

  removeEventListener(type, listener) {
    this.listeners.get(type)?.delete(listener);
  }

  dispatchEvent(event) {
    this[`on${event.type}`]?.call(this, event);

    for (const listener of this.listeners.get(event.type) ?? []) {
      listener.call(this, event);
    }

    return true;
  }

  open() {
    this.readyState = 1;
  }

  setRequestHeader() {}

  getResponseHeader() {
    return null;
  }

  getAllResponseHeaders() {
    return "";
  }

  send() {
    throw new Error("서비스 API를 실제 네트워크로 보내면 안 됩니다.");
  }

  abort() {}
}

Object.assign(NativeXMLHttpRequest, { UNSENT: 0, OPENED: 1, HEADERS_RECEIVED: 2, LOADING: 3, DONE: 4 });
Object.assign(NativeXMLHttpRequest.prototype, { UNSENT: 0, OPENED: 1, HEADERS_RECEIVED: 2, LOADING: 3, DONE: 4 });
Object.assign(globalThis, {
  XMLHttpRequest: NativeXMLHttpRequest,
  Headers: nativeFetch.Headers,
  Request: nativeFetch.Request,
  Response: nativeFetch.Response,
  fetch: nativeFetch.fetch,
});

const { http, HttpResponse } = require("msw");
const { setupServer } = require("msw/native");
const load = Module._load;

Module._load = function (id, ...args) {
  if (id === "expo-secure-store") {
    return {
      getItemAsync: async () => {
        return null;
      },
    };
  }

  return load.call(this, id, ...args);
};

process.env.EXPO_PUBLIC_API_URL = "https://api.yeogirok.test";
const { api } = require("../.expo/native-check/api/client");
Module._load = load;
const fixture = { success: true, data: { authenticated: true } };

const server = setupServer(http.post("https://api.yeogirok.test/api/v1/auth/token", () => {
  return HttpResponse.json(fixture);
}));

async function check() {
  assert.equal(new Response("native").body, undefined, "RN fetch의 스트림 없는 Response를 재현해야 합니다.");
  server.listen({ onUnhandledRequest: "error" });

  try {
    const config = process.argv[2] ? { adapter: process.argv[2] } : undefined;
    const { data } = await api.post("/auth/token", { provider: "google" }, config);
    assert.deepEqual(data, fixture.data, "RN 응답 본문이 공통 API 클라이언트에서도 보존되어야 합니다.");
    console.info("RN transport: 스트림 없는 Response에서 MSW 본문·공통 봉투 보존 확인");
  } finally {
    server.close();
  }
}

check().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
