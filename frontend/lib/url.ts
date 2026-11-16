const INSECURE_SCHEME = "http://";
const SECURE_SCHEME = "https://";

/**
 * 서버가 준 사진 주소가 `http://`면 `https://`로 바꾼다. Android 기본 네트워크 보안 정책과 iOS ATS는 암호화하지 않은
 * `http` 요청을 막아서, 한국관광공사 TourAPI 사진(`http://tong.visitkorea.or.kr/...`)이 뜨지 않았다(2026-11-16 실기기 로그).
 * 같은 주소가 `https`로도 열린다. `http`는 어차피 막히므로 다른 주소도 `https`로 바꿔 시도한다.
 */
export function toHttpsUrl(url: string): string {
  return url.startsWith(INSECURE_SCHEME) ? `${SECURE_SCHEME}${url.slice(INSECURE_SCHEME.length)}` : url;
}
