import { expect, test } from "@playwright/test";

test("previews and explicitly confirms a paused FAKE Google Campaign", async ({ page }) => {
  const campaignPlan = await page.request.post("/api/campaigns", {
    data: {
      campaignName: `Stage7C2 ${Date.now()}`,
      activityType: "SALES",
      startDate: "2030-01-01",
      endDate: "2030-01-31",
      objective: "OUTCOME_SALES",
      platform: "META",
      budgetDaily: 100,
      budgetTotal: 300,
      currency: "TWD",
      promotion: null,
      landingPage: null,
    },
  });
  expect(campaignPlan.status()).toBe(201);
  const plan = await campaignPlan.json();
  await page.goto("/platforms/google");
  await expect(page.getByRole("heading", { name: "Google platform operations" })).toBeVisible();
  await page.getByLabel("Campaign Plan UUID").fill(plan.campaignUuid);
  await page.getByRole("button", { name: "Preview paused Campaign" }).click();
  await expect(page.getByRole("dialog", { name: "Confirm platform mutation" })).toContainText("PAUSED");
  await page.getByRole("button", { name: "Confirm FAKE operation" }).click();
  await expect(page.getByText("SUCCEEDED", { exact: true })).toBeVisible();
});

test("Compose-backed /platforms/google does not auto-POST platform writes", async ({ page }) => {
  const posts: string[] = [];
  page.on("request", (request) => {
    if (request.method() !== "POST") {
      return;
    }
    let path = request.url();
    try {
      path = new URL(request.url()).pathname;
    } catch {
      /* keep the raw URL when the browser emits a relative or opaque request */
    }
    if (path.startsWith("/api/platforms/google/")) {
      posts.push(`${request.method()} ${path}`);
    }
  });
  await page.goto("/platforms/google");
  await expect(page.getByRole("heading", { name: "Google platform operations" })).toBeVisible();
  await page.waitForTimeout(2000);
  expect(posts).toEqual([]);
});
