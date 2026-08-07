import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

type Product = { productUuid: string; version: number };
type Quality = {
  productMasterScore: number;
  productKnowledgeScore: number;
  creativePlanScore: number;
  assetMetadataScore: number;
  campaignReadinessScore: number;
  systemScore: number;
  manualAdjustment: number;
  finalScore: number;
  readinessStatus: "DRAFT" | "NEEDS_REVIEW" | "READY";
  blockers: Array<{ code: string }>;
  version: number;
};

const unique = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;

async function createCompleteProduct(request: APIRequestContext): Promise<Product> {
  const response = await request.post("/api/products", {
    data: {
      sku: unique("QUALITY-SKU"),
      productName: unique("Quality Product"),
      brand: "Quality Brand",
      category: "Quality Category",
      subcategory: "Quality Subcategory",
      shortDescription: "A complete Product Master record for deterministic Quality verification.",
      cost: "60.0000",
      salePrice: "100.0000",
      currency: "TWD",
      stock: "50",
      productUrl: "https://example.com/products/quality",
    },
  });
  expect(response.status()).toBe(201);
  return response.json() as Promise<Product>;
}

async function quality(request: APIRequestContext, productUuid: string) {
  const response = await request.get(`/api/products/${productUuid}/quality`);
  expect(response.status()).toBe(200);
  expect(response.headers().etag).toBeTruthy();
  return { body: (await response.json()) as Quality, etag: response.headers().etag! };
}

async function createCompleteKnowledge(request: APIRequestContext, productUuid: string) {
  for (const knowledgeType of ["FEATURE", "BENEFIT", "AUDIENCE", "PROOF"]) {
    const response = await request.post(`/api/products/${productUuid}/knowledge`, {
      data: {
        knowledgeType,
        title: `${knowledgeType} title`,
        content: `${knowledgeType} content provides complete deterministic evidence.`,
        source: "Quality E2E",
      },
    });
    expect(response.status()).toBe(201);
  }
}

async function createCompleteCreativePlan(request: APIRequestContext, productUuid: string) {
  const response = await request.post(`/api/products/${productUuid}/creative-plans`, {
    data: {
      planName: unique("Quality Plan"),
      primaryAudience: "Quality audience",
      secondaryAudience: null,
      painPoint: "Quality pain point",
      coreBenefit: "Quality core benefit",
      creativeAngle: "Quality creative angle",
      emotionalDirection: null,
      brandTone: "Confident",
      visualStyle: "Editorial",
      mainColor: null,
      characterSetting: null,
      cta: "Learn more",
    },
  });
  expect(response.status()).toBe(201);
}

async function createCompleteCampaignAssociation(request: APIRequestContext, productUuid: string) {
  const campaignResponse = await request.post("/api/campaigns", {
    data: {
      campaignName: unique("Quality Campaign"),
      activityType: null,
      startDate: null,
      endDate: null,
      objective: "Quality readiness objective",
      platform: null,
      budgetDaily: null,
      budgetTotal: "1000.0000",
      currency: "TWD",
      promotion: null,
      landingPage: "https://example.com/quality-campaign",
    },
  });
  expect(campaignResponse.status()).toBe(201);
  const campaign = (await campaignResponse.json()) as { campaignUuid: string };
  const association = await request.post(`/api/campaigns/${campaign.campaignUuid}/products`, {
    data: { productUuid, role: "PRIMARY", priority: 1, budgetWeight: "100.00" },
  });
  expect(association.status()).toBe(201);
}

async function createCompleteImage(request: APIRequestContext, productUuid: string) {
  const response = await request.post(`/api/products/${productUuid}/assets`, {
    data: {
      creativePlanUuid: null,
      campaignUuid: null,
      assetType: "IMAGE",
      purpose: "Quality product image",
      storageProvider: "LOCAL_TEST",
      providerFileId: unique("quality-image"),
      fileUrl: null,
      mediaType: "image/png",
      originalFilename: "quality-product.png",
      sizeBytes: 1024,
      checksumSha256: null,
      providerMetadata: null,
    },
  });
  expect(response.status()).toBe(201);
}

async function openQuality(page: Page, productUuid: string) {
  await page.goto(`/products/${productUuid}?tab=quality`);
  await expect(page.getByRole("heading", { name: "Quality Score" })).toBeVisible();
}

test("progresses deterministically and a blocker prevents Ready even when adjustment clamps at 100", async ({ page }) => {
  const product = await createCompleteProduct(page.request);
  expect((await quality(page.request, product.productUuid)).body).toMatchObject({
    productMasterScore: 35,
    productKnowledgeScore: 0,
    creativePlanScore: 0,
    assetMetadataScore: 0,
    campaignReadinessScore: 0,
    systemScore: 35,
    readinessStatus: "DRAFT",
  });

  await createCompleteKnowledge(page.request, product.productUuid);
  expect((await quality(page.request, product.productUuid)).body).toMatchObject({ productKnowledgeScore: 25, systemScore: 60 });

  await createCompleteCreativePlan(page.request, product.productUuid);
  expect((await quality(page.request, product.productUuid)).body).toMatchObject({ creativePlanScore: 25, systemScore: 85 });

  await createCompleteCampaignAssociation(page.request, product.productUuid);
  const blocked = (await quality(page.request, product.productUuid)).body;
  expect(blocked).toMatchObject({ campaignReadinessScore: 5, systemScore: 90, readinessStatus: "NEEDS_REVIEW" });
  expect(blocked.blockers.map((value) => value.code)).toEqual(["IMAGE_ASSET_MISSING"]);

  await openQuality(page, product.productUuid);
  const adjustment = page.locator("form.quality-adjustment input[type=number]");
  await adjustment.fill("5");
  await page.locator("form.quality-adjustment button.primary-button").click();
  await expect(page.locator(".quality-tab .state-card[role=alert]")).toContainText("理由");

  await adjustment.fill("20");
  await page.locator("form.quality-adjustment textarea").fill("Approved Quality E2E adjustment");
  const adjustmentResponse = page.waitForResponse((response) =>
    response.url().endsWith(`/api/products/${product.productUuid}/quality/manual-adjustment`)
      && response.request().method() === "PATCH",
  );
  await page.locator("form.quality-adjustment button.primary-button").click();
  expect((await adjustmentResponse).status()).toBe(200);
  await expect(page.locator(".quality-total")).toContainText("100");
  await expect(page.locator(".readiness-badge")).toHaveText("Needs review");
  await expect(page.getByText("IMAGE_ASSET_MISSING", { exact: true })).toBeVisible();

  await page.reload();
  await expect(page.locator("form.quality-adjustment input[type=number]")).toHaveValue("20");
  await expect(page.locator("form.quality-adjustment textarea")).toHaveValue("Approved Quality E2E adjustment");

  await createCompleteImage(page.request, product.productUuid);
  await page.reload();
  await expect(page.locator(".quality-total")).toContainText("100");
  await expect(page.locator(".readiness-badge")).toHaveText("Ready");
  await expect(page.getByText("IMAGE_ASSET_MISSING", { exact: true })).toHaveCount(0);
  expect((await quality(page.request, product.productUuid)).body).toMatchObject({
    assetMetadataScore: 10,
    systemScore: 100,
    manualAdjustment: 20,
    finalScore: 100,
    readinessStatus: "READY",
  });
});

test("rejects a stale manual-adjustment ETag and reloads the persisted winner", async ({ context, page }) => {
  const product = await createCompleteProduct(page.request);
  const first = await context.newPage();
  const second = await context.newPage();
  await Promise.all([openQuality(first, product.productUuid), openQuality(second, product.productUuid)]);

  await first.locator("form.quality-adjustment input[type=number]").fill("3");
  await first.locator("form.quality-adjustment textarea").fill("First reviewer wins");
  const winner = first.waitForResponse((response) => response.url().includes("/quality/manual-adjustment") && response.request().method() === "PATCH");
  await first.locator("form.quality-adjustment button.primary-button").click();
  expect((await winner).status()).toBe(200);

  await second.locator("form.quality-adjustment input[type=number]").fill("4");
  await second.locator("form.quality-adjustment textarea").fill("Stale reviewer loses");
  const stale = second.waitForResponse((response) => response.url().includes("/quality/manual-adjustment") && response.request().method() === "PATCH");
  await second.locator("form.quality-adjustment button.primary-button").click();
  expect((await stale).status()).toBe(412);
  const alert = second.locator(".quality-tab .state-card[role=alert]");
  await expect(alert).toContainText("Quality score");
  await alert.getByRole("button").click();
  await expect(second.locator("form.quality-adjustment input[type=number]")).toHaveValue("3");
  await expect(second.locator("form.quality-adjustment textarea")).toHaveValue("First reviewer wins");
});

test("becomes read-only after archive and permits adjustment again after restore", async ({ page }) => {
  const product = await createCompleteProduct(page.request);
  await openQuality(page, product.productUuid);

  const archive = await page.request.delete(`/api/products/${product.productUuid}`, {
    headers: { "If-Match": `W/\"${product.version}\"` },
  });
  expect(archive.status()).toBe(204);
  expect(archive.headers().etag).toBeTruthy();

  await page.locator("form.quality-adjustment input[type=number]").fill("1");
  await page.locator("form.quality-adjustment textarea").fill("Must be blocked while archived");
  const blocked = page.waitForResponse((response) => response.url().includes("/quality/manual-adjustment") && response.request().method() === "PATCH");
  await page.locator("form.quality-adjustment button.primary-button").click();
  expect((await blocked).status()).toBe(409);
  const alert = page.getByRole("alert");
  await alert.getByRole("button").click();
  await expect(page.getByText("PRODUCT_ARCHIVED", { exact: true })).toBeVisible();
  await expect(page.locator("form.quality-adjustment")).toHaveCount(0);

  const restore = await page.request.post(`/api/products/${product.productUuid}/restore`, {
    headers: { "If-Match": archive.headers().etag! },
  });
  expect(restore.status()).toBe(200);
  await page.reload();
  await expect(page.getByText("PRODUCT_ARCHIVED", { exact: true })).toHaveCount(0);
  await expect(page.locator("form.quality-adjustment")).toBeVisible();
});
