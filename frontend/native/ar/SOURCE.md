# AR 네이티브 소스

- 인수인계 저장소: https://github.com/seoboyoung18/yeogirok-ar-poc
- 브랜치: `poc/arcore-basic`
- 기준 커밋: `9b1cf83d2d27eba02992d8c4ebd443cc2bd7f4f9`
- 가져온 범위: `android/app/src/main/java/com/yeogirokarpoc/geo`, Google ARCore Java 배경 렌더러, 셰이더와 고지문.
- 기존 팀 패키지 이름은 브리지 내부에 유지하며 앱 ID는 `com.ssafya109.yeogirok`다.
- Google 샘플의 Apache-2.0 헤더와 `android/assets/ARCORE_SAMPLE_LICENSE.txt`를 보존했다.
- Viro 및 Kakao 네이티브 지도 SDK는 도입하지 않았다. 지도는 Android WebView에서 카카오 JavaScript SDK를 사용한다.
- `GeoArActivity`: 탐색 진입, 팁 선택, + 배치 시작, 전체 카메라 UI, 기존 여기록 마커 사용.
- `GeoValidationModule`: 탐색 Activity 결과를 RN에 반환하는 `openTipExplorer(selectedId)` 추가.
- `PinProjection`, `PinTapPolicy`, `GeoArActivity`: 앵커에 고정된 48cm 마커(초기 16cm의 3배)의 투영 크기와 터치 영역에 원근감 적용. 작성·복원 경로에서 공통 사용.
- `CloudNavigation`, `GeoMapActivity`, `KakaoWebMap`: WebView 미니맵과 탐색 위치 선택.
- `ArTheme`: prebuild에서 프로젝트 토큰으로 생성한 `ar-theme.json`을 읽는다.
- `DepthOcclusion` / `DepthOcclusionPolicy`: 기존 ARCore Depth 이미지로 벽 뒤 앵커를 감지해 기본/주변 마커와 터치 대상을 숨긴다. 깊이 미지원/누락 시 기존 표시를 유지한다. 새 SDK나 config plugin 변경은 없다.
- `plugins/with-ar-tip.cjs`가 Gradle·Manifest·RN 패키지 등록과 별도 프로세스 초기화 경계를 관리한다. 생성된 `android/`를 수동 편집하지 않는다.

## 2026-11-02 디자인 반영

- 디자인: https://www.figma.com/design/gzYo8vG6h2LrKGOa22UCFP/Untitled?node-id=2-4438
- W17 `2:4439`, W17-A `4:2630`, W18 `2:4493`, W19 `2:4525`, W19-1 `2:4561`의 핀/조준점/아이콘을 `android/assets/figma/`에 보관한다. 카메라/지도 예시 이미지는 제품 데이터로 사용하지 않는다.
- `ArSvg`는 내려받은 SVG 경로를 그리며 `GeoArActivity`와 `TipDetailSheet`가 사용한다. `NearbyTipAnchors`가 나머지 로컬 앵커의 복원/취소/해제를 담당한다.
- Noto Sans KR 원본: https://github.com/google/fonts/tree/main/ofl/notosanskr . 가변 폰트 `NotoSansKR.ttf`와 SIL Open Font License `NotoSansKR-OFL.txt`를 함께 보존한다. 네이티브는 명시적 가중치로 로드하고 RN도 같은 파일을 사용한다.
- `TipExperienceModule`은 시스템 사진 선택/촬영 결과를 내부 JPEG로 가져온다. 기존 config plugin에 좁은 cache FileProvider 설정을 추가했으며 새 라이브러리는 도입하지 않았다.

## 2026-11-02 Keyless 인증과 365일 TTL

- 기존 config plugin에서 ARCore API 키 metadata/placeholder/전달을 제거하고 `com.google.android.gms:play-services-auth:22.0.0`과 공식 인증 ProGuard 규칙을 포함한다. 새 RN 패키지나 config plugin은 추가하지 않는다.
- `GeoValidationModule.getStatus()`의 `arcoreAuthMode`는 Manifest의 인증 방식을 표시한다. Google Cloud OAuth 등록이나 실제 인증 성공을 확인한 값은 아니다. API 키가 남은 빌드는 Keyless 경로에서 차단한다.
- `CloudAnchorExperiment`와 `CloudRecord`는 공통 TTL 365일로 신규 호스팅/예상 만료일을 계산한다. Kotlin·TS 검증은 기존 1일 기록과 새 365일 기록을 함께 허용한다. 기존 앵커의 수명을 변경하거나 데이터를 삭제하지 않는다.
- 근거: https://developers.google.com/ar/develop/authorization?platform=android , https://developers.google.com/android/guides/setup . 실제 Host/Resolve 인증 성공과 공간 정밀도는 현장 검증이 필요하다.
