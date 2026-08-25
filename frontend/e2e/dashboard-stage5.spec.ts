import { expect, test, type APIRequestContext, type APIResponse } from "@playwright/test";

type Product = { productUuid: string; version: number };
type Plan = { creativePlanUuid: string };
type Job = { generationJobUuid: string; status: string; version: number };
type Batch = { generationBatchUuid: string; jobs: Job[] };
type Output = { generationOutputUuid: string; version: number };
type Budget = { textTemplateKeys: string[] };

const unique = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;

async function body<T>(response: APIResponse, status: number): Promise<T> {
  expect(response.status(), await response.text()).toBe(status);
  return response.json() as Promise<T>;
}

async function pendingOutputs(request: APIRequestContext, count: number): Promise<Output[]> {
  const product = await body<Product>(await request.post("/api/products", {
    data: { productName: unique("Dashboard Product"), brand: "Stage 05", category: "Dashboard" },
  }), 201);
  const plan = await body<Plan>(await request.post(`/api/products/${product.productUuid}/creative-plans`, {
    data: {
      planName: unique("Dashboard Plan"), primaryAudience: "Operators",
      coreBenefit: "Workbench", creativeAngle: "Review", brandTone: "Clear",
    },
  }), 201);
  const budget = await body<Budget>(await request.get("/api/ai-budget/status"), 200);
  expect(budget.textTemplateKeys.length).toBeGreaterThan(0);
  const batch = await body<Batch>(await request.post(`/api/products/${product.productUuid}/ai-generation-batches`, {
    data: { creativePlanUuid: plan.creativePlanUuid, templateKey: budget.textTemplateKeys[0], modelProfile: "STANDARD", variationCount: count },
  }), 201);
  const outputs: Output[] = [];
  for (const job of batch.jobs) {
    const response = await request.post(`/api/ai-generation-jobs/${job.generationJobUuid}/execute`, {
      headers: { "If-Match": `W/"${job.version}"` },
    });
    outputs.push(await body<Output>(response, 200));
  }
  return outputs;
}

test("Compose-backed /dashboard first load is GET-only then approve and reject use Stage 03D routes", async ({ page }) => {
  const outputs = await pendingOutputs(page.request, 2);
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

  await page.goto("/");
  await expect(page.getByRole("link", { name: "Dashboard" })).toBeVisible();
  await page.goto("/dashboard");
  await expect(page.getByRole("heading", { name: "今日待辦" })).toBeVisible();
  for (const name of ["商品與資料完整度", "素材待審核", "Campaign 狀態", "KPI Overview", "AI 建議", "異常事件"]) {
    await expect(page.getByRole("heading", { name })).toBeVisible();
  }
  await expect(page.getByRole("status")).toHaveCount(0);
  await page.waitForTimeout(2000);
  expect(mutating).toEqual([]);

  const approved = page.locator(`[data-output-uuid="${outputs[0].generationOutputUuid}"]`);
  await expect(approved).toBeVisible();
  const approval = page.waitForResponse((response) =>
    response.url().endsWith(`/api/ai-generation-outputs/${outputs[0].generationOutputUuid}/approve`));
  await approved.getByRole("button", { name: "Approve output" }).click();
  expect(mutating).toEqual([]);
  await approved.getByRole("button", { name: "Confirm approve" }).click();
  expect((await approval).status()).toBe(200);
  expect(mutating).toEqual([`POST /api/ai-generation-outputs/${outputs[0].generationOutputUuid}/approve`]);

  const rejected = page.locator(`[data-output-uuid="${outputs[1].generationOutputUuid}"]`);
  await expect(rejected).toBeVisible();
  await rejected.getByLabel("Rejection reason").fill("Dashboard reject");
  const rejection = page.waitForResponse((response) =>
    response.url().endsWith(`/api/ai-generation-outputs/${outputs[1].generationOutputUuid}/reject`));
  await rejected.getByRole("button", { name: "Reject output" }).click();
  expect(mutating.filter((item) => item.includes("/reject"))).toEqual([]);
  await rejected.getByRole("button", { name: "Confirm reject" }).click();
  expect((await rejection).status()).toBe(200);
  expect(mutating).toEqual([
    `POST /api/ai-generation-outputs/${outputs[0].generationOutputUuid}/approve`,
    `POST /api/ai-generation-outputs/${outputs[1].generationOutputUuid}/reject`,
  ]);
});
