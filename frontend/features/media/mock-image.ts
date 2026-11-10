const BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
const IMAGE_CONTENT_TYPES = ["image/jpeg", "image/png", "image/webp"];

function encodeBase64(bytes: Uint8Array): string {
  const encoded = new Uint8Array(Math.ceil(bytes.length / 3) * 4);

  for (let input = 0, output = 0; input < bytes.length; input += 3, output += 4) {
    const first = bytes[input];
    const second = bytes[input + 1] ?? 0;
    const third = bytes[input + 2] ?? 0;
    encoded[output] = BASE64_ALPHABET.charCodeAt(first >> 2);
    encoded[output + 1] = BASE64_ALPHABET.charCodeAt(((first & 3) << 4) | (second >> 4));
    encoded[output + 2] = input + 1 < bytes.length ? BASE64_ALPHABET.charCodeAt(((second & 15) << 2) | (third >> 6)) : 61;
    encoded[output + 3] = input + 2 < bytes.length ? BASE64_ALPHABET.charCodeAt(third & 63) : 61;
  }

  return new TextDecoder().decode(encoded);
}

/** 네이티브 이미지 로더의 HTTP 요청은 MSW의 JS 인터셉터를 거치지 않는다. */
export async function getMockImageSource(uri: string, signal: AbortSignal): Promise<string> {
  const response = await fetch(uri, { signal, credentials: "omit" });
  const contentType = response.headers.get("Content-Type")?.split(";")[0] ?? "";

  if (!response.ok || !IMAGE_CONTENT_TYPES.includes(contentType)) {
    throw new Error("목 이미지 응답을 읽을 수 없습니다.");
  }

  const bytes = new Uint8Array(await response.arrayBuffer());
  return `data:${contentType};base64,${encodeBase64(bytes)}`;
}
