# 네이티브 카카오맵 연결 테스트

개발 홈의 **카카오맵 테스트** 버튼 또는 `/kakao-map-test`로 연다. 출시 빌드에서는 홈으로 이동한다.

## 키 입력

`frontend/.env.local`에 카카오 Developers의 **네이티브 앱 키**를 입력한다. `.env.local`은 git에서 제외되어 있다. 다른 개발자는 `.env.example`을 복사해 사용한다.

```dotenv
EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY=여기에_네이티브_앱_키
```

이 값은 앱에 포함되는 클라이언트용 키다. REST API 키·어드민 키·서버 비밀 키를 넣지 않는다. 키를 입력하거나 변경한 뒤에는 개발 서버를 재시작한다. config plugin에도 쓰이므로 개발 빌드도 다시 생성한다.

카카오 콘솔에 등록할 식별자는 Android 패키지명과 iOS 번들 ID 모두 `com.ssafya109.yeogirok`이다. Android에서는 **실행하는 빌드의 서명 키 해시**도 등록한다. 개발 빌드·배포 빌드의 서명 키가 다르면 각각 등록해야 한다. 카카오맵 사용 설정도 확인한다.

## Android 실행

Android Studio의 Android SDK, 프로젝트의 Expo/React Native가 요구하는 JDK, 에뮬레이터 또는 USB 디버깅을 허용한 Android 기기가 필요하다.

```powershell
cd frontend
pnpm exec expo run:android
```

이 명령은 네이티브 프로젝트 생성·빌드·앱 설치·개발 서버 실행을 진행한다. 네이티브 설정과 모듈은 config plugin으로 관리하며 생성된 `android/`를 직접 수정하지 않는다.

이미 설치한 개발 빌드에서 JS만 수정했다면 다음 명령으로 시작할 수 있다.

```powershell
pnpm exec expo start --dev-client
```

테스트 화면에 표시되는 **Android 키 해시**를 카카오 콘솔에 등록한다. SDK 초기화에 실패해 해시가 표시되지 않으면 Android 빌드 폴더에서 `gradlew.bat signingReport`로 실제 서명 파일·별칭을 확인한 뒤 해당 인증서의 SHA-1을 Base64로 변환한다. `signingReport`의 콜론으로 구분된 SHA-1 문자열 자체는 카카오 키 해시가 아니다.

## iOS 실행

macOS·Xcode와 iOS 개발 환경이 필요하다. Windows에서는 로컬 iOS 빌드를 할 수 없다.

```sh
cd frontend
pnpm exec expo run:ios
```

## 확인 기준

1. 실제 서울시청 주변 도로·건물 타일이 표시된다.
2. 지도 드래그와 두 손가락 확대·축소가 동작한다.
3. 뒤로 갔다 다시 열어도 지도가 표시된다.

`SDK 초기화 완료`는 초기화 함수가 반환했다는 뜻이다. 설치한 모듈은 지도 인증·타일 표시 성공 이벤트를 JavaScript로 제공하지 않으므로, 이 문구만으로 지도 연결 성공을 판단하지 않는다. 빈 지도나 인증 오류가 발생하면 카카오 앱 키·플랫폼 등록·서명 키 해시·네트워크와 Android Logcat/iOS 콘솔을 확인한다.

웹과 Expo Go에서는 네이티브 모듈을 불러오지 않고 개발 빌드 안내를 표시한다. 키가 비어 있으면 설정 안내를 표시한다. 초기화 중에는 진행 표시, 초기화 실패 시에는 재시도 버튼을 표시한다. 현재 위치 권한이나 서버 API는 사용하지 않는다.

## 검증 범위

연결한 네이티브 모듈은 `@react-native-kakao/map@2.2.7`, 같은 peer 버전의 `@react-native-kakao/core@2.2.7`, `expo-dev-client`다. `app.config.ts`에서 개발 클라이언트와 Android Kakao Maven 저장소(`expo-build-properties`)를 설정하며, 키가 입력된 경우 Kakao core config plugin도 적용한다. 스타일은 기존 토큰에 NativeWind를 연결했다. 네이티브 모듈·config plugin이 추가되었으므로 **개발 빌드 재생성이 필요하다**.

타입 검사·린트·토큰 검사·JavaScript 번들 생성은 실제 네이티브 빌드 및 지도 인증 검증과 별개다. 실제 키 입력과 Android/iOS 빌드 실행 전에는 지도 표시 성공으로 기록하지 않는다. React Native 0.88/Expo 58과 카카오맵 모듈의 네이티브 빌드 호환성도 실기기 실행 전에는 미확인이다.

Pencil: 대응 시안 없음, `design/DESIGN.md` 기준 구현(미대조). 신규 디자인 토큰 없음.
