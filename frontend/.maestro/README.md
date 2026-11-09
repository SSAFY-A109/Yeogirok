# Maestro E2E

규칙은 `.agents/rules/60-dependencies-tests.md`의 "E2E (Maestro)" 절에 있다. 이 문서는 실행 방법과 현재 flow 목록만 둔다.

## 실행

```bash
# Maestro CLI는 npm 패키지가 아니다 — 각자 설치한다 (https://maestro.dev)
MAESTRO_APP_ID=<번들 id> pnpm e2e                                         # 전체 flow
MAESTRO_APP_ID=<번들 id> maestro test .maestro/flows/00-app-launch.yaml   # 하나만
```

- 대상 앱은 개발 빌드나 E2E용 빌드다. Expo Go로는 네이티브 모듈 화면을 확인할 수 없다(규칙 85).
- 서버 없이 같은 결과를 내도록 `__DEV__ === true`인 개발 빌드를 사용한다. 전체 API는 MSW 목으로 처리한다. 배포 빌드는 실제 백엔드에 연결하므로 서버 없는 E2E에 쓰지 않는다.
- AR 카메라 화면은 자동화하지 않는다. 권한 거부·미지원 폴백만 다룬다.

## 폴더

| 위치 | 내용 |
| --- | --- |
| `flows/<NN>-<흐름>.yaml` | P0 시연 흐름당 1개. 번호는 시연 순서 |
| `subflows/` | 여러 flow가 쓰는 단계(`runFlow`로 호출) |

## flow 목록

| 파일 | 흐름 | 기능 ID | 상태 |
| --- | --- | --- | --- |
| `00-app-launch.yaml` | 앱 실행 → 로그인 화면·소셜 버튼 → MSW 로그인 → 메인·탭 | COM-01 | 작성 완료. 사용자 요청으로 브라우저·실기기 미실행 |
| `20-ar-tip-entry.yaml` | 하단 AR 팁 탭 → 최초 권한 안내 → 카메라 거부 안내 → 닫기 → 카메라 재진입·다른 탭 | TIP-01 | 2026-11-06 변경. 위치 허용·카메라 거부를 분리하고 이전 소개·등록 상태 화면이 나오지 않는지 확인. Maestro CLI 미설치로 flow 미실행. 연결된 Android에서는 권한 허용 상태의 탭 → AR 카메라 직접 진입을 확인 |
| `30-my-ui.yaml` | My → 팁 수정 상태 확인·삭제 취소/확인 → 방명록 수정 → 로그아웃 취소 → 탈퇴 동의·취소 | MY-01·02·03·05·06 | MSW 개발 빌드용. API 연동 후 Maestro 기기 실행은 미확인 |

## 결정 필요

- **소셜 로그인 자동화**: 구글·카카오 로그인은 시스템 브라우저를 거치므로 Maestro로 끝까지 자동화하기 어렵다. 개발·E2E 빌드에만 있는 테스트 로그인 진입점을 둘지는 결정이 필요하다. 이 진입점은 MSW 목 토큰만 쓰고 출시 빌드에서는 제외한다.
