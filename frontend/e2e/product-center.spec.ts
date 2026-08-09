import { execFileSync } from "node:child_process";
import { resolve } from "node:path";
import { expect, test, type Page } from "@playwright/test";

type Product = { productUuid: string; productName: string; version: number; lifecycleStatus: string };
type Knowledge = { knowledgeUuid: string; title: string; content: string; version: number; lifecycleStatus: string };
type CreativePlan = { creativePlanUuid: string; planName: string };
type Campaign = { campaignUuid: string; campaignName: string };
type Asset = { assetUuid: string; originalFilename: string; version: number };

const unique = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;

async function createProduct(page: Page, name = unique("E2E Product")): Promise<Product> {
  await page.goto("/products/new");
  const form = page.locator("form.product-form");
  await form.locator("input").nth(0).fill(name);
  await form.locator("input").nth(1).fill(unique("SKU"));
  const responsePromise = page.waitForResponse(
    (response) => response.url().endsWith("/api/products") && response.request().method() === "POST",
  );
  await form.locator("button.primary-button").click();
  const response = await responsePromise;
  expect(response.status()).toBe(201);
  const product = (await response.json()) as Product;
  await page.waitForURL(new RegExp(`/products/${product.productUuid}(?:\\?.*)?$`));
  await expect(page.getByRole("heading", { name })).toBeVisible();
  return product;
}

async function createKnowledge(page: Page, productUuid: string, title = unique("Knowledge")): Promise<Knowledge> {
  await page.goto(`/products/${productUuid}?tab=knowledge`);
  await page.getByRole("button", { name: "Add knowledge" }).click();
  await page.getByRole("textbox", { name: "Title", exact: true }).fill(title);
  await page.getByRole("textbox", { name: "Content", exact: true }).fill(`${title} content`);
  const responsePromise = page.waitForResponse(
    (response) => response.url().endsWith(`/api/products/${productUuid}/knowledge`) && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Save knowledge" }).click();
  const response = await responsePromise;
  expect(response.status()).toBe(201);
  const knowledge = (await response.json()) as Knowledge;
  await expect(page.getByRole("heading", { name: title })).toBeVisible();
  return knowledge;
}

async function createCreativePlan(page: Page, productUuid: string, name = unique("Plan")): Promise<CreativePlan> {
  await page.goto(`/products/${productUuid}?tab=creative-plans`);
  const form = page.locator("form.creative-form");
  await form.locator("input[required]").fill(name);
  const responsePromise = page.waitForResponse(
    (response) => response.url().endsWith(`/api/products/${productUuid}/creative-plans`) && response.request().method() === "POST",
  );
  await form.locator("button.primary-button").click();
  const response = await responsePromise;
  expect(response.status()).toBe(201);
  const plan = (await response.json()) as CreativePlan;
  await expect(page.getByText(name, { exact: true })).toBeVisible();
  return plan;
}

async function createCampaign(page: Page, name = unique("Campaign")): Promise<Campaign> {
  await page.goto("/campaigns/new");
  const form = page.locator("form.product-form");
  await form.locator("input[required]").fill(name);
  const responsePromise = page.waitForResponse(
    (response) => response.url().endsWith("/api/campaigns") && response.request().method() === "POST",
  );
  await form.locator("button.primary-button").click();
  const response = await responsePromise;
  expect(response.status()).toBe(201);
  const campaign = (await response.json()) as Campaign;
  await page.waitForURL(new RegExp(`/campaigns/${campaign.campaignUuid}$`));
  return campaign;
}

async function associateCampaign(page: Page, productUuid: string, campaign: Campaign): Promise<void> {
  await page.goto(`/products/${productUuid}?tab=campaigns`);
  const form = page.locator("form.creative-form");
  await page.getByLabel("Campaign UUID").fill(campaign.campaignUuid);
  const responsePromise = page.waitForResponse(
    (response) => response.url().endsWith(`/api/campaigns/${campaign.campaignUuid}/products`) && response.request().method() === "POST",
  );
  await form.locator("button.primary-button").click();
  expect((await responsePromise).status()).toBe(201);
  await expect(page.getByText(campaign.campaignName, { exact: true })).toBeVisible();
}

async function createAsset(page: Page, productUuid: string, filename = unique("asset") + ".png"): Promise<Asset> {
  await page.goto(`/products/${productUuid}?tab=assets`);
  const form = page.locator("form.creative-form");
  await page.getByLabel("purpose").fill("E2E product image");
  await page.getByLabel("storageProvider").fill("LOCAL_TEST");
  await page.getByLabel("providerFileId").fill(unique("file"));
  await page.getByLabel("originalFilename").fill(filename);
  const responsePromise = page.waitForResponse(
    (response) => response.url().endsWith(`/api/products/${productUuid}/assets`) && response.request().method() === "POST",
  );
  await form.locator("button.primary-button").click();
  const response = await responsePromise;
  expect(response.status()).toBe(201);
  const asset = (await response.json()) as Asset;
  await expect(page.getByText(filename, { exact: true })).toBeVisible();
  return asset;
}

async function getKnowledge(page: Page, productUuid: string, knowledgeUuid: string) {
  const response = await page.request.get(`/api/products/${productUuid}/knowledge/${knowledgeUuid}`);
  expect(response.ok()).toBeTruthy();
  const etag = response.headers().etag;
  expect(etag).toBeTruthy();
  return { response, etag, body: (await response.json()) as Knowledge };
}

function auditActions(knowledgeUuid: string): Record<string, number> {
  expect(process.env.PLAYWRIGHT_AUDIT_DB_ASSERTION).toBe("1");
  expect(knowledgeUuid).toMatch(/^[0-9a-f-]{36}$/i);
  const composeFile = resolve(process.cwd(), "../docker-compose.yml");
  const composeProjectDirectory = resolve(process.cwd(), "..");
  const sql = [
    "SELECT action || ':' || COUNT(*)",
    "FROM audit_logs",
    "WHERE entity_type = 'PRODUCT_KNOWLEDGE'",
    `AND entity_uuid = '${knowledgeUuid}'::uuid`,
    "GROUP BY action ORDER BY action;",
  ].join(" ");
  const postgresCommand = [
    ...(process.env.PLAYWRIGHT_DOCKER_HOST
      ? ["--host", process.env.PLAYWRIGHT_DOCKER_HOST]
      : []),
    ...(process.env.PLAYWRIGHT_POSTGRES_CONTAINER
      ? ["exec", process.env.PLAYWRIGHT_POSTGRES_CONTAINER]
      : [
          "compose",
          "--project-directory",
          composeProjectDirectory,
          ...(process.env.PLAYWRIGHT_COMPOSE_PROJECT_NAME
            ? ["-p", process.env.PLAYWRIGHT_COMPOSE_PROJECT_NAME]
            : []),
          "-f",
          composeFile,
          "exec",
          "-T",
          "postgres",
        ]),
    "psql",
    "-U",
    "ai_commerce",
    "-d",
    "ai_commerce",
    "-tA",
    "-c",
    sql,
  ];
  let output = "";
  let lastError: unknown;
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    try {
      output = execFileSync(process.platform === "win32" ? "docker.exe" : "docker", postgresCommand, { encoding: "utf8" });
      lastError = undefined;
      break;
    } catch (error) {
      lastError = error;
      if (attempt < 3) Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 500);
    }
  }
  if (lastError) throw lastError;
  return Object.fromEntries(output.trim().split(/\r?\n/).filter(Boolean).map((line) => {
    const [action, count] = line.split(":");
    return [action, Number(count)];
  }));
}

test("creates the complete Product graph and renders Aggregate and tabs", async ({ page }) => {
  const product = await createProduct(page);
  const knowledge = await createKnowledge(page, product.productUuid);
  const plan = await createCreativePlan(page, product.productUuid);
  const campaign = await createCampaign(page);
  await associateCampaign(page, product.productUuid, campaign);
  const asset = await createAsset(page, product.productUuid);

  await page.goto(`/products/${product.productUuid}`);
  const aggregate = page.locator('section[aria-labelledby="aggregate-summary-heading"]');
  await expect(aggregate.getByText(knowledge.title, { exact: true })).toBeVisible();
  await expect(aggregate.getByText(plan.planName, { exact: true })).toBeVisible();
  await expect(aggregate.getByText(campaign.campaignName, { exact: true })).toBeVisible();
  await expect(aggregate.getByText(asset.originalFilename, { exact: true })).toBeVisible();

  await page.goto(`/products/${product.productUuid}?tab=knowledge`);
  await expect(page.getByRole("heading", { name: knowledge.title })).toBeVisible();
  await page.goto(`/products/${product.productUuid}?tab=creative-plans`);
  await expect(page.getByText(plan.planName, { exact: true })).toBeVisible();
  await page.goto(`/products/${product.productUuid}?tab=campaigns`);
  await expect(page.getByText(campaign.campaignName, { exact: true })).toBeVisible();
  await page.goto(`/products/${product.productUuid}?tab=assets`);
  await expect(page.getByText(asset.originalFilename, { exact: true })).toBeVisible();
});

test("shows a stale Knowledge conflict and reloads the latest value", async ({ context, page }) => {
  const product = await createProduct(page);
  const knowledge = await createKnowledge(page, product.productUuid);
  const first = await context.newPage();
  const second = await context.newPage();
  const url = `/products/${product.productUuid}?tab=knowledge`;
  await Promise.all([first.goto(url), second.goto(url)]);

  for (const editor of [first, second]) {
    const card = editor.locator("article.knowledge-card").filter({ hasText: knowledge.title });
    await card.getByRole("button", { name: "Edit" }).click();
    await expect(editor.getByRole("textbox", { name: "Content", exact: true })).toHaveValue(knowledge.content);
  }

  const latestContent = unique("latest content");
  await first.getByRole("textbox", { name: "Content", exact: true }).fill(latestContent);
  const firstPatch = first.waitForResponse((response) => response.url().includes(`/knowledge/${knowledge.knowledgeUuid}`) && response.request().method() === "PATCH");
  await first.getByRole("button", { name: "Save knowledge" }).click();
  expect((await firstPatch).status()).toBe(200);

  await second.getByRole("textbox", { name: "Content", exact: true }).fill(unique("stale content"));
  const stalePatch = second.waitForResponse((response) => response.url().includes(`/knowledge/${knowledge.knowledgeUuid}`) && response.request().method() === "PATCH");
  await second.getByRole("button", { name: "Save knowledge" }).click();
  expect((await stalePatch).status()).toBe(412);
  const alert = second.getByRole("alert").filter({ hasText: "Knowledge changed" });
  await expect(alert).toContainText("Knowledge changed");
  await alert.getByRole("button", { name: "Reload latest product" }).click();
  await expect(second.getByText(latestContent, { exact: true })).toBeVisible();
});

test("makes child tabs read-only while a Product is archived and restores mutation", async ({ page }) => {
  const product = await createProduct(page);
  const knowledge = await createKnowledge(page, product.productUuid);
  await page.goto(`/products/${product.productUuid}`);
  const lifecycleButton = page.locator("section.content-card").first().locator("button").first();
  const archiveResponse = page.waitForResponse((response) => response.url().endsWith(`/api/products/${product.productUuid}`) && response.request().method() === "DELETE");
  await lifecycleButton.click();
  expect((await archiveResponse).status()).toBe(204);

  await page.goto(`/products/${product.productUuid}?tab=knowledge`);
  await expect(page.getByText(/Knowledge remains readable/)).toBeVisible();
  await expect(page.locator("form.knowledge-form")).toHaveCount(0);
  await page.goto(`/products/${product.productUuid}?tab=creative-plans`);
  await expect(page.locator("form.creative-form")).toHaveCount(0);
  await page.goto(`/products/${product.productUuid}?tab=campaigns`);
  await expect(page.locator("form.creative-form")).toHaveCount(0);
  await page.goto(`/products/${product.productUuid}?tab=assets`);
  await expect(page.getByText(/Asset metadata is read-only/)).toBeVisible();
  await expect(page.locator("form.creative-form")).toHaveCount(0);

  const blocked = await page.request.patch(`/api/products/${product.productUuid}/knowledge/${knowledge.knowledgeUuid}`, {
    headers: { "Content-Type": "application/merge-patch+json", "If-Match": 'W/"0"' },
    data: { content: "must remain blocked" },
  });
  expect(blocked.status()).toBe(409);
  expect((await blocked.json()).code).toBe("PRODUCT_ARCHIVED");

  await page.goto(`/products/${product.productUuid}`);
  const restoreResponse = page.waitForResponse((response) => response.url().endsWith(`/api/products/${product.productUuid}/restore`) && response.request().method() === "POST");
  await page.locator("section.content-card").first().locator("button").first().click();
  expect((await restoreResponse).status()).toBe(200);
  await page.goto(`/products/${product.productUuid}?tab=knowledge`);
  await expect(page.getByRole("button", { name: "Add knowledge" })).toBeVisible();
});

test("archives and restores Knowledge without duplicate no-op Audit events", async ({ page }) => {
  const product = await createProduct(page);
  const knowledge = await createKnowledge(page, product.productUuid);
  const card = page.locator("article.knowledge-card").filter({ hasText: knowledge.title });
  const archiveResponse = page.waitForResponse((response) => response.url().includes(`/knowledge/${knowledge.knowledgeUuid}`) && response.request().method() === "DELETE");
  await card.getByRole("button", { name: "Archive" }).click();
  expect((await archiveResponse).status()).toBe(204);
  await expect(page.getByText("No knowledge entries.")).toBeVisible();

  const archived = await getKnowledge(page, product.productUuid, knowledge.knowledgeUuid);
  expect(archived.body.lifecycleStatus).toBe("ARCHIVED");
  expect(archived.body.version).toBe(1);
  const archiveNoOp = await page.request.delete(`/api/products/${product.productUuid}/knowledge/${knowledge.knowledgeUuid}`, {
    headers: { "If-Match": archived.etag },
  });
  expect(archiveNoOp.status()).toBe(204);
  expect(archiveNoOp.headers().etag).toBe(archived.etag);

  await page.locator(".knowledge-filters select").first().selectOption("ARCHIVED");
  const archivedCard = page.locator("article.knowledge-card").filter({ hasText: knowledge.title });
  const restoreResponse = page.waitForResponse((response) => response.url().endsWith(`/knowledge/${knowledge.knowledgeUuid}/restore`) && response.request().method() === "POST");
  await archivedCard.getByRole("button", { name: "Restore" }).click();
  expect((await restoreResponse).status()).toBe(200);

  const restored = await getKnowledge(page, product.productUuid, knowledge.knowledgeUuid);
  expect(restored.body.lifecycleStatus).toBe("ACTIVE");
  expect(restored.body.version).toBe(2);
  const restoreNoOp = await page.request.post(`/api/products/${product.productUuid}/knowledge/${knowledge.knowledgeUuid}/restore`, {
    headers: { "If-Match": restored.etag },
  });
  expect(restoreNoOp.status()).toBe(200);
  expect((await restoreNoOp.json()).version).toBe(2);
  expect(restoreNoOp.headers().etag).toBe(restored.etag);

  await page.locator(".knowledge-filters select").first().selectOption("ACTIVE");
  await expect(page.getByRole("heading", { name: knowledge.title })).toBeVisible();
  expect(auditActions(knowledge.knowledgeUuid)).toEqual({ ARCHIVE: 1, CREATE: 1, RESTORE: 1 });
});
