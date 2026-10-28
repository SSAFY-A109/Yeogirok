# 브랜치 규칙

## 이름 형식

```
<part>/<type>/<issue_no>-<작업내용>
```

| 항목 | 규칙 |
| --- | --- |
| `part` | `frontend` \| `backend` \| `ai` \| `infra` \| `common` |
| `type` | 커밋 Type을 소문자로 쓴다 (`feat`, `fix`, `refactor` 등). 파트별 추가 type은 각 파트에서 정한다. |
| `issue_no` | Jira 이슈 키. 예: `S15P31A109-4` |
| `작업내용` | 짧은 영문 kebab-case. 예: `kakao-login` |

예시:

```
frontend/feat/S15P31A109-4-kakao-login
backend/fix/S15P31A109-12-token-refresh
infra/chore/S15P31A109-20-docker-compose
common/docs/S15P31A109-7-commit-convention
```

## 브랜치 구조

```
main
├─ frontend/dev
│   └─ frontend/<type>/<issue_no>-<작업내용>
├─ backend/dev
│   └─ backend/<type>/<issue_no>-<작업내용>
├─ ai/dev
│   └─ ai/<type>/<issue_no>-<작업내용>
├─ infra/dev
│   └─ infra/<type>/<issue_no>-<작업내용>
└─ common/docs/<issue_no>-<작업내용>
```

## 병합 흐름

```
기능 브랜치 → <part>/dev → main
```

- 기능 브랜치는 해당 파트의 `<part>/dev`에서 분기하고, `<part>/dev`로 PR을 보낸다.
- `<part>/dev`가 안정되면 `main`으로 PR을 보낸다.
- `common` 브랜치(공통 문서)는 dev가 없다. `main`에서 분기해 `main`으로 바로 PR을 보낸다.
- `main`과 `<part>/dev`에는 직접 커밋하거나 push하지 않는다. 항상 PR로 병합한다.
- PR 본문은 `.github/pull_request_template.md`를 따른다.
