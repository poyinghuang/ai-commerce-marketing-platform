# Stage 07C-1 Implementation Completion Report

## Delivery identity

- Branch: `codex/stage-07c-1-fake-google-adapter`
- Base: `da01b13cf796913576dbd95591fb2965abff7ddd` (PR [#78](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/78) squash merge)
- Scope: LOCAL/TEST `FAKE_GOOGLE` adapter behind existing platform ports; additive V17 provider-key allow-list; second LOCAL/TEST account row; no REST, no `/platforms/google`, no Google Ads SDK
- Specification: PR [#76](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/76); 7A PR [#77](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/77) at `eb2618d`; 7B PR [#78](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/78) at `da01b13`; post-merge main CI Run `32944155884` passed
- Status: Runtime Draft PR not yet numbered; fill after `gh pr create`
- Manager Decision: Not started

## Implemented scope

- `DeterministicFakeGooglePlatformAdapter` under `@Profile("(local | test) & !production")` and `platform.adapter=fake`.
- Meta write/read adapters remain `@Primary` so Stage 4D and existing FAKE Meta paths keep a unique default bean. Application selects write/reconcile ports by `account.providerKey()`.
- Java `ProviderKey` / `PlatformProvider` gain `FAKE_GOOGLE`. `NormalizedPlatformEvidence` schema version stays `1` and accepts `FAKE` or `FAKE_GOOGLE`.
- Additive `V17__allow_fake_google_provider_key.sql` expands `platform_accounts.provider_key` and attempt evidence `providerKey` to `IN ('FAKE','FAKE_GOOGLE')`. V1–V16 stay byte-identical.
- `Stage7C1AccountInitializer` inserts a second LOCAL/TEST account (`provider_key=FAKE_GOOGLE`, distinct `account_reference`, `TWD` / `Asia/Taipei`) when V17 is applied. V12-targeted tests skip the insert.
- No `/platforms/google`, no Frontend page, no Compose rewrite, no Google Ads API/SDK/MCC/OAuth/spend.

## Boundaries preserved

Domain campaign/ad-set/ad types gain no Google-only fields. `delivery.domain` and `delivery.application` do not import the Google fake class. Stage 05 `GET /api/dashboard` JSON is unchanged. Stage 06 remains suggestion-only. `/platforms/meta` and 4E Playwright stay on the default CI path. Live ads stay locked.

## Local verification

Recorded on this runtime branch before exact-head CI. Unrun checks are not Passed.

| Check | Result |
| --- | --- |
| Focused Backend 7C-1 tests | Passed — 25 tests, 0 failures/errors/skips (`DeterministicFakeGooglePlatformAdapterTest`, `GooglePlatformAdapterProfileTest`, `DeliveryApplicationPortIsolationTest`, `FakeGooglePlatformIntegrationTest`, `MigrationCompatibilityTest`) |
| Meta 4A regression `PlatformOperationIntegrationTest` | Passed — 16 tests |
| Hibernate `PersistenceFoundationIntegrationTest` V17 validate | Passed |
| Full Backend `mvnw -B test` | Not run locally; executed on CI |
| Frontend / Playwright / Compose / Smoke / actionlint / Gitleaks | Not run locally; executed on CI |
| `git diff --check` | Passed |
| `git diff --exit-code origin/main --` V1–V16 SQL | Passed — empty; only additive `V17__allow_fake_google_provider_key.sql` |

## Exact-head CI

Not yet recorded. Fill after Push and Pull Request `quality-and-compose` plus `secret-scan` pass on the reviewed Head.

## Next gate

7C-2 gated `/platforms/google` stays locked until this 7C-1 runtime is Manager `APPROVE`, squash-merged, and post-merge `main` CI passes.
