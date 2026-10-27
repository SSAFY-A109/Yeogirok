---
paths:
  - "app/**"
  - "components/**"
  - "features/**"
  - "global.css"
  - "tailwind.config.js"
---

# 스타일링, 디자인 시스템 및 접근성 (React Native)

## NativeWind와 디자인 토큰

- 스타일링은 NativeWind 유틸리티(`className`)와 `tailwind.config.js` 테마 토큰만 사용한다. 테마는 `design/tokens.json`에서 생성되므로 `tailwind.config.js`를 직접 수정하지 않는다.
- 인라인 `style` prop은 애니메이션 값, 측정 기반 크기처럼 NativeWind로 표현할 수 없는 동적 값에만 사용한다.
- `StyleSheet.create` 기반 스타일을 새로 작성하지 않는다. 기존 `StyleSheet` 코드를 수정할 때는 NativeWind로의 전환을 검토한다.
- 디자인 톤, 타이포그래피, 간격 기준은 `design/DESIGN.md`를 따른다. 토큰 사용 규칙은 `70-design-tokens.md`에 있다.
- 색상, 간격, 크기 값을 임의의 hex 값이나 arbitrary value로 반복하지 않고 디자인 토큰을 사용한다.
- 필요한 디자인 값이 토큰에 없다면 기존 토큰으로 표현할 수 있는지 먼저 검토하고, 반복 사용되는 값은 테마 토큰으로 추가한다.
- 동일한 class 조합이 반복되더라도 단순히 코드를 줄이기 위한 컴포넌트 추출은 피하고, 의미 있는 UI 단위일 때만 추출한다.
- RN에는 hover가 없으므로 pressed, disabled, loading 상태를 함께 검토한다.
- 다양한 화면 크기에서 레이아웃이 유지되는지 확인하고, 노치·상태 바 영역은 `react-native-safe-area-context`로 처리한다.

## 컴포넌트 선택

- **탭 화면에는 제목 헤더를 두지 않는다**(KeyFin 때 사용자 결정 2026-09-18 — 여기록 시안이 확정되면 다시 확인 `[확인]`). 탭바가 현재 위치를 알려 주므로 제목 헤더는 중복이다. 대신 `components/ui/screen.tsx` 의 `useHeaderlessTop()`(상태바 + 20)으로 상단 여백만 준다. 메인 페이지의 검색 필드·My 진입, 지도 탭의 카테고리 칩처럼 기능이 있는 상단 영역은 제목 헤더가 아니므로 둘 수 있다.
- 뒤로가기가 있는 화면(상세·온보딩·설정)의 상단 헤더는 `@/components/ui/screen-header`의 `ScreenHeader`(`<` + `text-h1` 제목 + 오른쪽 액션)만 쓴다. 헤더 행을 화면마다 새로 만들거나 제목 크기를 `text-h2`·`text-h3`로 낮추지 않는다. 그 헤더가 돌아가는 유일한 길이므로 없애지 않는다.
- 터치 상호작용에는 `Pressable`을 사용하고, `Text`에 `onPress`를 직접 붙이는 방식은 본문 내 링크처럼 필요한 경우로 제한한다.
- 항목 수가 많거나 가변적인 목록은 `ScrollView` + `map`이 아니라 `FlatList` 또는 `SectionList`를 사용한다.
- 키보드가 입력 요소를 가리지 않도록 `KeyboardAvoidingView` 등으로 처리한다.
- 이미지는 원본 크기를 그대로 로드하지 않고 표시 크기에 맞는 리소스를 사용한다.

## 접근성

- 터치 타깃은 최소 44x44pt, 터치 요소 사이 간격은 8pt 이상을 확보한다(Figma 디자인 시스템 사용 규칙 05). 시각적 크기를 키울 수 없으면(필터 칩 30·37pt 등) `hitSlop`을 지정한다.
- 상호작용 요소에는 `accessibilityRole`을 지정하고, 아이콘 전용 버튼에는 `accessibilityLabel`을 반드시 제공한다.
- 선택·비활성화 등 요소의 상태는 `accessibilityState`로 전달한다.
- 의미 있는 이미지에는 `accessibilityLabel`을 제공하고, 장식용 요소는 `accessible={false}` 등으로 보조 기술에서 제외한다.
- 오류를 색상만으로 전달하지 않고 텍스트 또는 아이콘과 함께 전달한다.
- 비동기 작업 결과처럼 화면이 동적으로 변경되는 경우 `accessibilityLiveRegion`(Android)이나 `AccessibilityInfo.announceForAccessibility` 적용을 검토한다.
