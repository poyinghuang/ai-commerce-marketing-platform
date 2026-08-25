## Stage and scope

- Stage／Milestone：
- Approved specification：
- Base Commit：
- Head Commit：

## Summary

<!-- 說明實際完成內容與目的。 -->

## Included

- <!-- Included item -->

## Out of scope

- <!-- Out-of-scope item -->

## Migration and data impact

- New Migration：`None`／`Vx__...`
- Previously merged Migration modified：`No`
- Destructive schema or data change：`No`
- Rollback／forward recovery：

## Contract changes

- API：
- Frontend／BFF：
- Backward compatibility：

## Security and external impact

- Authentication／RBAC／Tenant change：`No`
- Secret／Credential／Production Access change：`No`
- External service or cost impact：`None`
- Security boundary notes：

## Local verification

- [ ] `git diff --check`
- [ ] Backend tests
- [ ] Migration tests／Hibernate validation
- [ ] Frontend lint／typecheck／tests／production build
- [ ] Docker Compose config／cold start／smoke
- [ ] Playwright E2E, when required by the Stage
- [ ] Gitleaks
- [ ] Dependency audit
- [ ] actionlint

Commands and results：

```text

```

## Known limitations and warnings

- <!-- Limitation or warning; write None when empty -->

## Remote CI

- Push Run：Pending
- Pull Request Run：Pending
- Required Jobs：`quality-and-compose`、`secret-scan`

## Manager Gate

> 本節由 Manager Reviewer 更新。PR 作者不得自行標記 Manager Decision 為 APPROVE。

- Manager Review：Not started
- Manager Decision：Pending
- Human Review Required：No
- Approved Commit：Pending
- Approved CI Run：Pending
- Merge：Not allowed until Manager Decision is APPROVE

## Checklist

- [ ] 實際 Diff 符合核准規格與排除項目。
- [ ] 沒有修改已合併 Migration。
- [ ] 沒有提交 Secret、`.env` 或 build artifacts。
- [ ] 完成報告與實際結果一致。
- [ ] Stage 文件已更新。
- [ ] 若此 PR 關閉一個 Stage／Sub-stage，已依證據產生 Voice Summary；否則標示不適用。
- [ ] PR 保持 Draft，直到 CI 與 Manager Review 完成。
