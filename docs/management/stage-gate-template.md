# Stage Gate Review Report

> 每次正式 Manager Review 複製本模板。不得刪除未執行項目；請標記 `Passed`、`Failed`、`Not verified` 或 `Not applicable`。

## Review identity

- Stage／Milestone：
- Review date：
- Reviewer：
- Repository：
- Branch：
- Base Commit：
- Head Commit：
- Pull Request：

## Status before review

- Implementation：
- Local Verification：
- Remote CI：
- Human Review Required：
- Merge：

## Scope reviewed

- Approved scope：
- Explicit out of scope：
- Files reviewed：
- Forbidden or unexpected files：
- Completion report compared：

## Architecture and contracts

- Architecture documents reviewed：
- Migration reviewed：
- Domain／Transaction／Audit boundary：
- API contract changes：
- Frontend／BFF contract changes：
- Backward compatibility：
- Rollback／forward recovery：

## Impact

- Security impact：
- Data impact：
- Production impact：
- External service／cost impact：

## Verification executed

| Verification | Command／Run | Result | Evidence／Notes |
| --- | --- | --- | --- |
| Git status | `git status --short` |  |  |
| Diff check | `git diff --check` |  |  |
| Commit history | `git log --oneline --decorate -10` |  |  |
| Backend tests |  |  |  |
| Migration tests |  |  |  |
| Hibernate validation |  |  |  |
| Frontend lint |  |  |  |
| Frontend typecheck |  |  |  |
| Frontend tests |  |  |  |
| Production build |  |  |  |
| Docker Compose config |  |  |  |
| Docker Compose cold start |  |  |  |
| Smoke tests |  |  |  |
| Playwright E2E |  |  |  |
| Gitleaks |  |  |  |
| Dependency audit |  |  |  |
| actionlint |  |  |  |

## Remote CI

- Workflow／Run ID：
- Head SHA matches：
- `quality-and-compose`：
- `secret-scan`：
- Required steps skipped：
- Warnings／annotations：

## Findings

| ID | Severity | File／Evidence | Finding | Required fix／test |
| --- | --- | --- | --- | --- |
|  |  |  |  |  |

若無 Finding，明確寫 `None`。不要刪除本節。

## Known limitations

- <!-- Limitation or warning; write None when empty -->

## Stage Gate decision

- Decision：`APPROVE`／`REQUEST_CHANGES`／`ESCALATE_TO_HUMAN`
- Decision rationale：
- Required next action：
- Human approval required：`Yes`／`No`
- Human approval reason／evidence：

## Approval record

只在 Decision 為 `APPROVE` 時填寫：

- Manager Review：Passed
- Manager Decision：APPROVE
- Approved Commit：
- Approved CI Run：
- Commands actually executed：
- Merge allowed：Yes
- Next Stage allowed：After merge and post-merge CI; Manager starts the next authorized stage immediately when no escalation applies
