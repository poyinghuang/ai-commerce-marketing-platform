# Stage 08C Implementation Completion Report

## Delivery identity

- Branch: `codex/stage-08c-live-insights`
- Base: `14e03d0849d60eb261c64380dbcd3aeff71e5c95` (PR [#84](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/84) squash merge)
- Scope: LOCAL/TEST opt-in `LiveMetaInsightsReadAdapter` behind `platform.stage8.insights.live`; additive V18 `META` provider key; default FAKE 4D; no live writes
- Specification: PR [#82](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/82) squash-merged at `21aca71`; post-merge main CI Run `33090522880` passed
- Prerequisite: 8B runtime PR [#84](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/84) squash-merged at `14e03d0`; post-merge `main` CI Run `33264670377` passed
- Status: Runtime Draft PR [#85](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/85)
- Manager Decision: Pending

## Implemented scope

- Additive `V18__allow_meta_provider_key.sql` expands `platform_accounts.provider_key` and `is_valid_platform_evidence` to `FAKE` / `FAKE_GOOGLE` / `META`. Schema version stays `1`. V1–V17 stay byte-identical.
- Java `ProviderKey` / `PlatformProvider` / `NormalizedPlatformEvidence` accept `META`.
- `LiveMetaInsightsReadAdapter` is `@Primary` on `(local | test) & !production` only when `platform.stage8.insights.live=true`. Token is `META_TEST_ACCESS_TOKEN`; blank fails closed. Origin is `https://graph.facebook.com/v22.0`. Bearer token stays out of the URI.
- `DeterministicFakePlatformReadAdapter` loads only when the live flag is false (default). Fake write adapters stay `@Primary` for create/pause. `PlatformOperationTransactions` still rejects `META` with `PLATFORM_PROVIDER_UNSUPPORTED`.
- Flag-on initializer seeds one META account (TWD / `Asia/Taipei`) plus a pristine paused Campaign / Ad Set. V12 insert protection forbids writing `external_id` on insert; env `META_TEST_*` IDs are not invented and are not applied on insert. Missing external ID keeps 4D refresh `PLATFORM_DELIVERY_NOT_SYNCABLE`.
- `/platforms/meta` 4B/4C writes stay on the existing FAKE account. 4D uses the META account only when the live flag is on. GET 4D stays PostgreSQL-only.
- Compose / `application-local.yml` / `application-test.yml` stay `platform.stage8.insights.live=false`. No `META_TEST_*` in git, Compose, or GitHub Actions.

## Boundaries preserved

CI remains FAKE 4D. Live Google Ads, LINE/TikTok, `META_TEST_DELIVERY`, production credentials, and Decision Engine auto-execute stay locked. Domain and application packages do not import the live adapter or a Meta SDK. 4E Playwright and default 4D tests stay on `live=false`.

## Local verification

Recorded on this runtime branch before exact-head CI. Unrun checks are not Passed.

| Check | Result |
| --- | --- |
| Focused Backend 8C tests | Passed — 96 tests, 0 failures/errors/skips (`LiveMetaInsightsReadAdapterTest`, `PlatformReadAdapterProfileTest`, `Stage8CLiveInsightsIntegrationTest`, `MigrationCompatibilityTest`, schema version lists, `Stage4DControllerIntegrationTest`, `Stage4DProfileGateTest`, `DeterministicFakePlatformReadAdapterTest`, isolation tests) |
| Full Backend `mvnw -B test` | Not run locally; executed on CI |
| Frontend / Playwright / Compose / Smoke / actionlint / Gitleaks | Not run locally; executed on CI |
| `git diff --check` | Passed |
| `git diff --exit-code origin/main --` V1–V17 | Passed — empty |

## Exact-head CI

Not recorded yet. Wait for the implementation Head’s `quality-and-compose` + `secret-scan` pair after the Draft PR opens.

## Next gate

Do not start live Google Ads, LINE/TikTok, or `META_TEST_DELIVERY`. Optional human confirm-refresh of one paused test Campaign is not CI.
