# Stage 4C runtime — Review Agent findings

- Reviewer：Review Agent (code / security / architecture consistency)
- Date：2026-08-20
- Compared Head：`887c2c56fba26ba7b7e69842a7a430f4fa07834c` vs spec `docs/stages/stage-04c-ad-creative-publication.md`
- PR：#64
- Gate Decision：none (Project Manager owns the Gate)

No escalation-policy trigger. V1–V13 untouched. SQL claim does not require an approved 4C account for every AD submit. BFF still blocks `outcomeEvidence` while allowing `evidenceEligible`. Feature gates remain FAKE LOCAL/TEST. Do not merge. Do not start Stage 4D.

QA independently recorded exact-head `quality-and-compose` Failed on `887c2c56` (seven Flyway lists ending at V13). That vector is **4C-RT-001**, already patched on this branch as `782064e`. Playwright `platform-stage4c.spec.ts` also failed: `getByRole('button', { name: 'Preview paused Ad' })` matches **Preview paused Ad Set**.

| ID | Severity | Finding | Required fix／test |
| --- | --- | --- | --- |
| R-4C-01 | BLOCKING | Required acceptance matrix is not executable (races, V13→V14 seven statuses, Audit, snapshots, fake outcome matrix). Overlaps Manager `4C-RT-002`–`005`. | Same executable cases as the Manager report. |
| R-4C-02 | BLOCKING | V14 `verify_platform_ad_dispatch_evidence` does not encode create `0→1` / fingerprint edge or correlated in-transaction operation; uses `ORDER BY updated_at DESC LIMIT 1`. | Named `ct_platform_ad_dispatch_result` predicates + direct-SQL false success / ID substitution / wrong correlation. |
| R-4C-03 | BLOCKING | BFF uses a denylist, not the spec safe-key **allowlist**. Extra keys that miss the regex are forwarded. | Allowlist per public DTO; tests for extra/missing/wrong-type/`operation`/`replay`/`outcomeEvidence`. |
| R-4C-04 | BLOCKING | Query rejection on 4C routes reports `field=body` / Invalid request body. Spec: `query` / Query parameters are not allowed. | MockMvc `?account=x` → 400 `PLATFORM_REQUEST_INVALID` `fieldErrors[0].field=query`. |
| R-4C-05 | MAJOR | SQL `ct_platform_ad_submit_claim_evidence` collapses parent version, parent state, and evidence; HTTP maps all to evidence-invalid. | Distinct constraint/SQLSTATE mapping: version → 412, parent state → 409 parent-invalid, checksum → evidence. |
| R-4C-06 | MAJOR | `Milestone4BLedgerIntegrationTest.insertUnapprovedOperation` still inserts legacy `CREATE_AD` after V14, so `23514` may come from the new-shape trigger instead of the ledger guard. | Post-V14 valid `CREATE_AD` asserting the **ledger** constraint; separate legacy-insert test. |
| R-4C-07 | MAJOR | Claim does not `FOR UPDATE` the operation row first; lock order can deadlock vs a finalizer. | Spec lock order + concurrent claim barrier, one winner, no deadlock. |
| R-4C-08 | MAJOR | Insert trigger only checks `request_sha256` is 64 hex, not canonical payload coherence. Same as Manager `4C-RT-007`. | Mismatched hash rolls back. |
| R-4C-09 | MINOR | Error-mapping test covers only a subset of the public matrix. | Parameterized row per spec source/route family. |
| R-4C-10 | NOTE | Claim Audit order follows Stage 4A (`ATTEMPT_CREATED` then `OPERATION_TRANSITIONED`), not 4C prose. Treat 4A executable order as SoR unless product restates. | No code change unless spec is amended. |
| QA-PW-01 | BLOCKING | Playwright locator `Preview paused Ad` is a prefix of `Preview paused Ad Set`. | Exact name match (`exact: true` or `/^Preview paused Ad$/`). |
