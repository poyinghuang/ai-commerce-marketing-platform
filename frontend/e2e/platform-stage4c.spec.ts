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
  await page.getByRole("button", { name: "Preview paused Ad" }).click();
  expect(creates).toBe(0);
  await expect(page.getByRole("dialog", { name: "Confirm Ad publication" })).toBeVisible();
  await page.getByRole("button", { name: "Confirm FAKE operation" }).click();
  await expect(page.getByLabel("Ad status")).toBeVisible();
  expect(creates).toBe(1);
});
