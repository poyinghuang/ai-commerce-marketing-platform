import { expect, test, type APIRequestContext, type APIResponse } from "@playwright/test";

type CampaignPlan = { campaignUuid: string; version: number };
type PlatformEntity = { entityUuid: string };

const unique = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;

async function body<T>(response: APIResponse, status: number): Promise<T> {
  expect(response.status(), await response.text()).toBe(status);
  return response.json() as Promise<T>;
}

async function pausedCampaignWithMetrics(request: APIRequestContext): Promise<string> {
  const plan = await body<CampaignPlan>(await request.post("/api/campaigns", {
    data: {
      campaignName: unique("Decision Campaign"), activityType: "SALES",
      startDate: "2030-01-01", endDate: "2030-01-31", objective: "OUTCOME_SALES",
      platform: "META", budgetDaily: 100, budgetTotal: 300, currency: "TWD",
      promotion: null, landingPage: null,
    },
  }), 201);
  const created = await body<PlatformEntity>(await request.post("/api/platforms/meta/campaigns", {
    data: { clientRequestUuid: crypto.randomUUID(), campaignUuid: plan.campaignUuid, expectedCampaignPlanVersion: 0 },
  }), 202);
  const refresh = await request.fetch(`/api/platform-entities/CAMPAIGN/${created.entityUuid}/metrics-refresh`, {
    method: "POST",
  });
  expect(refresh.status(), await refresh.text()).toBe(200);
  return created.entityUuid;
}

test("Compose-backed /dashboard generate and approve stay on decision routes and do not POST on load", async ({ page }) => {
  await pausedCampaignWithMetrics(page.request);
  await pausedCampaignWithMetrics(page.request);
  const mutating: string[] = [];
  page.on("request", (request) => {
    if (!["POST", "PATCH", "DELETE"].includes(request.method())) return;
    let path = request.url();
    try {
      path = new URL(request.url()).pathname;
    } catch {
      /* keep the raw URL when the browser emits a relative or opaque request */
    }
    if (path.startsWith("/api/")) mutating.push(`${request.method()} ${path}`);
  });

  await page.goto("/dashboard");
  await expect(page.getByRole("heading", { name: "今日待辦" })).toBeVisible();
  for (const name of ["商品與資料完整度", "素材待審核", "Campaign 狀態", "KPI Overview", "AI 建議", "異常事件", "優化建議"]) {
    await expect(page.getByRole("heading", { name })).toBeVisible();
  }
  await expect(page.getByRole("status")).toHaveCount(0);
  await page.waitForTimeout(2000);
  expect(mutating).toEqual([]);

  await page.getByRole("button", { name: "Generate suggestions" }).click();
  expect(mutating).toEqual([]);
  const generated = page.waitForResponse((response) =>
    new URL(response.url()).pathname === "/api/decision-recommendations/generate");
  await page.getByRole("button", { name: "Confirm generate suggestions" }).click();
  expect((await generated).status()).toBe(200);
  expect(mutating).toEqual(["POST /api/decision-recommendations/generate"]);

  await expect(page.locator("[data-recommendation-uuid]")).toHaveCount(2);
  const card = page.locator("[data-recommendation-uuid]").first();
  await expect(card).toBeVisible();
  await expect(card.getByText("INCREASE_BUDGET")).toBeVisible();
  const recommendationUuid = await card.getAttribute("data-recommendation-uuid");
  expect(recommendationUuid).toBeTruthy();
  await card.getByRole("button", { name: "Approve suggestion" }).click();
  expect(mutating.filter((item) => item.includes("/approve"))).toEqual([]);
  const approved = page.waitForResponse((response) =>
    new URL(response.url()).pathname === `/api/decision-recommendations/${recommendationUuid}/approve`);
  await card.getByRole("button", { name: "Confirm approve suggestion" }).click();
  expect((await approved).status()).toBe(200);
  expect(mutating).toEqual([
    "POST /api/decision-recommendations/generate",
    `POST /api/decision-recommendations/${recommendationUuid}/approve`,
  ]);
  await expect(page.getByText("Ads state did not change. Approval records the operator decision only.")).toBeVisible();

  const remaining = page.locator("[data-recommendation-uuid]");
  await expect(remaining).toHaveCount(1);
  const rejectUuid = await remaining.first().getAttribute("data-recommendation-uuid");
  expect(rejectUuid).toBeTruthy();
  await remaining.getByLabel("Rejection reason").fill("Not a useful suggestion");
  await remaining.getByRole("button", { name: "Reject suggestion" }).click();
  expect(mutating.filter((item) => item.includes("/reject"))).toEqual([]);
  const rejected = page.waitForResponse((response) =>
    new URL(response.url()).pathname === `/api/decision-recommendations/${rejectUuid}/reject`);
  await remaining.getByRole("button", { name: "Confirm reject suggestion" }).click();
  expect((await rejected).status()).toBe(200);
  expect(mutating).toEqual([
    "POST /api/decision-recommendations/generate",
    `POST /api/decision-recommendations/${recommendationUuid}/approve`,
    `POST /api/decision-recommendations/${rejectUuid}/reject`,
  ]);
  await expect(page.getByText("目前沒有優化建議。")).toBeVisible();
});
