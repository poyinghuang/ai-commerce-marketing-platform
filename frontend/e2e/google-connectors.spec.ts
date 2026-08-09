import { expect, test } from "@playwright/test";

type Product = { productUuid: string; productId: string; productName: string; version: number };
type ImportRow = {
  plannedAction: "CREATE" | "UPDATE" | "INVALID";
  executionStatus: "PENDING" | "SUCCEEDED" | "FAILED" | "SKIPPED";
  resultProductUuid: string | null;
  validationErrors: Array<{ field: string; code: string }>;
};
type SheetImport = {
  importJobUuid: string;
  status: "PREVIEWED" | "COMPLETED_WITH_ERRORS";
  totalRows: number;
  validRows: number;
  invalidRows: number;
  createdCount: number;
  updatedCount: number;
  failedCount: number;
  rows: ImportRow[];
};
type StorageFolder = { productFolderId: string; subfolders: Record<string, string> };

test("previews and executes mixed Sheet rows, recalculates Products, and reuses Drive folders", async ({ page }) => {
  const existingResponse = await page.request.post("/api/products", {
    data: { productName: "Connector Existing Product" },
  });
  expect(existingResponse.status()).toBe(201);
  const existing = (await existingResponse.json()) as Product;

  const template = await page.request.get("/api/connectors/google-sheets/template");
  expect(template.status()).toBe(200);
  expect(template.headers()["content-disposition"]).toContain("product-import-template.csv");

  await page.goto("/connectors/google-sheets");
  await page.getByLabel("Spreadsheet ID").fill(`stub-products-mixed_${existing.productId}`);
  const previewResponsePromise = page.waitForResponse((response) =>
    response.url().endsWith("/api/connectors/google-sheets/imports/preview")
      && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Preview import" }).click();
  const previewResponse = await previewResponsePromise;
  expect(previewResponse.status()).toBe(201);
  const preview = (await previewResponse.json()) as SheetImport;
  expect(preview).toMatchObject({ status: "PREVIEWED", totalRows: 3, validRows: 2, invalidRows: 1 });
  expect(preview.rows.map((row) => row.plannedAction)).toEqual(["CREATE", "UPDATE", "INVALID"]);
  expect(preview.rows[2].validationErrors).toContainEqual(expect.objectContaining({ field: "productName" }));
  await expect(page.locator(".connector-results tbody tr")).toHaveCount(3);
  await expect(page.getByText("productName: productName is required", { exact: true })).toBeVisible();

  const executeResponsePromise = page.waitForResponse((response) =>
    response.url().endsWith(`/api/connectors/google-sheets/imports/${preview.importJobUuid}/execute`)
      && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Execute valid rows" }).click();
  const executeResponse = await executeResponsePromise;
  expect(executeResponse.status()).toBe(200);
  const executed = (await executeResponse.json()) as SheetImport;
  expect(executed).toMatchObject({
    status: "COMPLETED_WITH_ERRORS", createdCount: 1, updatedCount: 1, failedCount: 0,
  });
  expect(executed.rows.map((row) => row.executionStatus)).toEqual(["SUCCEEDED", "SUCCEEDED", "SKIPPED"]);
  await expect(page.getByText("COMPLETED_WITH_ERRORS", { exact: true })).toBeVisible();

  const persistedResponse = await page.request.get(`/api/connectors/google-sheets/imports/${preview.importJobUuid}`);
  expect(persistedResponse.status()).toBe(200);
  expect(await persistedResponse.json()).toMatchObject({
    importJobUuid: executed.importJobUuid,
    status: executed.status,
    totalRows: executed.totalRows,
    validRows: executed.validRows,
    invalidRows: executed.invalidRows,
    createdCount: executed.createdCount,
    updatedCount: executed.updatedCount,
    failedCount: executed.failedCount,
    rows: executed.rows,
  });

  const updatedResponse = await page.request.get(`/api/products/${existing.productUuid}`);
  expect(updatedResponse.status()).toBe(200);
  expect(await updatedResponse.json()).toMatchObject({
    productName: "Stub Updated Product", sku: "STUB-UPDATE-001", brand: "Updated Brand",
  });
  const createdUuid = executed.rows.find((row) => row.plannedAction === "CREATE")?.resultProductUuid;
  expect(createdUuid).toBeTruthy();

  for (const [productUuid, expectedName] of [
    [existing.productUuid, "Stub Updated Product"],
    [createdUuid!, "Stub Created Product"],
  ]) {
    const aggregate = await page.request.get(`/api/products/${productUuid}/aggregate`);
    expect(aggregate.status()).toBe(200);
    expect(await aggregate.json()).toMatchObject({
      product: { productName: expectedName },
      quality: { productMasterScore: 35 },
    });
    const quality = await page.request.get(`/api/products/${productUuid}/quality`);
    expect(quality.status()).toBe(200);
    expect(await quality.json()).toMatchObject({ productMasterScore: 35 });
  }

  await page.goto(`/products/${existing.productUuid}?tab=assets`);
  const createFolderResponsePromise = page.waitForResponse((response) =>
    response.url().endsWith(`/api/products/${existing.productUuid}/storage-folder`)
      && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Create folder structure" }).click();
  const createFolderResponse = await createFolderResponsePromise;
  expect(createFolderResponse.status()).toBe(201);
  const createdFolder = (await createFolderResponse.json()) as StorageFolder;
  await expect(page.locator(".storage-folder-panel").getByText("Connected")).toBeVisible();
  await expect(page.locator(".storage-folder-panel li")).toHaveCount(6);

  const repeatFolderResponse = await page.request.post(`/api/products/${existing.productUuid}/storage-folder`);
  expect(repeatFolderResponse.status()).toBe(200);
  expect(await repeatFolderResponse.json()).toMatchObject({ productFolderId: createdFolder.productFolderId });
  await page.reload();
  await expect(page.locator(".storage-folder-panel code").filter({ hasText: createdFolder.productFolderId })).toBeVisible();
  await expect(page.locator(".storage-folder-panel li")).toHaveCount(6);
});
