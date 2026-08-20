import { expect, test } from "@playwright/test";

test("mocked Ad preview requires explicit confirmation and never auto-submits", async ({ page }) => {
  const adSet = "11111111-1111-4111-8111-111111111111";
  const ad = "22222222-2222-4222-8222-222222222222";
  const request = "44444444-4444-4444-8444-444444444444";
  let creates = 0;
  await page.route("**/api/platforms/meta/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith("/ads/preview")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          clientRequestUuid: request,
          platformAdSetUuid: adSet,
          expectedParentVersion: 1,
          productUuid: request,
          assetUuid: request,
          generationOutputUuid: request,
          reviewDecisionUuid: request,
          approvedChecksumFingerprint: "a".repeat(64),
          creativeMappingKey: "APPROVED_IMAGE_ASSET_V1",
          parentCampaignDesiredState: "PAUSED",
          parentAdSetDesiredState: "PAUSED",
          newAdDesiredState: "PAUSED",
          evidenceEligible: true,
          warnings: ["DETERMINISTIC_FAKE_ONLY", "NO_REAL_PROVIDER_OR_SPEND", "EVIDENCE_DIVERGENCE_BLOCKS_CREATE_OR_RESUME"],
          confirmable: true,
        }),
      });
      return;
    }
    if (url.pathname.endsWith("/ads") && route.request().method() === "POST") {
      creates += 1;
      await route.fulfill({
        status: 202,
        contentType: "application/json",
        headers: { etag: 'W/"2"', Location: `/api/platform-operations/${ad}` },
        body: JSON.stringify({
          operationUuid: ad,
          operationType: "CREATE_AD",
          entityType: "AD",
          entityUuid: ad,
          status: "SUCCEEDED",
          attemptCount: 1,
          reconciliationCount: 0,
          maxAttempts: 3,
          createdAt: "2000-01-01T00:00:00Z",
          updatedAt: "2000-01-01T00:00:00Z",
          version: 2,
        }),
      });
      return;
    }
    if (url.pathname.includes("/ads/") && route.request().method() === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        headers: { etag: 'W/"1"' },
        body: JSON.stringify({
          platformAdUuid: ad,
          platformAdSetUuid: adSet,
          productUuid: request,
          assetUuid: request,
          generationOutputUuid: request,
          reviewDecisionUuid: request,
          approvedChecksumFingerprint: "b".repeat(64),
          creativeMappingKey: "APPROVED_IMAGE_ASSET_V1",
          desiredState: "PAUSED",
          createdAt: "2000-01-01T00:00:00Z",
          updatedAt: "2000-01-01T00:00:00Z",
          version: 1,
        }),
      });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
  });
  await page.goto("/platforms/meta");
  await page.getByLabel("Platform Ad Set UUID").fill(adSet);
  await page.getByLabel("Product UUID").fill(request);
  await page.getByLabel("Asset UUID").fill(request);
  await page.getByLabel("Generation output UUID").fill(request);
  await page.getByLabel("Review decision UUID").fill(request);
  expect(creates).toBe(0);
  await page.getByRole("button", { name: "Preview paused Ad", exact: true }).click();
  expect(creates).toBe(0);
  await expect(page.getByRole("dialog", { name: "Confirm Ad publication" })).toBeVisible();
  await page.getByRole("button", { name: "Confirm FAKE operation" }).click();
  await expect(page.getByLabel("Ad status")).toBeVisible();
  expect(creates).toBe(1);
});

test("mocked Ad pause, resume, stale 412, due retry, unknown reconcile, divergence, weak ETag, malformed If-Match, and no automatic action", async ({ page }) => {
  const adSet = "11111111-1111-4111-8111-111111111111";
  const ad = "22222222-2222-4222-8222-222222222222";
  const request = "44444444-4444-4444-8444-444444444444";
  const operation = "55555555-5555-4555-8555-555555555555";
  let pauses = 0, resumes = 0, retries = 0, reconciles = 0, creates = 0;
  let adDesired = "PAUSED";
  let adVersion = 1;
  let lastPauseIfMatch = "";
  await page.route("**/api/platforms/meta/**", async (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();
    if (url.search) {
      await route.fulfill({ status: 400, contentType: "application/json", body: JSON.stringify({ code: "PLATFORM_REQUEST_INVALID", message: "Platform request is invalid", fieldErrors: [{ field: "query", message: "Query parameters are not allowed" }] }) });
      return;
    }
    if (url.pathname.endsWith("/ads/preview")) {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ clientRequestUuid: request, platformAdSetUuid: adSet, expectedParentVersion: 1, productUuid: request, assetUuid: request, generationOutputUuid: request, reviewDecisionUuid: request, approvedChecksumFingerprint: "a".repeat(64), creativeMappingKey: "APPROVED_IMAGE_ASSET_V1", parentCampaignDesiredState: "PAUSED", parentAdSetDesiredState: "PAUSED", newAdDesiredState: "PAUSED", evidenceEligible: true, warnings: ["DETERMINISTIC_FAKE_ONLY", "NO_REAL_PROVIDER_OR_SPEND", "EVIDENCE_DIVERGENCE_BLOCKS_CREATE_OR_RESUME"], confirmable: true }) });
      return;
    }
    if (url.pathname.endsWith("/ads") && method === "POST") {
      creates += 1;
      await route.fulfill({ status: 202, contentType: "application/json", headers: { etag: 'W/"2"', Location: `/api/platform-operations/${operation}` }, body: JSON.stringify({ operationUuid: operation, operationType: "CREATE_AD", entityType: "AD", entityUuid: ad, status: "FAILED_RETRYABLE", attemptCount: 1, reconciliationCount: 0, maxAttempts: 3, nextAttemptAt: "2000-01-01T00:00:00Z", createdAt: "2000-01-01T00:00:00Z", updatedAt: "2000-01-01T00:00:00Z", version: 2 }) });
      return;
    }
    if (url.pathname.endsWith("/state/preview")) {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ clientRequestUuid: request, entityType: "AD", entityUuid: ad, expectedEntityVersion: adVersion, previousDesiredState: adDesired, targetDesiredState: adDesired === "ACTIVE" ? "PAUSED" : "ACTIVE", parentCampaignDesiredState: "ACTIVE", parentAdSetDesiredState: "ACTIVE", evidenceEligible: true, warnings: ["DETERMINISTIC_FAKE_ONLY", "NO_REAL_PROVIDER_OR_SPEND", "EVIDENCE_DIVERGENCE_BLOCKS_CREATE_OR_RESUME"], confirmable: true }) });
      return;
    }
    if (url.pathname.endsWith("/resume") && method === "POST") {
      resumes += 1;
      await route.fulfill({ status: 409, contentType: "application/json", body: JSON.stringify({ code: "PLATFORM_AD_EVIDENCE_INVALID", message: "The approved Ad evidence is no longer eligible" }) });
      return;
    }
    if (url.pathname.endsWith("/pause") && method === "POST") {
      pauses += 1;
      lastPauseIfMatch = route.request().headers()["if-match"] ?? "";
      if (lastPauseIfMatch && !/^W\/"[0-9]+"$/.test(lastPauseIfMatch)) {
        await route.fulfill({ status: 400, contentType: "application/json", body: JSON.stringify({ code: "PLATFORM_REQUEST_INVALID", message: "Platform request is invalid", fieldErrors: [{ field: "If-Match", message: "Invalid If-Match" }] }) });
        return;
      }
      await route.fulfill({ status: 412, contentType: "application/json", body: JSON.stringify({ code: "PLATFORM_ENTITY_STALE", message: "The platform entity changed; reload and preview again" }) });
      return;
    }
    if (url.pathname.includes("/ads/") && method === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", headers: { etag: `W/"${adVersion}"` }, body: JSON.stringify({ platformAdUuid: ad, platformAdSetUuid: adSet, productUuid: request, assetUuid: request, generationOutputUuid: request, reviewDecisionUuid: request, approvedChecksumFingerprint: "b".repeat(64), creativeMappingKey: "APPROVED_IMAGE_ASSET_V1", desiredState: adDesired, createdAt: "2000-01-01T00:00:00Z", updatedAt: "2000-01-01T00:00:00Z", version: adVersion }) });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
  });
  await page.route("**/api/platform-operations/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/retry")) { retries += 1; await route.fulfill({ status: 202, contentType: "application/json", headers: { etag: 'W/"3"', Location: `/api/platform-operations/${operation}` }, body: JSON.stringify({ operationUuid: operation, operationType: "CREATE_AD", entityType: "AD", entityUuid: ad, status: "UNKNOWN_OUTCOME", attemptCount: 2, reconciliationCount: 0, maxAttempts: 3, createdAt: "2000-01-01T00:00:00Z", updatedAt: "2000-01-01T00:00:00Z", version: 3 }) }); return; }
    if (path.endsWith("/reconcile")) { reconciles += 1; await route.fulfill({ status: 202, contentType: "application/json", headers: { etag: 'W/"4"' }, body: JSON.stringify({ operationUuid: operation, operationType: "CREATE_AD", entityType: "AD", entityUuid: ad, status: "SUCCEEDED", attemptCount: 1, reconciliationCount: 1, maxAttempts: 3, createdAt: "2000-01-01T00:00:00Z", updatedAt: "2000-01-01T00:00:00Z", version: 4 }) }); return; }
    await route.fulfill({ status: 200, contentType: "application/json", headers: { etag: 'W/"3"' }, body: JSON.stringify({ operationUuid: operation, operationType: "CREATE_AD", entityType: "AD", entityUuid: ad, status: "UNKNOWN_OUTCOME", attemptCount: 1, reconciliationCount: 0, maxAttempts: 3, createdAt: "2000-01-01T00:00:00Z", updatedAt: "2000-01-01T00:00:00Z", version: 3 }) });
  });
  await page.goto("/platforms/meta");
  await page.getByLabel("Platform Ad Set UUID").fill(adSet);
  await page.getByLabel("Product UUID").fill(request);
  await page.getByLabel("Asset UUID").fill(request);
  await page.getByLabel("Generation output UUID").fill(request);
  await page.getByLabel("Review decision UUID").fill(request);
  expect(creates).toBe(0);
  expect(pauses).toBe(0);
  expect(resumes).toBe(0);
  expect(retries).toBe(0);
  expect(reconciles).toBe(0);
  await page.getByRole("button", { name: "Preview paused Ad", exact: true }).click();
  expect(creates).toBe(0);
  await page.getByRole("button", { name: "Confirm FAKE operation" }).click();
  await expect(page.getByText("Retry due operation")).toBeVisible();
  expect(creates).toBe(1);
  await page.getByRole("button", { name: "Retry due operation" }).click();
  expect(retries).toBe(0);
  await page.getByRole("button", { name: "Confirm retry" }).click();
  await expect(page.getByText("Reconcile unknown outcome")).toBeVisible();
  expect(retries).toBe(1);
  await page.getByRole("button", { name: "Reconcile unknown outcome" }).click();
  expect(reconciles).toBe(0);
  await page.getByRole("button", { name: "Confirm reconcile" }).click();
  await expect(page.getByText("SUCCEEDED", { exact: true })).toBeVisible();
  expect(reconciles).toBe(1);
  await page.getByLabel("Platform Ad UUID").fill(ad);
  await page.getByRole("button", { name: "Load Ad", exact: true }).click();
  await expect(page.getByLabel("Ad status")).toBeVisible();
  await expect(page.getByText("create and resume then stay blocked")).toBeVisible();
  await page.getByRole("button", { name: /^Preview resume Ad$/ }).click();
  await page.getByRole("button", { name: "Confirm FAKE operation" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "no longer eligible" })).toBeVisible();
  expect(resumes).toBe(1);
  expect(pauses).toBe(0);
  adDesired = "ACTIVE";
  adVersion = 2;
  await page.getByRole("button", { name: "Load Ad", exact: true }).click();
  await expect(page.getByRole("button", { name: /^Preview pause Ad$/ })).toBeVisible();
  await page.getByRole("button", { name: /^Preview pause Ad$/ }).click();
  expect(pauses).toBe(0);
  await page.getByRole("button", { name: "Confirm FAKE operation" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "reload and preview again" })).toBeVisible();
  expect(pauses).toBe(1);
  expect(lastPauseIfMatch).toBe('W/"2"');
  expect(creates).toBe(1);
  expect(resumes).toBe(1);
});
