---
paths:
  - "app/**"
  - "components/**"
  - "features/**"
  - "hooks/**"
---

# React 및 상태 관리

## 상태 분류

- 상태를 추가하기 전에 서버 상태, 내비게이션(라우트 파라미터) 상태, 폼 상태, 로컬 UI 상태, 기존 값에서 계산 가능한 파생 상태 중 어디에 해당하는지 판단한다.
- props나 기존 상태에서 계산할 수 있는 값은 별도 state로 저장하지 말고 렌더링 중 계산한다.
- 동일한 데이터를 여러 state에 중복 저장하지 않고 단일 진실 공급원을 유지한다.
- 화면 간에 공유되거나 딥링크로 복원되어야 하는 검색어, 정렬, 필터 같은 값은 Expo Router의 라우트·검색 파라미터(`useLocalSearchParams`)로 관리한다.

## 서버 상태와 전역 상태

- 서버 상태 조회는 TanStack Query의 `useQuery` 또는 `useSuspenseQuery`로 관리한다.
- 서버 상태 생성, 수정, 삭제는 `useMutation`으로 관리한다.
- Mutation 요청 상태는 `isPending`, 오류 상태는 `error` 등 Mutation이 제공하는 값을 사용한다.
- Query 캐시 기반 낙관적 업데이트는 `useMutation`의 `onMutate`, `queryClient.setQueryData`, 오류 시 롤백, 완료 후 무효화 방식으로 구현한다.
- `useOptimistic`은 Query 캐시의 원본 상태와 분리된 일시적인 UI 표현이 필요한 경우에 사용한다.
- 전역 클라이언트 상태는 Zustand로 관리하되, 서버 데이터를 Zustand 스토어에 복제해 보관하지 않는다.

## Hook 선택

- Hook이 최신이라는 이유만으로 사용하지 말고, 해결하려는 문제의 의미와 생명주기에 맞는 Hook을 선택한다.
- 상태를 `useState`로 직접 관리하기 전에 같은 문제를 더 적절하게 해결하는 React Hook이 있는지 검토한다.
- `useEffect`는 외부 시스템과의 동기화에만 사용한다.
- 파생 값 계산, 사용자 이벤트 처리, 서버 데이터 요청을 위해 `useEffect`를 사용하지 않는다.
- 사용자 동작으로 발생하는 로직은 가능한 한 이벤트 핸들러에서 처리한다.
- `useTransition`은 긴급하지 않은 렌더링 업데이트를 비차단 방식으로 처리할 때만 사용한다.
- `useDeferredValue`는 입력 자체가 아니라 입력에 따라 갱신되는 비용이 큰 UI를 지연할 때 검토한다.
- `use()`는 Suspense 기반 리소스 소비가 기존 아키텍처에 정의된 경우에만 사용하고, TanStack Query를 대신한 임의의 데이터 요청에 사용하지 않는다.
- `useMemo`, `useCallback`, `memo`는 습관적으로 추가하지 않고 연산 비용이나 참조 동일성이 실제로 필요한 경우에만 사용한다.
