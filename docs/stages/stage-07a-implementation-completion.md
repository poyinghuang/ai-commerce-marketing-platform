# Stage 07A Implementation Completion Report

## Delivery identity

- Branch: `codex/stage-07a-secondary-image-provider`
- Base: `2851003bce4765e989296a124e3a7679a81ca6fd` (PR [#76](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/76) squash merge)
- Scope: LOCAL/TEST second `ImageGenerationProvider` bean behind `platform.image.provider`; persist `FAKE_SECONDARY_IMAGE`; no migration, no ComfyUI workflow edit, no live ads
- Specification: PR [#76](https://github.com/poyinghuang/ai-commerce-marketing-platform/pull/76) squash-merged at `2851003`; post-merge main CI Run `32928585609` passed
- Status: Runtime not merged; Manager Review not started
- Manager Decision: Not started

## Implemented scope

- `FakeSecondaryImageGenerationProvider` under `@Profile("(local | test) & !production")` and `platform.image.provider=fake-secondary`.
- Existing `StubImageGenerationProvider` remains the default via `platform.image.provider=stub` (`matchIfMissing=true`). Compose / `application-local.yml` / `application-test.yml` stay on `stub`.
- Exactly one `ImageGenerationProvider` bean. Production / default continue to load `DenyImageGenerationProvider`. Wrong flag values load neither stub nor secondary.
- Job identity is read from the injected port (`jobProviderKey` / `jobModelKey`). Secondary persist `provider_key=FAKE_SECONDARY_IMAGE` and `model_key=deterministic-fake-secondary`. Stub persist keys are unchanged. Cost remains `BigDecimal.ZERO`. Bytes follow the stub preservation contract.
- Allowlisted cost ceiling `IMAGE:FAKE_SECONDARY_IMAGE:deterministic-fake-secondary` with the same numeric envelope as `IMAGE:stub:stub-image`.
- No Flyway change. V1–V16 remain byte-identical. No REST, BFF, UI, or Compose service rewrite.

## Boundaries preserved

`ImageGenerationService` job FSM, prompt-version binding, ComfyUI workflow JSON, `workflow_key` / `workflow_version`, `AssetBinaryStore`, `ImagePreservationVerifier`, and `RGBA_MASK_EXACT_V1` are unchanged except the create-time identity is taken from the port instead of string literals. Application sources do not mention `FakeSecondaryImageGenerationProvider` or `ComfyUiImageGenerationProvider`. Stage 06 remains suggestion-only. Live ads, credentials, and 7B/7C stay locked.

## Local verification

Recorded on this runtime branch before exact-head CI. Unrun checks are not Passed.

| Check | Result |
| --- | --- |
| Focused Backend 7A tests | Passed — 22 tests, 0 failures/errors/skips (`FakeSecondaryImageGenerationProviderTest`, `AiGenerationProviderProfileTest`, `ImageGenerationApplicationPortIsolationTest`, `FakeSecondaryImageGenerationIntegrationTest`, `StubImageGenerationProviderTest`, `ImageGenerationIntegrationTest`) |
| Full Backend `mvnw -B test` | Not run locally; executed on CI |
| Frontend lint / typecheck / Vitest / build | Not required for this Backend-only slice; executed on CI |
| Playwright `dashboard-stage5` / `dashboard-stage6` / `platform-stage4e` / Compose / Smoke / actionlint / Gitleaks | Not run locally; executed on CI |
| `git diff --check` | Passed |
| `git diff --exit-code origin/main -- backend/src/main/resources/db/migration` | Passed — empty |

## Exact-head CI

Not yet recorded. Fill after Push and Pull Request `quality-and-compose` plus `secret-scan` pass on the reviewed Head.

## Next gate

7B fake-object `StorageProvider` stays locked until this 7A runtime is Manager `APPROVE`, squash-merged, and post-merge `main` CI passes. Live Google Sheets / Drive read and Meta Insights remain a later human-gated specification.
