# Stage 07C-1 Implementation Completion Report

## Delivery identity

- Branch: `codex/stage-07c-1-fake-google-adapter`
- Base: `da01b13cf796913576dbd95591fb2965abff7ddd` (PR [#78](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/78) squash merge)
- Scope: LOCAL/TEST `FAKE_GOOGLE` adapter behind existing platform ports; additive V17 provider-key allow-list; second LOCAL/TEST account row; no REST, no `/platforms/google`, no Google Ads SDK
- Specification: PR [#76](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/76); 7A PR [#77](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/77) at `eb2618d`; 7B PR [#78](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/78) at `da01b13`; post-merge main CI Run `32944155884` passed
- Status: Runtime PR [#79](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/79) squash-merged at `f5faa28`; post-merge main CI Run `32971029793` passed
- Manager Decision: Merged on `main`

## Implemented scope

- `DeterministicFakeGooglePlatformAdapter` under `@Profile("(local | test) & !production")` and `platform.adapter=fake`.
- Meta write/read adapters remain `@Primary` so Stage 4D and existing FAKE Meta paths keep a unique default bean. Application selects write/reconcile ports by `account.providerKey()`.
- Java `ProviderKey` / `PlatformProvider` gain `FAKE_GOOGLE`. `NormalizedPlatformEvidence` schema version stays `1` and accepts `FAKE` or `FAKE_GOOGLE`.
- Additive `V17__allow_fake_google_provider_key.sql` expands `platform_accounts.provider_key` and attempt evidence `providerKey` to `IN ('FAKE','FAKE_GOOGLE')`. V1–V16 stay byte-identical.
- `Stage7C1AccountInitializer` inserts a second LOCAL/TEST account (`provider_key=FAKE_GOOGLE`, distinct `account_reference`, `TWD` / `Asia/Taipei`) when V17 is applied. V12-targeted tests skip the insert.
- No `/platforms/google`, no Frontend page, no Compose rewrite, no Google Ads API/SDK/MCC/OAuth/spend in this slice.

## Boundaries preserved

Domain campaign/ad-set/ad types gain no Google-only fields. `delivery.domain` and `delivery.application` do not import the Google fake class. Stage 05 `GET /api/dashboard` JSON is unchanged. Stage 06 remains suggestion-only. `/platforms/meta` and 4E Playwright stay on the default CI path. Live ads stay locked.

## Exact-head CI

Runtime PR [#79](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/79) squash-merged at `f5faa28`. Post-merge `main` CI Run [`32971029793`](https://github.com/poyinghuang/ai-commerce-marketing-platform/actions/runs/32971029793) passed both jobs.

Stage 07C-1 FAKE is closed on `main`. 7C-2 is merged. Stage 07 FAKE close-out is the current gate. 7D/7E stay locked.
