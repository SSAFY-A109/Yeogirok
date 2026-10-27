---
paths:
  - "app/**"
  - "components/**"
  - "features/**"
  - "hooks/**"
  - "lib/**"
  - "global.css"
  - "tailwind.config.js"
  - "babel.config.js"
  - "metro.config.js"
  - "app.json"
---

# React Native 기본 규칙

- 코드 작성, 수정, 리뷰 전에 아래 "코드 품질 4기준"을 확인한다.
- 명세나 사용자 요청에 없는 기능은 추가하지 않는다.
- 기존 프로젝트의 디렉토리 구조, 컴포넌트 패턴, 명명 규칙을 우선 따른다.
- 스타일링은 NativeWind 유틸리티(`className`)와 `tailwind.config.js` 테마 토큰만 사용한다. 테마는 `design/tokens.json`에서 생성되므로 `tailwind.config.js`를 직접 수정하지 않는다.
- `div`, `button`, `input` 같은 HTML 요소와 `document`, `window`, `localStorage` 같은 웹 전용 API를 사용하지 않는다.
- 컴포넌트와 화면에서 `fetch`, `axios` 또는 도메인 API 함수를 직접 호출하지 않는다.
- 서버 데이터는 TanStack Query로 관리하고, `useEffect`와 `useState`에 요청 결과를 저장하지 않는다.
- 요청 범위와 관계없는 코드 정리나 리팩터링을 함께 수행하지 않는다.

## 코드 품질 4기준

토스 Frontend Fundamentals 의 코드 품질 4기준과 Clean Code 가이드의 원칙을 옮긴 것이다(웹 예시로 된 원본 문서 2종은 2026-09-08 삭제). 코드를 쓰거나 고치기 전에 확인한다.

- **가독성** — 조건 분기가 많아 한눈에 안 읽히면 이름 있는 단위로 쪼갠다. 매직 넘버와 복잡한 조건식에는 의미를 드러내는 이름을 붙인다. 중첩 삼항과 실행 순서에 어긋나는 조건 배치를 피하고 위에서 아래로 읽히게 쓴다.
- **예측 가능성** — 이름과 시그니처만 보고 동작을 예측할 수 있어야 한다. 같은 패턴의 함수는 같은 종류의 값을 돌려주고, 이름에 없는 부작용(로깅·전역 변경)을 숨기지 않는다.
- **응집도** — 함께 고쳐야 할 코드는 함께 둔다(도메인 단위 `features/<domain>/`). 상수는 관련 로직 근처에 두고, 핵심 비즈니스 로직을 블랙박스 훅 뒤로 감추지 않는다.
- **결합도** — 한 함수·컴포넌트는 한 가지 책임만 진다. 같은 코드가 보인다는 이유만의 섣부른 공통화를 하지 않고, props drilling 대신 합성을 쓴다. JSX 반환부의 추상화 수준을 한 단계로 맞춘다.
