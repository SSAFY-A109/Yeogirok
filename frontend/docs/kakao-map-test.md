# 카카오맵 연결 테스트

개발 홈의 **카카오맵 테스트** 버튼 또는 `/kakao-map-test`로 연다. 출시 빌드에서는 홈으로 이동한다.

## 방식

카카오맵 **JavaScript API**를 `react-native-webview` 안에서 띄운다. 지도 어댑터는 `lib/native/map/`에 있고, 화면은 어댑터의 `KakaoMapView`만 쓴다.

- 앱용은 `index.native.tsx`, 웹용 빈 구현은 `index.tsx`다. 두 파일의 확장자를 같게 둔다. Metro는 확장자(`.ts` → `.tsx`)를 플랫폼(`.native`)보다 먼저 보기 때문에, 웹용을 `index.ts`로 두면 Android에서도 웹용 빈 구현이 쓰여 지도 자리가 빈 화면으로 남는다(2026-10-28 실제로 겪음).

- Android와 iOS에서 동작한다. `react-native-webview`는 Expo Go에 들어 있으므로 Expo Go에서도 확인할 수 있다.
- 웹(`expo start --web`)에서는 지도를 띄우지 않고 안내 문구만 보여준다.
- 지도 타일이 실제로 그려지면(`tilesloaded`) "지도 타일 표시 완료"를 보여준다. 15초 안에 그려지지 않거나 SDK 스크립트를 불러오지 못하면 원인 안내와 다시 시도 버튼을 보여준다.
- 지도 페이지의 메시지가 20초 동안 앱에 한 번도 오지 않으면 "응답 없음"으로 끝낸다. 이때는 WebView와 앱 사이 통신 문제이므로 지도 영역에 무엇이 보이는지 확인한다.

### 네이티브 모듈을 쓰지 않는 이유 (2026-10-28 확인)

처음에는 `@react-native-kakao/map@2.2.7`을 썼지만 다음 이유로 바꿨다.

- 이 패키지는 지도 화면(`RNCKakaoMapView`)을 iOS에만 구현했다. Android에는 SDK 초기화 함수만 있어 지도가 그려지지 않는다.
- 원본 저장소(mym0404/react-native-kakao)에서 2025-03-19 커밋 "feat: remove map package"로 지도 패키지를 뺐다. 2.2.7 이후 버전이 없다.
- 같이 쓰는 `@react-native-kakao/core@2.2.7`은 React Native 0.88에서 Android 컴파일이 실패한다(`RNCKakaoUtil.kt:80`, `ReadableArray.getMap()`이 nullable을 반환). EAS 개발 빌드에서 확인했다.
- 문서 사이트 `rnkakao.dev`는 지금 다른 사이트로 넘어간다. 열지 않는다.

## 키 입력

`frontend/.env.local`에 카카오 Developers의 **JavaScript 키**를 입력한다. `.env.local`은 git에서 제외되어 있다. 다른 개발자는 `.env.example`을 복사해 사용한다.

```dotenv
EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY=여기에_JavaScript_키
```

이 값은 앱에 포함되는 클라이언트용 키다. REST API 키·어드민 키를 넣지 않는다. 키를 입력하거나 바꾼 뒤에는 개발 서버를 재시작한다.

## 카카오 콘솔 설정

1. [카카오 Developers](https://developers.kakao.com) › 내 애플리케이션 › 여기록 앱을 연다.
2. **[앱] › [플랫폼 키] › [JavaScript 키]**에서 키를 복사한다.
3. 키를 눌러 들어간 **JavaScript 키 수정** 화면의 **JavaScript SDK 도메인**에 `http://localhost`를 **포트 없이** 추가하고 저장한다. WebView 페이지를 이 주소 기준으로 열기 때문이다(`KAKAO_JS_SDK_DOMAIN`).
   - 카카오는 포트까지 비교한다. `http://localhost:8081`만 등록되어 있으면 `http://localhost`는 거부된다(401 `domain mismatched! caller=http://localhost`, 2026-10-28 확인).
   - 앱에 넣은 키(`.env.local`)와 도메인을 등록한 키가 같은지 확인한다. 이 앱에는 JavaScript 키가 여러 개 있을 수 있다.

네이티브 앱 키, Android 패키지명, 키 해시 등록은 이 방식에 필요하지 않다.

## 실행

```powershell
cd frontend
pnpm exec expo start --go
```

폰의 Expo Go로 QR을 찍는다. 이미 개발 빌드를 설치했다면 `pnpm exec expo start --dev-client`로 연다. PC와 폰이 같은 네트워크에서 연결되지 않으면 `--tunnel`을 붙인다.

## 확인 기준

1. 실제 서울시청 주변 도로·건물 타일이 표시된다.
2. 지도 드래그와 두 손가락 확대·축소가 동작한다.
3. 뒤로 갔다 다시 열어도 지도가 표시된다.

"지도 타일 표시 완료"는 카카오 JS 지도의 `tilesloaded` 이벤트가 왔다는 뜻이다. 오류가 나면 화면 안내에 따라 JavaScript 키, SDK 도메인, 네트워크를 확인한다.

키와 도메인 등록은 PC에서 먼저 확인할 수 있다. 등록 여부는 SDK 요청의 응답 코드로 알 수 있다(200 통과, 401 거부).

```sh
curl -s -o /dev/null -w "%{http_code}\n" -H "Referer: http://localhost/" "https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=<JavaScript 키>"
```

개발 빌드·Expo Go에서는 WebView 디버깅이 켜져 있다(`webviewDebuggingEnabled={__DEV__}`). Android 폰을 USB 디버깅으로 PC에 연결하면 PC 크롬의 `chrome://inspect`에서 지도 페이지의 콘솔과 네트워크를 볼 수 있다.

## 부하 테스트

테스트 화면 아래 **부하 테스트**에서 핀 0·100·500·2000개를 고른 뒤 지도를 드래그하고 확대·축소한다. 핀은 서울시청 주변 약 4km 범위에 고정 시드 난수로 찍으므로 매번 같은 위치다. 앱이 `markers`로 넘긴 핀을 지도 페이지가 클러스터(`MarkerClusterer`)에 넣는다. 실제 기능에서 서버가 준 핀을 넘기는 경로와 같다.

| 항목 | 뜻 |
| --- | --- |
| 핀 그리기 ms | 지도 페이지가 핀을 클러스터에 넣고 다음 화면 갱신까지 걸린 시간 |
| 앱에서 보낸 뒤 표시까지 ms | 앱이 핀 목록을 보낸 순간부터 위 측정 결과가 앱에 도착할 때까지(전달 시간 포함) |
| 평균 fps · 가장 느린 프레임 | 드래그·확대가 시작될 때부터 지도가 멈출 때(`idle`)까지의 프레임. 5프레임 미만인 짧은 조작은 버린다 |
| 지도 메모리 | 지도 페이지의 JS 힙(MB). Chromium 계열(Android WebView)에서만 나오고 iOS에서는 "측정 불가" |

- 지도 레벨 5부터 클러스터로 묶는다. 레벨 3~4에서는 화면 밖 핀까지 하나씩 그린다.
- 실기기 결과(2026-10-28, Galaxy S10 SM-G973N · Android 12 · Expo Go):

  | 핀 | 그리기 | 보낸 뒤 표시까지 | 확대·축소 평균 | 가장 느린 프레임 | 지도 메모리 |
  | --- | --- | --- | --- | --- | --- |
  | 500개 | 93ms | 113ms | 58fps | 17ms | 10MB |
  | 2000개 | 335ms | 352ms | 51fps | 33ms | 10MB |

  - 확대·축소 측정 구간은 0.1초로 짧았다. 드래그 수치는 따로 기록하지 못했다.
  - 기본 마커 이미지 기준이다. 팁 종류별 아이콘 같은 커스텀 마커를 쓰면 다시 잰다.
  - 같은 날 PC 크롬(브라우저 자동화로 조작)에서는 500개 1.1초, 2000개 2.2초로 폰보다 느렸다. 원인은 확인하지 않았고, 기준값은 실기기 결과로 한다.

## 검증 범위

타입 검사·린트·토큰 검사는 실제 기기 표시와 별개다. 실기기에서 지도가 보이기 전에는 지도 표시 성공으로 기록하지 않는다.

Pencil: 대응 시안 없음, `design/DESIGN.md` 기준 구현(미대조). 신규 디자인 토큰 없음.
