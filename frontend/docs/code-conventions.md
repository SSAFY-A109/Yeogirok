# 코드 컨벤션

Expo의 React·React Hooks·TypeScript 권장 ESLint 설정을 기반으로 한다.
프로젝트의 추가 규칙은 `eslint.config.js`에서 오류로 검사한다.

- 들여쓰기는 공백 2칸, 문자열은 큰따옴표, 문장 끝은 세미콜론을 사용한다.
- `if`, `for`, `while` 등의 본문은 한 문장이더라도 중괄호와 줄바꿈을 사용한다.
- 함수·화살표 함수·메서드도 중괄호 안에서 줄바꿈한다. 화살표 함수의 반환값은 `return`으로 명시한다.
- 제어문과 함수·메서드 앞뒤에는 인접한 코드와 빈 줄 한 줄을 둔다. 함수 선언, 함수 할당, 콜백을 받는 호출문, 객체·클래스 메서드에도 적용한다.
- 파일이나 블록의 시작·끝에는 별도의 빈 줄을 넣지 않는다. 연속된 빈 줄은 한 줄까지만 허용한다.
- 함수에 붙는 설명 주석은 함수와 함께 둔다.
- `else`, `catch`, `finally`는 앞 블록의 닫는 중괄호와 같은 줄에 쓴다.
- React Hooks 호출 순서와 의존성, JSX의 key·중복 props를 검사한다.
- 재할당이 없는 변수는 `const`, 동등 비교는 `===`/`!==`를 사용한다. `value == null`처럼 null과 undefined를 함께 검사하는 패턴은 허용한다.

```ts
const ready = checkReady();

if (ready) {
  start();
} else {
  stop();
}

try {
  save();
} catch (error) {
  handleError(error);
}

const getName = (user: User) => {
  return user.name;
};

showName(getName(user));
```

검사는 `pnpm lint`, 자동 수정은 `pnpm lint:fix`, 규칙의 자동 수정 검증은 `pnpm lint:check`로 실행한다.

빌드 결과(`.expo/`, `dist/`, `android/`, `ios/`), MSW 생성 파일, 디자인 토큰 생성 파일(`lib/theme.ts`, `tailwind.config.js`)은 직접 수정하지 않도록 ESLint 대상에서 제외한다.
