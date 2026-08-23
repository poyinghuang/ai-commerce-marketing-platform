import { expect, test } from "@playwright/test";

test("mocked delivery and metrics require explicit confirmation and never auto-refresh", async ({ page }) => {
  const entity = "11111111-1111-4111-8111-111111111111";
  let posts = 0;
  await page.route("**/api/platform-entities/**", async (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();
    if (method === "POST") {
      posts += 1;
      expect(route.request().headers()["content-type"] ?? "").toBe("");
      expect(route.request().postData() ?? "").toBe("");
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          entityType: "CAMPAIGN",
          entityUuid: entity,
          desiredState: "PAUSED",
          observedState: "PAUSED",
          syncEligible: true,
          refreshEligible: true,
          confirmable: true,
          present: true,
          freshnessStatus: "FRESH",
          windowStart: "2026-08-21T16:00:00Z",
          windowEnd: "2026-08-22T16:00:00Z",
          impressions: 10000,
          spend: "25.000000",
          roas: "4.000000",
          warnings: ["DETERMINISTIC_FAKE_ONLY", "NO_REAL_PROVIDER_OR_SPEND", "NULL_METRICS_MEAN_UNKNOWN"],
        }),
      });
      return;
    }
    if (url.pathname.endsWith("/delivery")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        headers: { etag: 'W/"1"' },
        body: JSON.stringify({
          entityType: "CAMPAIGN",
          entityUuid: entity,
          desiredState: "PAUSED",
          observedState: "PAUSED",
          updatedAt: "2026-08-22T00:00:00Z",
          version: 1,
        }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        entityType: "CAMPAIGN",
        entityUuid: entity,
        present: true,
        freshnessStatus: "FRESH",
        windowStart: "2026-08-21T16:00:00Z",
        windowEnd: "2026-08-22T16:00:00Z",
        impressions: 10000,
        spend: "25.000000",
        roas: "4.000000",
        warnings: ["DETERMINISTIC_FAKE_ONLY", "NO_REAL_PROVIDER_OR_SPEND", "NULL_METRICS_MEAN_UNKNOWN"],
      }),
    });
  });
  await page.goto("/platforms/meta");
  expect(posts).toBe(0);
  await expect(page.getByRole("heading", { name: "4. Delivery and metrics" })).toBeVisible();
  await expect(page.getByText("Null metrics mean unknown")).toBeVisible();
  await page.getByLabel("Platform entity UUID").fill(entity);
  await page.getByRole("button", { name: "Load delivery and metrics" }).click();
  await expect(page.getByLabel("Delivery status")).toBeVisible();
  await expect(page.getByLabel("Metrics status")).toContainText("FRESH");
  expect(posts).toBe(0);
  await page.getByRole("button", { name: "Preview metrics refresh" }).click();
  await expect(page.getByRole("dialog", { name: "Confirm metrics refresh" })).toBeVisible();
  expect(posts).toBe(1);
  await page.getByRole("button", { name: "Confirm metrics refresh" }).click();
  await expect(page.getByLabel("Metrics status")).toContainText("25.000000");
  expect(posts).toBe(2);
});
