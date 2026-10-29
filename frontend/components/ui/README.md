# 공용 UI

[Figma 원본 컴포넌트 프레임](https://www.figma.com/design/t6whqCoBlVvwqBs8aCCuvG?node-id=488-411)의 15종을 React Native + NativeWind로 구현했다. 각 파일에서 직접 import한다. 값과 예외는 `design/DESIGN.md`를 따른다.

| Figma | 파일·컴포넌트 | 주요 props |
| --- | --- | --- |
| Action / Button | `button.tsx` · `Button` | `variant="default\|secondary\|outline"`, `size="default\|lg"`, `disabled`, `loading`, `loadingLabel` |
| Search / TextField | `text-field.tsx` · `TextField` (`Input` 별칭) | RN TextInput props, `error`, `containerClassName` |
| Search / Textarea | `textarea.tsx` · `Textarea` | Input props, 기본 높이 144 |
| Search / SearchField | `search-field.tsx` · `SearchField` | TextInput props, `error`, 검색 아이콘·높이 44 |
| Selection / Dropdown | `dropdown.tsx` · `Dropdown` | `options`, `value`, `onValueChange`, `disabled`, `accessibilityLabel` |
| Selection / FilterChip | `filter-chip.tsx` · `FilterChip` | `size="sm\|md"`, `selected`, `onSelectedChange`, `disabled` |
| Selection / Checkbox | `checkbox.tsx` · `Checkbox` | `checked`, `onCheckedChange`, `disabled` |
| Selection / Radio | `radio-group.tsx` · `RadioGroup`, `RadioGroupItem` | 그룹 `value`, `onValueChange` / 항목 `value` |
| Selection / Switch | `switch.tsx` · `Switch` | `checked`, `onCheckedChange`, `disabled` |
| Feedback / Badge | `badge.tsx` · `Badge` | `tone="neutral\|brand\|coral\|success"` |
| Feedback / PageLoading | `page-loading.tsx` · `PageLoading` | `type="skeleton\|spinner"`, `title`, `description` |
| Map / FootprintMarker | `footprint-marker.tsx` · `FootprintMarker` | `state="default\|selected\|cluster"`, `count`, Pressable props |
| Auth / KakaoLoginButton | `social-login-button.tsx` · `KakaoLoginButton` | Button의 `onPress`, `disabled`, `loading` 등 |
| Auth / GoogleLoginButton | `social-login-button.tsx` · `GoogleLoginButton` | Button의 `onPress`, `disabled`, `loading` 등 |
| Navigation / TabBar | `tab-bar.tsx` · `TabBar` | `items`, `value`, `onValueChange`; `tabBarIcons` 제공 |

`Text`, `Icon`, `Skeleton`, `Spinner`, RNR Select 조합도 제공한다. 버튼·배지의 글자는 `Text`로 감싸면 크기와 색을 자동으로 상속한다.

```tsx
import { Button } from "@/components/ui/button";
import { Dropdown } from "@/components/ui/dropdown";
import { Text } from "@/components/ui/text";

<Button size="lg" loading={isPending} loadingLabel="담는 중" onPress={handleAdd}>
  <Text>여행방에 담기</Text>
</Button>

<Dropdown
  accessibilityLabel="코스 정렬"
  options={[{ value: "latest", label: "최신순" }, { value: "popular", label: "인기순" }]}
  value={sort}
  onValueChange={setSort}
/>
```

입력·선택 요소에는 화면의 의미에 맞는 `accessibilityLabel`을 전달한다. 작은 컨트롤은 `hitSlop`을 포함하므로 부모가 터치 영역을 자르지 않도록 배치하고 요소 사이 간격을 확보한다. 입력의 `className`은 컨트롤에, `containerClassName`은 오류 문구를 포함한 래퍼에 적용된다. `ref`는 입력으로 전달된다.

Dropdown의 바깥 누르기·뒤로가기·웹 키보드 이동은 RNR Select가 처리한다. 네이티브 메뉴를 위한 `PortalHost`는 `app/_layout.tsx`에 있다. TabBar는 SafeAreaProvider 안에서 사용하며, OS 홈 인디케이터를 직접 그리지 않고 실제 하단 안전 영역을 사용한다. 탭 목록과 라우팅, 검색·인증·저장 같은 업무 로직은 호출 화면에서 연결한다.

Figma 로고·아이콘·마커는 `assets/ui/`에 로컬로 보관했다. 소셜 로그인 색은 승인된 `auth-kakao`, `auth-google-border`, `auth-google-foreground` 토큰이다. Brand·Success 배지는 기존 사용자 결정에 따라 `DESIGN.md`의 쇼케이스 색을 쓴다. 선택된 FilterChip은 현재 원본처럼 체크 아이콘 없이 테두리·배경으로 표시한다.

로딩 스켈레톤의 너비는 `w-skeleton-heading`(116), `w-skeleton-line`(168) 토큰을 쓴다. Pencil 대응 노드는 없어 **Pencil 미대조**다. `pnpm ui:check`로 타이포·크기 클래스 충돌과 에셋 파일을 검사한다. SVG 의존성을 추가했으므로 기존 개발 클라이언트는 네이티브 빌드 재생성이 필요하다.
