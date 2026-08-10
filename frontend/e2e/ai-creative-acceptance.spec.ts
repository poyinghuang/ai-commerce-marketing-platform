import { execFileSync } from "node:child_process";
import { resolve } from "node:path";
import { expect, test, type APIRequestContext, type APIResponse, type Page } from "@playwright/test";

type Product = { productUuid: string; version: number };
type Plan = { creativePlanUuid: string };
type Asset = { assetUuid: string };
type Job = {
  generationJobUuid: string;
  status: string;
  version: number;
  failureCode: string | null;
  outputUuid: string | null;
};
type Batch = {
  generationBatchUuid: string;
  status: string;
  succeededJobCount: number;
  failedJobCount: number;
  rejectedJobCount: number;
  jobs: Job[];
};
type Output = {
  generationOutputUuid: string;
  generationType: "TEXT" | "IMAGE";
  textContent: string | null;
  reviewStatus: "PENDING_REVIEW" | "APPROVED" | "REJECTED";
  reviewBlockers: string[];
  reviewDecisions: Array<{ decision: string; reason: string | null }>;
  generatedAssetUuid: string | null;
  preservationStatus: string | null;
  preservationDetails: { changedPixelCount: number; protectedPixelCount: number } | null;
  version: number;
};
type Budget = { textTemplateKeys: string[]; imageTemplateKeys: string[]; imageWorkflowKeys: string[] };

const SOURCE_SIZE = 78;
const SOURCE_SHA256 = "6556b8f8051bafb300ea4ecd6d4bcacf58f5ead5d1f79146f2d1e373f3bc50a5";
const SOURCE_HANDLE = "stub-alpha-source-v1";
const CHANGED_SOURCE_HANDLE = "stub-alpha-source-changed-pixel-v1";
const unique = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;

async function body<T>(response: APIResponse, status: number): Promise<T> {
  expect(response.status(), await response.text()).toBe(status);
  return response.json() as Promise<T>;
}

async function foundation(request: APIRequestContext) {
  const product = await body<Product>(await request.post("/api/products", {
    data: { productName: unique("AI Acceptance Product"), brand: "Stage 03", category: "Acceptance" },
  }), 201);
  const plan = await body<Plan>(await request.post(`/api/products/${product.productUuid}/creative-plans`, {
    data: {
      planName: unique("AI Acceptance Plan"), primaryAudience: "Responsible shoppers",
      coreBenefit: "Deterministic acceptance", creativeAngle: "Evidence first", brandTone: "Clear",
    },
  }), 201);
  const budget = await body<Budget>(await request.get("/api/ai-budget/status"), 200);
  expect(budget.textTemplateKeys.length).toBeGreaterThan(0);
  expect(budget.imageTemplateKeys.length).toBeGreaterThan(0);
  return { product, plan, budget };
}

async function textBatch(request: APIRequestContext, product: Product, plan: Plan, budget: Budget,
  modelProfile: string, variationCount: number) {
  return body<Batch>(await request.post(`/api/products/${product.productUuid}/ai-generation-batches`, {
    data: { creativePlanUuid: plan.creativePlanUuid, templateKey: budget.textTemplateKeys[0], modelProfile, variationCount },
  }), 201);
}

async function execute(request: APIRequestContext, job: Job) {
  const response = await request.post(`/api/ai-generation-jobs/${job.generationJobUuid}/execute`, {
    headers: { "If-Match": `W/"${job.version}"` },
  });
  return { response, output: response.ok() ? await response.json() as Output : null };
}

async function imageAsset(request: APIRequestContext, product: Product, handle: string): Promise<Asset> {
  return body<Asset>(await request.post(`/api/products/${product.productUuid}/assets`, {
    data: {
      assetType: "IMAGE", purpose: "PRODUCT_SOURCE", storageProvider: "LOCAL_STUB",
      providerFileId: handle, mediaType: "image/png", originalFilename: `${handle}.png`,
      sizeBytes: SOURCE_SIZE, checksumSha256: SOURCE_SHA256,
    },
  }), 201);
}

async function imageOutput(request: APIRequestContext, product: Product, plan: Plan, budget: Budget, asset: Asset) {
  const batch = await body<Batch>(await request.post(`/api/products/${product.productUuid}/ai-generation-batches`, {
    data: {
      generationType: "IMAGE", creativePlanUuid: plan.creativePlanUuid,
      templateKey: budget.imageTemplateKeys[0], workflowKey: budget.imageWorkflowKeys[0],
      modelProfile: "STANDARD_IMAGE", variationCount: 1, sourceAssetUuid: asset.assetUuid, maskAssetUuid: null,
    },
  }), 201);
  const result = await execute(request, batch.jobs[0]);
  expect(result.response.status(), await result.response.text()).toBe(200);
  return result.output!;
}

async function openFactory(page: Page, productUuid: string) {
  await page.goto(`/products/${productUuid}?tab=creative-factory`);
  await expect(page.getByRole("heading", { name: "Creative Factory" })).toBeVisible();
  await expect(page.getByRole("status", { name: /Loading generation history/i })).toHaveCount(0);
}

function outputCard(page: Page, output: Output) {
  const marker = output.textContent ?? output.generatedAssetUuid!.slice(0, 8);
  return page.locator("article.state-card > .content-card").filter({ hasText: marker }).first();
}

function postgresScalar(sql: string): string {
  expect(process.env.PLAYWRIGHT_AUDIT_DB_ASSERTION).toBe("1");
  const composeFile = resolve(process.cwd(), "../docker-compose.yml");
  const projectDirectory = resolve(process.cwd(), "..");
  const args = [
    ...(process.env.PLAYWRIGHT_DOCKER_HOST ? ["--host", process.env.PLAYWRIGHT_DOCKER_HOST] : []),
    ...(process.env.PLAYWRIGHT_POSTGRES_CONTAINER
      ? ["exec", process.env.PLAYWRIGHT_POSTGRES_CONTAINER]
      : ["compose", "--project-directory", projectDirectory,
          ...(process.env.PLAYWRIGHT_COMPOSE_PROJECT_NAME ? ["-p", process.env.PLAYWRIGHT_COMPOSE_PROJECT_NAME] : []),
          "-f", composeFile, "exec", "-T", "postgres"]),
    "psql", "-U", "ai_commerce", "-d", "ai_commerce", "-tA", "-c", sql,
  ];
  return execFileSync(process.platform === "win32" ? "docker.exe" : "docker", args, { encoding: "utf8" }).trim();
}

function uuid(value: string) {
  expect(value).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
  return value;
}

test("approves and rejects text outputs with immutable history and exact Audit", async ({ page }) => {
  const setup = await foundation(page.request);
  const batch = await textBatch(page.request, setup.product, setup.plan, setup.budget, "STANDARD", 2);
  const outputs: Output[] = [];
  for (const job of batch.jobs) {
    const result = await execute(page.request, job);
    expect(result.response.status()).toBe(200);
    outputs.push(result.output!);
  }

  await openFactory(page, setup.product.productUuid);
  const approvedCard = outputCard(page, outputs[0]);
  const approval = page.waitForResponse((response) => response.url().endsWith(`/api/ai-generation-outputs/${outputs[0].generationOutputUuid}/approve`));
  await approvedCard.getByRole("button", { name: "Approve output" }).click();
  expect((await approval).status()).toBe(200);
  await expect(outputCard(page, outputs[0])).toContainText("APPROVED");

  const rejectedCard = outputCard(page, outputs[1]);
  await rejectedCard.getByLabel("Rejection reason").fill("Not aligned with the approved campaign tone");
  const rejection = page.waitForResponse((response) => response.url().endsWith(`/api/ai-generation-outputs/${outputs[1].generationOutputUuid}/reject`));
  await rejectedCard.getByRole("button", { name: "Reject output" }).click();
  expect((await rejection).status()).toBe(200);
  await expect(outputCard(page, outputs[1])).toContainText("REJECTED");
  await expect(page.getByText("Not aligned with the approved campaign tone", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: /Publish/i })).toHaveCount(0);

  await page.reload();
  await expect(outputCard(page, outputs[0]).getByRole("button", { name: "Approve output" })).toHaveCount(0);
  await expect(outputCard(page, outputs[1]).getByRole("button", { name: "Reject output" })).toHaveCount(0);
  for (const output of outputs) {
    expect(postgresScalar(`SELECT COUNT(*) FROM ai_review_decisions WHERE generation_output_uuid='${uuid(output.generationOutputUuid)}'::uuid;`)).toBe("1");
    expect(postgresScalar(`SELECT COUNT(*) FROM audit_logs WHERE entity_uuid='${uuid(output.generationOutputUuid)}'::uuid AND entity_type='AI_GENERATION_OUTPUT' AND action='UPDATE';`)).toBe("1");
  }
  const noPublish = postgresScalar("SELECT COUNT(*) FROM audit_logs WHERE entity_type ILIKE '%PUBLISH%' OR entity_type ILIKE '%META%';");
  expect(noPublish).toBe("0");
  expect((await page.request.post(`/api/ai-generation-outputs/${outputs[0].generationOutputUuid}/publish`)).status()).toBe(404);
});

test("stale Browser reviewer receives 412 and reloads the winning decision", async ({ context, page }) => {
  const setup = await foundation(page.request);
  const batch = await textBatch(page.request, setup.product, setup.plan, setup.budget, "LOW_COST", 1);
  const result = await execute(page.request, batch.jobs[0]);
  const output = result.output!;
  const first = await context.newPage();
  const second = await context.newPage();
  await Promise.all([openFactory(first, setup.product.productUuid), openFactory(second, setup.product.productUuid)]);

  const winner = first.waitForResponse((response) => response.url().endsWith(`/api/ai-generation-outputs/${output.generationOutputUuid}/approve`));
  await outputCard(first, output).getByRole("button", { name: "Approve output" }).click();
  expect((await winner).status()).toBe(200);

  const staleCard = outputCard(second, output);
  await staleCard.getByLabel("Rejection reason").fill("Stale reviewer must lose");
  const stale = second.waitForResponse((response) => response.url().endsWith(`/api/ai-generation-outputs/${output.generationOutputUuid}/reject`));
  await staleCard.getByRole("button", { name: "Reject output" }).click();
  expect((await stale).status()).toBe(412);
  const alert = second.getByRole("alert").filter({ hasText: "This output changed" });
  await expect(alert).toContainText("output changed");
  await alert.getByRole("button", { name: "Reload" }).click();
  await expect(outputCard(second, output)).toContainText("APPROVED");
  expect(postgresScalar(`SELECT COUNT(*) FROM ai_review_decisions WHERE generation_output_uuid='${uuid(output.generationOutputUuid)}'::uuid;`)).toBe("1");
});

test("persists an over-job budget rejection without provider execution or output", async ({ page }) => {
  const setup = await foundation(page.request);
  await openFactory(page, setup.product.productUuid);
  await page.getByLabel("Model profile").selectOption("OVER_JOB_BUDGET_FIXTURE");
  await page.getByLabel("Variations").selectOption("1");
  const created = page.waitForResponse((response) => response.url().endsWith(`/api/products/${setup.product.productUuid}/ai-generation-batches`) && response.request().method() === "POST");
  await page.getByRole("button", { name: "Create text batch" }).click();
  const createdResponse = await created;
  expect(createdResponse.status()).toBe(201);
  const batch = await createdResponse.json() as Batch;
  expect(batch.status).toBe("BUDGET_REJECTED");
  expect(batch.jobs).toHaveLength(1);
  expect(batch.jobs[0]).toMatchObject({ status: "BUDGET_REJECTED", failureCode: "AI_JOB_BUDGET_EXCEEDED" });
  await expect(page.getByText("BUDGET_REJECTED", { exact: true })).toBeVisible();
  await expect(page.getByText(/AI_JOB_BUDGET_EXCEEDED/)).toBeVisible();
  const executeRejected = await page.request.post(`/api/ai-generation-jobs/${batch.jobs[0].generationJobUuid}/execute`, {
    headers: { "If-Match": `W/"${batch.jobs[0].version}"` },
  });
  expect(executeRejected.status()).toBe(409);
  expect(postgresScalar(`SELECT COUNT(*) FROM ai_generation_outputs WHERE generation_batch_uuid='${uuid(batch.generationBatchUuid)}'::uuid;`)).toBe("0");
  expect(postgresScalar(`SELECT COUNT(*) FROM ai_budget_ledger WHERE generation_job_uuid='${uuid(batch.jobs[0].generationJobUuid)}'::uuid;`)).toBe("0");
  expect(postgresScalar(`SELECT COUNT(*) FROM audit_logs WHERE entity_uuid='${uuid(batch.generationBatchUuid)}'::uuid AND entity_type='AI_GENERATION_BATCH' AND action='UPDATE';`)).toBe("1");
  expect(postgresScalar(`SELECT COUNT(*) FROM audit_logs WHERE entity_uuid='${uuid(batch.jobs[0].generationJobUuid)}'::uuid AND entity_type='AI_GENERATION_JOB' AND action='UPDATE';`)).toBe("1");
});

test("retains deterministic partial batch evidence and reviews successful siblings", async ({ page }) => {
  const setup = await foundation(page.request);
  const batch = await textBatch(page.request, setup.product, setup.plan, setup.budget, "PARTIAL_FAILURE_FIXTURE", 3);
  const outputs: Output[] = [];
  for (const job of batch.jobs) {
    const result = await execute(page.request, job);
    if (result.response.ok()) outputs.push(result.output!); else expect((await result.response.json()).code).toBe("AI_PROVIDER_REJECTED");
  }
  const current = await body<Batch>(await page.request.get(`/api/ai-generation-batches/${batch.generationBatchUuid}`), 200);
  expect(current).toMatchObject({ status: "COMPLETED_WITH_ERRORS", succeededJobCount: 2, failedJobCount: 1 });
  await openFactory(page, setup.product.productUuid);
  await expect(page.getByText("2 succeeded · 1 failed · 0 rejected", { exact: true })).toBeVisible();
  await expect(page.getByText(/AI_PROVIDER_REJECTED/)).toBeVisible();
  const failed = current.jobs.find((job) => job.status === "FAILED")!;
  const retry = await page.request.post(`/api/ai-generation-jobs/${failed.generationJobUuid}/execute`, {
    headers: { "If-Match": `W/"${failed.version}"` },
  });
  expect(retry.status()).toBe(409);
  const card = outputCard(page, outputs[0]);
  const review = page.waitForResponse((response) => response.url().endsWith(`/api/ai-generation-outputs/${outputs[0].generationOutputUuid}/approve`));
  await card.getByRole("button", { name: "Approve output" }).click();
  expect((await review).status()).toBe(200);
  expect(postgresScalar(`SELECT COUNT(*) FROM ai_generation_outputs WHERE generation_batch_uuid='${uuid(batch.generationBatchUuid)}'::uuid;`)).toBe("2");
});

test("approves preserved image pixels and rejects a changed-pixel blocked output", async ({ page }) => {
  const setup = await foundation(page.request);
  const preserved = await imageOutput(page.request, setup.product, setup.plan, setup.budget,
    await imageAsset(page.request, setup.product, SOURCE_HANDLE));
  const changed = await imageOutput(page.request, setup.product, setup.plan, setup.budget,
    await imageAsset(page.request, setup.product, CHANGED_SOURCE_HANDLE));
  expect(preserved).toMatchObject({ preservationStatus: "PASSED", reviewBlockers: [] });
  expect(changed.preservationStatus).toBe("BLOCKED");
  expect(changed.preservationDetails?.changedPixelCount).toBe(1);
  expect(changed.reviewBlockers).toContain("AI_PRODUCT_PIXELS_CHANGED");

  await openFactory(page, setup.product.productUuid);
  const preservedCard = outputCard(page, preserved);
  await expect(preservedCard).toContainText("Protected pixels: PASSED");
  await expect(preservedCard).toContainText("0 changed of 4 protected pixels");
  const approval = page.waitForResponse((response) => response.url().endsWith(`/api/ai-generation-outputs/${preserved.generationOutputUuid}/approve`));
  await preservedCard.getByRole("button", { name: "Approve output" }).click();
  expect((await approval).status()).toBe(200);

  const changedCard = outputCard(page, changed);
  await expect(changedCard).toContainText("Protected pixels: BLOCKED");
  await expect(changedCard).toContainText("1 changed of 4 protected pixels");
  await expect(changedCard.getByRole("button", { name: "Approve output" })).toBeDisabled();
  const directApproval = await page.request.post(`/api/ai-generation-outputs/${changed.generationOutputUuid}/approve`, {
    headers: { "Content-Type": "application/json", "If-Match": `W/"${changed.version}"` }, data: {},
  });
  expect(directApproval.status()).toBe(409);
  expect((await directApproval.json()).code).toBe("AI_REVIEW_BLOCKED");
  await changedCard.getByLabel("Rejection reason").fill("Protected Product pixels changed");
  const rejection = page.waitForResponse((response) => response.url().endsWith(`/api/ai-generation-outputs/${changed.generationOutputUuid}/reject`));
  await changedCard.getByRole("button", { name: "Reject output" }).click();
  expect((await rejection).status()).toBe(200);
  await expect(outputCard(page, changed)).toContainText("REJECTED");
});

test("archived Product blocks generation and pending approval without mutating the decision", async ({ page }) => {
  const setup = await foundation(page.request);
  const batch = await textBatch(page.request, setup.product, setup.plan, setup.budget, "LOW_COST", 1);
  const output = (await execute(page.request, batch.jobs[0])).output!;
  const productResponse = await page.request.get(`/api/products/${setup.product.productUuid}`);
  const archived = await page.request.delete(`/api/products/${setup.product.productUuid}`, {
    headers: { "If-Match": productResponse.headers().etag! },
  });
  expect(archived.status()).toBe(204);
  const generation = await page.request.post(`/api/products/${setup.product.productUuid}/ai-generation-batches`, {
    data: { creativePlanUuid: setup.plan.creativePlanUuid, templateKey: setup.budget.textTemplateKeys[0], modelProfile: "LOW_COST", variationCount: 1 },
  });
  expect(generation.status()).toBe(409);
  expect((await generation.json()).code).toBe("PRODUCT_ARCHIVED");
  const approval = await page.request.post(`/api/ai-generation-outputs/${output.generationOutputUuid}/approve`, {
    headers: { "Content-Type": "application/json", "If-Match": `W/"${output.version}"` }, data: {},
  });
  expect(approval.status()).toBe(409);
  expect((await approval.json()).code).toBe("AI_REVIEW_BLOCKED");
  await openFactory(page, setup.product.productUuid);
  await expect(page.getByText("Archived Products cannot start AI generation.")).toBeVisible();
  await expect(outputCard(page, output).getByRole("button", { name: "Approve output" })).toBeDisabled();
  expect(postgresScalar(`SELECT COUNT(*) FROM ai_review_decisions WHERE generation_output_uuid='${uuid(output.generationOutputUuid)}'::uuid;`)).toBe("0");
  expect(postgresScalar(`SELECT review_status FROM ai_generation_outputs WHERE generation_output_uuid='${uuid(output.generationOutputUuid)}'::uuid;`)).toBe("PENDING_REVIEW");
});
